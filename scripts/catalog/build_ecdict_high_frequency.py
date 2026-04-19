#!/usr/bin/env python3
"""Build the vetted ECDICT high-frequency catalog consumed by search-service."""

from __future__ import annotations

import csv
import io
import json
import re
import sys
import urllib.request
from pathlib import Path
from typing import Any

ECDICT_URL = "https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv"
ROOT = Path(__file__).resolve().parents[2]
HIGH_FREQUENCY_WORDS = ROOT / "BackEnd-EnglishNova" / "distributed" / "search-service" / "src" / "main" / "resources" / "public-catalog" / "high-frequency-5000.txt"
OUTPUT = ROOT / "BackEnd-EnglishNova" / "distributed" / "search-service" / "src" / "main" / "resources" / "public-catalog" / "ecdict-high-frequency-5000.tsv"

HEADER = [
    "word",
    "phonetic",
    "meaning_cn",
    "category",
    "definition_en",
    "tags",
    "bnc_rank",
    "frq_rank",
    "wordfreq_zipf",
    "exchange_info",
    "data_quality",
    "example_sentence",
]


try:
    from wordfreq import zipf_frequency
except ImportError:  # pragma: no cover - optional local quality enrichment.
    zipf_frequency = None


def clean_cell(value: str | None, max_len: int | None = None) -> str:
    if not value:
        return ""
    value = value.replace("\ufeff", "").replace("\t", " ").replace("\r", " ").replace("\n", " ")
    value = re.sub(r"\s+", " ", value).strip()
    if max_len and len(value) > max_len:
        value = value[:max_len].strip()
    return value


def has_han(value: str) -> bool:
    return any("\u4e00" <= char <= "\u9fff" for char in value)


def has_mojibake(value: str) -> bool:
    bad_tokens = ("\ufffd", "锟", "閿", "鍩", "???")
    return any(token in value for token in bad_tokens)


def load_high_frequency_words() -> list[str]:
    words: list[str] = []
    seen: set[str] = set()
    for raw in HIGH_FREQUENCY_WORDS.read_text(encoding="utf-8").splitlines():
        word = raw.strip().lower()
        if re.fullmatch(r"[a-z][a-z\-']*", word) and word not in seen:
            seen.add(word)
            words.append(word)
        if len(words) >= 5000:
            break
    return words


def normalize_translation(value: str) -> str:
    parts = [clean_cell(part) for part in re.split(r"[;\n]+", value or "") if clean_cell(part)]
    if not parts:
        return ""
    return clean_cell(" / ".join(parts), 255)


def normalize_category(pos: str, translation: str) -> str:
    pos = clean_cell(pos, 120)
    if pos:
        return pos
    matches = re.findall(r"(?m)(?:^|\s)([a-z]{1,6}\.)", translation or "")
    unique: list[str] = []
    for item in matches:
        if item not in unique:
            unique.append(item)
    return clean_cell(" / ".join(unique), 120)


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


def build_rows() -> tuple[list[list[str]], list[str]]:
    high_words = load_high_frequency_words()
    wanted = {word: index for index, word in enumerate(high_words)}
    rows_by_word: dict[str, list[str]] = {}

    with urllib.request.urlopen(ECDICT_URL, timeout=60) as response:
        text_stream = io.TextIOWrapper(response, encoding="utf-8-sig", newline="")
        reader = csv.DictReader(text_stream)
        for row in reader:
            word = clean_cell(row.get("word", "")).lower()
            if word not in wanted or word in rows_by_word:
                continue

            phonetic = clean_cell(row.get("phonetic", ""), 120)
            meaning_cn = normalize_translation(row.get("translation", ""))
            category = normalize_category(row.get("pos", ""), row.get("translation", ""))
            definition_en = clean_cell(row.get("definition", ""), 1024)
            tags = clean_cell(row.get("tag", ""), 255)
            bnc_rank = clean_cell(row.get("bnc", ""))
            frq_rank = clean_cell(row.get("frq", ""))
            exchange_info = clean_cell(row.get("exchange", ""), 255)
            example_sentence = first_example(row.get("detail", ""))

            required_text = " ".join([word, phonetic, meaning_cn, category, definition_en])
            if not all([word, phonetic, meaning_cn, category, definition_en]):
                continue
            if not has_han(meaning_cn) or has_mojibake(required_text):
                continue

            wordfreq_zipf = ""
            if zipf_frequency is not None:
                score = zipf_frequency(word, "en")
                if score > 0:
                    wordfreq_zipf = f"{score:.2f}"

            rows_by_word[word] = [
                word,
                phonetic,
                meaning_cn,
                category,
                definition_en,
                tags,
                bnc_rank,
                frq_rank,
                wordfreq_zipf,
                exchange_info,
                "ecdict_complete",
                example_sentence,
            ]

    rows = [rows_by_word[word] for word in high_words if word in rows_by_word]
    missing = [word for word in high_words if word not in rows_by_word]
    return rows, missing


def main() -> int:
    rows, missing = build_rows()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
        writer.writerow(HEADER)
        writer.writerows(rows)

    print(f"wrote={len(rows)} output={OUTPUT}")
    print(f"missing_or_incomplete={len(missing)}")
    if missing:
        print("sample_missing=" + ",".join(missing[:20]))
    return 0 if rows else 1


if __name__ == "__main__":
    raise SystemExit(main())
