# Music Recommendation System (MRS)

Internal web application for **playlist curation** over a licensed music catalog. Music Operation Specialists search, rank, and assemble campaign playlists in one place instead of relying on Excel/Sheets and scattered folders.

This repository is the graduation project for **SWP490x** (Funix).

| | |
|---|---|
| **Author** | Nguyễn Ngọc Luân |
| **Mentor** | Đào Thị Thanh |
| **Schedule** | 12 weeks (06/07/2026 – 27/09/2026) |
| **App module** | [`mrs/`](mrs/) |

---

## Why MRS exists

The company licenses ~5,000 Epidemic Sound tracks for music-game campaigns and seasonal events. Specialists currently spend about **one week per playlist** listening, filtering, and assembling songs by instinct. Three gaps drive the project:

| Gap | Problem |
|-----|---------|
| **GAP-01** | No objective fitness signal when choosing tracks |
| **GAP-02** | Playlists live in personal files/sheets — no shared store or reuse |
| **GAP-03** | Spreadsheet search cannot combine metadata and context efficiently |

MRS closes those gaps with centralized catalog + playlist management, metadata/LLM-assisted search, and metadata-match ranking.

---

## Features

| ID | Feature |
|----|---------|
| FE-01 | Authentication & role-based access (ADMIN, Content Designer, Customer) |
| FE-02 | Profile management (view account, display name, password) |
| FE-03 | Multi-criteria metadata search (Genre, Mood, Artist, Tags) |
| FE-04 | LLM-assisted contextual search (with fallback to plain filters) |
| FE-05 | Recommendation & ranking using metadata match (chip count, then title; optional Top-N) |
| FE-06 | Playlist create / edit / save (Draft); owner can share edit rights with other Content Designers; concurrent saves are optimistically locked (UC-19) |
| FE-07 | Publish to shared workspace; every internal user can view published playlists; Content Designers and ADMIN can duplicate one into their own Draft |
| FE-08 | CSV export of playlists |
| FE-09 | Admin: users, songs, metadata, catalog import, playlist oversight |
| FE-10 | In-app audio playback while curating, continuing across navigation; the preview bar walks up to 100 tracks of the current filter or ranked result |

**Out of scope (v1):** native mobile apps, public streaming, large-scale ML recommenders, commercial production infra, third-party chart or popularity APIs.

**Dropped from v1.** Playlist history on P-05 (UC-04 Zone B) is not built:
resume unfinished playlists from My Playlists (P-03a). The one remaining
leftover (provider CSV/XLSX on P-06b) is called out in the screen table below.

---

## Roles

| Role | Purpose |
|------|---------|
| **ADMIN** | Users, catalog/metadata, import, system settings, audit; full playlist oversight |
| **Content Designer** | Primary operators — search, recommend, curate, share Drafts with other Designers, publish, duplicate, export |
| **Customer** | Stakeholders — read published playlists in the Shared Workspace; no export, no playlist of their own |

---

## Tech stack

| Layer | Choice |
|-------|--------|
| Backend | Java 25, Spring Boot 4.1, Spring Security, Spring Data JPA, Flyway |
| Frontend | Thymeleaf (server-side rendered), Bootstrap 5.3.8 and Tom Select (both self-hosted) |
| Database | MySQL 8.x (MariaDB 10.11 on the demo host — wire-compatible, same driver and migrations) |
| Object storage | Amazon S3 — staged catalog JSON plus company-hosted audio and artwork |
| CDN | CloudFront in front of the media prefixes; the player and the shell wash read through it |
| LLM | Gemini through the Google GenAI SDK, for query interpretation only; optional |
| CI | GitHub Actions (`./mvnw verify` + JaCoCo) against MySQL 8 |
| Deployment target | AWS — one EC2 instance running the app and the database |

---

## Repository layout

```
SWP490x/
├── docs/                 # Vision & Scope, Project Plan, SRS, RTW, screen design, figures
│   ├── md/               # Reports converted to markdown for reading and diffing
│   ├── srs_fig/          # Context, use case, ERD, flows, playlist state machine
│   ├── tds_fig/          # Component, package, deployment, physical ERD, auth flow
│   └── design/           # Figma export — one folder per screen
├── infra/                # CloudFront stack and the S3 / IAM policy documents
├── mrs/                  # Spring Boot application
│   ├── src/main/java/…   # Application code
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   ├── catalog/      # Cached MusicBrainz genre list for the tag allowlist
│   │   ├── db/migration/ # V1 schema, V2 initial admin account
│   │   ├── static/       # Design tokens, theme, CSS, JS, self-hosted Inter, vendored Bootstrap + Tom Select
│   │   └── templates/    # Thymeleaf layouts, fragments, screens
│   ├── Dockerfile        # Two-stage build: ./mvnw package, then a JRE image
│   └── pom.xml
├── .github/workflows/    # CI
└── README.md
```

Detailed requirements and design live under [`docs/`](docs/):

| Document | Description |
|----------|-------------|
| Report 1 — Vision & Scope | Problem, gaps, features FE-01–FE-10, limitations LI-01–LI-05 |
| Report 2 — Project Plan | WBS, risks, process, tools |
| Report 3.0 — SRS | Scenarios SC-01–SC-06, features FT-01–FT-10, NFRs |
| Report 3.1 — MRS RTW | Traceability, permission matrix, data dictionary, business rules (workbook) |
| Report 3.2 — Screen Design Spec | IA, flows F-01–F-06, screen specs P-00–P-09 |
| Report 4 — TDS | Architecture, interfaces, data model, security |
| Report 5.1 — Unit Test Report | Service and component test cases and results |
| Report 5.2 — Integration Test Report | End-to-end procedures, expected results and execution records |
| [Search interaction and test cases](docs/search-interaction.md) | Tracked P-02 interaction decision and revised Search test procedures |

