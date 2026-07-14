# Testing Record and Strategy

## Audit run — 2026-07-13

Command:

```text
rtk ./gradlew build
```

Result: `BUILD SUCCESSFUL in 41s`, 49 actionable tasks executed.

Observed warnings:

- Architectury Loom 1.7.435 is outdated.
- Deprecated Gradle features will be incompatible with Gradle 9.
- Forge entrypoints use a removal-marked `FMLJavaModLoadingContext.get()` API.
- Some common sources use deprecated or unchecked APIs.

Test result: all six module test tasks reported `NO-SOURCE`. The build therefore verified compilation, transformation, remapping and packaging, but did not exercise application behavior.

## Regression run — 2026-07-13

Command:

```text
rtk ./gradlew test
```

Result: `BUILD SUCCESSFUL in 7s` after correcting one initially mistyped legacy-key expectation in the new test.

- `common`: 7 bounds/curve/endpoint tests passed.
- `tts-addon`: 6 bridge/cache-key tests passed.
- Loader modules: `NO-SOURCE` (no loader-specific tests yet).
- Total tests: 13.

Covered behavior:

- Fake provider request forwarding through the existing `AddonTTSBridge`.
- Provider metadata exposure and unknown-engine rejection.
- Full synthesis cache key determinism independent of parameter map order.
- Engine, speaker, text and tuning parameters all affect the full cache key.
- Existing legacy command-cache key remains unchanged.
- All four bounds shapes and all four current attenuation curves.
- Dedicated-server hostname, IPv6 and integrated-server audio endpoint generation.

Milestone 2 test run:

```text
rtk ./gradlew test
BUILD SUCCESSFUL in 12s

rtk ./gradlew :common:test
BUILD SUCCESSFUL in 9s
```

The second run includes the three endpoint tests added after the full test run.

Final verification after all lifecycle/timeout changes:

```text
rtk ./gradlew build
BUILD SUCCESSFUL in 1m 10s
53 actionable tasks: 17 executed, 36 up-to-date
```

Final verification after the test and cache-key changes:

```text
rtk ./gradlew build
BUILD SUCCESSFUL in 27s
53 actionable tasks: 18 executed, 35 up-to-date
```

Environment:

- Host: macOS
- Initial audit Java: Temurin 25.0.3
- Release-gate Java: Microsoft OpenJDK 17.0.19
- Java release configured by Gradle: 17
- Java 17 runtime build: passed on 2026-07-13

## Automated test layers

### Unit tests (ordinary Gradle test)

- Bridge/provider request forwarding with a fake provider
- Provider parameter schema
- Cache-key determinism and parameter ordering
- Bounds geometry and attenuation functions
- Token single-use and expiry
- Input validation and error mapping where Minecraft classes are not required
- Temporary/atomic file behavior

### Minecraft integration tests

- Packet encode/decode and authorization on the server thread
- Block entity persistence and TOML/NBT precedence
- Dynamic sound registration and cleanup
- Controller queue/retrigger behavior

These tests may require a dedicated test source set or GameTest and must not make ordinary unit tests require a game launch.

### Manual acceptance tests

- WebUI login, configuration and library CRUD
- TTS synthesis, edit/re-synthesis, registration and preview
- Speaker/controller assignment and playback
- Server restart and cache reuse
- Provider stopped, connection refused, timeout and invalid response
- Single player, dedicated server, multiple clients and remote clients
- Client disconnect and server shutdown during download/synthesis
- Missing/hung/failing ffmpeg

## Not yet executed

- Minecraft client launch
- Forge dedicated-server launch
- Fabric runtime launch
- VOICEVOX/compatible provider call
- ffmpeg integration test
- Remote client Minecraft audio transfer
- Sound Physics Remastered compatibility

## Transcoder consolidation

The TTS-side `FfmpegTranscoder` is now a compatibility adapter over the Core generic transcoder. Compilation and the full multi-module build verify the dependency and packaged class linkage; an actual ffmpeg upload/TTS conversion remains a manual integration test.

```text
rtk ./gradlew test
BUILD SUCCESSFUL in 20s

rtk ./gradlew build
BUILD SUCCESSFUL in 1m 7s
53 actionable tasks: 22 executed, 31 up-to-date
```

