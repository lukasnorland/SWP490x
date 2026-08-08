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
| `docs/diagrams/` | Context, use case, ERD, flows, sequence, playlist state machine |

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

Flyway applies the migrations under `mrs/src/main/resources/db/migration/` (V1 schema, V2 sample data) on every startup — create the empty database and run the app.

If you applied V1/V2 by hand before Flyway was wired in, no action is needed: the app baselines an existing schema at V2 (`spring.flyway.baseline-version`) so those two migrations are not replayed over your tables.

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
| P-06a User Management | Implemented — Spring REST CRUD at `/api/admin/users` (GET/POST/PATCH/DELETE), Thymeleaf shell, filters/pagination, soft-delete with session invalidation, role change, credentials email (BR-15) |
| P-02 – P-06e | Scaffolded — real headings and navigation, with each specified zone marked as outstanding |

Each scaffolded screen renders its zones from the spec as dashed placeholders, so what remains on that screen is visible in the running app. Data-backed zones arrive with their feature slice.

---

## License / usage

Internal graduation project for company playlist-curation workflows over a licensed catalog. Not a public streaming product.