The reports themselves are Word and Excel files kept out of git (`.gitignore`
excludes `docs/*` apart from [`docs/aws-setup.md`](docs/aws-setup.md) and
[`docs/search-interaction.md`](docs/search-interaction.md)). Catalog
behaviour, Add Song validation, and the P-06b routes live in this README. To
read or diff a report against the code, convert the whole set:

```powershell
cd docs
pandoc "Report 4_TDS_luannnfx05543.docx" -f docx -t gfm --wrap=none `
  --extract-media=.\md\media -o .\md\report-4-tds.md
python .\md\_xlsx_to_md.py ".\Report 3.1_MRS_RTW_luannnfx05543.xlsx" .\md\report-3.1-rtw.md
```

`docs/md/` is gitignored along with the sources; it is a local reading aid, not
a second copy of the deliverable.

The local Word/Excel reports track the as-built. Every leftover recorded here
in September 2026 has since been closed: the Popularity columns, the Spotify
token subsection, the missing P-06f section and the P-05 playlist-history zone
are gone from the current SRS, Report 3.2 and TDS.

The P-02 panel-removal update revised the local SRS UC-10/UC-11, RTW use-case
and feature entries, Screen Design P-02 layout/interactions, and TDS search
flow. These sections describe prompt → Interpret → chips and results, with
any-chip inclusion on Search. Pop + Happy includes Pop-only and Happy-only
tracks, ranked below songs matching both. Manual metadata filtering remains
on P-06b / P-08, with AND across categories. Where the P-02 Figma frame still
shows the metadata rail, the layout text in Report 3.2 is the specification.

Report 5.2 procedures were revised for `SRC-IT-01`-`06` and `09`: prompt-based
entry, any-chip matches, empty results, pagination, ranking, Top-N validation,
immediate interpreted results, and chip/prompt refinement. Exact-count cases
need an isolated fixture built on a single chip, since inclusion is any-chip.
Report 5.1 service-level cases are unaffected. The reports themselves are not
in Git; this README and the tracked Search note are.

---

## Getting started

### Prerequisites

- JDK **25**
- MySQL **8.x**
- Maven Wrapper (included in `mrs/`)

### Database

```sql
CREATE DATABASE mrs CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

Flyway applies the migrations under `mrs/src/main/resources/db/migration/` (V1 schema, V2 initial admin account) on every startup — create the empty database and run the app.

If you applied an older schema by hand before Flyway was wired in, no action is needed: the app baselines an existing schema at V2 (`spring.flyway.baseline-version`) so migrations are not replayed over your tables.

### Configuration

Defaults in `mrs/src/main/resources/application.properties` (override with env vars):

| Variable | Default |
|----------|---------|
| `DB_URL` | `jdbc:mysql://localhost:3306/mrs?...` |
| `DB_USERNAME` | `root` |
| `DB_PASSWORD` | *(empty)* |

If you would rather not export variables, create `mrs/local.properties` and put
the same keys there in Spring form:

```properties
spring.datasource.username=your_user
spring.datasource.password=your_password
```

`application.properties` imports that file when it exists, and it is gitignored,
so real credentials never reach the repository.

### Email

The app sends five messages: the password-reset link (UC-02); the login email
and initial password for an account an ADMIN has just created (BR-15); a
deactivation notice; a role-change notice; and the account request raised from
the login page, which goes to `mrs.mail.from` rather than to the person who
asked for it.

**Without configuration it sends neither.** When `spring.mail.host` is unset, a
logging transport takes over and writes each message to the application log
instead, so a fresh checkout and the test suite run without a mail server and
you can still follow a reset link or read an initial password off the console.

To send for real, the app talks to Amazon SES over SMTP. Two things must be true
before a message leaves: SES has verified the sender address in the region you
point at, and you hold SMTP credentials for that region. Those credentials are
not an IAM access key — the SES console derives them from one under *SMTP
settings → Create SMTP credentials*, and a key pasted in their place only ever
fails authentication.

The block is not committed, since both the credentials and the verified sender
belong to whoever set the AWS account up. Put it in `mrs/local.properties`
beside the database account:

```properties
spring.mail.host=email-smtp.ap-southeast-1.amazonaws.com
spring.mail.port=587
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true
spring.mail.username=your_ses_smtp_user
spring.mail.password=your_ses_smtp_password
mrs.mail.from=the_verified_address
```

`spring.mail.host` is the switch: omit it and the logging transport stays, which
is the quickest way to review either flow without an AWS account at all, since
the whole message including the reset link lands in the console.

#### Reaching a real inbox while SES is in the sandbox

A new SES account sits in the *sandbox*, and the restriction that catches people
out is that it applies to the recipient, not only to the sender: SES refuses to
deliver to any address it has not verified. Verification is per address and only
needed once, but the owner of the mailbox has to follow the link themselves.

The address is checked before any of this runs: a malformed address answers 422
with the invalid-address message (the SRS catalogue has no code for it) and one
already on an account answers 409 with MSG_012, so a
duplicate never reaches the SES step and never depends on the sandbox to be
caught (UC-06 E2, DC-06).

**Preferred path for a live demo:** on P-06a, open *New user*, type the internal
email, and click **Verify for SES**. That button calls
`ses:CreateEmailIdentity` through the app (SES API credentials from
`AWS_PROFILE` / the EC2 instance role — not the SMTP username in
`local.properties`). AWS emails the confirmation link; after the owner confirms
it, press *Create account*.

