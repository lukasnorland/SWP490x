#!/usr/bin/env python3
"""Convert Epidemic Sound page JSON dumps into per-song JSON and upload to S3.

Each input file is expected to look like page-001.json:
  { "entities": { "tracks": { "<id>": { ... } } }, "meta": {...} }

Output objects (one per track) are written locally and optionally uploaded to:
  s3://<bucket>/<prefix>/<externalSourceId>.json

Audio/cover stay as Epidemic CDN public URLs (no raw audio uploaded).
Only the full mix stem is kept for the client.

Examples:
  # Convert all pages under ./epidemic-pages into ./song-data
  python3 scripts/epidemic_pages_to_s3.py --input ./epidemic-pages --out ./song-data

  # Convert + upload
  python3 scripts/epidemic_pages_to_s3.py \\
    --input ./epidemic-pages \\
    --out ./song-data \\
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


SOURCE_PROVIDER = "EpidemicSound"
DEFAULT_BUCKET = "mrs-133857166188-assets"
DEFAULT_PREFIX = "song-data/"
DEFAULT_REGION = "ap-southeast-1"


def _tag_names(items: Any) -> list[str]:
    """Normalize genres/moods which are either strings or {displayTag|tag} objects."""
    if not items:
        return []
    names: list[str] = []
    seen: set[str] = set()
    for item in items:
        if isinstance(item, str):
            name = item.strip()
        elif isinstance(item, dict):
            name = (item.get("displayTag") or item.get("tag") or "").strip()
        else:
            continue
        if not name:
            continue
        key = name.casefold()
        if key in seen:
            continue
        seen.add(key)
        names.append(name)
    return names


def _artist_name(track: dict[str, Any]) -> str | None:
    creatives = track.get("creatives") or {}
    mains = creatives.get("mainArtists") or []
    names = [a.get("name", "").strip() for a in mains if isinstance(a, dict) and a.get("name")]
    names = [n for n in names if n]
    return ", ".join(names) if names else None


def _full_audio_url(track: dict[str, Any]) -> str | None:
    stems = track.get("stems") or {}
    full = stems.get("full") or {}
    url = full.get("lqMp3Url")
    return url if isinstance(url, str) and url else None


def _cover_url(track: dict[str, Any]) -> str | None:
    for key in ("cover", "imageUrl"):
        url = track.get(key)
        if isinstance(url, str) and url:
            return url
    cover_art = track.get("coverArt") or {}
    base = cover_art.get("baseUrl")
    sizes = cover_art.get("sizes") or {}
    if isinstance(base, str) and base:
        suffix = sizes.get("S") or sizes.get("M") or sizes.get("L")
        if isinstance(suffix, str) and suffix:
            return base + suffix
    return None


def track_to_song(track: dict[str, Any]) -> dict[str, Any] | None:
    """Map one Epidemic track entity to our per-song JSON. Skip invalid rows."""
    external_id = track.get("kosmosId") or (
        str(track["id"]) if track.get("id") is not None else None
    )
    title = (track.get("title") or "").strip()
    audio_url = _full_audio_url(track)

    if not external_id or not title or not audio_url:
        return None

    metadata_tags = track.get("metadataTags") or []
    if isinstance(metadata_tags, list):
        tags = [t.strip() for t in metadata_tags if isinstance(t, str) and t.strip()]
    else:
        tags = []

    song: dict[str, Any] = {
        "externalSourceId": external_id,
        "epidemicTrackId": track.get("id"),
        "sourceProvider": SOURCE_PROVIDER,
        "title": title,
        "artist": _artist_name(track),
        "duration": track.get("length"),
        "bpm": track.get("bpm"),
        "energyLevel": track.get("energyLevel"),
        "hasVocals": track.get("hasVocals"),
        "isExplicit": track.get("isExplicit"),
        "isrc": track.get("isrc"),
        "publicSlug": track.get("publicSlug"),
        "audioUrl": audio_url,
        "coverUrl": _cover_url(track),
        "genres": _tag_names(track.get("genres")),
        "moods": _tag_names(track.get("moods")),
        "tags": tags,
    }
    return song


def load_tracks_from_page(path: Path) -> list[dict[str, Any]]:
    with path.open(encoding="utf-8") as fh:
        data = json.load(fh)
    tracks = (data.get("entities") or {}).get("tracks") or {}
    if not isinstance(tracks, dict):
        raise ValueError(f"{path}: entities.tracks must be an object")
    return [t for t in tracks.values() if isinstance(t, dict)]


def convert_pages(input_dir: Path, out_dir: Path) -> tuple[int, int, list[Path]]:
    page_files = sorted(input_dir.glob("page-*.json"))
    if not page_files:
        # Also accept any *.json for convenience (e.g. renamed dumps)
        page_files = sorted(p for p in input_dir.glob("*.json") if p.is_file())

    if not page_files:
        raise SystemExit(f"No JSON page files found in {input_dir}")

    out_dir.mkdir(parents=True, exist_ok=True)

    written = 0
    skipped = 0
    # Deduplicate across pages by externalSourceId (last write wins)
    by_id: dict[str, dict[str, Any]] = {}

    for page in page_files:
        tracks = load_tracks_from_page(page)
        print(f"Reading {page.name}: {len(tracks)} tracks")
        for track in tracks:
            song = track_to_song(track)
            if song is None:
                skipped += 1
                continue
            by_id[song["externalSourceId"]] = song

    out_paths: list[Path] = []
    for external_id, song in sorted(by_id.items()):
        out_path = out_dir / f"{external_id}.json"
        out_path.write_text(json.dumps(song, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        out_paths.append(out_path)
        written += 1

    print(f"Wrote {written} song JSON file(s) to {out_dir} (skipped {skipped})")
    return written, skipped, out_paths


def upload_to_s3(
    out_dir: Path,
    bucket: str,
    prefix: str,
    region: str,
    dry_run: bool = False,
) -> int:
    try:
        import boto3
        from botocore.exceptions import BotoCoreError, ClientError
    except ImportError as exc:
        raise SystemExit(
            "boto3 is required for --upload. Install with: pip install boto3"
        ) from exc

    prefix = prefix.lstrip("/")
    if prefix and not prefix.endswith("/"):
        prefix += "/"

    client = boto3.client("s3", region_name=region)
    files = sorted(out_dir.glob("*.json"))
    if not files:
        print(f"No JSON files to upload in {out_dir}")
        return 0

    uploaded = 0
    for path in files:
        key = f"{prefix}{path.name}"
        if dry_run:
            print(f"DRY-RUN put s3://{bucket}/{key}")
            uploaded += 1
            continue
        try:
            client.upload_file(
                str(path),
                bucket,
                key,
                ExtraArgs={"ContentType": "application/json; charset=utf-8"},
            )
        except (BotoCoreError, ClientError) as err:
            raise SystemExit(f"Failed uploading {path.name}: {err}") from err
        uploaded += 1
        if uploaded % 50 == 0 or uploaded == len(files):
            print(f"Uploaded {uploaded}/{len(files)} -> s3://{bucket}/{prefix}")

    return uploaded


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Convert Epidemic page JSON dumps to per-song JSON and upload to S3."
    )
    parser.add_argument(
        "--input",
        "-i",
        type=Path,
        required=True,
        help="Directory containing page-*.json (or any *.json Epidemic dumps)",
    )
    parser.add_argument(
        "--out",
        "-o",
        type=Path,
        default=Path("song-data"),
        help="Local output directory for per-song JSON (default: ./song-data)",
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

    if not input_dir.is_dir():
        raise SystemExit(f"Input directory not found: {input_dir}")

    convert_pages(input_dir, out_dir)

    if args.upload:
        count = upload_to_s3(
            out_dir=out_dir,
            bucket=args.bucket,
            prefix=args.prefix,
            region=args.region,
            dry_run=args.dry_run,
        )
        action = "Would upload" if args.dry_run else "Uploaded"
        print(f"{action} {count} object(s) to s3://{args.bucket}/{args.prefix.lstrip('/')}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
