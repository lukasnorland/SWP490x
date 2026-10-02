# Music Recommendation System (MRS)

MRS is an internal web application for finding music and building campaign playlists from a licensed catalog. It brings contextual search, metadata-based ranking, playlist collaboration, and audio preview into one workspace.

Graduation project for **SWP490x — FUNiX**, developed by **Nguyễn Ngọc Luân** with mentor **Đào Thị Thanh**, following a Waterfall process.

## How it works

1. **Prepare the catalog.** An administrator manages providers, imports song metadata, and uploads audio and artwork. Catalog files are stored in Amazon S3 or a local staging directory; MySQL stores the searchable data.
2. **Find music.** Browse by genre, mood, artist, and tags, or describe a playlist need in natural language. Gemini can translate the prompt into catalog criteria. Without an API key, the app uses vocabulary matching; keyword search provides a fallback.
3. **Review recommendations.** Search includes songs matching any selected criterion, ranks them by the number of matches and then title, and optionally limits the results with Top-N. Catalog browsing combines filters across categories.
4. **Curate together.** Create a Draft playlist, add and reorder songs, and invite other Content Designers to edit. Concurrent edits are checked against the playlist version so stale changes cannot silently overwrite another user's work.
5. **Publish and reuse.** Publish a playlist containing at least one song to the shared workspace. Curators can duplicate playlists into independent Drafts; the owner or an ADMIN can export CSV. Audio preview continues across navigation, with queues limited to the first 100 tracks in display order from a catalog filter, search result, or playlist.

| Role | Access |
|------|--------|
| **ADMIN** | Manage users, catalog, settings, audit logs, and oversee playlists |
| **Content Designer** | Search, curate, collaborate, publish, duplicate, and export playlists |
| **Customer** | View and play published playlists in the shared workspace |

MRS is intended for internal curation over licensed music, not public streaming.

## Technology

- **Backend:** Java 25, Spring Boot 4.1, Spring Security, Spring Data JPA, Flyway.
- **Frontend:** Thymeleaf, Bootstrap, Tom Select, and JavaScript; no Node build step required.
- **Database:** MySQL 8.x.
- **Integrations:** Amazon S3 / CloudFront for media, Amazon SES for email, and Gemini for prompt interpretation.

The application and database run locally. AWS EC2 is an optional deployment target.

## Run locally

Install **JDK 25** and **MySQL 8.x**. The Maven Wrapper is included in `mrs/`; its first run downloads Maven and project dependencies.

Create an empty database:

```sql
CREATE DATABASE mrs CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

Create `mrs/local.properties` with your database credentials:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/mrs?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true
spring.datasource.username=your_user
spring.datasource.password=your_password

# Use local catalog staging without S3 credentials.
mrs.catalog.local-dir=./catalog-staging
```

`local.properties` is ignored by Git. Database settings can also be supplied through `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. Other defaults are in [application.properties](mrs/src/main/resources/application.properties).

Start from the `mrs/` directory so the local configuration is loaded:

```powershell
cd mrs
.\mvnw.cmd spring-boot:run
```

On Linux or macOS:

```bash
cd mrs
chmod +x mvnw
./mvnw spring-boot:run
```

Open **http://localhost:8080**. Flyway creates the schema and initial administrator on a new database:

- Email: `admin@mrs.local`
- Initial password: `Admin@2026`

The first login requires a password change. Use the admin screens to create other accounts. Stop the application with **Ctrl+C** in its terminal.

These credentials apply only to a new database. Flyway does not reset an existing administrator's password. The migrations seed provider names but no songs or playlists; import catalog data before testing search and playback. Let Flyway apply the migrations rather than running the SQL files manually first.

## Optional configuration

Add the settings below to `mrs/local.properties` as needed. Keep real credentials out of version control.

See [local test configuration](evaluation/local-test-config.txt) for the S3/CloudFront values, AWS `mrs-admin` test credentials, and Gemini API key. Follow its setup instructions; the app does not load this file automatically. For Git checkouts, start from the [configuration template](evaluation/local-test-config.example.txt).

### Catalog and media

The local staging setting stores catalog JSON and uploaded media on disk. Place staged song JSON directly in `mrs/catalog-staging/`, one `<externalSourceId>.json` file per song, then select **Sync Catalog** as ADMIN. Playable audio still requires URLs reachable by the browser; local staging alone does not publish those files as a media server.

To use S3 and a media host instead:

```properties
mrs.catalog.local-dir=
mrs.catalog.bucket=your_bucket
mrs.catalog.region=ap-southeast-1
mrs.catalog.aws-profile=your_aws_profile
mrs.catalog.prefix=song-data/
mrs.catalog.media.public-base-url=https://your-media-host
```

Install AWS CLI v2 and configure the named profile with access to your bucket. The app resolves named profiles through `aws configure export-credentials`; an AWS console password alone is not enough. A blank `mrs.catalog.aws-profile` uses the SDK's default credential chain instead.

Keep the S3 bucket private. CloudFront reads company-hosted audio and artwork through Origin Access Control (OAC); staged JSON is excluded from that access. Browser playback uses CloudFront URLs without AWS credentials. See [AWS setup](docs/aws-setup.md) for the optional deployment configuration.

Use **Sync Catalog** to import staged song JSON into the local MySQL database. Import needs S3 List/Get access; catalog add, edit, and delete also need Put/Delete access. Automatic startup import and scheduled synchronization are off by default; enable them with `mrs.catalog.import-on-start=true` and `mrs.catalog.sync.enabled=true` if needed.

Sync treats the staged catalog as its source: a non-empty listing can remove database songs whose staged JSON is missing, including their playlist memberships. Keep synchronization disabled when evaluating a database snapshot unless its matching staged catalog is available.

### Gemini

```properties
mrs.llm.api-key=your_gemini_key
```

Leave the key blank to use deterministic vocabulary matching without Gemini calls. Administrators can configure the model, timeout, and prompt-length limits in **System Settings**. The API key stays in local configuration.

### Email

Without `spring.mail.host`, messages are written to the application log instead of sent. This allows local account creation and password-reset flows without an email service; the log contains the generated credentials or reset link.

For real delivery through Amazon SES:

```properties
spring.mail.host=email-smtp.ap-southeast-1.amazonaws.com
spring.mail.port=587
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true
spring.mail.username=your_ses_smtp_user
spring.mail.password=your_ses_smtp_password
mrs.mail.from=your_verified_sender
mrs.mail.base-url=http://localhost:8080
mrs.mail.ses-region=ap-southeast-1
mrs.mail.aws-profile=your_aws_profile
```

Use SES SMTP credentials for delivery. The named AWS profile is used separately by **Verify for SES** in user management. In the SES sandbox, verify the recipient before creating the account; the mailbox owner must follow the verification link. If credentials were not delivered, verify the address and use **Resend credentials**.

Set `mrs.mail.base-url` to the address users open when the application is accessed from another machine.