The order still matters, because the credentials message goes out the moment
*Create account* is pressed. The CLI remains available if the button cannot
reach AWS:

```bash
aws sesv2 create-email-identity --email-identity someone@example.com --region ap-southeast-1
# the owner opens the message from AWS and follows the link, which expires after
# 24 hours; run the command again (or click Verify for SES again) to issue a fresh one
aws sesv2 get-email-identity --email-identity someone@example.com --region ap-southeast-1
```

Once `VerificationStatus` reads `SUCCESS`, create the account in P-06a and the
message arrives. Do it the other way round and the send is rejected, so the
screen reports MSG_022: the account exists but nobody was told about it. That is
UC-07 E3 behaving as specified rather than a defect, and it recovers without
deleting anything — verify the address, then press *Resend credentials* in the
user table. A resend issues a *new* initial password, because only the hash is
kept and the original cannot be repeated.

Two ways to skip the dance. `success@simulator.amazonses.com` and
`bounce@simulator.amazonses.com` need no verification and simulate a clean
delivery and a hard bounce, which is the cheapest way to exercise both outcomes
of UC-07 E3. Alternatively an address such as `you+designer@gmail.com` does need
its own verification, but the confirmation lands in your own inbox, so one
person can stand up several distinct test users unaided.

Requesting production access lifts the recipient restriction altogether, at the
cost of an AWS support review. The Verify for SES button is then unnecessary for
recipients (the sender identity must still be verified).

Message settings and their defaults:

| Property | Default | Purpose |
|----------|---------|---------|
| `mrs.mail.from` | `no-reply@mrs.local` | Sender address |
| `mrs.mail.from-name` | `MRS` | Sender display name |
| `mrs.mail.base-url` | `http://localhost:8080` | Origin for links in messages |
| `mrs.mail.ses-region` | `ap-southeast-1` | Region for the Verify for SES API call |
| `mrs.mail.aws-profile` | `mrs-admin` | Named AWS profile for that API call; clear on EC2 to use the instance role |

`mrs.mail.base-url` matters as soon as the app is not read on the machine it
runs on: a message is opened outside any request, so links are built against
this value rather than the incoming host. `mrs.mail.aws-profile` defaults to
`mrs-admin` for the same reason: without it, Verify for SES can pick up another
AWS account from the machine default credentials. That profile is resolved via
`aws configure export-credentials` because `aws login` stores a login session
the Java profile provider cannot read. On the demo EC2 host set
`mrs.mail.aws-profile=` (empty) so the instance role is used instead.

#### Account requests from the login page

There is no self-registration (FE-01). The login screen carries a *Request an
account* modal instead: `POST /register-request` is anonymous, validates the
address, and **creates no user row**. All it does is mail `mrs.mail.from` so an
ADMIN can decide and then create the account on P-06a in the usual way. With no
mail configured the request lands in the application log like every other
message, which is enough to demonstrate the flow offline.

### Song catalog

S3 is the source of truth for catalog songs; MySQL is the read model the UI
queries. Every create/update/delete of catalog identity or classification must
touch the staged JSON first, then MySQL, so the two stay in step. Import is
one-way S3 → MySQL and will not overwrite a row whose `source_etag` already
moved to a later put. Wash colours (`ambience_*`) are derived at import from
`coverUrl` and are MySQL-only. Song JSON is staged at
`s3://mrs-133857166188-assets/song-data/<externalSourceId>.json`, and an import
upserts those objects into `song` / `tag` / `song_tag`. Ways to stage a song:

- **Add Song** on P-06b (ADMIN drops one or more audio files, fills metadata and
  cover art per song; the server writes media under `song-data/audio/<vendor>/`
  and `song-data/artwork/<vendor>/`, generates the song JSON, then auto-imports)
- A direct `aws s3 cp` / console put of `<externalSourceId>.json`
- Pointing `mrs.catalog.local-dir` at a directory of `*.json` for offline/dev

The JSON object key is always `<externalSourceId>.json`. Audio/artwork uploads
mint a UUID for that id. Required fields are
`externalSourceId`, `sourceProvider`, and `title`. `sourceProvider` must be one
of `EpidemicSound`, `NCS`, or `OneOff` (SC-05). Unknown fields are ignored so
provider dumps can carry extra keys without failing the import. Optional fields
that are blank, zero, or negative are stored as null rather than rejecting the
row. Genre and mood names that the taxonomy does not recognise are kept as Tags
rather than dropped. Wash colours (`ambience_a` / `ambience_b`) are sampled at
import from `coverUrl` and are not part of the staged JSON.

```json
{
  "externalSourceId": "GB2LD0901581",
  "sourceProvider": "NCS",
  "title": "Shine",
  "artist": "Spektrem",
  "duration": 255,
  "bpm": 128,
  "isExplicit": false,
  "isrc": "GB2LD0901581",
  "audioUrl": "https://d34ixswlpjs53y.cloudfront.net/song-data/audio/ncs/GB2LD0901581.mp3",
  "coverUrl": "https://d34ixswlpjs53y.cloudfront.net/song-data/artwork/ncs/GB2LD0901581.jpg",
  "genres": ["Electronic"],
  "moods": ["Energetic"],
  "tags": ["NCS"]
}
```

