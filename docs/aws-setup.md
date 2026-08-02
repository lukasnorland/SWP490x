# MRS AWS setup (credit-optimized)

Personal account stack for the SWP490x graduation demo: **one EC2** (`t3.small`) running the app + MariaDB (MySQL-compatible), plus a private **S3** assets bucket. No RDS, no ALB, no custom domain.

Use IAM user **`mrs-admin`** for day-to-day work (not the root account). CLI profile: `mrs-admin`.

This workspace is wired to that profile via:

- `.env` / `.env.example` → `AWS_PROFILE=mrs-admin`
- `.vscode/settings.json` → integrated terminal env (`AWS_PROFILE`, region)

Open a **new** terminal in Cursor after pulling these settings. Confirm with:

```bash
aws sts get-caller-identity
# expect: arn:aws:iam::133857166188:user/mrs-admin
```

Refresh CLI session (after password change / expiry):

```bash
aws login --profile mrs-admin
```

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

- `mrs-admin-console.txt` — console temp password / access key notes  
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
| DB on EC2 | MariaDB **10.11**, database `mrs`, user `mrsapp`@`localhost`, bound to `127.0.0.1:3306` |
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

SES: EC2 role inline policy `mrs-ses-send` allows `ses:SendEmail` / `ses:SendRawEmail` (plus identity read). Still verify a from-address in SES (sandbox) before the app can send mail.

Apply SQL under `mrs/src/main/resources/db/migration/` when you first bring the app up (V1 schema, V2 seed).

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

# 2. Empty and delete S3
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

Optional: delete IAM user `mrs-admin` access keys / the user itself from the root account when finished.

## Security notes

- Day-to-day: `mrs-admin`, not root. Enable **MFA** on root and on `mrs-admin`.
- Prefer `aws login --profile mrs-admin` long-term; delete long-lived access keys when you no longer need them.
- Port **3306** is bound to localhost and not opened in the security group.
- S3 bucket has Block Public Access and SSE-S3 encryption.
