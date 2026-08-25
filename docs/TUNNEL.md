# Phase 0: your own Nuva server for 0 rubles

Goal: a real, publicly reachable Nuva server with valid HTTPS, running on your
own PC, with no VPS, no domain, no card and no exposed home IP address.

This is not a downgraded mode. It is the same server image, the same database,
the same compose file that will later run on a VPS. Only the way traffic reaches
it differs.

## What you need

* Docker Desktop (Windows) or Docker Engine (Linux)
* The Nuva repo
* Your phone on any network - mobile data works, same Wi-Fi is not required

## Steps

```bash
cp .env.example .env
./scripts/gen-secrets.sh          # fills NUVA_JWT_SECRET and POSTGRES_PASSWORD

docker compose --profile tunnel up -d --build
./scripts/tunnel-url.sh
```

The last command prints something like:

```
https://calm-river-1234.trycloudflare.com
```

That is your server. Open Nuva on the phone, paste it into **Choose your
server**, tap **Connect**. Register. Done - you are talking to your own machine.

Verify from your side too:

```bash
./scripts/smoke-test.sh https://calm-river-1234.trycloudflare.com
```

## What Cloudflare does here

`cloudflared` opens an **outbound** connection from your PC to Cloudflare and
receives requests through it. Consequences worth understanding:

* No port forwarding, works behind provider NAT.
* Your home IP is never published. Users see a Cloudflare address.
* TLS is terminated by Cloudflare with a valid certificate, so the app's
  HTTPS-only rule is satisfied.
* WebSockets pass through, which is what Nuva's realtime channel needs.
* Cloudflare sees the traffic. For a test circle this is an acceptable trade;
  for the public release we move to a VPS with Caddy, where nobody sits in the
  middle. Do not claim otherwise on the landing page.

## Limitations, honestly

* The URL changes on every tunnel restart. This is why the server address is a
  field in the app instead of a build constant - the whole reason we made that
  decision on day one.
* If your PC sleeps, the server is down. Fine for a test group of twenty.
* Quick tunnels are throttled and not meant for hundreds of users.

## Stable address, still free

Add any domain you own to Cloudflare (free plan), create a named tunnel in the
dashboard, drop its token into `.env` as `CLOUDFLARE_TUNNEL_TOKEN`, then:

```bash
docker compose --profile tunnel-named up -d --build
```

Now the hostname is permanent and you can stop retyping the address.

## Moving to a VPS later

Nothing to port. On the VPS:

```bash
cp .env.example .env && ./scripts/gen-secrets.sh
# set NUVA_DOMAIN and NUVA_ACME_EMAIL
docker compose --profile tls up -d --build
```

Move the data with `pg_dump` / `pg_restore` if you want to keep test accounts.
Users just type the new address - no reinstall, no lost accounts.