| Field | Type | Notes |
|-------|------|--------|
| `externalSourceId` | string | Required. Unique per provider; also the object filename |
| `sourceProvider` | string | Required. `EpidemicSound`, `NCS`, or `OneOff` |
| `title` | string | Required. Truncated to 255 characters |
| `artist` | string | Truncated to 255. Also stored as an ARTIST tag |
| `duration` | integer | Seconds. Non-positive values are dropped |
| `bpm` | integer | Non-positive values are dropped |
| `isExplicit` | boolean | |
| `isrc` | string | Truncated to 20 characters |
| `audioUrl` | string | HTTPS URL the player streams. Truncated to 500 |
| `coverUrl` | string | HTTPS URL for art and the shell wash. Truncated to 500 |
| `genres` | string[] | Become GENRE tags |
| `moods` | string[] | Become MOOD tags |
| `tags` | string[] | Become TAGS tags |

#### Add Song is stricter than the JSON contract

The table above is what **import** enforces, and it is deliberately permissive
so a provider dump lands rather than bouncing. The Add Song form on P-06b is the
curated path, so it refuses a row unless it also carries:

| Requirement | Detail |
|-------------|--------|
| Artist | Non-blank |
| ISRC | Non-blank, and not already held by another song — duplicates are rejected before anything is written |
| Genres | At least one, and every name must be a known MusicBrainz genre |
| Moods | At least one, and every name must be on the mood list |
| Audio | **Required** — `mp3`, `wav`, `flac`, `m4a`, `mp4`, `ogg`, `aac`, at most 100 MB |
| Cover art | **Required** — `jpg`/`jpeg`, `png`, `webp`, at most 5 MB |
| Batch size | At most 10 songs per upload |

Content type is checked against an allowlist as well as the extension; a browser
that sends nothing, or `application/octet-stream` for a dragged file, falls back
to the type the extension implies. Each entry is validated on its own, and a
rejected one no longer holds the rest of the batch back: the valid entries are
staged and imported, and every rejection travels with them into the same
`CATALOG_IMPORT` run — the summary counts it among the skipped rows, and the
audit `details` carry `skippedRows` with the key, the reason, and MSG_019 or
MSG_020 where one applies. A duplicate ISRC inside the batch is caught on the
server rather than in the browser, so the per-entry result is what reports it
(UC-37 E3). A failure partway through the writes themselves is reported rather
than rolled back.

Genres and moods are closed lists here and freeform on import. That asymmetry is
intentional: a vendor dump should not be lost because it used a genre name we do
not know, but an ADMIN typing into the form should not quietly invent a new one.

Epidemic and OneOff catalog entries that arrived as provider dumps keep
`audioUrl` / `coverUrl` on the vendor CDN; only the JSON object is in our
bucket. NCS audio and covers were rehosted under `song-data/audio/ncs/` and
`song-data/artwork/ncs/`. Songs uploaded through Add Song always rehost both
files, regardless of vendor:

```
song-data/audio/<vendor-slug>/<uuid>.<ext>
song-data/artwork/<vendor-slug>/<uuid>.<ext>
song-data/<uuid>.json
```

Vendor slugs: `EpidemicSound` → `epidemic`, `NCS` → `ncs`, `OneOff` → `one-off`.
The staged JSON `audioUrl` / `coverUrl` values point at CloudFront
(`d34ixswlpjs53y.cloudfront.net`). The import copies those URLs into MySQL as
usual. Duration is read in the browser from the file and can be edited before
upload. The app streams multipart parts through the IAM S3 client; there are
no pre-signed browser PUTs and no browser JSON-file upload.

An import compares each object's ETag against the `song.source_etag` of the row
it produced, so it only downloads objects that are new or whose content changed.
Re-uploading a corrected file for a song already in the catalog updates it in
place; running an import twice does nothing the second time. Triggers that call
the same service:

| Trigger | When |
|---------|------|
| **Add Song** on P-06b | After writing media + generated JSON |
| `mrs.catalog.import-on-start=true` | Once at startup, for the first bulk load |
| **Sync Catalog** on P-06b | Immediately, attributed to the ADMIN who pressed it — converts staged song-data JSON into MySQL |
| `CatalogSyncJob` | Every `mrs.catalog.sync.interval`, when `mrs.catalog.sync.enabled=true` |

P-06b write routes (ADMIN + CSRF). `GET /admin/catalog` also returns a fragment
when the request carries `X-MRS-Partial: results`, so the preview player stays
mounted across paging and filtering.

| Path | Method | What it does |
|------|--------|--------------|
| `/admin/catalog/songs` | POST | Add Song — stage media + JSON, then queue sync |
| `/admin/catalog/sync` | POST | Sync Catalog now |
| `/admin/catalog/sync/status` | GET | JSON progress for the import modal |
| `/admin/catalog/tags/suggest` | GET | Typeahead for genre / mood / tag fields |
| `/admin/catalog/tags` | GET | Tags tab — the dictionary with a per-name song count |
| `/admin/catalog/tags` | POST | Add a name to a vocabulary (Genre and Mood must be on the allowlist) |
| `/admin/catalog/tags/{id}/rename` | POST | Rename an unused name |
| `/admin/catalog/tags/{id}/delete` | POST | Remove an unused name |
| `/admin/catalog/{id}` | POST | Save classification (409 on stale version) |
| `/admin/catalog/{id}/delete` | POST | Delete hosted media, staged JSON, then the row |

The Tags tab writes MySQL only, and refuses to touch a name that is on at least
one song: renaming or deleting it would change `song_tag` without patching the
staged JSON that owns that classification. Change those from the song instead.
Unused rows are dictionary entries for typeahead and the next song edit, so
retiring them is safe — and an import already drops unused names afterwards.

