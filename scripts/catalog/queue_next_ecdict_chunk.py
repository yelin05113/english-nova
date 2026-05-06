#!/usr/bin/env python3
"""Create the next unfinished external ECDICT import job through the gateway API."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MANIFEST = ROOT / ".local" / "public-catalog" / "manifest.json"
DEFAULT_GATEWAY_BASE_URL = "http://localhost:8087"
DEFAULT_MYSQL_CONTAINER = "english-nova-mysql-1"
DEFAULT_MYSQL_DATABASE = "english_nova"
DEFAULT_MYSQL_USER = "root"
DEFAULT_MYSQL_PASSWORD = "root"
DEFAULT_BATCH_SIZE = 150
DEFAULT_INSERT_BATCH_SIZE = 500


@dataclass(frozen=True)
class ManifestSource:
    name: str
    file: str
    word_count: int
    sequence: int


@dataclass(frozen=True)
class JobRow:
    job_id: int
    source_name: str
    status: str
    total_words: int
    processed_words: int
    failed_words: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--gateway-base-url", default=DEFAULT_GATEWAY_BASE_URL)
    parser.add_argument("--token", help="Bearer token for gateway requests.")
    parser.add_argument("--account", help="Login account when token is omitted.")
    parser.add_argument("--password", help="Login password when token is omitted.")
    parser.add_argument("--mysql-container", default=DEFAULT_MYSQL_CONTAINER)
    parser.add_argument("--mysql-database", default=DEFAULT_MYSQL_DATABASE)
    parser.add_argument("--mysql-user", default=DEFAULT_MYSQL_USER)
    parser.add_argument("--mysql-password", default=DEFAULT_MYSQL_PASSWORD)
    parser.add_argument("--batch-size", type=int, default=DEFAULT_BATCH_SIZE)
    parser.add_argument("--count", type=int, default=1, help="How many sequential chunk jobs to queue.")
    parser.add_argument("--created-by-user-id", type=int, help="Override created_by_user_id for direct DB queueing.")
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args()


def load_manifest(path: Path) -> list[ManifestSource]:
    if not path.is_file():
        raise SystemExit(f"manifest not found: {path}")
    payload = json.loads(path.read_text(encoding="utf-8"))
    source_nodes = payload.get("sources") or []
    sources = [
        ManifestSource(
            name=str(node["name"]).strip().lower(),
            file=str(node["file"]).strip(),
            word_count=int(node.get("wordCount", 0) or 0),
            sequence=int(node.get("sequence", index)),
        )
        for index, node in enumerate(source_nodes, start=1)
    ]
    if not sources:
        raise SystemExit(f"manifest has no sources: {path}")
    return sorted(sources, key=lambda item: (item.sequence, item.name))


def run_mysql_query(args: argparse.Namespace, sql: str) -> list[str]:
    command = [
        "docker",
        "exec",
        args.mysql_container,
        "mysql",
        f"-u{args.mysql_user}",
        f"-p{args.mysql_password}",
        args.mysql_database,
        "-N",
        "-B",
        "-e",
        sql,
    ]
    completed = subprocess.run(command, capture_output=True, text=True, check=False)
    if completed.returncode != 0:
        message = completed.stderr.strip() or completed.stdout.strip() or "mysql query failed"
        raise SystemExit(message)
    return [line for line in completed.stdout.splitlines() if line.strip()]


def sql_quote(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"


def load_latest_jobs(args: argparse.Namespace, sources: list[ManifestSource]) -> dict[str, JobRow]:
    source_names = ", ".join(sql_quote(source.name) for source in sources)
    rows = run_mysql_query(
        args,
        "SELECT id, source_name, status, total_words, processed_words, failed_words "
        "FROM public_catalog_import_jobs "
        f"WHERE source_name IN ({source_names}) "
        "ORDER BY id DESC",
    )
    latest_by_source: dict[str, JobRow] = {}
    for row in rows:
        columns = row.split("\t")
        if len(columns) < 6:
            continue
        source_name = columns[1].strip().lower()
        if source_name in latest_by_source:
            continue
        latest_by_source[source_name] = JobRow(
            job_id=int(columns[0]),
            source_name=source_name,
            status=columns[2].strip().upper(),
            total_words=int(columns[3] or 0),
            processed_words=int(columns[4] or 0),
            failed_words=int(columns[5] or 0),
        )
    return latest_by_source


def load_latest_creator_user_id(args: argparse.Namespace) -> int | None:
    rows = run_mysql_query(
        args,
        "SELECT created_by_user_id "
        "FROM public_catalog_import_jobs "
        "WHERE created_by_user_id IS NOT NULL "
        "ORDER BY id DESC LIMIT 1",
    )
    if not rows:
        return None
    try:
        return int(rows[0].strip())
    except ValueError:
        return None


def resolve_bearer_token(args: argparse.Namespace) -> str:
    if args.token:
        return args.token.strip()
    if not args.account or not args.password:
        raise SystemExit("provide --token or --account/--password")
    payload = api_request(
        args.gateway_base_url.rstrip("/") + "/auth/login",
        {"account": args.account, "password": args.password},
        token=None,
    )
    token = ((payload.get("data") or {}).get("accessToken") or "").strip()
    if not token:
        raise SystemExit("login succeeded but no accessToken was returned")
    return token


def api_request(url: str, payload: dict[str, object], token: str | None) -> dict[str, object]:
    body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(url, data=body, method="POST")
    request.add_header("Content-Type", "application/json")
    request.add_header("Accept", "application/json")
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        content = error.read().decode("utf-8", errors="replace")
        raise SystemExit(f"{url} -> HTTP {error.code}: {content}") from error


def pick_next_source(sources: list[ManifestSource], latest_jobs: dict[str, JobRow]) -> tuple[ManifestSource | None, JobRow | None]:
    for source in sources:
        latest = latest_jobs.get(source.name)
        if latest and latest.status == "COMPLETED":
            continue
        return source, latest
    return None, None


def pick_next_sources(
    sources: list[ManifestSource],
    latest_jobs: dict[str, JobRow],
    count: int,
) -> tuple[list[ManifestSource], JobRow | None]:
    selected: list[ManifestSource] = []
    first_existing_job: JobRow | None = None
    for source in sources:
        latest = latest_jobs.get(source.name)
        if latest and latest.status in {"PENDING", "RUNNING"}:
            return [], latest
        if latest and latest.status == "COMPLETED":
            continue
        if first_existing_job is None:
            first_existing_job = latest
        selected.append(source)
        if len(selected) >= count:
            break
    return selected, first_existing_job


def load_source_words(manifest_path: Path, source: ManifestSource) -> list[str]:
    source_path = manifest_path.parent / source.file
    if not source_path.is_file():
        raise SystemExit(f"catalog source file not found: {source_path}")
    words: list[str] = []
    with source_path.open("r", encoding="utf-8", newline="") as handle:
        for index, raw in enumerate(handle):
            line = raw.rstrip("\r\n")
            if not line:
                continue
            if index == 0 and line.startswith("word\t"):
                continue
            columns = line.split("\t")
            if not columns:
                continue
            word = columns[0].strip().lower()
            if word:
                words.append(word)
    if not words:
        raise SystemExit(f"catalog source file is empty: {source_path}")
    return words


def execute_mysql_statement(args: argparse.Namespace, sql: str) -> str:
    command = [
        "docker",
        "exec",
        args.mysql_container,
        "mysql",
        f"-u{args.mysql_user}",
        f"-p{args.mysql_password}",
        args.mysql_database,
        "-N",
        "-B",
        "-e",
        sql,
    ]
    completed = subprocess.run(command, capture_output=True, text=True, check=False)
    if completed.returncode != 0:
        message = completed.stderr.strip() or completed.stdout.strip() or "mysql statement failed"
        raise SystemExit(message)
    return completed.stdout


def chunked_words(words: list[str], chunk_size: int) -> list[list[str]]:
    return [words[index:index + chunk_size] for index in range(0, len(words), chunk_size)]


def queue_job_via_db(args: argparse.Namespace, manifest_path: Path, source: ManifestSource) -> None:
    created_by_user_id = args.created_by_user_id
    if created_by_user_id is None:
        created_by_user_id = load_latest_creator_user_id(args)

    created_by_sql = "NULL" if created_by_user_id is None else str(created_by_user_id)
    insert_job_sql = (
        "INSERT INTO public_catalog_import_jobs("
        "source_name, status, total_words, refresh_existing, batch_size, created_by_user_id"
        ") VALUES("
        f"{sql_quote(source.name)}, 'PENDING', {source.word_count}, 0, {args.batch_size}, {created_by_sql}"
        "); SELECT LAST_INSERT_ID();"
    )
    output = execute_mysql_statement(args, insert_job_sql)
    lines = [line.strip() for line in output.splitlines() if line.strip()]
    if not lines:
        raise SystemExit("failed to obtain inserted job id")
    try:
        job_id = int(lines[-1])
    except ValueError as error:
        raise SystemExit(f"unexpected job id output: {output}") from error

    words = load_source_words(manifest_path, source)
    if len(words) != source.word_count:
        print(
            f"warning: manifest wordCount={source.word_count} but source file has {len(words)} words",
            file=sys.stderr,
        )

    for batch in chunked_words(words, DEFAULT_INSERT_BATCH_SIZE):
        values = ", ".join(f"({job_id}, {sql_quote(word)}, 'PENDING')" for word in batch)
        execute_mysql_statement(
            args,
            "INSERT IGNORE INTO public_catalog_import_items(job_id, word, status) VALUES " + values,
        )

    print(
        f"queued_via_db source={source.name} job_id={job_id} "
        f"words={len(words)} created_by_user_id={created_by_user_id if created_by_user_id is not None else 'NULL'}"
    )


def main() -> int:
    args = parse_args()
    if args.batch_size <= 0:
        raise SystemExit("--batch-size must be positive")
    if args.count <= 0:
        raise SystemExit("--count must be positive")

    sources = load_manifest(args.manifest)
    latest_jobs = load_latest_jobs(args, sources)
    completed_count = sum(1 for source in sources if latest_jobs.get(source.name, JobRow(0, source.name, "", 0, 0, 0)).status == "COMPLETED")
    print(f"manifest_sources={len(sources)} completed_sources={completed_count}")

    next_sources, existing_job = pick_next_sources(sources, latest_jobs, args.count)
    if not next_sources and existing_job is None:
        print("all manifest chunks are already completed")
        return 0

    if existing_job and existing_job.status in {"PENDING", "RUNNING"}:
        print(
            f"active_job_exists source={existing_job.source_name} "
            f"job_id={existing_job.job_id} status={existing_job.status} "
            f"processed={existing_job.processed_words}/{existing_job.total_words}"
        )
        return 0

    planned_names = ", ".join(source.name for source in next_sources)
    total_words = sum(source.word_count for source in next_sources)
    print(
        f"next_sources={planned_names} count={len(next_sources)} total_words={total_words} "
        f"previous_status={existing_job.status if existing_job else 'NONE'}"
    )
    if args.dry_run:
        return 0

    if args.token or (args.account and args.password):
        token = resolve_bearer_token(args)
        for source in next_sources:
            payload = {
                "sourceName": source.name,
                "limit": source.word_count,
                "batchSize": args.batch_size,
                "refreshExisting": False,
            }
            response = api_request(
                args.gateway_base_url.rstrip("/") + "/search/public-catalog/import-high-frequency",
                payload,
                token,
            )
            data = response.get("data") or {}
            print(
                f"created_job source={data.get('sourceName', source.name)} "
                f"job_id={data.get('id')} status={data.get('status')}"
            )
    else:
        for source in next_sources:
            queue_job_via_db(args, args.manifest, source)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
