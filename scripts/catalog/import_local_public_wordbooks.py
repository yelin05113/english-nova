#!/usr/bin/env python3
"""Build SQL for local public wordbooks from english-vocabulary JSON files.

Default mode is a dry run that reports import coverage against the current
public_vocabulary_entries table. Pass --output-sql to write idempotent SQL that:
1. switches public wordbooks to the local 7-book catalog,
2. rewrites public_wordbook_entries in source order, and
3. overrides meaning_cn for matched public entries only.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
from collections import OrderedDict
from dataclasses import dataclass
from pathlib import Path

DEFAULT_ROOT = Path(r"D:\Projects\EnglishBookResource\english-vocabulary")
DEFAULT_JSON_DIR = DEFAULT_ROOT / "json"
DEFAULT_SENTENCE_DIR = DEFAULT_ROOT / "json_original" / "json-sentence"
DEFAULT_OUTPUT_SQL = Path("scripts/catalog/generated/local_public_wordbooks.sql")
MYSQL_BIN = "mysql"

SOURCE_NAME = "EnglishBookResource/english-vocabulary"
SOURCE_URL = "https://github.com/KyleBing/dict"
LICENSE_NAME = ""
LICENSE_URL = ""

TARGET_WORDBOOKS = [
    ("初中", "1-初中-顺序.json"),
    ("高中", "2-高中-顺序.json"),
    ("英语四级", "3-CET4-顺序.json"),
    ("英语六级", "4-CET6-顺序.json"),
    ("考研英语", "5-考研-顺序.json"),
    ("托福", "6-托福-顺序.json"),
    ("SAT", "7-SAT-顺序.json"),
]
REMOVED_WORDBOOK_NAMES = ("雅思", "GRE")


@dataclass(frozen=True)
class WordbookCatalog:
    name: str
    ordered_words: list[str]
    meanings: dict[str, str]
    raw_count: int
    deduped_count: int


def clean_text(value: str | None, max_len: int | None = None) -> str:
    if value is None:
        return ""
    value = (
        str(value)
        .replace("\ufeff", "")
        .replace("\\n", " ")
        .replace("\\r", " ")
        .replace("\\t", " ")
        .replace("\r", " ")
        .replace("\n", " ")
        .replace("\t", " ")
    )
    value = re.sub(r"\s+", " ", value).strip()
    if max_len and len(value) > max_len:
        value = value[:max_len].strip()
    return value


def normalize_word(value: str | None) -> str:
    word = clean_text(value, 120).lower()
    return re.sub(r"\s+", " ", word)


def normalize_pos(value: str) -> str:
    pos = clean_text(value, 24)
    if not pos:
        return ""
    return pos if pos.endswith(".") else f"{pos}."


def normalize_meaning(value: str) -> str:
    value = clean_text(value)
    if not value:
        return ""
    parts = [clean_text(part) for part in re.split(r"[;/\n]+", value) if clean_text(part)]
    return clean_text(" / ".join(parts), 255)


def build_meaning_from_translations(translations: object) -> str:
    if not isinstance(translations, list):
        return ""

    parts: list[str] = []
    seen: set[str] = set()
    for item in translations:
        if not isinstance(item, dict):
            continue
        text = normalize_meaning(item.get("translation"))
        if not text:
            continue
        pos = normalize_pos(item.get("type", ""))
        candidate = clean_text(f"{pos} {text}" if pos else text, 255)
        if candidate and candidate not in seen:
            seen.add(candidate)
            parts.append(candidate)
    return clean_text(" / ".join(parts), 255)


def load_wordbook_catalog(json_path: Path, name: str) -> WordbookCatalog:
    payload = json.loads(json_path.read_text(encoding="utf-8"))
    if not isinstance(payload, list):
        raise ValueError(f"{json_path} does not contain a top-level array")

    ordered: OrderedDict[str, str] = OrderedDict()
    raw_count = 0
    for item in payload:
        if not isinstance(item, dict):
            continue
        raw_count += 1
        word = normalize_word(item.get("word"))
        if not word:
            continue
        meaning = build_meaning_from_translations(item.get("translations"))
        if word not in ordered:
            ordered[word] = meaning
        elif not ordered[word] and meaning:
            ordered[word] = meaning

    return WordbookCatalog(
        name=name,
        ordered_words=list(ordered.keys()),
        meanings=dict(ordered),
        raw_count=raw_count,
        deduped_count=len(ordered),
    )


def load_sentence_fallbacks(sentence_dir: Path) -> dict[str, str]:
    fallbacks: dict[str, str] = {}
    for path in sorted(sentence_dir.glob("*.json")):
        payload = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(payload, list):
            continue
        for item in payload:
            if not isinstance(item, dict):
                continue
            word = normalize_word(item.get("word"))
            if not word or word in fallbacks:
                continue
            meaning = build_meaning_from_translations(item.get("translations"))
            if meaning:
                fallbacks[word] = meaning
    return fallbacks


def load_existing_public_words(
    mysql_host: str,
    mysql_port: int,
    mysql_user: str,
    mysql_password: str,
    mysql_database: str,
) -> set[str]:
    command = [
        MYSQL_BIN,
        "-N",
        "-B",
        "-h",
        mysql_host,
        "-P",
        str(mysql_port),
        "-u",
        mysql_user,
        f"-p{mysql_password}",
        "-D",
        mysql_database,
        "-e",
        "SELECT word FROM public_vocabulary_entries",
    ]
    result = subprocess.run(command, capture_output=True, text=True, check=True)
    return {normalize_word(line) for line in result.stdout.splitlines() if clean_text(line)}


def sql(value: str | int | None) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, int):
        return str(value)
    return "CONVERT(UNHEX('" + value.encode("utf-8").hex() + "') USING utf8mb4) COLLATE utf8mb4_unicode_ci"


def write_sql(
    output: Path,
    matched_wordbooks: dict[str, list[str]],
    override_meanings: dict[str, str],
) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    active_wordbook_names = [name for name, _ in TARGET_WORDBOOKS] + list(REMOVED_WORDBOOK_NAMES)

    with output.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("START TRANSACTION;\n\n")
        handle.write(
            "UPDATE quiz_sessions qs\n"
            "JOIN public_wordbooks pw ON pw.id = qs.target_id\n"
            "SET qs.status = 'CANCELLED',\n"
            "    qs.finished_at = CURRENT_TIMESTAMP\n"
            "WHERE qs.target_type = 'PUBLIC_WORDBOOK'\n"
            "  AND qs.status = 'ACTIVE'\n"
            f"  AND pw.name IN ({', '.join(sql(name) for name in active_wordbook_names)});\n\n"
        )

        for name, _ in TARGET_WORDBOOKS:
            handle.write(
                "INSERT INTO public_wordbooks(name, source_name, source_url, license_name, license_url, word_count) "
                f"SELECT {sql(name)}, {sql(SOURCE_NAME)}, {sql(SOURCE_URL)}, {sql(LICENSE_NAME)}, {sql(LICENSE_URL)}, 0 "
                "FROM DUAL WHERE NOT EXISTS ("
                f"SELECT 1 FROM public_wordbooks WHERE name = {sql(name)}"
                ");\n"
            )
            handle.write(
                "UPDATE public_wordbooks "
                f"SET source_name = {sql(SOURCE_NAME)}, "
                f"source_url = {sql(SOURCE_URL)}, "
                f"license_name = {sql(LICENSE_NAME)}, "
                f"license_url = {sql(LICENSE_URL)} "
                f"WHERE name = {sql(name)};\n"
            )
        handle.write("\n")

        if REMOVED_WORDBOOK_NAMES:
            handle.write(
                "DELETE FROM public_wordbooks "
                f"WHERE name IN ({', '.join(sql(name) for name in REMOVED_WORDBOOK_NAMES)});\n\n"
            )

        for word, meaning in sorted(override_meanings.items()):
            handle.write(
                "UPDATE public_vocabulary_entries "
                f"SET meaning_cn = {sql(meaning)} "
                f"WHERE word = {sql(word)};\n"
            )
        handle.write("\n")

        for name, _ in TARGET_WORDBOOKS:
            handle.write(
                "DELETE m FROM public_wordbook_entries m "
                "JOIN public_wordbooks w ON w.id = m.public_wordbook_id "
                f"WHERE w.name = {sql(name)};\n"
            )
        handle.write("\n")

        for name, words in matched_wordbooks.items():
            for index, word in enumerate(words, start=1):
                handle.write(
                    "INSERT INTO public_wordbook_entries(public_wordbook_id, public_entry_id, sort_order) "
                    "SELECT w.id, v.id, "
                    f"{index} FROM public_wordbooks w JOIN public_vocabulary_entries v "
                    f"WHERE w.name = {sql(name)} AND v.word = {sql(word)};\n"
                )
        handle.write("\n")

        handle.write(
            "UPDATE public_wordbooks w SET word_count = (\n"
            "    SELECT COUNT(*)\n"
            "    FROM public_wordbook_entries m\n"
            "    WHERE m.public_wordbook_id = w.id\n"
            ")\n"
            f"WHERE w.name IN ({', '.join(sql(name) for name, _ in TARGET_WORDBOOKS)});\n"
        )
        handle.write("COMMIT;\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json-dir", type=Path, default=DEFAULT_JSON_DIR)
    parser.add_argument("--sentence-dir", type=Path, default=DEFAULT_SENTENCE_DIR)
    parser.add_argument("--output-sql", type=Path, default=None)
    parser.add_argument("--mysql-host", default="127.0.0.1")
    parser.add_argument("--mysql-port", type=int, default=4407)
    parser.add_argument("--mysql-user", default="english_nova")
    parser.add_argument("--mysql-password", default="english_nova")
    parser.add_argument("--mysql-database", default="english_nova")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    existing_words = load_existing_public_words(
        mysql_host=args.mysql_host,
        mysql_port=args.mysql_port,
        mysql_user=args.mysql_user,
        mysql_password=args.mysql_password,
        mysql_database=args.mysql_database,
    )
    sentence_fallbacks = load_sentence_fallbacks(args.sentence_dir)

    matched_wordbooks: dict[str, list[str]] = {}
    override_meanings: dict[str, str] = {}
    missing_meaning_words: set[str] = set()
    unmatched_total = 0

    for name, filename in TARGET_WORDBOOKS:
        catalog = load_wordbook_catalog(args.json_dir / filename, name)
        matched: list[str] = []
        unmatched = 0
        for word in catalog.ordered_words:
            if word not in existing_words:
                unmatched += 1
                continue
            matched.append(word)
            meaning = catalog.meanings.get(word) or sentence_fallbacks.get(word, "")
            if meaning:
                override_meanings.setdefault(word, meaning)
            else:
                missing_meaning_words.add(word)

        unmatched_total += unmatched
        matched_wordbooks[name] = matched
        print(
            f"{name}: raw={catalog.raw_count} deduped={catalog.deduped_count} "
            f"matched={len(matched)} unmatched={unmatched}"
        )

    print(f"matched_unique_words={len({word for words in matched_wordbooks.values() for word in words})}")
    print(f"meaning_override_words={len(override_meanings)}")
    print(f"missing_meaning_words={len(missing_meaning_words)}")
    print(f"unmatched_total={unmatched_total}")

    if args.output_sql:
        write_sql(args.output_sql, matched_wordbooks, override_meanings)
        print(f"sql_output={args.output_sql}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
