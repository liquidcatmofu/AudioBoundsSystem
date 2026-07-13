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
- Host Java: Temurin 25.0.3
- Java release configured by Gradle: 17
- Java 17 runtime build: not yet run

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

- Java 17 runtime build
- Minecraft client launch
- Forge dedicated-server launch
- Fabric runtime launch
- VOICEVOX/compatible provider call
- ffmpeg integration test
- Remote client audio download
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

- Confirm the second playback of a new hash performs no HTTP audio transfer.
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