| Property | Default | Meaning |
|----------|---------|---------|
| `mrs.catalog.bucket` | `mrs-133857166188-assets` | Bucket holding the staged JSON |
| `mrs.catalog.prefix` | `song-data/` | Key prefix within it |
| `mrs.catalog.region` | `ap-southeast-1` | Region of the bucket |
| `mrs.catalog.aws-profile` | `mrs-admin` | Named profile; clear on EC2 to use the instance role |
| `mrs.catalog.local-dir` | *(empty)* | Set to a directory of `*.json` to import from disk instead of S3 |
| `mrs.catalog.providers` | `EpidemicSound,NCS,OneOff` | Registered providers; anything else is skipped (SC-05) |
| `mrs.catalog.import-on-start` | `false` | Import the whole prefix at startup |
| `mrs.catalog.sync.enabled` | `false` | Register the scheduled poller |
| `mrs.catalog.sync.interval` | `15m` | Delay between the end of one sync and the start of the next |
| `mrs.catalog.cover-art.*` | *(on)* | Sample cover colours during import for the shell wash |
| `mrs.catalog.media.public-base-url` | CloudFront origin | Prefix for generated `audioUrl` / `coverUrl` |
| `mrs.catalog.media.audio-prefix` | `song-data/audio/` | Key prefix for company-hosted audio |
| `mrs.catalog.media.artwork-prefix` | `song-data/artwork/` | Key prefix for company-hosted cover art |
| `mrs.catalog.media.max-audio-bytes` | 100 MB | Per-file cap for the audio upload |
| `mrs.catalog.media.max-cover-bytes` | 5 MB | Per-file cap for cover art |
| `mrs.catalog.media.audio-types` | five `audio/*` types | Content-type allowlist for the upload |
| `mrs.catalog.media.cover-types` | `image/jpeg,image/png,image/webp` | Content-type allowlist for the upload |
| `mrs.catalog.media.vendor-slugs.*` | epidemic / ncs / one-off | Folder name under `audio/` and `artwork/` |

The servlet is sized to match: `spring.servlet.multipart.max-file-size=100MB`
and `max-request-size=1100MB`, with a 256 KB threshold so large parts spill to
disk rather than the t3.small heap. Several Tomcat defaults are raised alongside
them (`max-swallow-size`, `max-part-count`, `max-part-header-size`,
`max-http-form-post-size`) because otherwise an oversized upload surfaces as a
dropped connection instead of a validation message.

Setting `mrs.catalog.local-dir` to a directory of `*.json` reads the staged files
straight off disk, so a fresh checkout can populate the catalog with no AWS
credentials — which is also how the tests exercise the import. Enable
`mrs.catalog.sync.enabled=true` on the deployed instance so an upload to S3 is
picked up without anyone opening the admin UI.

P-06b does not list the prefix on page load — opening the Songs screen only
reads MySQL for the last-sync line. Listing and the ETag check run when you
press **Sync Catalog**, or after an audio upload stages files. Staging still
needs a working AWS profile, or a local directory:

```properties
# mrs/local.properties — stay off S3 while developing
mrs.catalog.local-dir=./catalog-staging
```

Audio, artwork, and generated JSON then land under that folder. Run
`aws login --profile mrs-admin` (and restart the app) when you want the real
bucket again.

### Concurrent playlist edits

Two Content Designers on the same shared Draft is the normal case, not the edge
case, so every playlist write is optimistically locked (UC-19, BR-06). Each
screen renders the version it read, each mutation submits it back as
`expectedVersion`, and the service refuses a write whose version has already
moved on. Nothing is merged and nothing is overwritten: the second writer is
told what happened and chooses (BR-11, NFR-A02).

A rejected save renders the conflict screen with **HTTP 409** rather than
redirecting, because a redirect would drop both the status and the change that
was refused:

| Path | Method | What it does |
|------|--------|--------------|
| `/playlists/{id}/clone-on-conflict` | POST | Copy the playlist as it stands now with the rejected change applied, into a new Draft owned by the requester |

The screen offers up to three ways out:

- **Refresh & Reapply** — reopen the playlist at its current version and redo
  the edit on top of what the other person saved.
- **Clone as New Playlist** — only for a content edit (add, remove, reorder,
  rename). Publishing, deleting, or resharing a playlist someone else has
  already changed has nothing to carry into a copy, so those offer refresh
  alone.
- **Discard** — walk away. The in-progress edit is gone and has to be retyped;
  the app never merges it in behind anyone's back.

The copy starts from the source as it stands now and takes the pending change on
top. There is no version history to rebuild the snapshot the requester was
looking at, and copying stale content would be exactly the silent data loss this
is here to prevent. No lineage is recorded either way — the unique name is what
tells the copies apart.

A playlist is created at **v1** and each accepted change adds exactly one
(DC-11), because this number is shown to whoever lost the race and a brand-new
playlist reading `v0` invites the wrong question. A song starts at **v0**
instead: its version is a hidden form token nobody reads, and it also moves when
an import rewrites the row, so it is not a count of anybody's edits.

Song catalog edits on P-06b use the same 409, but refresh only: a song is
licensed identity, so there is nothing to clone (BR-06, DC-02). An import
touching a row while an admin has the edit modal open is a conflict like any
other (UC-29 E2).

### Contextual search

FT-04 turns a free-text prompt on P-02 into catalog filters. With
`mrs.llm.api-key` set, `POST /search/interpret` calls Gemini through the Google
GenAI SDK; without it the app matches the prompt against the tag vocabulary
instead, and falls back again to a plain title/artist search when nothing maps.
All three paths render the same screen, but the keyword fallback announces
itself: whenever Gemini failed or mapped to no catalog chips, P-02 flashes
*Could not interpret that as catalog filters. Showing keyword matches instead.*
A missing key therefore reads as that warning plus chips that are only ever
literal tag names. A Gemini call that times out or errors returns no
interpretation at all rather than guessing offline moods, so the request takes
the keyword path and the `recommendation_log` row records the fallback
(UC-11 E1, BR-07).

