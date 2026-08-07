#!/usr/bin/env python3
"""Convert Epidemic Sound *playlist* page JSON dumps into per-song JSON.

Unlike epidemic_pages_to_songs.py (catalog / search page dumps shaped as
entities.tracks), playlist exports look like:

  {
    "count": 1307,
    "next": "...",
    "results": [
      { "id": ..., "track_id": ..., "track": { ... Epidemic track ... } },
      ...
    ]
  }

Each nested track is mapped to the same per-song JSON shape as the catalog
converter. Audio/cover stay as Epidemic CDN public URLs.

Examples:
  # Convert playlist pages under ./epidemic-backfill into song JSON
  python3 scripts/epidemic_playlist_to_songs.py \\
    --input scripts/data/epidemic-backfill \\
    --out scripts/data/epidemic-playlist-songs

  # Only keep tracks not already present in the catalog conversion
  python3 scripts/epidemic_playlist_to_songs.py \\
    --input scripts/data/epidemic-backfill \\
    --out scripts/data/epidemic-playlist-songs-unique \\
    --exclude-dir scripts/data/epidemic-songs

  # Convert + upload
  python3 scripts/epidemic_playlist_to_songs.py \\
    --input scripts/data/epidemic-backfill \\
    --out scripts/data/epidemic-playlist-songs \\
    --upload \\
    --bucket mrs-133857166188-assets \\
    --prefix song-data/
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

# Reuse the shared Epidemic track -> song mapping / S3 upload helpers.
from epidemic_pages_to_songs import (
    DEFAULT_BUCKET,
    DEFAULT_PREFIX,
    DEFAULT_REGION,
    track_to_song,
    upload_to_s3,
)


def load_tracks_from_playlist_page(path: Path) -> list[dict[str, Any]]:
    """Load nested track objects from a playlist entries page dump."""
    with path.open(encoding="utf-8") as fh:
        data = json.load(fh)

    results = data.get("results")
    if not isinstance(results, list):
        raise ValueError(f"{path}: expected playlist dump with a results[] array")

    tracks: list[dict[str, Any]] = []
    for entry in results:
        if not isinstance(entry, dict):
            continue
        track = entry.get("track")
        if isinstance(track, dict):
            tracks.append(track)
    return tracks


def _existing_ids(exclude_dir: Path | None) -> set[str]:
    """Collect externalSourceIds already converted (filename stem or JSON field)."""
    if exclude_dir is None:
        return set()
    if not exclude_dir.is_dir():
        raise SystemExit(f"Exclude directory not found: {exclude_dir}")

    ids: set[str] = set()
    for path in exclude_dir.glob("*.json"):
        ids.add(path.stem)
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        if isinstance(data, dict):
            ext = data.get("externalSourceId")
            if isinstance(ext, str) and ext:
                ids.add(ext)
    return ids


def convert_playlist_pages(
    input_dir: Path,
    out_dir: Path,
    exclude_dir: Path | None = None,
) -> tuple[int, int, int, list[Path]]:
    page_files = sorted(input_dir.glob("page-*.json"))
    if not page_files:
        page_files = sorted(p for p in input_dir.glob("*.json") if p.is_file())

    if not page_files:
        raise SystemExit(f"No JSON page files found in {input_dir}")

    excluded = _existing_ids(exclude_dir)
    if excluded:
        print(f"Excluding {len(excluded)} id(s) already in {exclude_dir}")

    out_dir.mkdir(parents=True, exist_ok=True)

    written = 0
    skipped = 0
    excluded_count = 0
    # Deduplicate across playlist pages by externalSourceId (first write wins)
    by_id: dict[str, dict[str, Any]] = {}

    for page in page_files:
        tracks = load_tracks_from_playlist_page(page)
        print(f"Reading {page.name}: {len(tracks)} playlist entries")
        for track in tracks:
            song = track_to_song(track)
            if song is None:
                skipped += 1
                continue
            ext_id = song["externalSourceId"]
            if ext_id in excluded:
                excluded_count += 1
                continue
            if ext_id not in by_id:
                by_id[ext_id] = song

    out_paths: list[Path] = []
    for external_id, song in sorted(by_id.items()):
        out_path = out_dir / f"{external_id}.json"
        out_path.write_text(
            json.dumps(song, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        out_paths.append(out_path)
        written += 1

    print(
        f"Wrote {written} song JSON file(s) to {out_dir} "
        f"(skipped {skipped}, excluded-as-duplicate {excluded_count})"
    )
    return written, skipped, excluded_count, out_paths


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Convert Epidemic playlist page JSON dumps to per-song JSON "
            "(results[].track shape)."
        )
    )
    parser.add_argument(
        "--input",
        "-i",
        type=Path,
        required=True,
        help="Directory containing playlist page-*.json dumps",
    )
    parser.add_argument(
        "--out",
        "-o",
        type=Path,
        default=Path("playlist-song-data"),
        help="Local output directory for per-song JSON (default: ./playlist-song-data)",
    )
    parser.add_argument(
        "--exclude-dir",
        type=Path,
        default=None,
        help=(
            "Optional directory of already-converted song JSON "
            "(e.g. epidemic-songs). Matching externalSourceIds are skipped."
        ),
    )
    parser.add_argument(
        "--upload",
        action="store_true",
        help="Upload converted JSON files to S3 after conversion",
    )
    parser.add_argument(
        "--bucket",
        default=DEFAULT_BUCKET,
        help=f"S3 bucket (default: {DEFAULT_BUCKET})",
    )
    parser.add_argument(
        "--prefix",
        default=DEFAULT_PREFIX,
        help=f"S3 key prefix (default: {DEFAULT_PREFIX})",
    )
    parser.add_argument(
        "--region",
        default=DEFAULT_REGION,
        help=f"AWS region (default: {DEFAULT_REGION})",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="With --upload, print S3 keys without uploading",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    input_dir = args.input.expanduser().resolve()
    out_dir = args.out.expanduser().resolve()
    exclude_dir = (
        args.exclude_dir.expanduser().resolve() if args.exclude_dir else None
    )

    if not input_dir.is_dir():
        raise SystemExit(f"Input directory not found: {input_dir}")

    convert_playlist_pages(input_dir, out_dir, exclude_dir=exclude_dir)

    if args.upload:
        count = upload_to_s3(
            out_dir=out_dir,
            bucket=args.bucket,
            prefix=args.prefix,
            region=args.region,
            dry_run=args.dry_run,
        )
        action = "Would upload" if args.dry_run else "Uploaded"
        print(
            f"{action} {count} object(s) to "
            f"s3://{args.bucket}/{args.prefix.lstrip('/')}"
        )

    return 0


if __name__ == "__main__":
    sys.exit(main())
