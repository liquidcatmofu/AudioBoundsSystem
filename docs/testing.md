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

- `common`: 4 bounds/curve tests passed.
- `tts-addon`: 6 bridge/cache-key tests passed.
- Loader modules: `NO-SOURCE` (no loader-specific tests yet).
- Total new tests: 10.

Covered behavior:

- Fake provider request forwarding through the existing `AddonTTSBridge`.
- Provider metadata exposure and unknown-engine rejection.
- Full synthesis cache key determinism independent of parameter map order.
- Engine, speaker, text and tuning parameters all affect the full cache key.
- Existing legacy command-cache key remains unchanged.
- All four bounds shapes and all four current attenuation curves.

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
