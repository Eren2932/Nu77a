# What Nuva is, structurally

A decision made on 2026-08-25, before writing the chat layer, because it cannot
be retrofitted cheaply.

## Nuva is software, not a service

The server is open source. Anyone can run it. The client asks which server to
talk to on first launch and can switch at any time. There is an official
instance for people who do not want to run anything, and it has no special
powers in the protocol.

This is the Matrix/Element model, not the Telegram model.

## Why, in plain terms

**Honesty.** The promise is not "we do not read your messages". The promise is
"pick a server whose owner you trust, or be that owner". The first is a claim,
the second is a property. Only the second survives someone reading the source.

**Money.** A single central server for thousands of users is a bill a student
cannot pay. Distributed hosting means the project's growth is not capped by one
person's wallet.

**Survivability.** A centralised messenger dies with one order to one hoster.
A blocked instance is an inconvenience; a blocked ecosystem does not exist.

**Personal exposure.** Under Russian law (149-FZ art. 10.1) the operator of a
messaging service - the "organiser of information dissemination" - carries
concrete duties: registration with Roskomnadzor, storing message content for
six months and metadata for a year on Russian soil, identifying users by phone
number, and handing over decryption means on request. Those duties are aimed at
whoever *runs* the system. Publishing software is a different act from operating
a service. This is not a trick and not a loophole - it is the honest shape of
the project, and it happens to also be the shape that does not put one student
personally in front of that list.

None of the above is legal advice. Talk to an IT lawyer before the public
release.

## What this forces in the code

Already done, in sprint 0:

* `ServerStore` - the server address is user data in SharedPreferences, never a
  build constant. `BuildConfig.API_BASE_URL` is only a prefilled suggestion.
* `ServerRepository.connectTo()` - a candidate address is probed against
  `/v1/meta` before it is stored, so a typo can never lock the app out.
* Changing server always clears the session. Tokens belong to exactly one
  server; replaying them elsewhere would be both broken and a privacy leak.
* Release builds refuse plain `http://` (`ServerUrl.normalize`). Debug builds
  allow it for `10.0.2.2`.
* `docker-compose.yml` exposes the stack either through Cloudflare Tunnel
  (no domain, no cost) or Caddy + Let's Encrypt (VPS), selected by profile.

Consequences accepted:

* Users see one extra screen before signing up. Worth it.
* Push notifications will need per-instance configuration in sprint 4.
* Any future federation between instances is a protocol problem for after 1.0.
  Instance choice is not federation, and we should not pretend otherwise.

## Wording for the landing page

Safe to write, because the code backs it:

* "Choose your server, or run your own. The source is open."
* "No phone number required."
* "Voice messages have no length limit."
* "Transport encryption, encrypted at rest on the server."

Do **not** write before it exists and has been reviewed:

* anything containing "end-to-end" (planned for 1.1, on the Signal Protocol)
* "nobody can read your messages" - untrue for a server operator in 1.0
* "uncontrollable", "beyond the reach of anyone", or any political framing.
  It invites attention the project cannot survive yet, and the technical claim
  is not ours to make on someone else's server.

## License

The intended license is **AGPL-3.0**: anyone may run and modify the server, but
a modified public instance must publish its source. That is what keeps "you can
run it yourself" true instead of decorative.

The `LICENSE` file is not in this archive on purpose - the AGPL text must be
byte-exact, and I will not reproduce it from memory. Add it with GitHub's
"Add file -> Create new file -> LICENSE -> choose a template -> GNU AGPLv3",
which inserts the official text, or copy it from
https://www.gnu.org/licenses/agpl-3.0.txt
