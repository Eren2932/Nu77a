# Running your own Nuva server

Nuva is built so that the person who runs the server and the person who wrote
the app do not have to be the same person. If you run this, the data is yours
and nobody else can read it, including us.

## Requirements

Minimum for a few hundred users:

| Resource | Minimum | Comfortable |
|---|---|---|
| vCPU | 1 | 2 |
| RAM | 1 GB | 2 GB |
| Disk | 15 GB | 40 GB+ (media grows) |
| OS | any Linux with Docker | Debian 12 / Ubuntu 24.04 |

The Go server idles at roughly 40 MB of RAM. Voice messages are Opus at
24 kbps, about 180 KB per minute, so 40 GB of disk is on the order of 200,000
minutes of audio. There is no artificial length limit anywhere in the code.

## Install

```bash
git clone <your fork or this repo>
cd nuva
cp .env.example .env
./scripts/gen-secrets.sh

# with a domain pointed at this machine:
#   NUVA_DOMAIN=api.example.com
#   NUVA_ACME_EMAIL=you@example.com
docker compose --profile tls up -d --build

./scripts/smoke-test.sh https://api.example.com
```

Caddy obtains and renews the certificate itself. There is no certbot, no cron
job, no nginx config to get wrong.

## Backups - do this before you have users, not after

```bash
docker compose exec -T db pg_dump -U nuva nuva | gzip > nuva-$(date +%F).sql.gz
docker run --rm -v nuva_media_data:/m -v "$PWD":/out alpine \
    tar czf /out/media-$(date +%F).tar.gz -C /m .
```

Two things must survive a dead disk: the Postgres dump and the media volume.
Losing `NUVA_JWT_SECRET` only signs everyone out; losing the database loses the
accounts.

## Updating

```bash
git pull
docker compose --profile tls up -d --build
```

Migrations run automatically on startup, each inside its own transaction
together with the record that it was applied. A half-applied migration is not a
state this server can be in.

## What you are taking on

Running a server that carries other people's messages is a responsibility, and
in some jurisdictions a legal role with concrete obligations. Read
`docs/MODEL.md` before you invite strangers, and understand the rules where
your server physically sits.
