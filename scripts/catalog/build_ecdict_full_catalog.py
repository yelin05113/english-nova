#!/usr/bin/env python3
"""Build chunked full ECDICT TSV sources for search-service background jobs."""

from __future__ import annotations

import argparse
import csv
import io
import json
import re
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

ECDICT_URL = "https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv"
ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OUTPUT_DIR = ROOT / ".local" / "public-catalog"
BASELINE_SOURCE = (
    ROOT
    / "BackEnd-EnglishNova"
    / "distributed"
    / "search-service"
    / "src"
    / "main"
    / "resources"
    / "public-catalog"
    / "ecdict-high-frequency-10000.tsv"
)
DEFAULT_CHUNK_SIZE = 10_000
SOURCE_PREFIX = "ecdict-full"
HEADER = [
    "word",
    "phonetic",
    "meaning_cn",
    "category",
    "bnc_rank",
    "frq_rank",
    "wordfreq_zipf",
    "exchange_info",
    "data_quality",
    "example_sentence",
]


@dataclass(frozen=True)
class Entry:
    word: str
    phonetic: str
    meaning_cn: str
    category: str
    bnc_rank: int | None
    frq_rank: int | None
    wordfreq_zipf: str
    exchange_info: str
    data_quality: str
    example_sentence: str

    @property
    def frequency_sort_key(self) -> tuple[int, int, str]:
        ranks = [rank for rank in (self.bnc_rank, self.frq_rank) if rank and rank > 0]
        primary_rank = min(ranks) if ranks else 999_999_999
        secondary_rank = self.frq_rank if self.frq_rank and self.frq_rank > 0 else 999_999_999
        return primary_rank, secondary_rank, self.word

    def as_row(self) -> list[str]:
        return [
            self.word,
            self.phonetic,
            self.meaning_cn,
            self.category,
            "" if self.bnc_rank is None else str(self.bnc_rank),
            "" if self.frq_rank is None else str(self.frq_rank),
            self.wordfreq_zipf,
            self.exchange_info,
            self.data_quality,
            self.example_sentence,
        ]


def clean_cell(value: str | None, max_len: int | None = None) -> str:
    if not value:
        return ""
    value = (
        value.replace("\ufeff", "")
        .replace("\\n", " ")
        .replace("\\r", " ")
        .replace("\\t", " ")
        .replace("\\", " ")
        .replace("\t", " ")
        .replace("\r", " ")
        .replace("\n", " ")
    )
    value = re.sub(r"\s+", " ", value).strip()
    if max_len and len(value) > max_len:
        value = value[:max_len].strip()
    return value


def normalize_word(value: str | None) -> str:
    return clean_cell(value).lower()


def supports_word_matching(word: str) -> bool:
    return bool(re.fullmatch(r"[a-z][a-z\-']*", word))


def has_han(value: str) -> bool:
    return any("\u4e00" <= char <= "\u9fff" for char in value)


def has_mojibake(value: str) -> bool:
    bad_tokens = ("\ufffd", "锟", "閿", "闁", "閸", "鍩", "???")
    return any(token in value for token in bad_tokens)


def parse_int(value: str | None) -> int | None:
    normalized = clean_cell(value)
    if not normalized:
        return None
    try:
        parsed = int(normalized)
    except ValueError:
        return None
    return parsed if parsed > 0 else None


def normalize_translation(value: str) -> str:
    parts = [clean_cell(part) for part in re.split(r"[;\n]+", value or "") if clean_cell(part)]
    if not parts:
        return ""
    return clean_cell(" / ".join(parts), 255)


def normalize_category(pos: str, translation: str) -> str:
    normalized_pos = clean_cell(pos, 120)
    if normalized_pos:
        return normalized_pos
    matches = re.findall(r"(?m)(?:^|\s)([a-z]{1,6}\.)", translation or "")
    ordered: list[str] = []
    for match in matches:
        if match not in ordered:
            ordered.append(match)
    return clean_cell(" / ".join(ordered), 120)


def find_example(value: Any) -> str:
    if isinstance(value, dict):
        for key in ("example", "examples", "sentence", "sentences"):
            found = find_example(value.get(key))
            if found:
                return found
        for nested in value.values():
            found = find_example(nested)
            if found:
                return found
    if isinstance(value, list):
        for item in value:
            found = find_example(item)
            if found:
                return found
    if isinstance(value, str):
        candidate = clean_cell(value)
        if len(candidate.split()) >= 3 and re.search(r"[A-Za-z]", candidate):
            return clean_cell(candidate, 255)
    return ""


def first_example(detail: str) -> str:
    detail = detail.strip() if detail else ""
    if not detail:
        return ""
    try:
        parsed = json.loads(detail)
    except json.JSONDecodeError:
        return ""
    return clean_cell(find_example(parsed), 255)


