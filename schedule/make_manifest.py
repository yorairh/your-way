#!/usr/bin/env python3
"""Writes manifest.json describing a freshly built schedule, for the app's automatic updates.

Usage: make_manifest.py <gtfs.zip> <dir with schedule.dat and stops.dat> <download base url> <version>

The app reads the manifest, downloads both files from <download base url>/<file>, and checks their sizes and sha256.
The source sha256 lets the nightly job skip publishing when the Ministry's feed hasn't changed.
"""
import hashlib
import json
import os
import sys

FILES = ["schedule.dat", "stops.dat"]


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def main():
    if len(sys.argv) != 5:
        sys.exit(__doc__)
    zip_path, directory, base_url, version = sys.argv[1:]
    manifest = {
        "format": 1,
        "version": version,
        "sourceSha256": sha256(zip_path),
        "files": {
            name: {
                "url": f"{base_url}/{name}",
                "sha256": sha256(os.path.join(directory, name)),
                "size": os.path.getsize(os.path.join(directory, name)),
            }
            for name in FILES
        },
    }
    with open(os.path.join(directory, "manifest.json"), "w", encoding="utf-8") as out:
        json.dump(manifest, out, indent=2)


if __name__ == "__main__":
    main()
