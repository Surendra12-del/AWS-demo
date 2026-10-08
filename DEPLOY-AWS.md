# aws.demo — EC2 + S3 + RDS + CloudWatch Practice Guide

A Spring Boot 3/4 app that exercises four core AWS services. The code is done — this guide is
the **manual AWS + GitHub** part you do yourself.

```
            ┌──────────────────────── EC2 instance ────────────────────────┐
  Browser → │  Spring Boot app (this jar)                                   │
  / curl    │    • /api/products  ─────────────► RDS MySQL  (persistence)   │
            │    • /api/products/{id}/image ───► S3 bucket  (file storage)  │
            │    • create/delete/upload ───────► CloudWatch (custom metrics)│
            │    • /actuator/health ───────────► CloudWatch alarm source    │
            └───────────────────────────────────────────────────────────────┘
```

The app reads all AWS/DB settings from **environment variables**, so you never edit code to deploy:

| Env var          | Meaning                          | Example                                   |
|------------------|----------------------------------|-------------------------------------------|
| `DB_HOST`        | RDS endpoint host                | `awsdemo-db.abc123.us-east-1.rds.amazonaws.com` |
| `DB_PORT`        | DB port                          | `3306`                                    |
| `DB_NAME`        | database/schema name             | `awsdemo`                                 |
| `DB_USER`        | DB username                      | `admin`                                   |
| `DB_PASSWORD`    | DB password                      | `YourStrongPass!`                         |
| `DB_USE_SSL`     | use TLS to RDS                   | `true` (recommended on AWS)               |
| `AWS_REGION`     | region for S3 + CloudWatch       | `us-east-1`                               |
| `AWS_S3_BUCKET`  | S3 bucket name                   | `awsdemo-images-<your-unique-suffix>`     |
| `CW_NAMESPACE`   | CloudWatch metric namespace      | `AwsDemo/Application`                      |

> Credentials: on EC2 you do **not** set access keys. The app uses the AWS *default credential
> chain*, which automatically picks up the **IAM role attached to the EC2 instance**. That is the
> AWS best practice — no secrets on the box.

---

## Part A — Push the project to GitHub

From the project root (`/home/cfx/Documents/aws.demo`):

```bash
git init
git add .
git commit -m "Spring Boot AWS demo: RDS + S3 + CloudWatch"

# Create the repo (choose ONE):
# 1) GitHub CLI:
gh repo create aws-demo --private --source=. --remote=origin --push
# 2) Or manually: create an empty repo on github.com, then:
git branch -M main
git remote add origin https://github.com/<your-username>/aws-demo.git
git push -u origin main
```

`.gitignore` already excludes `target/`, so only source is pushed. **Never commit AWS keys or DB
passwords** — they live in env vars on the server, not in the repo.

---

## Part B — Create the S3 bucket (file storage)

1. **Console → S3 → Create bucket.**
   - Name: `awsdemo-images-<something-unique>` (bucket names are globally unique).
   - Region: same region you'll use everywhere (e.g. `us-east-1`).
   - Keep **Block all public access = ON** (the app serves files through its own endpoint; the
     bucket stays private).
2. Create. That's it — the app creates object keys like `products/<uuid>-<filename>`.

CLI equivalent:
```bash
aws s3 mb s3://awsdemo-images-<something-unique> --region us-east-1
```

---

## Part C — Create the RDS MySQL database

1. **Console → RDS → Create database.**
   - Engine: **MySQL**.
   - Template: **Free tier** (for practice).
   - DB instance identifier: `awsdemo-db`.
   - Master username: `admin`; set a master password you'll remember.
   - Instance: `db.t3.micro` (or `db.t4g.micro`), storage 20 GB.
   - **Public access**: for simple practice choose **Yes** (so you can reach it); for a realistic
     setup choose **No** and keep DB + EC2 in the same VPC.
   - Additional config → **Initial database name: `awsdemo`**.
2. Create, wait ~5–10 min until status **Available**, then copy the **Endpoint** (this is `DB_HOST`).
3. **Security group**: edit the RDS instance's security group → **Inbound rules** → allow
   **MySQL/Aurora (port 3306)** from:
   - the **EC2 instance's security group** (best), or
   - your own IP for quick local testing.