The Forge and Fabric addon metadata both declare `abs` as a required dependency. The packaged TTS addon retains its adapter class and resolves the generic transcoder from the required Core mod at runtime.

Manual result: the user confirmed successful operation after the Core/TTS transcoder consolidation on 2026-07-13. Exact loader, server topology and failure scenarios were not recorded, so those broader acceptance cases remain open.

## Input and authorization hardening

```text
rtk ./gradlew test
BUILD SUCCESSFUL in 13s
```

Nineteen tests now pass: 12 in `common` and 7 in `tts-addon`. New coverage verifies declared and chunked body limits, mutation-header policy, and rejection of unknown, non-finite and out-of-range provider parameters. Minecraft-side owner/OP/distance packet checks and the two-request synthesis semaphore still require integration or manual tests.

```text
rtk ./gradlew build
BUILD SUCCESSFUL in 43s
53 actionable tasks: 26 executed, 27 up-to-date
```

Behavioral limits added in this phase:

- JSON request body: 64 KiB
- Audio upload: 64 MiB, enforced while reading
- TTS text: 10,000 characters
- Concurrent create/re-synthesis operations: 2 per running HTTP handler, excess returns HTTP 429
- Controller configuration: at most 256 targets, 15 queues and 256 entries per queue

## Cache and registration integrity

```text
rtk ./gradlew test
BUILD SUCCESSFUL in 6s
```

Twenty-two tests now pass: 15 in `common` and 7 in `tts-addon`. New tests cover atomic replacement without leftover temporary files, Ogg magic rejection and deterministic content hashing. Library-level interrupted-write and duplicate-registration tests still require a controllable cache-root seam or Minecraft integration fixture.

```text
rtk ./gradlew build
BUILD SUCCESSFUL in 39s
53 actionable tasks: 22 executed, 31 up-to-date
```

New registrations store a SHA-256 content hash and use hash-suffixed Ogg filenames. Source, Ogg and JSON writes use `AtomicFiles`; metadata is committed last. Existing UUID-only cache filenames and metadata without `contentHash` remain readable.

## Client content cache

```text
rtk ./gradlew test
BUILD SUCCESSFUL in 16s
```

Twenty-five tests now pass: 18 in `common` and 7 in `tts-addon`. Client-cache tests cover verified store/reload, corrupted-file deletion and least-recently-accessed eviction. The production maximum is 128 MiB and individual audio files remain limited to 64 MiB.

```text
rtk ./gradlew build
BUILD SUCCESSFUL in 28s
53 actionable tasks: 13 executed, 40 up-to-date
```

Manual tests still required:

- Confirm the second playback of a new hash performs no Minecraft audio transfer.
- Start the same hash on multiple speakers concurrently and verify one download and independent playback.
- Fill beyond 128 MiB with real Ogg files and inspect oldest-access eviction.
- Verify behavior when the server deletes an entry that remains in the client LRU.
- Confirm legacy hashless entries continue to play through the fallback path.

## Server cache maintenance

```text
rtk ./gradlew test
BUILD SUCCESSFUL in 17s
```

Twenty-seven tests now pass: 20 in `common` and 7 in `tts-addon`. New coverage verifies rejection of cache paths outside (or below) the root cache directory, root-level orphan deletion, protection of referenced/recent files, and preservation of TTS cache subdirectories.

Runtime verification is still required for maintenance shutdown and orphan cleanup while WebUI uploads or TTS re-synthesis are active.

## Pre-synthesis TTS cache

```text
rtk ./gradlew :tts-addon:test
BUILD SUCCESSFUL in 18s

rtk ./gradlew build
BUILD SUCCESSFUL in 1m 3s
```

Thirty tests now pass across the project: 21 in `common` and 9 in `tts-addon`. New coverage verifies validated full-request cache persistence, corrupt-entry deletion, reuse of a cached result without a second Provider invocation, and request-based duplicate identity independent of generated Ogg bytes.

Manual verification is still required with a real Provider to confirm that repeated WebUI requests skip both the Provider HTTP call and FFmpeg process.

