# TDS addendum — P-06c audio & artwork upload

This addendum records the design of the ADMIN audio upload path against
[Report 4_TDS_luannnfx05543.docx](Report%204_TDS_luannnfx05543.docx). The
numbered sections match the TDS so a reader can attach the delta without
rewriting the Word file.

The CSV/XLSX provider dump of UC-28 remains outstanding. This path is a
second way to stage a song for the same import pipeline (S3 JSON → ETag
diff → MySQL upsert).

## §2.3 Server-Side Pages — additional row

| URL pattern | Method | Controller#method | Template | Model attributes | Roles |
|-------------|--------|-------------------|----------|------------------|-------|
| `/admin/import/media` | POST | `AdminImportController#uploadMedia` | redirect to `/admin/import`, or JSON when `X-Requested-With: XMLHttpRequest` | `drafts[]` (`SongDraftForm`: audio, cover, sourceProvider, title, artist, duration, bpm, isExplicit, isrc, genres, moods, tags) | ADMIN |

`GET /admin/import` now also exposes `providers` (from `mrs.catalog.providers`)
for the vendor dropdown on the Audio & artwork tab.

## §5.4 Input Validation Strategy — File upload row

The TDS row currently reads "Catalog import accepts CSV and XLSX only". The
built screens accept:

- **Song JSON tab:** `.json` only, 1 MB per file, 50 files per request
  (`CatalogUploadService`).
- **Audio & artwork tab:** audio extensions `mp3`, `wav`, `flac`, `m4a`,
  `ogg`, `aac` and cover extensions `jpg`/`jpeg`, `png`, `webp`; content-type
  allowlist plus size caps (`mrs.catalog.media.max-audio-bytes` = 50 MB,
  `max-cover-bytes` = 5 MB). The whole batch is validated before any S3 write
  (the instance role has no `s3:DeleteObject`). Title and a registered
  `sourceProvider` are required. Cover art is optional.

Servlet multipart limits are 50 MB/file and 600 MB/request, with
`file-size-threshold=256KB` so large parts spill to disk rather than the
t3.small heap. The JSON tab still enforces its own 1 MB cap in code.

## §9.5 Implementation Conformance

| Area | Specified in TDS | Current build | Resolution |
|------|------------------|---------------|------------|
| UC-28 staging | Provider CSV/XLSX upload | JSON upload + audio/artwork upload that generates the same JSON schema | CSV/XLSX remains outstanding; audio upload is an additional staging path, not a substitute |
| Object storage (1.1, 1.4) | S3 holds staged catalog data and company-hosted audio; CloudFront delivers it | Audio and artwork from P-06c are PutObject'd under `song-data/audio/<slug>/` and `song-data/artwork/<slug>/`; generated JSON uses the CloudFront public base URL | Matches the stated design. Pre-signed browser PUTs were not used; the app streams multipart parts through the existing IAM S3 client |
| `POST /admin/import/media` | Not in the original §2.3 table | Implemented, ADMIN + CSRF | This addendum |