P-02 is a prompt-and-results screen: enter a playlist need, optionally set
Top-N beside **Interpret**, and submit to see chips and ranked results immediately.
Remove a chip to refresh the results without interpreting again; edit the prompt
and Interpret again to change or add criteria. Manual metadata dropdowns and
the Filter/Clear form belong to Songs (P-08) and the admin catalog (P-06b).
Do not add a metadata filter panel or a second Search step to P-02.
Top-N is optional; 0 or a negative integer returns HTTP 422 with an error under
the prompt's Top-N field, retaining the entered prompt and value. Invalid Top-N
does not run interpretation or a result query, on both GET `/search` and
POST `/search/interpret`.

Every play control points at `GET /songs/{id}/play` rather than the raw media
URL. The endpoint answers 302 to the audio recorded against the song, or HTTP
404 with MSG_029 *Audio is not available for this song.* when the song carries
none — so a row whose audio is missing renders as a disabled control with that
tooltip, a hand-built request gets the same answer, and the preview queue skips
the entry silently (UC-33 E1). It is a curation surface, so the route sits
beside `/search` and `/songs` under the ADMIN / Content Designer rule.

This restores the prompt-based interaction introduced in `6b1d4e5` and
supersedes the panel added in `966e28d` to follow the older test design.
The panel-only vocabulary lookup and shared-fragment options were removed;
the filter parameters used by interpretation, paging, chip removal, preview
and create-from-results remain in place. Songs/Catalog retain their filter UI.

Search UI regression coverage lives in `SearchFlowTest`; ranking, chip removal,
preview queues and create-from-results coverage remain in `SearchServiceTest`.
The current interaction contract and manual test procedures are recorded in
[Search interaction and test cases](docs/search-interaction.md).

The suite ran green on 17 September 2026: **728 tests, 0 failures, 2 skipped**.
The 14 errors in that run are all environmental, not product defects —
`CoverAmbienceServiceTest` (7) and `LoginPageSmokeTest` (5) cannot open a
loopback socket in the sandbox, and `GeminiApiSmokeTest` (2) calls the live
API. The revised browser integration procedures have not been rerun; evidence
from the earlier panel UI does not carry over to them.

The key is a credential, so it belongs in `mrs/local.properties` beside the
database account:

```properties
mrs.llm.api-key=your_gemini_key
```

| Property | Default | Meaning |
|----------|---------|---------|
| `mrs.llm.api-key` | *(empty)* | Blank keeps the app on vocabulary matching, with no network call |
| `mrs.llm.model` | `gemini-3.8-flash` | Model used for interpretation |
| `mrs.llm.timeout` | `30s` | Per-call budget. P-06d can set 1–30 seconds; default matches this value |
| `mrs.llm.min-query-chars` | `10` | Shorter prompts skip interpretation |
| `mrs.llm.max-query-chars` | `200` | Longer prompts are refused |
| `mrs.llm.provider` | `gemini` | Reserved for a second provider; nothing reads it today |

Ranking is the metadata match of FE-05. A song enters the P-02 result set if it
hits **any** selected chip or the keyword — Pop + Happy still includes Pop-only
and Happy-only tracks — then the set is ordered by how many chips each song
matched, then by title. Partial matches sit lower; they are not dropped, so a
curator assembling a playlist is not left with an empty candidate set. An
optional Top-N caps the ranked list before paging. Catalog browse (P-06b /
P-08) stays AND-across categories.

Every interpret writes one `recommendation_log` row — the query, the resolved
filters, whether the LLM ran and whether it succeeded, and the result count.
Paging and chip removal do not: they query the existing criteria without
calling the interpreter again.

### Run

```bash
cd mrs
./mvnw spring-boot:run
```

On Windows: `.\mvnw.cmd spring-boot:run`

A two-stage `mrs/Dockerfile` packages the same app, so the demo host does not
need a JDK or Maven of its own. It builds with the wrapper inside the image and
ships only a JRE layer, and it carries no configuration: pass the database and
any credentials the way `application.properties` expects them.

```bash
cd mrs
docker build -t mrs .
docker run --rm -p 8080:8080 -e DB_URL=jdbc:mysql://host.docker.internal:3306/mrs \
  -e DB_USERNAME=root -e DB_PASSWORD=secret mrs
```

### Test

```bash
cd mrs
./mvnw -B verify
```

CI runs the same verify step against MySQL 8 on every push/PR to `main`. The
suite includes ArchUnit rules (`ArchitectureTest`) so a controller cannot drift
out of `web`, and the repository layer cannot start depending on it. Live Gemini
classes that talk to Google (`GeminiApiSmokeTest` and the live method on
`GeminiLlmInterpreterLiveTest`) are skipped unless `MRS_LIVE_GEMINI=1` is set
and `mrs.llm.api-key` is present — the same opt-in as `MRS_LIVE_AWS=1` for S3.

### Security and operations

Defaults that matter once the app is not on localhost, none of which need
configuration:

