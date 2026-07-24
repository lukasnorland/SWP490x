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
| Frontend | Thymeleaf (server-side rendered) |
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
│   │   └── db/migration/ # Schema + seed SQL (V1, V2)
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

Apply migrations under `mrs/src/main/resources/db/migration/` (V1 schema, V2 sample data), or configure Flyway if you enable it in the app.

### Configuration

Defaults in `mrs/src/main/resources/application.properties` (override with env vars):

| Variable | Default |
|----------|---------|
| `DB_URL` | `jdbc:mysql://localhost:3306/mrs?...` |
| `DB_USERNAME` | `root` |
| `DB_PASSWORD` | *(empty)* |

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

Current codebase is early-stage (Spring Boot skeleton, schema, and sample catalog for the vertical prototype).

---

## License / usage

Internal graduation project for company playlist-curation workflows over a licensed catalog. Not a public streaming product.
