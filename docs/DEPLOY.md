# Deploying ServiceLog

ServiceLog runs on **one small Linux server** with three containers (`deploy/docker-compose.yml`):

| Container | What it does | Reachable from the internet? |
|---|---|---|
| `web` (Caddy) | Serves the app, gets and renews the HTTPS certificate automatically, forwards `/api` | Yes: ports 80 and 443 |
| `backend` (Spring Boot) | The API | No: only through `web` |
| `db` (PostgreSQL 17) | The data | No: only the backend can reach it |

GitHub Actions builds the two images, publishes them to GitHub Container Registry, and
deploys them over SSH (`.github/workflows/deploy.yml`, started by hand from the Actions tab).
All secrets live in the server's `.env` file and in GitHub's secret store, never in git.

The whole stack has been run locally with HTTPS (`DOMAIN=localhost`): app, API, migrations,
owner-account creation and email all worked.

---

## What you need (only you can set these up)

1. **A server:** Ubuntu 24.04 with **2 GB of RAM** (Spring Boot and PostgreSQL together need
   more than 1 GB). For example AWS Lightsail, an EC2 `t4g.small`, or any VPS provider.
   Check current prices; small servers like this are typically in the $10–20/month range.
2. **A domain name:** for example `servicelogapp.com` or a subdomain of one you own. HTTPS
   certificates are issued for a domain, not a bare IP address.
3. **An email-sending service with SMTP:** for example Resend, Amazon SES or Postmark. You'll
   verify your domain with them by adding the DNS records they give you (SPF/DKIM), so
   reminders don't land in spam.

---

## One-time setup

### 1. Point the domain at the server
In your DNS provider, add an **A record**: `app.yourdomain.com` → the server's public IP address.

### 2. Prepare the server
SSH in, then:

```bash
# Docker
curl -fsSL https://get.docker.com | sh

# A user for deployments, allowed to run Docker
sudo adduser --disabled-password --gecos "" deploy
sudo usermod -aG docker deploy

# The app's folder
sudo mkdir -p /opt/servicelog && sudo chown deploy:deploy /opt/servicelog
```

Firewall: allow inbound **22** (SSH), **80** and **443** only.

### 3. Add the compose file and secrets
Copy `deploy/docker-compose.yml` to `/opt/servicelog/docker-compose.yml`, and
`deploy/.env.example` to `/opt/servicelog/.env`, then fill in `.env`:

```bash
openssl rand -base64 48   # run twice: one for DB_PASSWORD, one for JWT_SECRET
chmod 600 /opt/servicelog/.env
```

Set `DOMAIN`, the owner's `OWNER_EMAIL` and a strong `OWNER_PASSWORD` (12+ characters),
the email service's SMTP settings, `MAIL_FROM` (an address on your verified domain), and
`BUSINESS_NAME` / `BUSINESS_PHONE` for the reminder emails.

### 4. Let the server pull the images
GitHub Container Registry packages start out private. Either make the two packages public
(GitHub → your profile → Packages → `servicelog-backend` / `servicelog-web` → Package
settings → Change visibility; they contain no secrets), or log the server in with a token
that has only `read:packages`:

```bash
echo <token> | docker login ghcr.io -u <your GitHub username> --password-stdin
```

### 5. Give GitHub Actions access to the server
1. Create an SSH key just for deploys: `ssh-keygen -t ed25519 -f servicelog_deploy -N ""`.
2. Append `servicelog_deploy.pub` to `/home/deploy/.ssh/authorized_keys` on the server.
3. In the GitHub repo: **Settings → Environments → New environment** `production`, then add
   these secrets:
   - `DEPLOY_HOST`: the server's IP address or domain
   - `DEPLOY_USER`: `deploy`
   - `DEPLOY_SSH_KEY`: the contents of the private key file `servicelog_deploy`
4. Delete the private key file from your computer once it's saved in GitHub.

### 6. First deploy
GitHub → **Actions → Deploy → Run workflow**. When it finishes, open `https://app.yourdomain.com`.

- Check the owner's inbox for the verification email and click the link.
- Then **remove `OWNER_PASSWORD` from `.env`**; the account already exists.

---

## Everyday

- **Deploy a new version:** merge to `main`, then Actions → Deploy → Run workflow.
- **Roll back:** on the server, run
  `BACKEND_IMAGE=ghcr.io/naturaltc/servicelog-backend:<older sha> WEB_IMAGE=ghcr.io/naturaltc/servicelog-web:<older sha> docker compose up -d`.
  Every deploy is tagged with its commit SHA.
- **Logs:** `docker compose logs -f backend` (or `web`, `db`) in `/opt/servicelog`.

## Backups
The database lives in the `db-data` Docker volume on the server. Back it up nightly to
somewhere other than the server (for example an S3 bucket):

```bash
# crontab -e (as deploy): every night at 2:30
30 2 * * * cd /opt/servicelog && docker compose exec -T db pg_dump -U servicelog servicelog | gzip > /opt/servicelog/backups/servicelog-$(date +\%F).sql.gz
```

Then copy `/opt/servicelog/backups` off the server (for example `aws s3 sync`) and keep a
few weeks of history. Restore with
`gunzip -c backup.sql.gz | docker compose exec -T db psql -U servicelog servicelog`.
Test a restore once, before you need it.

## Security notes
- Only Caddy is reachable from the internet. The database and backend have no published ports.
- Caddy adds HSTS, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff` and a strict
  referrer policy, and redirects HTTP to HTTPS.
- The backend trusts Caddy's `X-Forwarded-For` (`SERVER_FORWARD_HEADERS_STRATEGY=framework`),
  so the login rate limit sees real client addresses.
- The backend container runs as a non-root user.