## Deterministic Ogg output

Two independently generated Ogg files for the same VOICEVOX request had different whole-file hashes but identical size, stream properties, duration and decoded PCM SHA-256. Byte differences were limited to Ogg stream serial fields and their page CRCs. Re-encoding twice with `-fflags +bitexact` produced byte-identical Ogg files. A command-construction regression test now requires that flag in the shared Core transcoder.

```text
rtk ./gradlew test
BUILD SUCCESSFUL in 31s

rtk ./gradlew build
BUILD SUCCESSFUL in 54s
```

## Bounded TTS synthesis cache

The TTS pre-synthesis cache now shares the Core oldest-accessed-file eviction utility with the client cache. Its production limit is 128 MiB. The new addon test uses a small temporary capacity and verifies that saving a newer result removes the older entry while retaining the new one.

```text
rtk ./gradlew test
BUILD SUCCESSFUL in 21s

rtk ./gradlew build
BUILD SUCCESSFUL in 36s
```

## Configurable TTS cache capacity and Java 17 gate

The addon now creates `config/abs-tts.toml`. `cache.maxSizeMiB` defaults to 128, accepts values from 1 through 1,048,576, and falls back to 128 when invalid. The same file persists `ffmpeg.path`. Two configuration tests cover default generation, configured loading and invalid-value normalization.

```text
Java: Microsoft OpenJDK 17.0.19

rtk ./gradlew test
BUILD SUCCESSFUL in 3s

rtk ./gradlew build
BUILD SUCCESSFUL in 15s
```

Forge development launch verification generated `forge/run/config/abs-tts.toml` and logged `ABS TTS: loaded config ... (cache 128 MiB)`. The prior launch had loaded a stale July 1 addon JAR from Loom's `remapped_mods` cache. Forge now loads addon source sets directly. Fabric Loader 0.19.3 did not recognize the multi-source-set group as a mod, so Fabric uses current named project artifacts as ordinary development runtime dependencies instead. A Fabric launch then recognized `abs_tts 1.0-SNAPSHOT`, generated `fabric/run/config/abs-tts.toml`, and logged the same 128 MiB configuration.

## Bounded Provider responses

VOICEVOX-compatible Provider reads now enforce 4 MiB JSON and 64 MiB WAV limits, and retain at most 16 KiB from an error response. Unit tests cover an exact-limit response, an oversized declared `Content-Length`, and an oversized stream without a usable declared length.

```text
rtk ./gradlew :tts-addon:test
BUILD SUCCESSFUL in 3s
```

Thirty-six tests now pass across the project: 22 in `common` and 14 in `tts-addon`. A live HTTP fixture test for connection cleanup, non-200 diagnostics and chunked transfer remains open.

## Provider discovery cache

The addon bridge now reuses Provider availability and engine/speaker discovery results for five seconds. A fake-clock regression test verifies that repeated availability-plus-engine queries perform one Provider probe and one speaker-list request, then refresh both after expiry.

```text
rtk ./gradlew :tts-addon:test
BUILD SUCCESSFUL in 3s
```

Thirty-seven tests now pass across the project: 22 in `common` and 15 in `tts-addon`.

## Persistent engine URLs

The generated `config/abs-tts.toml` now includes validated base URLs under `[engines]` for VOICEVOX, COEIROINK, AivisSpeech, Sharevox and LMROID. Tests cover a custom HTTP URL with a base path, trailing-slash normalization, default generation, and fallback from malformed or unsafe URL forms.

```text
rtk ./gradlew :tts-addon:test
BUILD SUCCESSFUL in 8s
```

Thirty-eight tests now pass across the project: 22 in `common` and 16 in `tts-addon`. Runtime verification with a non-default Provider URL remains open.

## Minecraft audio transfer foundation

CC:Tweaked's 1.20.x upload protocol was reviewed as a design reference. ABS uses the same conservative 30 KiB packet scale and the general pattern of transfer identity, declared size, ordered offsets and final digest validation; no CC:Tweaked source was copied into the implementation.