| Concern | Behaviour |
|---------|-----------|
| Content Security Policy | `default-src 'self'` with `frame-ancestors 'none'`; scripts and fonts are same-origin only, which the vendored Bootstrap / Tom Select / Inter already satisfy. Images and audio may also come from HTTPS (CloudFront). No template carries an inline `<script>` or an `onclick`, so no `unsafe-inline` is needed for scripts |
| Other security headers | `Referrer-Policy: strict-origin-when-cross-origin` and `Permissions-Policy: camera=(), microphone=(), geolocation=()`. `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` and HSTS come from the Spring Security defaults; HSTS only goes out on a secure request, so it needs TLS terminating at the app rather than at a plain-HTTP proxy |
| Request correlation | `CorrelationIdFilter` stamps every response with `X-Request-Id` (echoing an incoming one, e.g. from a load balancer) and puts the same value on the MDC, so `logging.pattern.console` prints it and one call's log lines can be grepped together |
| Health check | `/actuator/health` is anonymous for a load balancer; every other Actuator endpoint is ADMIN-only, and only `health` is exposed at all |
| Password hashing | BCrypt at cost 12 |
| Password change | The new password is refused if it matches the current hash, on top of the BR-12 checklist |
| Failed sign-in and 403 | Audited — see P-06e in the screen table |

### Initial admin account (dev only)

Seeded in `V2__seed_admin_account.sql` (change immediately outside local demos):

| Email | Role |
|-------|------|
| `admin@mrs.local` | ADMIN |

Password: `Admin@2026` (forced change on first login is intended by FT-09 / BR-12). Create Content Designer and Customer accounts from the admin screens.

---

## Implementation roadmap

Three two-week iterations after design:

1. **Iteration 1** — Auth, users & roles, song & metadata admin  
2. **Iteration 2** — Search/filter, recommendation & ranking, playlists, concurrency  
3. **Iteration 3** — Web UI, LLM-assisted search, shared workspace, CSV export  

All three slices have landed. Playlist history on P-05 was dropped from v1; the
provider CSV/XLSX leftover on P-06b is listed in the screen table below.

### UI implementation status

The UI follows **Report 3.2 — Screen Design Spec**. Its foundation is in place: the design tokens of Part 1, the authenticated shell of section 4.0 with role-aware navigation (2.1), and the responsive rules of Part 5.

#### Styling approach

Bootstrap 5.3 supplies the component layer — buttons, forms, cards, tables, modals, dropdowns, tabs, pagination, toasts — and its JavaScript bundle supplies their behaviour. Report 3.2 does not name a CSS framework, so this is an implementation decision, made on three grounds: Bootstrap needs no Node toolchain, so the build stays `./mvnw` alone; it covers the accessible interactive components the spec calls for without hand-rolling them; and 5.3's dark mode plus CSS-variable theming let the spec's own palette drive it rather than the reverse.

One thing Bootstrap has no component for is the genre / mood / tag fields on P-06b, which need a searchable multi-select fed from the server: chips with remove buttons, typeahead over the MusicBrainz genre list, and freeform entry for Tags but not for Genre or Mood. Bootstrap offers dropdowns, not comboboxes, and dropped its typeahead back in v3. **Tom Select** fills exactly that gap and nothing else — it is bound to the genre, mood and tag fields of the Edit Song modal and of each song section in Add Song, and nowhere else. The underlying control stays a plain comma-separated text input, so the form still submits correctly with JavaScript off. Where a closed, short list is enough, the catalog filters stay ordinary Bootstrap dropdowns with checkboxes.

Bootstrap and Tom Select are both vendored under `static/vendor/` rather than loaded from a CDN, so the app renders correctly with no internet access and carries no third-party runtime dependency — the same reasoning already applied to the self-hosted Inter font.

The stylesheets load in this order, and the order matters:

| File | Role |
|------|------|
| `vendor/bootstrap/bootstrap.min.css` | Bootstrap 5.3.8, unmodified |
| `css/tokens.css` | Every value from Part 1, and the only place a spec value is written down |
| `css/theme.css` | Re-points Bootstrap's `--bs-*` variables at those tokens under `data-bs-theme="dark"` |
| `vendor/tom-select/tom-select.bootstrap5.min.css` | Tom Select in its Bootstrap 5 skin, so it inherits the `--bs-*` variables rebound above rather than carrying a second palette |
| `css/mrs.css` | Only what Bootstrap cannot express — see below |
| `css/shell.css` | The application shell of 4.0 — sidebar, glass top bar, preview bar — and the responsive rules of Part 5 |

Because `theme.css` rebinds Bootstrap's variables instead of overriding component rules one by one, a spec change means editing `tokens.css` and nothing else. Screens use ordinary Bootstrap class names and utilities, so they can be read against the Bootstrap documentation directly.

Components are taken from Bootstrap wherever it has one, including several that are easy to miss: the sidebar is an `offcanvas` (so the mobile drawer gets a focus trap, Escape handling and scroll lock without custom code), its entries are `nav-pills`, the breadcrumb trail is `breadcrumb` with the spec's `>` divider, the top bar is `sticky-top`, the preview bar is `fixed-bottom` with a `progress` element, in-page tabs are `nav-underline`, loading states are `placeholder`, and the password show/hide control is an `input-group`. Shell stacking deliberately uses Bootstrap's z-index scale so it cannot collide with dropdowns, modals or the offcanvas backdrop.

What remains in `mrs.css` needs a CSS property or selector Bootstrap has no utility for, and each rule says which:

- SVG stroke and sizing for the Lucide icon set (1.5)
- arbitrary `linear-gradient` fills, for the accent gradient — Bootstrap's `.bg-gradient` is a fixed white overlay
- `border-style: dashed`, for the zone placeholders
- `font-variant-numeric: tabular-nums`, for numeric table columns
- `backdrop-filter: blur()`, for the Glass surfaces of 1.2
- hover-revealed row actions, and the fixed dimensions of 1.4 that fall between Bootstrap's spacer steps

