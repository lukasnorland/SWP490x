# MRS AWS setup (credit-optimized)

Personal account stack for the SWP490x graduation demo: **one EC2** (`t3.small`) running the app + MariaDB (MySQL-compatible), plus a private **S3** assets bucket. No RDS, no ALB, no custom domain.

Use IAM user **`mrs-admin`** for day-to-day work (not the root account). CLI profile: `mrs-admin`.

This workspace is wired to that profile via:

- `.env` / `.env.example` → `AWS_PROFILE=mrs-admin`
- `.vscode/settings.json` → integrated terminal env (`AWS_PROFILE`, region)

The `mrs-admin` profile and console password are in `evaluation/local-test-config.txt`. That file is not in Git. Sign in in the browser before local AWS calls:

```bash
aws login --profile mrs-admin
aws sts get-caller-identity --profile mrs-admin
# expect: arn:aws:iam::133857166188:user/mrs-admin
```

Run `aws login --profile mrs-admin` again after a password change or when the session expires.

## Account

| Item | Value |
|------|-------|
| Account ID | `133857166188` |
| Region | `ap-southeast-1` (Singapore) |
| Day-to-day IAM user | `mrs-admin` |
| CLI profile | `mrs-admin` (project default) |
| Root profile (billing / break-glass only) | `personal` |

Console login (IAM): https://133857166188.signin.aws.amazon.com/console  

Local secrets (not in git): `%USERPROFILE%\.mrs-aws\`

- `mrs-admin-console.txt` — console password for the browser sign-in  
- `mrs-db-credentials.txt` — local DB username/password for the app  

## Resources created

| Resource | ID / name |
|----------|-----------|
| VPC (default) | `vpc-05efb2e5d79a3bf39` |
| Subnet | `subnet-074e862b4ef766b0a` (`ap-southeast-1c`) |
| Security group | `mrs-ec2-sg` → `sg-097a8fd0a5fd0cbe1` (inbound **8080** only; MySQL not public) |
| IAM role | `mrs-ec2-role` (SSM + S3 + SES send) |
| Instance profile | `mrs-ec2-profile` |
| EC2 | `i-0d7e63cb4552af32d` (`t3.small`, Amazon Linux 2023, 30 GB encrypted gp3) |
| S3 bucket | `mrs-133857166188-assets` (prefix `song-data/`) |
| CloudFront | stack `mrs-audio-cdn` → `d34ixswlpjs53y.cloudfront.net` (`song-data/audio/`, `song-data/artwork/`) |
| DB on EC2 | MariaDB **10.11**, database `mrs`, user `mrsapp`@`localhost`, bound to `127.0.0.1:3306`. Wire-compatible with the MySQL 8 the app and CI target — same JDBC driver, same Flyway migrations |
| Budget | `mrs-monthly-5usd` ($5 / month COST) |
| Billing alarm | `mrs-estimated-charges-5usd` (CloudWatch, `us-east-1`, threshold $5) |

Demo URL once the Spring Boot app is deployed:

`http://<public-ip>:8080`

Current public IP changes if you stop/start the instance. Look it up with:

```bash
aws ec2 describe-instances --profile mrs-admin --region ap-southeast-1 \
  --instance-ids i-0d7e63cb4552af32d \
  --query "Reservations[0].Instances[0].PublicIpAddress" --output text
```

## App wiring (`DB_*` / S3)

On the instance (after you deploy the JAR), set env vars from `%USERPROFILE%\.mrs-aws\mrs-db-credentials.txt`:

```text
DB_URL=jdbc:mysql://localhost:3306/mrs?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true
DB_USERNAME=mrsapp
DB_PASSWORD=<from mrs-db-credentials.txt>
```

S3 bucket for assets: `mrs-133857166188-assets` (objects under `song-data/`).

The catalog import and the ADMIN upload form both talk to that prefix, so the
EC2 role needs `s3:ListBucket` on the bucket (scoped to the `song-data/*`
prefix), plus `s3:GetObject`, `s3:PutObject` and `s3:DeleteObject` on
`arn:aws:s3:::mrs-133857166188-assets/song-data/*`. `ListBucket` is a
bucket-level action and is what returns the ETags the import diffs against, so
`GetObject` alone is not enough — without it every run reports the prefix as
empty. `PutObject` is what the Add Song modal on the Song Catalog & Metadata screen uses; without it the form
can validate files but cannot stage them. `DeleteObject` is what the Song Catalog & Metadata screen uses to
remove a song's JSON and hosted audio/cover; without it the catalog row would
stay because a failed store delete never reaches MySQL. A direct `aws s3 cp`
remains a valid way to stage JSON as well.