The public `/audio` HTTP route has been removed. A cache miss now redeems its per-player one-time token over a C2S packet, and the server returns bounded Ogg chunks from an owned scheduled executor. Transfers are limited to two globally and paced in eight-chunk batches. Four pure-Java assembler tests cover exact-limit completion plus rejection of oversized, out-of-order, overflowing and length-changing transfers.

```text
rtk ./gradlew test
BUILD SUCCESSFUL in 5s

rtk ./gradlew build
BUILD SUCCESSFUL in 13s
```

Thirty-nine tests now pass across the project: 23 in `common` and 16 in `tts-addon`. Actual two-client playback and disconnect-during-transfer remain manual acceptance tests.

## WebUI RPC routing foundation

The existing Me, TTS, Library and Blocks handlers now sit behind one `WebApiRouter`. A bounded Minecraft RPC request can be represented as a `MemoryHttpExchange`, preserving the same request headers, response status, content type, body limits and authorization paths as the temporary HTTP transport. A unit test covers request metadata and captured JSON responses.

```text
rtk ./gradlew :common:test
BUILD SUCCESSFUL in 5s

rtk ./gradlew build
BUILD SUCCESSFUL in 13s
```

Forty tests now pass across the project: 24 in `common` and 16 in `tts-addon`.

## Loopback WebUI and Minecraft Web RPC

`/abs ui` now starts an authenticated client listener on `127.0.0.1` with a dynamic port. The dedicated server contains no HTTP listener implementation. API requests and responses share 64 MiB bounds, 30 KiB chunks, ordered offsets and SHA-256 validation; uploads and preview Ogg responses therefore use the same transport. Server cache initialization was separated into `ServerAudioCache` so removing HTTP does not affect upload, TTS registration, maintenance or playback.

One new pure-Java test verifies Web RPC chunk sizing and digest rejection. Existing assembler, input-limit, authorization and in-memory exchange tests cover the reused lower layers.

```text
rtk ./gradlew :common:test
BUILD SUCCESSFUL in 3s
```

Forty-one tests now pass across the project: 25 in `common` and 16 in `tts-addon`. A real `/abs ui` browser session, 64 MiB upload, Ogg preview and two-client authorization test remain required.

## File-backed WebUI uploads

Web RPC request bodies above 64 KiB are assembled sequentially in an OS-managed temporary file with incremental SHA-256 validation. Cleanup covers success, malformed chunks, checksum failure, timeout, player disconnect and server stop. Audio imports then stream into atomic source storage and pass that file directly to FFmpeg, avoiding a full upload-sized Java heap copy.

Two request-assembly tests cover the file-backed path, digest verification, ordering and cleanup. Two atomic-file tests cover bounded stream writes and non-replacement on overflow. Forty-five tests now pass across the project: 29 in `common` and 16 in `tts-addon`.

```text
rtk ./gradlew test
BUILD SUCCESSFUL in 5s

rtk ./gradlew build
BUILD SUCCESSFUL in 14s
```

## Provider error classification

Core and the TTS addon now share typed synthesis failures for unavailable engines, timeouts, Provider HTTP errors and malformed responses. WebUI responses map these to 503, 504 or 502 without exposing the Provider response body. A loopback HTTP fixture verifies HTTP status preservation and malformed `audio_query` classification without requiring a real TTS engine or FFmpeg.

```text
rtk ./gradlew :tts-addon:test
BUILD SUCCESSFUL in 12s
```

Forty-seven tests now pass across the project: 29 in `common` and 18 in `tts-addon`.

Web RPC request dispatch now uses a separate four-thread worker pool, while response pacing and expiry cleanup use a dedicated single-thread scheduler. This prevents slow Provider or FFmpeg calls from consuming the scheduler needed by unrelated RPC transfers. Runtime concurrency and shutdown interruption remain manual acceptance cases.

## Bridge v1 contract and CI

The supported `ttsbridge` types and post-release additive compatibility rules are documented in `tts-bridge-api-v1.md`. A reflection test protects the three synchronous `TTSBridge` v1 method signatures. The project now contains a GitHub Actions workflow that runs `./gradlew build --no-daemon` on Temurin Java 17; its first hosted run remains unverified until the commit is pushed.

