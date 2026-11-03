#!/usr/bin/env python3
"""Builds the stop list the app searches, in Hebrew and English, from the Ministry of Transport GTFS zip.

Usage: build_stops.py <gtfs.zip> <stops.dat>

The output is gzipped tab-separated text. It must not be named .gz, because the Android build unpacks
assets with that extension and renames them.

Columns: stop code, latitude, longitude, Hebrew name, English name, Hebrew city, English city.
An English column is empty when the Ministry has no translation.
"""
import csv
import gzip
import io
import re
import sys
import zipfile

CITY_RE = re.compile(r"עיר:\s*(.*?)\s*רציף:")


def rows(archive, name):
    with archive.open(name) as raw:
        yield from csv.DictReader(io.TextIOWrapper(raw, encoding="utf-8-sig", newline=""))


def clean(text):
    return " ".join(text.replace("''", '"').replace("\t", " ").split())


def used_stops(archive):
    """Stops that some trip calls at, in the order stops.dat lists them. build_schedule.py indexes stops the same way."""
    used = {r["stop_id"] for r in rows(archive, "stop_times.txt")}
    return [r for r in rows(archive, "stops.txt") if r["stop_id"] in used]


def build(zip_path):
    with zipfile.ZipFile(zip_path) as archive:
        english = {r["trans_id"]: r["translation"] for r in rows(archive, "translations.txt") if r["lang"] == "EN"}
        lines = []
        for r in used_stops(archive):
            match = CITY_RE.search(r["stop_desc"] or "")
            city = match.group(1) if match else ""
            lines.append("\t".join([
                r["stop_code"] or "0", r["stop_lat"], r["stop_lon"],
                clean(r["stop_name"]), clean(english.get(r["stop_name"], "")),
                clean(city), clean(english.get(city, "")),
            ]))
    return lines


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    lines = build(sys.argv[1])
    with gzip.open(sys.argv[2], "wt", encoding="utf-8", compresslevel=9) as out:
        out.write("\n".join(lines))
    print(f"{len(lines)} stops")


if __name__ == "__main__":
    main()