def load_baseline_words(path: Path) -> set[str]:
    baseline: set[str] = set()
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.reader(handle, delimiter="\t")
        for index, row in enumerate(reader):
            if not row:
                continue
            if index == 0 and row[0] == "word":
                continue
            word = normalize_word(row[0])
            if supports_word_matching(word):
                baseline.add(word)
    return baseline


def read_ecdict(input_file: Path | None) -> csv.DictReader:
    if input_file:
        handle = input_file.open("r", encoding="utf-8-sig", newline="")
        return csv.DictReader(handle)
    response = urllib.request.urlopen(ECDICT_URL, timeout=120)
    stream = io.TextIOWrapper(response, encoding="utf-8-sig", newline="")
    return csv.DictReader(stream)


def build_entries(input_file: Path | None, baseline_words: set[str]) -> list[Entry]:
    entries: dict[str, Entry] = {}
    reader = read_ecdict(input_file)
    try:
        for row in reader:
            word = normalize_word(row.get("word"))
            if not supports_word_matching(word) or word in baseline_words or word in entries:
                continue

            phonetic = clean_cell(row.get("phonetic"), 120)
            meaning_cn = normalize_translation(row.get("translation", ""))
            category = normalize_category(row.get("pos", ""), row.get("translation", ""))
            example_sentence = first_example(row.get("detail", ""))
            exchange_info = clean_cell(row.get("exchange"), 255)
            required_text = " ".join([word, phonetic, meaning_cn, category, example_sentence])
            if not all([word, phonetic, meaning_cn]):
                continue
            if not has_han(meaning_cn) or has_mojibake(required_text):
                continue

            entries[word] = Entry(
                word=word,
                phonetic=phonetic,
                meaning_cn=meaning_cn,
                category=category,
                bnc_rank=parse_int(row.get("bnc")),
                frq_rank=parse_int(row.get("frq")),
                wordfreq_zipf="",
                exchange_info=exchange_info,
                data_quality="ecdict_complete",
                example_sentence=example_sentence,
            )
    finally:
        source = getattr(reader, "f", None)
        if source is not None:
            source.close()
    return sorted(entries.values(), key=lambda item: item.frequency_sort_key)


def chunked(values: list[Entry], chunk_size: int) -> Iterable[list[Entry]]:
    for index in range(0, len(values), chunk_size):
        yield values[index:index + chunk_size]


def write_chunk(output_dir: Path, source_name: str, entries: list[Entry]) -> dict[str, Any]:
    file_name = f"{source_name}.tsv"
    target = output_dir / file_name
    with target.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
        writer.writerow(HEADER)
        writer.writerows(entry.as_row() for entry in entries)
    return {
        "name": source_name,
        "file": file_name,
        "wordCount": len(entries),
        "firstWord": entries[0].word,
        "lastWord": entries[-1].word,
    }


def write_manifest(output_dir: Path, chunk_size: int, baseline_path: Path, sources: list[dict[str, Any]], total_words: int) -> Path:
    manifest = {
        "version": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "sourceUrl": ECDICT_URL,
        "chunkSize": chunk_size,
        "baselineSource": str(baseline_path),
        "totalWords": total_words,
        "sources": [
            {
                **source,
                "sequence": index,
            }
            for index, source in enumerate(sources, start=1)
        ],
    }
    manifest_path = output_dir / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return manifest_path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, help="Local ecdict.csv. Defaults to GitHub Raw.")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--baseline", type=Path, default=BASELINE_SOURCE)
    parser.add_argument("--chunk-size", type=int, default=DEFAULT_CHUNK_SIZE)
    parser.add_argument("--source-prefix", default=SOURCE_PREFIX)
    parser.add_argument("--clean", action="store_true", help="Delete existing generated TSV files before writing.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.chunk_size <= 0:
        raise SystemExit("--chunk-size must be positive")
    if not args.baseline.is_file():
        raise SystemExit(f"baseline source not found: {args.baseline}")

    baseline_words = load_baseline_words(args.baseline)
    entries = build_entries(args.input, baseline_words)
    if not entries:
        raise SystemExit("no importable ECDICT entries remained after baseline filtering")

    output_dir: Path = args.output_dir
    output_dir.mkdir(parents=True, exist_ok=True)
    if args.clean:
        for old_file in output_dir.glob(f"{args.source_prefix}-*.tsv"):
            old_file.unlink()
        manifest_path = output_dir / "manifest.json"
        if manifest_path.exists():
            manifest_path.unlink()

    sources: list[dict[str, Any]] = []
    for index, chunk in enumerate(chunked(entries, args.chunk_size), start=1):
        source_name = f"{args.source_prefix}-{index:04d}"
        sources.append(write_chunk(output_dir, source_name, chunk))

    manifest_path = write_manifest(output_dir, args.chunk_size, args.baseline, sources, len(entries))
    print(f"baseline_words={len(baseline_words)}")
    print(f"generated_words={len(entries)}")
    print(f"chunk_count={len(sources)}")
    print(f"output_dir={output_dir}")
    print(f"manifest={manifest_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