```text
rtk ./gradlew test
BUILD SUCCESSFUL in 4s

rtk ./gradlew build
BUILD SUCCESSFUL in 20s
```

Seventy-seven tests now pass across the project: 59 in `common` and 18 in `tts-addon`.

## Forge GameTest foundation

Forge has a separate `gameTest` source set, so test classes and SNBT structures are not packaged in the production mod. `:forge:runGameTestServer` starts the Forge 1.20.1 GameTest launch target headlessly on Java 17, runs the enabled `abs` namespace and exits with the test result. The suite verifies Speaker BlockEntity creation and round-trips representative Speaker and Audio Controller configurations through update NBT, including bounds, modes, audio/subtitle metadata, targets, queues, display names and ownership.

Architectury Loom 1.7 requires two scoped launch workarounds: preserving the case-sensitive `gameTestServer` DLI environment and providing a non-duplicated Java module path. These apply only to the GameTest task and should be removed when a future Loom version handles the Forge template correctly.

```text
rtk ./gradlew :forge:runGameTestServer
3 required tests passed
BUILD SUCCESSFUL in 20s
```

## Audio Controller signal transitions

Controller input decisions are isolated in `ControllerSignalTransition`, allowing their semantics to be checked without starting Minecraft. The unit tests cover PULSE rising edges, LEVEL strength changes and falling-to-zero stops, signal clamping, and active-playback STOP/RESTART behavior. The BlockEntity still owns the side effects (starting queues and stopping speakers), while its first server tick now samples the actual neighboring redstone signal after the level is attached.

Sequence expansion is similarly isolated in `ControllerQueuePlan`. Tests verify ordered mixing of ordinary and sequence refs, omission of missing sequences, malformed step handling and non-negative post-track delays. Controller playback schedules the following entry after the decoded track duration plus that step delay, matching WebUI sequence preview semantics.

`ControllerQueueTiming` covers the remaining clock decisions without a running world: exact due-tick boundaries, minimum duration handling, deferred LEVEL loop restarts, and the conditions that stop rather than loop an exhausted queue. Speaker packet side effects still require integration coverage with controlled audio fixtures.

## One-time audio transfer authorization

`TokenStoreTest` uses an injected test clock rather than sleeping. It verifies that a token is valid through its exact 60-second boundary and rejected afterward, can be consumed only once even when two threads race, and is revoked by service cleanup. Minecraft packet delivery remains a separate integration concern.

`AudioTransferServiceLifecycleTest` verifies that transfer-service start and stop are idempotent, that the executor can be started again after shutdown, and that stopping revokes every outstanding transfer token. The test never sends a Minecraft packet.

## Speaker bounds and falloff

`AudioBoundsTest` covers normalized distances for sphere, box, cylinder and hemisphere bounds, including the hemisphere base. It also verifies every falloff curve is monotonic and exactly silent at and outside the configured boundary. This hard cutoff is required because dynamic speaker sounds use `Attenuation.NONE`; without it, the inverse-square curve remained audible everywhere at 10% volume.

## Web session lifetime

`WebSessionStoreTest` uses an injected clock to verify the exact eight-hour validity boundary and rejection immediately afterward. It also covers per-session invalidation, server-wide clearing, unknown tokens and null input without waiting or launching Minecraft.

`WebRpcServiceMetadataTest` fixes the request envelope accepted before chunk assembly: only GET/POST/PATCH/DELETE, local `/api` paths without `.` or `..` segments, bodies from zero through 64 MiB, and exactly 32-byte SHA-256 digests. Absolute, malformed and encoded traversal paths are rejected before routing.

## Block configuration persistence authority

BlockEntity NBT is the sole automatic restore source for Speakers and Audio Controllers. TOML files written on placement or configuration save are operator-readable snapshots and are never loaded implicitly during chunk attachment. This prevents nondeterministic NBT/TOML precedence; representative NBT round trips remain covered by Forge GameTest.

## Sequence metadata persistence

`LibrarySequenceTest` exercises create, load, update, sorted listing and delete against a temporary library root. Folder and sequence IDs are validated inside `LibrarySequence` itself, so crafted Controller refs cannot use `.`/`..` or slash-containing IDs to read JSON outside the library tree.