> The app's `spring.jpa.hibernate.ddl-auto=update` will auto-create the `products` table on first
> run, so no manual SQL is needed.

---

## Part D — Create the IAM role for EC2 (S3 + CloudWatch access)

1. **Console → IAM → Roles → Create role.**
   - Trusted entity: **AWS service → EC2**.
2. Attach permissions. For practice you can use the AWS-managed policies
   **`AmazonS3FullAccess`** and **`CloudWatchFullAccess`**. For least-privilege, create this inline
   policy instead (replace the bucket name):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "S3Objects",
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": "arn:aws:s3:::awsdemo-images-<something-unique>/*"
    },
    {
      "Sid": "CloudWatchPut",
      "Effect": "Allow",
      "Action": ["cloudwatch:PutMetricData"],
      "Resource": "*"
    }
  ]
}
```

3. Name it `awsdemo-ec2-role` and create. You'll attach it to the EC2 instance in Part E.

---

## Part E — Launch EC2 and run the app

### 1. Launch the instance
- **Console → EC2 → Launch instance.**
  - AMI: **Amazon Linux 2023**.
  - Type: **t2.micro / t3.micro** (free tier).
  - Key pair: create/download one so you can SSH.
  - Network/security group inbound rules:
    - **SSH (22)** from *your IP*.
    - **Custom TCP (8080)** from *your IP* (or `0.0.0.0/0` for open practice) — the app port.
  - **Advanced details → IAM instance profile → `awsdemo-ec2-role`** (from Part D).
- Launch and note the **Public IPv4 address / DNS**.

### 2. Connect and install Java 17
```bash
ssh -i your-key.pem ec2-user@<EC2_PUBLIC_IP>

sudo dnf install -y java-17-amazon-corretto   # Amazon Linux 2023
java -version
```

### 3. Get the app onto the instance (two options)

**Option 1 — build on EC2 from GitHub (simple):**
```bash
sudo dnf install -y git
git clone https://github.com/<your-username>/aws-demo.git
cd aws-demo
./mvnw clean package -DskipTests
# jar is at target/aws.demo-0.0.1-SNAPSHOT.jar
```

**Option 2 — build locally, copy the jar up:**
```bash
# on your laptop
scp -i your-key.pem target/aws.demo-0.0.1-SNAPSHOT.jar ec2-user@<EC2_PUBLIC_IP>:~/app.jar
```

### 4. Set environment and run
```bash
export DB_HOST=awsdemo-db.xxxxxxxx.us-east-1.rds.amazonaws.com
export DB_PORT=3306
export DB_NAME=awsdemo
export DB_USER=admin
export DB_PASSWORD='YourStrongPass!'
export DB_USE_SSL=true
export AWS_REGION=us-east-1
export AWS_S3_BUCKET=awsdemo-images-<something-unique>
export CW_NAMESPACE=AwsDemo/Application

# default port is 8080
java -jar target/aws.demo-0.0.1-SNAPSHOT.jar
```

Test from your laptop:
```bash
curl http://<EC2_PUBLIC_IP>:8080/actuator/health
curl -X POST http://<EC2_PUBLIC_IP>:8080/api/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"Keyboard","description":"Mechanical","price":49.99}'
curl http://<EC2_PUBLIC_IP>:8080/api/products
```

### 5. Run it as a service (survives logout/reboot)
Create `/etc/systemd/system/awsdemo.service`:
```ini
[Unit]
Description=aws.demo Spring Boot app
After=network.target

[Service]
User=ec2-user
WorkingDirectory=/home/ec2-user/aws-demo
EnvironmentFile=/home/ec2-user/awsdemo.env
ExecStart=/usr/bin/java -jar /home/ec2-user/aws-demo/target/aws.demo-0.0.1-SNAPSHOT.jar
SuccessExitStatus=143
Restart=on-failure

[Install]
WantedBy=multi-user.target
```
Put the `export` values (without the word `export`) into `/home/ec2-user/awsdemo.env`:
```
DB_HOST=...
DB_PORT=3306
DB_NAME=awsdemo
DB_USER=admin
DB_PASSWORD=YourStrongPass!
DB_USE_SSL=true
AWS_REGION=us-east-1
AWS_S3_BUCKET=awsdemo-images-<something-unique>
CW_NAMESPACE=AwsDemo/Application
```
Then:
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now awsdemo
sudo systemctl status awsdemo
journalctl -u awsdemo -f     # live logs
```

---

## Part F — Exercise S3

```bash
# upload an image to product 1 (stored in S3)
curl -X POST http://<EC2_PUBLIC_IP>:8080/api/products/1/image -F file=@/path/to/pic.jpg
# the product JSON now has "imageKey": "products/<uuid>-pic.jpg"

# download it back (streamed from S3)
curl http://<EC2_PUBLIC_IP>:8080/api/products/1/image --output got.jpg
```
Verify in **Console → S3 → your bucket**: you'll see the object under `products/`.

---

## Part G — Exercise CloudWatch

The app calls `PutMetricData` on every create/delete/image-upload.

1. Create a few products / upload images (Part F).
2. **Console → CloudWatch → Metrics → All metrics → `AwsDemo/Application`** namespace.
3. You'll see custom metrics: `ProductCreated`, `ProductDeleted`, `ProductImageUploaded`
   (each with an `Operation` dimension). Graph them.

### Create an alarm (uses the actuator health check idea)
- **CloudWatch → Alarms → Create alarm** → pick e.g. `ProductCreated` → threshold
  "≥ 1 in 5 minutes" → notify an SNS topic/email. Good for practicing alarms + notifications.

### (Optional) Ship application logs to CloudWatch Logs
Install the CloudWatch agent on EC2 and point it at the app's journald/log output, or add
`EC2 → CloudWatch agent`. For journald-based systemd logs:
```bash
sudo dnf install -y amazon-cloudwatch-agent
# then configure /opt/aws/amazon-cloudwatch-agent/etc/... to collect journald or a log file
```
(Your IAM role already allows CloudWatch; add `CloudWatchAgentServerPolicy` for the agent.)

---

## Part H — Clean up (avoid charges!)

When you're done practicing, delete in this order:
1. **EC2** → terminate the instance.
2. **RDS** → delete the DB (you can skip the final snapshot for practice).
3. **S3** → empty, then delete the bucket.
4. **CloudWatch** → delete any alarms; custom metrics expire on their own.
5. **IAM** → delete the `awsdemo-ec2-role` if not reused.

---

## Local development reference

The app currently runs locally against a Docker MySQL on port **3307** (set up during coding):
```bash
# the local MySQL container (already running)
docker ps | grep awsdemo-mysql

# run the app locally
./mvnw spring-boot:run
# or
java -jar target/aws.demo-0.0.1-SNAPSHOT.jar
```
Local defaults (in `application.properties`): `localhost:3307`, db `awsdemo`, user `appuser`,
password `apppass`. S3/CloudWatch stay disabled locally until you set `AWS_S3_BUCKET` and have
credentials — their endpoints return a clear "not configured" message instead of crashing.

## API summary

| Method | Path                       | Service      | Notes                               |
|--------|----------------------------|--------------|-------------------------------------|
| GET    | `/`                        | —            | API index                           |
| GET    | `/api/products`            | RDS          | list all                            |
| GET    | `/api/products/{id}`       | RDS          | one product                         |
| POST   | `/api/products`            | RDS + CW     | create (`name`,`description`,`price`)|
| PUT    | `/api/products/{id}`       | RDS          | update                              |
| DELETE | `/api/products/{id}`       | RDS + S3 +CW | delete (also removes S3 image)      |
| POST   | `/api/products/{id}/image` | S3 + CW      | multipart form field `file`         |
| GET    | `/api/products/{id}/image` | S3           | download image                      |
| GET    | `/actuator/health`         | —            | health (CloudWatch alarm source)    |
| GET    | `/actuator/metrics`        | —            | built-in metrics                    |
