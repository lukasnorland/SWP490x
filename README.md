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
| FE-03 | Multi-criteria metadata search (Genre, Mood, Theme, Artist, Event) |
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
| P-01 Password Reset | Implemented — both steps, live BR-12 checklist (reset link is logged, not emailed) |
| Forced password change (FT-09) | Implemented |
| P-02 – P-06e | Scaffolded — real headings and navigation, with each specified zone marked as outstanding |

Each scaffolded screen renders its zones from the spec as dashed placeholders, so what remains on that screen is visible in the running app. Data-backed zones arrive with their feature slice.

---

## License / usage

Internal graduation project for company playlist-curation workflows over a licensed catalog. Not a public streaming product.
