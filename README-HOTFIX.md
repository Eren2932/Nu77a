# nuva 4.0 - hotfix for the layer 2 build failures

Drop these files over the repo root, keeping their paths. One file is new,
three are replacements. Nothing else in the tree is touched.

| path | state | why |
|---|---|---|
| `server/internal/ws/protocol_chat.go` | NEW | declares the four frame types the api package referenced but nobody defined |
| `server/internal/api/handlers_ws_chat.go` | replaced | removes `ctx := ctx` |
| `server/internal/api/server.go` | replaced | gofmt alignment in the `/v1/meta` map literal |
| `android/gradle/libs.versions.toml` | replaced | media3 moved from `[plugins]` to `[libraries]` |

## still to do by hand, in `android/app/build.gradle.kts`

The catalog now declares media3, but nothing consumes it yet. Add inside
`dependencies { ... }` when layer 3 (voice playback) lands:

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common)

Unused catalog entries do not fail the build, so this can wait.
