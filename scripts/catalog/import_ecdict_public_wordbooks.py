#!/usr/bin/env python3
"""Build SQL for ECDICT-backed public wordbooks.

Default mode is a dry run that reports tag coverage and skipped rows. Pass
--output-sql to write idempotent SQL inserts for public_vocabulary_entries,
public_wordbooks, and public_wordbook_entries.
"""

from __future__ import annotations

import argparse
import csv
import io
import json
import re
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any

ECDICT_URL = "https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv"
SOURCE_URL = "https://github.com/skywind3000/ECDICT"
LICENSE_URL = "https://github.com/skywind3000/ECDICT/blob/master/LICENSE"
SOURCE_NAME = "skywind3000/ECDICT"
LICENSE_NAME = "MIT"

WORDBOOKS = [
    ("cet4", "CET4"),
    ("cet6", "CET6"),
    ("ky", "Postgraduate English"),
    ("toefl", "TOEFL"),
    ("ielts", "IELTS"),
    ("gre", "GRE"),
]


@dataclass(frozen=True)
class Entry:
    word: str
    phonetic: str
    meaning_cn: str
    example_sentence: str
    bnc_rank: int | None
    frq_rank: int | None
    wordfreq_zipf: str | None
    exchange_info: str
    data_quality: str

    @property
    def frequency_sort_key(self) -> tuple[int, str]:
        ranks = [rank for rank in (self.bnc_rank, self.frq_rank) if rank and rank > 0]
        return (min(ranks) if ranks else 999_999_999, self.word)


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


def normalize_translation(value: str) -> str:
    parts = [clean_cell(part) for part in re.split(r"[;\n]+", value or "") if clean_cell(part)]
    return clean_cell(" / ".join(parts), 255)


def normalize_tags(value: str) -> set[str]:
    return {item.lower() for item in re.split(r"[^A-Za-z0-9]+", value or "") if item}


def has_han(value: str) -> bool:
    return any("\u4e00" <= char <= "\u9fff" for char in value)


def has_mojibake(value: str) -> bool:
    bad_tokens = ("\ufffd", "閿", "闁", "閸", "???")
    return any(token in value for token in bad_tokens)


def parse_int(value: str | None) -> int | None:
    value = clean_cell(value)
    if not value:
        return None
    try:
        parsed = int(value)
    except ValueError:
        return None
    return parsed if parsed > 0 else None


def first_example(detail: str) -> str:
    detail = detail.strip() if detail else ""
    if not detail:
        return ""
    try:
        parsed = json.loads(detail)
    except json.JSONDecodeError:
        return ""
    return clean_cell(find_example(parsed), 255)


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
            return candidate
    return ""


def read_ecdict(input_file: Path | None) -> csv.DictReader:
    if input_file:
        handle = input_file.open("r", encoding="utf-8-sig", newline="")
        return csv.DictReader(handle)
    response = urllib.request.urlopen(ECDICT_URL, timeout=90)
    stream = io.TextIOWrapper(response, encoding="utf-8-sig", newline="")
    return csv.DictReader(stream)


def build_entry(row: dict[str, str]) -> Entry | None:
    word = clean_cell(row.get("word", "")).lower()
    if not re.fullmatch(r"[a-z][a-z\-']*", word):
        return None

    phonetic = clean_cell(row.get("phonetic", ""), 120)
    meaning_cn = normalize_translation(row.get("translation", ""))
    definition_text = clean_cell(row.get("definition", ""), 1024)
    example_sentence = first_example(row.get("detail", "")) or clean_cell(definition_text, 255)
    required_text = " ".join([word, phonetic, meaning_cn, example_sentence])

    if not all([word, phonetic, meaning_cn]):
        return None
    if not has_han(meaning_cn) or has_mojibake(required_text):
        return None

    return Entry(
        word=word,
        phonetic=phonetic,
        meaning_cn=meaning_cn,
        example_sentence=example_sentence,
        bnc_rank=parse_int(row.get("bnc")),
        frq_rank=parse_int(row.get("frq")),
        wordfreq_zipf=None,
        exchange_info=clean_cell(row.get("exchange", ""), 255),
        data_quality="ecdict_complete",
    )