The live inline policy is `mrs-s3-assets` on `mrs-ec2-role` (source:
`infra/ec2-s3-assets-policy.json`). It already allows Get, Put and Delete on
the bucket. Re-apply after editing the file:

```bash
aws iam put-role-policy --profile mrs-admin --role-name mrs-ec2-role \
  --policy-name mrs-s3-assets \
  --policy-document file://infra/ec2-s3-assets-policy.json
```

IAM updates apply to the instance role without a reboot. The Spring process
picks them up on the next S3 call once the instance metadata credentials
refresh.

Catalog audio lives under `song-data/audio/` in the same bucket (NCS today
under `song-data/audio/ncs/`; other vendors can share that prefix later). NCS
cover art is the same idea under `song-data/artwork/ncs/`. The player and the
shell wash load those files through CloudFront (stack `mrs-audio-cdn`, Price
Class 200) so a first request hits a nearby edge instead of the Singapore S3
origin. S3 stays private. The CloudFormation stack owns the bucket policy and
allows only its CloudFront distribution to read `song-data/audio/*` and
`song-data/artwork/*` through OAC. Catalog JSON is not exposed through the CDN.
CloudFront viewer URLs remain accessible without signing; OAC protects the S3
origin, not viewer access. The import lists only top-level `*.json`, so media
and nested verification objects are not treated as songs.

Create or update the distribution and its bucket policy in the bucket's region:

```bash
aws cloudformation deploy --profile mrs-admin --region ap-southeast-1 \
  --stack-name mrs-audio-cdn \
  --template-file infra/cloudfront-audio.yaml
```

Before updating an existing stack, back up the live bucket policy, public-access
block and ownership controls, and inspect the change set. If a bucket policy
already exists outside CloudFormation, import it as `AudioBucketPolicy` first
using resource identifier `Bucket`. The import template must preserve the live
policy and the existing resources and Outputs, including their YAML intrinsic
syntax. Then update to the private policy. Creating a new policy resource over
an existing manual policy fails with "The bucket policy already exists".
The final change set should modify only `AudioBucketPolicy`, with no resource
replacement. Preserve any unrelated bucket-policy grants in the template before
deployment. The policy
is retained on stack deletion; the bucket itself is managed outside this stack.

After the private policy is deployed and OAC is confirmed on the live origin,
apply the existing bucket's public-access and ownership settings:

```bash
aws s3api put-public-access-block --profile mrs-admin --region ap-southeast-1 \
  --bucket mrs-133857166188-assets \
  --public-access-block-configuration file://infra/assets-bucket-public-access-block.json
aws s3api put-bucket-ownership-controls --profile mrs-admin --region ap-southeast-1 \
  --bucket mrs-133857166188-assets \
  --ownership-controls file://infra/assets-bucket-ownership-controls.json
```

Check the existing bucket ACL first: `BucketOwnerEnforced` requires that the
bucket ACL grants access only to its owner. The app uploads without ACL headers,
so its Get/Put/Delete requests continue to use the EC2 role's IAM policy.

The OAC migration preserves CloudFront URLs; it needs no catalog reimport or
application restart. Reimport only if staged URL values actually change.

Catalog and search settings, on the instance:

```text
mrs.catalog.aws-profile=
mrs.catalog.sync.enabled=true
mrs.llm.api-key=<gemini key>
```

Clearing `mrs.catalog.aws-profile` makes the S3 client fall back to the instance
role, exactly as `mrs.mail.aws-profile` does for SES; leaving it as the local
default `mrs-admin` would have the app shell out to an AWS CLI profile that does
not exist there. `mrs.catalog.sync.enabled=true` registers the poller, which is
what makes uploading a JSON file to `song-data/` all you have to do to add a
song. Full list of `mrs.catalog.*` settings is in the README's *Catalog and media*
section.

`mrs.llm.api-key` is what makes FT-04 do real interpretation on the Search & Recommendation screen. It is not
an AWS credential and does not come from the instance role — it is a Gemini key,
and it belongs in the instance's property file, never in git. Leave it out and
contextual search silently falls back to vocabulary matching: the screen still
works and returns results, which is exactly why the omission survives a demo
unnoticed. Copy the key from `evaluation/local-test-config.txt` into the instance property file. That file is not in Git.

