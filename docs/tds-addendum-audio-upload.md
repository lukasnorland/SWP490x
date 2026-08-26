# TDS addendum — P-06b audio & artwork upload

This addendum records the design of the ADMIN audio upload path against
[Report 4_TDS_luannnfx05543.docx](Report%204_TDS_luannnfx05543.docx). The
numbered sections match the TDS so a reader can attach the delta without
rewriting the Word file.

The CSV/XLSX provider dump of UC-28 remains outstanding. Audio upload is
the ADMIN UI for staging a song; song-data JSON remains the object store
format that the ETag import upserts into MySQL.

## §2.3 Server-Side Pages — additional row

| URL pattern | Method | Controller#method | Template | Model attributes | Roles |
|-------------|--------|-------------------|----------|------------------|-------|
| `/admin/catalog/songs` | POST | `AdminCatalogController#uploadSongs` | redirect to `/admin/catalog`, or JSON when `X-Requested-With: XMLHttpRequest` | `drafts[]` (`SongDraftForm`: audio, cover, sourceProvider, title, artist, duration, bpm, isExplicit, isrc, genres, moods, tags) | ADMIN |

The separate Catalog Import screen (P-06c) was retired: `GET /admin/catalog`
carries the Add Song modal and the Sync Catalog control, and exposes
`uploadProviders` (from `mrs.catalog.providers`) for the vendor dropdown. It
does not list the staged prefix; that listing runs on
`POST /admin/catalog/sync` and after a successful audio upload. There is no
browser JSON-file upload.

## §5.4 Input Validation Strategy — File upload row

The TDS row currently reads "Catalog import accepts CSV and XLSX only". The
built screen accepts audio extensions `mp3`, `wav`, `flac`, `m4a`, `ogg`,
`aac` and cover extensions `jpg`/`jpeg`, `png`, `webp`; content-type
allowlist plus size caps (`mrs.catalog.media.max-audio-bytes` = 50 MB,
`max-cover-bytes` = 5 MB). The whole batch is validated before any S3 write
(the upload path still does not roll back a partial stage). Title and a registered
`sourceProvider` are required. Cover art is optional.

Servlet multipart limits are 50 MB/file and 600 MB/request, with
`file-size-threshold=256KB` so large parts spill to disk rather than the
t3.small heap.

Staged song-data JSON (written by this form, or put with the CLI) is still
validated by `SongJsonMapper` when **Sync Catalog** applies it to MySQL.

## §9.5 Implementation Conformance

| Area | Specified in TDS | Current build | Resolution |
|------|------------------|---------------|------------|
| UC-28 staging | Provider CSV/XLSX upload | Audio/artwork upload that generates the same JSON schema; CLI/S3 put of JSON remains | CSV/XLSX remains outstanding; browser JSON upload is not offered |
| Object storage (1.1, 1.4) | S3 holds staged catalog data and company-hosted audio; CloudFront delivers it | Audio and artwork from Add Song are PutObject'd under `song-data/audio/<slug>/` and `song-data/artwork/<slug>/`; generated JSON uses the CloudFront public base URL | Matches the stated design. Pre-signed browser PUTs were not used; the app streams multipart parts through the existing IAM S3 client |
| `POST /admin/catalog/songs` | Not in the original §2.3 table | Implemented, ADMIN + CSRF | This addendum |
| P-06c Catalog Import screen | Own page under `/admin/import` | Folded into P-06b: Add Song modal plus a Sync Catalog header action on `/admin/catalog` | One catalog screen owns create, read, update and delete; `/admin/import` no longer exists |