def load_entries(input_file: Path | None) -> tuple[dict[str, Entry], dict[str, list[str]], dict[str, int]]:
    entries: dict[str, Entry] = {}
    wordbooks: dict[str, list[str]] = {tag: [] for tag, _ in WORDBOOKS}
    raw_counts: dict[str, int] = {tag: 0 for tag, _ in WORDBOOKS}

    reader = read_ecdict(input_file)
    try:
        for row in reader:
            row_tags = normalize_tags(row.get("tag", ""))
            matched_tags = [tag for tag, _ in WORDBOOKS if tag in row_tags]
            if not matched_tags:
                continue
            for tag in matched_tags:
                raw_counts[tag] += 1

            entry = build_entry(row)
            if entry is None:
                continue
            entries.setdefault(entry.word, entry)
            for tag in matched_tags:
                wordbooks[tag].append(entry.word)
    finally:
        source = getattr(reader, "f", None)
        if source is not None:
            source.close()

    for tag in wordbooks:
        unique = sorted(set(wordbooks[tag]), key=lambda word: entries[word].frequency_sort_key)
        wordbooks[tag] = unique
    return entries, wordbooks, raw_counts


def sql(value: str | int | None) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, int):
        return str(value)
    return "CONVERT(UNHEX('" + value.replace("\\", " ").encode("utf-8").hex() + "') USING utf8mb4) COLLATE utf8mb4_unicode_ci"


def write_sql(output: Path, entries: dict[str, Entry], wordbooks: dict[str, list[str]]) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("START TRANSACTION;\n\n")
        for tag, name in WORDBOOKS:
            handle.write(
                "INSERT INTO public_wordbooks(name, source_name, source_url, license_name, license_url, tag, word_count) "
                f"VALUES({sql(name)}, {sql(SOURCE_NAME)}, {sql(SOURCE_URL)}, {sql(LICENSE_NAME)}, {sql(LICENSE_URL)}, {sql(tag)}, 0) "
                "ON DUPLICATE KEY UPDATE "
                "name = VALUES(name), source_name = VALUES(source_name), source_url = VALUES(source_url), "
                "license_name = VALUES(license_name), license_url = VALUES(license_url);\n"
            )
        handle.write("\n")

        for entry in sorted(entries.values(), key=lambda item: item.word):
            values = [
                entry.word,
                entry.phonetic,
                entry.meaning_cn,
                entry.example_sentence,
                entry.bnc_rank,
                entry.frq_rank,
                entry.wordfreq_zipf,
                entry.exchange_info,
                entry.data_quality,
                "",
                "ecdict",
            ]
            handle.write(
                "INSERT INTO public_vocabulary_entries("
                "word, phonetic, meaning_cn, example_sentence, bnc_rank, frq_rank, "
                "wordfreq_zipf, exchange_info, data_quality, audio_url, import_source"
                ") VALUES("
                + ", ".join(sql(value) for value in values)
                + ") ON DUPLICATE KEY UPDATE "
                "phonetic = VALUES(phonetic), meaning_cn = VALUES(meaning_cn), example_sentence = VALUES(example_sentence), "
                "bnc_rank = VALUES(bnc_rank), frq_rank = VALUES(frq_rank), "
                "wordfreq_zipf = VALUES(wordfreq_zipf), exchange_info = VALUES(exchange_info), "
                "data_quality = VALUES(data_quality), import_source = VALUES(import_source);\n"
            )
        handle.write("\n")

        for tag, words in wordbooks.items():
            handle.write(f"DELETE m FROM public_wordbook_entries m JOIN public_wordbooks w ON w.id = m.public_wordbook_id WHERE w.tag = {sql(tag)};\n")
            for index, word in enumerate(words, start=1):
                handle.write(
                    "INSERT INTO public_wordbook_entries(public_wordbook_id, public_entry_id, sort_order) "
                    "SELECT w.id, v.id, "
                    f"{index} FROM public_wordbooks w JOIN public_vocabulary_entries v "
                    f"WHERE w.tag = {sql(tag)} AND v.word = {sql(word)};\n"
                )
        handle.write("\n")
        handle.write(
            "UPDATE public_wordbooks w SET word_count = ("
            "SELECT COUNT(*) FROM public_wordbook_entries m WHERE m.public_wordbook_id = w.id"
            ");\n"
        )
        handle.write("COMMIT;\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, help="Local ecdict.csv. Defaults to GitHub Raw.")
    parser.add_argument("--output-sql", type=Path, help="Write idempotent import SQL to this path.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    entries, wordbooks, raw_counts = load_entries(args.input)
    print(f"unique_importable_entries={len(entries)}")
    for tag, name in WORDBOOKS:
        print(f"{tag} {name}: tagged={raw_counts[tag]} importable={len(wordbooks[tag])}")
    if args.output_sql:
        write_sql(args.output_sql, entries, wordbooks)
        print(f"sql_output={args.output_sql}")
    return 0 if entries else 1


if __name__ == "__main__":
    raise SystemExit(main())
