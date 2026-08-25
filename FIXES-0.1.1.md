# Build fix log

## 2026-08-25 - first CI run, Kotlin compilation failed (3 errors)

All three were frontend (type-checking) errors, which means the Kotlin compiler
reported the complete set: there is nothing else hiding behind them.

### 1. NuvaApi.kt:125 - Unresolved reference 'encodedPath'

`sendWithoutRequest { request -> ... }` hands you an `HttpRequestBuilder`, whose
`url` is a `URLBuilder`. `URLBuilder` has no `encodedPath` member in Ktor 2.x
(only `Url` does; on the builder it is `encodedPathSegments`).

Fixed by matching against `request.url.buildString()`, a stable member on both
Ktor 2.x and 3.x, and matching the full `/v1/auth/` prefix so a future endpoint
named e.g. `/v1/chats/auth-test` cannot accidentally lose its token.

### 2. NuvaApi.kt:134 - Return type of 'log' is not a subtype of Unit

`override fun log(message: String) = Log.d(...)` - `Log.d` returns `Int`, so the
expression body made the override return `Int` where the interface declares
`Unit`. Changed to a block body.

### 3. RealtimeClient.kt:119 - For-loop range must have an 'iterator()' method

The real cause was **name shadowing**, and it was the dangerous one:

* the class exposed `val incoming: SharedFlow<Envelope>`
* inside `webSocket { }` the session exposes `incoming: ReceiveChannel<Frame>`
* the outer property won name resolution, so `for (frame in incoming)` tried to
  iterate a Flow

The same trap existed silently for `send`: the class had `fun send(type, payload,
id)` with defaults, so `send(frame)` was one resolution rule away from queueing a
frame into our own outbox instead of writing it to the socket. That one would
have compiled and shipped as "messages never arrive".

Fixed structurally, not cosmetically:

* `incoming` -> `events` (public envelope flow)
* `send(...)` -> `enqueue(...)` (public queue call)
* `val session: DefaultClientWebSocketSession = this` inside the block, and every
  socket call written as `session.send` / `session.incoming`

Call sites updated in `ui/home/HomeViewModel.kt`.

## Also fixed in the same pass (not yet failures, but scheduled ones)

* `android.defaults.buildfeatures.buildconfig` removed from gradle.properties -
  deprecated, removed in AGP 9. `buildFeatures { buildConfig = true }` in
  app/build.gradle.kts already covers it.
* `actions/checkout@v4` -> `@v5`, `actions/setup-java@v4` -> `@v5` in both
  workflows - kills the Node 20 deprecation annotations.
* proguard-rules.pro: added `-dontwarn` lines for the JVM-only classes Ktor
  references. These do not affect the debug build, but R8 aborts
  `assembleRelease` on missing classes - so this would have blown up on the
  first `v0.1.0` tag, after a green debug build.

## Known, accepted for sprint 0

The `outgoing` channel is shared across reconnects. If the socket dies while the
writer pump is suspended on a `send`, that single frame is lost. Correct fix is
per-message acknowledgement from the server, which lands in sprint 2 together
with the offline queue - not worth a half-measure now.

## 2026-08-25, second CI run - same class of bug, mirrored (my miss)

`RealtimeClient.kt:131:38 For-loop range must have an 'iterator()' method`

I fixed `incoming` and `send` and did not check `outgoing`. A Ktor session has
**three** colliding members, not two:

| session member | type | our member (before) |
|---|---|---|
| `incoming` | `ReceiveChannel<Frame>` | `SharedFlow<Envelope>` |
| `outgoing` | `SendChannel<Frame>` | `Channel<String>` |
| `send(String)` | extension | `fun send(type, payload, id)` |

So `for (text in outgoing)` resolved to the session's **SendChannel**, which by
definition has no `iterator()`. Same root cause, third member. Symmetric misses
like this are exactly why the fix has to be structural.

### Structural fix, not a third rename

`RealtimeClient` was rewritten so the `webSocket { }` block contains only two
statements: `attempt = 0` and `runSession(this)`.

`runSession(session: DefaultClientWebSocketSession)` takes the session as an
ordinary parameter, so inside it **there is no implicit session receiver at
all**. Every socket access must be spelled `session.send` / `session.incoming`,
and the compiler has nothing left to guess. Any future property added to this
class is now immune, whatever it is called.

Our members are also named `events` / `outbox` / `enqueue`, and the rule is
written at the top of the file so nobody "tidies" it back.

Verified mechanically: no bare `incoming` / `outgoing` reference remains in the
file, and `RealtimeClient` is the only file in the app that touches a WebSocket
session.