SES: EC2 role inline policy `mrs-ses-send` allows send (`ses:SendEmail` /
`ses:SendRawEmail`), identity read, and identity manage
(`ses:CreateEmailIdentity`, `ses:DeleteEmailIdentity`) so the User Management screen's
*Verify for SES* button works on the instance. Still verify a from-address in
SES (sandbox) before the app can send mail; recipients need the same until
production access is approved.

Flyway applies everything under `mrs/src/main/resources/db/migration/` on startup
(V1 schema, V2 initial admin account).

## SSM access (no SSH)

```bash
aws ssm start-session --profile mrs-admin --region ap-southeast-1 --target i-0d7e63cb4552af32d
```

Requires [Session Manager plugin](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html).

## Start / stop (save credits)

**Before a demo**

```bash
aws ec2 start-instances --profile mrs-admin --region ap-southeast-1 --instance-ids i-0d7e63cb4552af32d
aws ec2 wait instance-running --profile mrs-admin --region ap-southeast-1 --instance-ids i-0d7e63cb4552af32d
# then fetch public IP and open http://<ip>:8080
```

**After a demo / study session**

```bash
aws ec2 stop-instances --profile mrs-admin --region ap-southeast-1 --instance-ids i-0d7e63cb4552af32d
```

Stopping keeps the EBS volume (and MySQL data). You still pay a little for the 30 GB disk while stopped.

## Credits and budget habits

1. In the console: **Billing → Credits** and complete **Explore AWS** tasks for up to **$200** total credits.
2. Budget `mrs-monthly-5usd` tracks spend; add an email subscriber under **Billing → Budgets** if you want alerts in your inbox.
3. Alarm `mrs-estimated-charges-5usd` watches EstimatedCharges (enable **Billing alerts** under Billing preferences if the metric stays empty).
4. Prefer **Free plan** until you need a blocked service; upgrade only then.
5. Do **not** allocate an unused Elastic IP.
6. Weekly: check Credits + Budgets burn rate.

## Teardown (end of course)

Order matters:

```bash
# 1. Terminate EC2 (deletes root volume if DeleteOnTermination=true)
aws ec2 terminate-instances --profile mrs-admin --region ap-southeast-1 --instance-ids i-0d7e63cb4552af32d

# 2. Empty and delete S3 (after deleting the CloudFront stack, which owns the
#    bucket policy)
aws cloudformation delete-stack --stack-name mrs-audio-cdn \
  --profile mrs-admin --region ap-southeast-1
aws cloudformation wait stack-delete-complete --stack-name mrs-audio-cdn \
  --profile mrs-admin --region ap-southeast-1
aws s3 rm s3://mrs-133857166188-assets --recursive --profile mrs-admin
aws s3api delete-bucket --bucket mrs-133857166188-assets --profile mrs-admin --region ap-southeast-1

# 3. Security group (after instance is terminated)
aws ec2 delete-security-group --group-id sg-097a8fd0a5fd0cbe1 --profile mrs-admin --region ap-southeast-1

# 4. Instance profile + role
aws iam remove-role-from-instance-profile --instance-profile-name mrs-ec2-profile --role-name mrs-ec2-role --profile mrs-admin
aws iam delete-instance-profile --instance-profile-name mrs-ec2-profile --profile mrs-admin
aws iam detach-role-policy --role-name mrs-ec2-role --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore --profile mrs-admin
aws iam delete-role-policy --role-name mrs-ec2-role --policy-name mrs-s3-assets --profile mrs-admin
aws iam delete-role-policy --role-name mrs-ec2-role --policy-name mrs-ses-send --profile mrs-admin
aws iam delete-role --role-name mrs-ec2-role --profile mrs-admin

# 5. Budget + alarm
aws budgets delete-budget --account-id 133857166188 --budget-name mrs-monthly-5usd --profile mrs-admin
aws cloudwatch delete-alarms --region us-east-1 --alarm-names mrs-estimated-charges-5usd --profile mrs-admin
```

Optional: delete IAM user `mrs-admin` from the root account when finished.

## Security notes

- Day-to-day sign-in is `aws login --profile mrs-admin`. Enable **MFA** on root and on `mrs-admin`.
- Port **3306** is bound to localhost and not opened in the security group.
- S3 bucket has Block Public Access and SSE-S3 encryption.