| Screen | State |
|--------|-------|
| P-00 Login | Implemented — all five screen states, plus the account-request modal. A wrong password answers MSG_009; the 5th failure inside the window locks the account for the configured window and the banner carries MSG_008. The lock end time is fixed when the lock trips, the form stays usable, and each further attempt is refused until it passes |
| P-01 Password Reset | Implemented — both steps, live BR-12 checklist, link emailed |
| P-02 Search & Recommendation | Implemented — prompt and optional Top-N beside Interpret; chips and ranked results appear immediately, with no metadata panel or separate Search action. Gemini interprets the prompt (vocabulary matching when no key is set); any-chip inclusion retains partial matches, ranked lower. Supports chip removal, multi-select add-to-playlist, and "create playlist from every result" across pages. Invalid Top-N returns 422 inline before interpretation. Valid interpretation writes a `recommendation_log` row; paging and chip removal reuse criteria without another interpretation/log |
| P-03a My Playlists | Implemented — playlists you own plus those shared with you; status and text filters, pagination, create, rename, duplicate, delete, publish, CSV export |
| P-03b Playlist Detail | Implemented — ordered song table with preview playback, add / remove / reorder while Draft, collaborator list (owner and ADMIN grant/revoke Content Designers; collaborators can edit songs in a Draft, but cannot publish, unpublish, delete, or invite). Publish and unpublish are owner or ADMIN. Duplicate creates an independent Draft with a unique name and no lineage back to the source. Every mutation is optimistically locked (UC-19, BR-06): a save at a version someone else has already moved past returns HTTP 409 and the conflict screen |
| P-04a Shared Workspace | Implemented — card grid of published playlists with owner and text filters, open to all three roles. BR-04 scoping is outstanding, so every published playlist is listed |
| P-04b Published Playlist View | Implemented — read-only song list; Duplicate and Export CSV are curator-only; unpublish is owner or ADMIN. A Draft id returns 403 rather than 404 |
| P-05 My Profile | Implemented — view account (UC-04), edit display name and password (UC-05). Playlist history was dropped; curators resume work from My Playlists |
| P-06a User Management | Implemented — Thymeleaf MVC CRUD: create + credentials email, filters, pagination, deactivate/reactivate with session invalidation, role change, resend. Deactivating a Designer or demoting them to Customer opens a successor picker per owned playlist (acting ADMIN or an existing collaborator) |
| P-06b Song Catalog | Implemented as CRUD — Songs and Tags tabs. Songs is a table with provider / genre / mood / artist / tag / text filters and an *Untagged only* switch (ADMIN only; the designer browse of P-08 has no use for it), pagination (partial fetch so the shell player stays mounted), a per-row untagged warning for DC-03 and a catalog-wide untagged count, CDN playback via clicking the song title, per-row edit modal (optimistic lock, HTTP 409 refresh-only, BR-06/DC-02; classification is written to MySQL and the staged song-data JSON), and delete (hosted audio/cover first, then staged JSON, then the MySQL row so the next import cannot recreate the song). Create is the Add Song modal (audio + artwork; the server writes media and generated song-data JSON, then auto-syncs into MySQL). Sync Catalog converts JSON already under the prefix, with per-row skip reasons, a last-sync line, and a scheduled poller. Authenticated shell soft-navigates sidebar/content links so the player survives leaving Catalog for Users, Audit Log, etc. The Tags tab is the vocabulary dictionary: every name with the number of songs carrying it, add for any vocabulary, and rename/delete for unused names only. The provider CSV/XLSX of UC-28 is still outstanding |
| P-06d System Settings | Implemented — three live sections: General (session inactivity, lockout threshold/window, reset-link validity), Catalog (provider name plus S3 folder slug: lowercase letters, digits and hyphens, 2–40 characters; delete cascades hosted media, staged JSON and MySQL after a typed-name confirm), and LLM (Gemini model dropdown, 1–30s timeout defaulting to `mrs.llm.timeout` 30s, min/max query chars). Save writes General and LLM together; Reset restores those defaults (providers stay). Values persist in `system_setting` / `catalog_provider`, are audited with before/after JSON (BR-10), and take effect without a restart. The Gemini API key stays in `local.properties` and is never shown |
| P-06e Audit & Recommendation Log | Implemented — ADMIN browses `audit_log` and `recommendation_log` (underline tabs, date / actor / action or user / LLM filters, newest-first pages of 20, expandable JSON). User administration, song edit/delete, tag dictionary add/rename/delete (`TAG_CREATE` / `TAG_RENAME` / `TAG_DELETE`), a failed sign-in against a registered address (`LOGIN_FAILED`) and an authenticated caller hitting a URL their role cannot use (`ACCESS_DENIED`) all write audit rows (BR-10). Rows older than 12 months are purged daily; there is no manual delete |
| P-06f All Playlists | Implemented — ADMIN list of every playlist, Draft or Published; inspect reuses P-03b with song edits and export hidden. ADMIN can publish or unpublish from that view, and collaborator share/remove still works there |
| P-07 First-Login Password Change | Implemented — enforced by an interceptor, not only by the post-login redirect |
| P-08 Song Browse | Implemented — the Content Designer's read-only view of the catalog: the same table and the same provider / genre / mood / artist / tag / text filters as P-06b, AND across vocabularies, with no *Untagged only* switch and no edit or delete. ADMIN opening `/songs` is redirected to P-06b |
| P-09 System Message Pages | Implemented — 403, 404 and 500 |

---

## License / usage

Internal graduation project for company playlist-curation workflows over a licensed catalog. Not a public streaming product.
