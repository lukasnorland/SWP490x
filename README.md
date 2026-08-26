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
| **GAP-01** | No objective fitness signal (e.g. Spotify popularity) when choosing tracks |
| **GAP-02** | Playlists live in personal files/sheets — no shared store or reuse |
| **GAP-03** | Spreadsheet search cannot combine metadata and context efficiently |

MRS closes those gaps with centralized catalog + playlist management, metadata/LLM-assisted search, and popularity-aware ranking.

---

## Features

| ID | Feature |
|----|---------|
| FE-01 | Authentication & role-based access (ADMIN, Content Designer, Customer) |
| FE-02 | Profile management & personal playlist history |
| FE-03 | Multi-criteria metadata search (Genre, Mood, Artist, Tags) |
| FE-04 | LLM-assisted contextual search (with fallback to plain filters) |
| FE-05 | Recommendation & ranking using metadata match + Spotify popularity snapshot |
| FE-06 | Playlist create / edit / save (Draft), concurrency via optimistic locking |
| FE-07 | Publish to shared workspace; view and duplicate published playlists |
| FE-08 | CSV export of playlists |
| FE-09 | Admin: users, songs, metadata, catalog import, audit log |

**Out of scope (v1):** native mobile apps, public streaming, large-scale ML recommenders, commercial production infra, fully real-time Spotify sync.

---

## Roles

| Role | Purpose |
|------|---------|
| **ADMIN** | Users, catalog/metadata, import, system settings, audit; full playlist oversight |
| **Content Designer** | Primary operators — search, recommend, curate, publish, export |
| **Customer** | Stakeholders — view scoped published playlists; duplicate into own Draft; no export |

---

## Tech stack

| Layer | Choice |
|-------|--------|
| Backend | Java 25, Spring Boot 4.x, Spring Security, Spring Data JPA |
| Frontend | Thymeleaf (server-side rendered), Bootstrap 5.3 (self-hosted) |
| Database | MySQL 8.x |
| Object storage | Amazon S3 (audio/asset keys; pre-signed URLs for export) |
| LLM | External API (e.g. Gemini) for query interpretation only |
| Popularity | Batch/reference Spotify popularity snapshots (not live sync) |
| CI | GitHub Actions (`./mvnw verify` + JaCoCo) |
| Deployment target | AWS |

---

## Repository layout

```
SWP490x/
├── docs/                 # Vision & Scope, Project Plan, SRS, RTW, screen design, diagrams
├── mrs/                  # Spring Boot application
│   ├── src/main/java/…   # Application code
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   ├── db/migration/ # Schema + seed SQL (V1, V2)
│   │   ├── static/       # Design tokens, theme, CSS, JS, vendored Bootstrap
│   │   └── templates/    # Thymeleaf layouts, fragments, screens
│   └── pom.xml
├── .github/workflows/    # CI
└── README.md
```

Detailed requirements and design live under [`docs/`](docs/):

| Document | Description |
|----------|-------------|
| Report 1 — Vision & Scope | Problem, gaps, features, limitations |
| Report 2 — Project Plan | WBS, risks, process, tools |
| Report 3.0 — SRS | Scenarios SC-01–SC-06, features FT-01–FT-09, NFRs |
| Report 3.1 — MRS RTW | Traceability, permission matrix, data dictionary, business rules |
| Report 3.2 — Screen Design Spec | IA, flows F-01–F-06, screen specs |
| Report 4 — TDS | Architecture, interfaces, data model, security |
| TDS addendum — audio upload | P-06b `POST /admin/catalog/songs` against TDS §2.3, §5.4, §9.5 |
| `docs/diagrams/` | Context, use case, ERD, flows, sequence, playlist state machine |

**Outstanding documentation update:** the ERD and the RTW data dictionary still
need catalog columns on `song` (`audio_url`, `cover_url`, `bpm`, `is_explicit`,
`isrc`, `source_etag`, `ambience_a`, `ambience_b`, `ambience_source_url`) and the
`catalog_import_run` table.

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

Flyway applies the migrations under `mrs/src/main/resources/db/migration/` (V1 schema, V2 seed) on every startup — create the empty database and run the app.

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

The app sends two messages: the password-reset link (UC-02) and the login email
and initial password for an account an ADMIN has just created (BR-15).

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

### Song catalog

S3 is the source of truth for catalog songs; MySQL is the read model the UI
queries. Every create/update/delete of catalog identity or classification must
touch the staged JSON first, then MySQL, so the two stay in step. Import is
one-way S3 → MySQL and will not overwrite a row whose `source_etag` already
moved to a later put. Wash colours (`ambience_*`) are derived at import from
`coverUrl` and are MySQL-only. Song JSON is staged at
`s3://mrs-133857166188-assets/song-data/<externalSourceId>.json`, and an import
upserts those objects into `song` / `tag` / `song_tag`. Ways to stage a song:

- **Add Song** on P-06b (ADMIN drops one or more audio files, fills
  metadata and optional cover art per song; the server writes media under
  `song-data/audio/<vendor>/` and `song-data/artwork/<vendor>/`, generates the
  song JSON, then auto-imports)
- A direct `aws s3 cp` / console put of `<externalSourceId>.json`
- Pointing `mrs.catalog.local-dir` at a directory of `*.json` for offline/dev

The JSON object key is always `<externalSourceId>.json`. Audio/artwork uploads
mint a UUID for that id. Required fields are
`externalSourceId`, `sourceProvider`, and `title`. `sourceProvider` must be one
of `EpidemicSound`, `NCS`, or `OneOff` (SC-05). Unknown fields are ignored so
provider dumps can carry extra keys without failing the import. Optional fields
that are blank, zero, or negative are stored as null rather than rejecting the
row. Wash colours (`ambience_a` / `ambience_b`) are sampled at import from
`coverUrl` and are not part of the staged JSON.

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
usual. Audio is required; cover art is optional. Duration is read in the
browser from the file and can be edited before upload.

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
| `mrs.catalog.media.max-audio-bytes` | 50 MB | Per-file cap for the audio upload |
| `mrs.catalog.media.max-cover-bytes` | 5 MB | Per-file cap for cover art |
| `mrs.catalog.media.vendor-slugs.*` | epidemic / ncs / one-off | Folder name under `audio/` and `artwork/` |

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

### Run

```bash
cd mrs
./mvnw spring-boot:run
```

On Windows: `.\mvnw.cmd spring-boot:run`

### Test

```bash
cd mrs
./mvnw -B verify
```

CI runs the same verify step against MySQL 8 on every push/PR to `main`.

### Demo seed accounts (dev only)

Seeded in `V2__seed_sample_data.sql` (change immediately outside local demos):

| Email | Role |
|-------|------|
| `admin@mrs.local` | ADMIN |
| `cd@mrs.local` | CONTENT_DESIGNER |
| `customer@mrs.local` | CUSTOMER |

Password for all three: `Admin@2026` (forced change on first login is intended by FT-09 / BR-12).

---

## Implementation roadmap

Three two-week iterations after design:

1. **Iteration 1** — Auth, users & roles, song & metadata admin  
2. **Iteration 2** — Search/filter, recommendation & ranking, playlists, concurrency  
3. **Iteration 3** — Web UI, LLM-assisted search, shared workspace, CSV export  

### UI implementation status

The UI follows **Report 3.2 — Screen Design Spec**. Its foundation is in place: the design tokens of Part 1, the authenticated shell of section 4.0 with role-aware navigation (2.1), and the responsive rules of Part 5.

#### Styling approach

Bootstrap 5.3 supplies the component layer — buttons, forms, cards, tables, modals, dropdowns, tabs, pagination, toasts — and its JavaScript bundle supplies their behaviour. Report 3.2 does not name a CSS framework, so this is an implementation decision, made on three grounds: Bootstrap needs no Node toolchain, so the build stays `./mvnw` alone; it covers the accessible interactive components the spec calls for without hand-rolling them; and 5.3's dark mode plus CSS-variable theming let the spec's own palette drive it rather than the reverse.

Both Bootstrap files are vendored under `static/vendor/bootstrap/` rather than loaded from a CDN, so the app renders correctly with no internet access and carries no third-party runtime dependency — the same reasoning already applied to the self-hosted Inter font.

The stylesheets load in this order, and the order matters:

| File | Role |
|------|------|
| `vendor/bootstrap/bootstrap.min.css` | Bootstrap 5.3.8, unmodified |
| `css/tokens.css` | Every value from Part 1, and the only place a spec value is written down |
| `css/theme.css` | Re-points Bootstrap's `--bs-*` variables at those tokens under `data-bs-theme="dark"` |
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
| P-00 Login | Implemented — all five screen states, lockout after 5 failures in 15 min |
| P-01 Password Reset | Implemented — both steps, live BR-12 checklist, link emailed |
| Forced password change (FT-09) | Implemented |
| P-06a User Management | Implemented — Thymeleaf MVC CRUD: create + credentials email, filters, pagination, deactivate/reactivate with session invalidation, role change, resend |
| P-06b Song Catalog | Implemented as CRUD — Songs table with provider/tag/text filters, untagged and no-preview filters, pagination (partial fetch so the shell player stays mounted), CDN playback via clicking the song title, per-row edit modal (optimistic lock, HTTP 409 refresh-only, BR-06/DC-02; classification is written to MySQL and the staged song-data JSON), and delete (hosted audio/cover first, then staged JSON, then the MySQL row so the next import cannot recreate the song). Create is the Add Song modal, and Sync Catalog converts JSON already under the prefix. Authenticated shell soft-navigates sidebar/content links so the player survives leaving Catalog for Users, Audit Log, etc. The Tags dictionary tab is still outstanding |
| Catalog import (was P-06c) | Implemented, folded into P-06b — ADMIN audio + artwork upload (server writes media + generated song-data JSON, then auto-syncs into MySQL), Sync Catalog to convert JSON already under the prefix, per-row skip reasons, a last-sync line, and a scheduled poller. The separate Catalog Import screen was retired; browser JSON file upload was removed, though JSON remains the staged object format. The provider CSV/XLSX of UC-28 is outstanding |
| P-02, P-03 – P-06e | Scaffolded — real headings and navigation, with each specified zone marked as outstanding |

Each scaffolded screen renders its zones from the spec as dashed placeholders, so what remains on that screen is visible in the running app. Data-backed zones arrive with their feature slice.

---

## License / usage

Internal graduation project for company playlist-curation workflows over a licensed catalog. Not a public streaming product.
