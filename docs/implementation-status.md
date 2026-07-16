# ABS / TTS Addon Implementation Status

Last audited: 2026-07-14

## Audit basis

This document records what is present in the repository and what was verified during development. A successful build proves compilation, packaging and automated-test behavior only. Runtime claims identify behavior explicitly confirmed through manual testing; they are kept distinct from automated verification.

Status terms used here are: **Verified working** for explicitly reported manual runtime success, **Implemented but untested** for complete code without runtime confirmation, **Partial** for an incomplete supported path, and **Missing** when no implementation exists.

## Build and modules

- Target: Minecraft 1.20.1, Architectury 9.2.14, Forge 47.4.10, Fabric Loader 0.19.3.
- Source and target compatibility: Java 17.
- Initial audit host JDK: Temurin 25.0.3. The release-gate build now also passes on Microsoft OpenJDK 17.0.19.
- Modules: `common`, `fabric`, `forge`, `tts-addon`, `tts-addon-fabric`, `tts-addon-forge`.
- `./gradlew build` succeeded on 2026-07-13 for all six modules.
- Ordinary unit tests run in `common` and `tts-addon`; GitHub Actions runs the full build and Forge GameTest on Java 17.

## Feature inventory

| Feature | Status | Evidence | Main classes | Risks | Next action |
|---|---|---|---|---|---|
| Web dashboard and authentication | Implemented, partially tested | Pure tests cover eight-hour session expiry boundaries, explicit invalidation, server-wide clearing and malformed tokens; `/abs ui` opens a token-authenticated loopback-only client server | `ClientWebServer`, `WebRpcClient`, `WebRpcService`, `WebApiRouter`, `WebSessionStore` | Full browser and multiplayer RPC verification remains | Run login-free identity, mutation and disconnect acceptance tests |
| WebUI library configuration | Implemented, partially tested | Pure tests cover bounded chunk assembly plus RPC method/path/body/digest metadata validation; relative path segments are rejected before routing; large bodies use temporary files | `LibraryApiHandler`, `WebRpcService`, `WebRpcRequestBody`, `WebRpcProtocol`, `webui/app.js` | Minecraft RPC upload, timeout and disconnect remain unverified; limits are fixed | Run upload, timeout and disconnect acceptance tests on Windows and Unix-like hosts |
| TTS provider discovery | Implemented but untested | Five providers are registered; fake-provider bridge, parameter validation and five-second discovery-cache tests pass | `TTSAddon`, `TTSProviderRegistry`, `AddonTTSBridge` | A cache miss still probes synchronously; registry has no lifecycle reset | Add an asynchronous refresh path if runtime latency remains visible |
| VOICEVOX-compatible synthesis | Verified working | Manual synthesis was confirmed during development; bounded-reader and loopback HTTP fixture tests cover response sizes, HTTP errors and malformed JSON | `VoiceVoxCompatibleProvider`, `TTSSynthesisException` | Blocking calls occupy a Web RPC worker; live timeout and connection-refused cases remain manual | Verify timeout and unavailable-engine messages at runtime |
| WAV/audio-to-Ogg transcoding | Verified working | User confirmed operation after Core/TTS consolidation; identical-PCM investigation verified bit-exact Ogg output locally | `audio.FfmpegTranscoder`, `tts-addon.transcode.FfmpegTranscoder` | 90-second timeout is fixed; automated external-process coverage remains | Add a process-runner test seam |
| TTS library registration | Implemented but untested | Original path was runtime-verified; same-request lookup now occurs before Provider synthesis and again under the synchronized registration path | `LibraryTts`, `TtsEntry`, `AtomicFiles`, `AudioContent` | Group commit is metadata-last rather than a filesystem transaction; existing duplicate development entries are not removed automatically | Run create/re-synthesis/restart integration tests |
| Audio upload and library storage | Implemented, partially tested | Upload is bounded; Ogg/source/metadata use metadata-last atomic commits; malformed folder/audio/TTS/sequence JSON is isolated and unsafe folder IDs are rejected | `LibraryAudio`, `LibraryTts`, `LibrarySequence`, `ABSLibrary`, `FfmpegTranscoder`, `AtomicFiles` | No capacity or eviction policy; full upload integration test remains | Run upload/failure/restart tests and define capacity policy |
| Core/Add-on boundary | Implemented and contract-tested | TTS addon implements the Core-owned v1 bridge; public types and additive compatibility policy are documented and the synchronous signature has a reflection test | `TTSBridge`, `TTSBridgeRegistry`, `AddonTTSBridge`, `tts-bridge-api-v1.md` | Synchronous byte-array API still hides cancellation | Add a compatible optional interface only when a concrete async consumer exists |
| One-time audio delivery | Implemented, partially tested | Per-player tokens reject cross-player consume/discard; busy/stopped services reject before consuming the token; clients retry typed transient errors up to three attempts with bounded backoff | `TokenStore`, `AudioTransferRetryPolicy`, `PlayAudioPacket`, `AudioTransferService` | Live Minecraft packet delivery and retry remain untested | Verify repeated playback and busy retry with two clients |
| Client download and playback | Implemented but untested | Bounded ordered-chunk assembly and the 128 MiB disk cache have unit coverage | `SpeakerAudioManager`, `ChunkedTransferAssembler`, `ClientAudioCache`, `ABSDynamicSoundStore` | Minecraft transfer needs multiplayer runtime verification; pre-release hashless data cannot verify content identity | Verify repeated and concurrent playback on two clients |
| Fixed speaker bounds audio | Implemented, partially tested | Pure tests cover all four bound shapes, curve monotonicity and hard silence at/outside the configured boundary; tickable sound reads those calculations | `ABSSpeakerSoundInstance`, `AudioBounds`, `FalloffCurve` | Client sound integration remains untested; no separately configured outer falloff width | Verify listener movement in Minecraft and document the single-region fade semantics |
| Moving 3D source | Missing | No entity-following sound implementation | — | Original specification is not fulfilled | Keep out of the stabilization milestones |
| 2D/UI playback | Missing | No separate 2D playback API or sound instance | — | Original specification is not fulfilled | Keep out of the stabilization milestones |
| Playback sequence | Implemented, partially tested | Tests cover metadata CRUD, queue decisions and Ogg duration parsing; a headless GameTest verifies Controller-to-Speaker start, temporary ref restoration and timed completion with a controlled audio fixture | `LibrarySequence`, `AudioControllerBlockEntity`, `ControllerSignalTransition`, `ControllerQueuePlan`, `ControllerQueueTiming` | Player packet and decoded client playback remain uncovered | Add packet integration coverage |
| Subtitle HUD | Partial | Pure tests cover packet metadata, text normalization, audio-length duration, final fade, source-specific clearing and replacement of the single title/subtitle overlay | `PlayAudioPacket`, `SubtitleOverlayManager`, `SubtitleHudRenderer` | Not a multi-cue timed `SubtitleTrack`; rendering remains untested | Define a compatible timed-track extension only if needed |
| Speaker/controller TOML | Implemented as snapshot | BlockEntity NBT is the authoritative restore source; configuration saves also produce comment-preserving operator-readable TOML | `SpeakerTomlConfig`, `AudioControllerTomlConfig`, ADR-018 | Manual TOML edits are not imported automatically | Add an explicit authorized import command only if operators need it |
| TTS configuration | Implemented but untested | `config/abs-tts.toml` is generated and loads validated cache capacity, FFmpeg path and five engine base URLs at startup | `TTSConfig`, `TTSAddon` | No live reload or WebUI editing; URL changes require restart | Verify a non-default local/remote engine URL at runtime |
| TTS cache | Implemented but untested | The addon checks a deterministic full-request cache before Provider synthesis; validated atomic entries use a 128 MiB access-time LRU and same-key requests are serialized | `AddonTTSBridge`, `TTSAudioCache`, `DiskCachePruner` | Cache key has no explicit synthesis-format version | Verify eviction and cache hits with a real Provider; define version policy before changing output format |
| Server lifecycle | Implemented, partially tested | Audio transfer start/stop is tested for idempotence, restart and token revocation; Minecraft audio/RPC, ffmpeg-probe and cache-maintenance executors are owned and stopped on server stopping | `ABSServerLifecycle`, `AudioTransferService`, `WebRpcService`, `FfmpegSupport`, `LibraryCacheMaintenance` | Full server runtime stop/restart remains unverified; provider bridge has no shutdown hook | Extend lifecycle tests to the remaining services |
| Client lifecycle | Implemented but untested | Active sound/subtitle state and in-flight download tasks clear on player quit | `AudioBoundsSystemClient`, `SpeakerAudioManager` | Download executor lives for the client process, although its threads are daemon threads | Add disconnect-during-download integration test |
| Authorization | Implemented, partially tested | Pure tests cover session lifetime, mutation headers, owner/operator folder access, inherited sharing, accessible subtrees and cyclic parent metadata; block mutation checks exist | `WebSessionStore`, `WebAuthHelper`, `ABSLibrary`, `ABSNetwork` | Existing ownerless controllers are claimed on first save; Minecraft packet paths lack integration tests | Run owner/non-owner/OP multiplayer tests |
| Error handling | Partial | Failures are caught at HTTP/provider boundaries; Provider JSON/WAV/error bodies are bounded | Multiple | Most errors collapse to HTTP 502 or null; diagnostic playback logs use WARN | Add typed failures and rate-limited/user-facing diagnostics |
| Automated tests | Partial | 105 pure-Java tests pass; four Forge GameTests boot a headless 1.20.1 server and verify BlockEntity creation, NBT round trips and Controller-to-Speaker playback timing | Test sources under `common`, `tts-addon` and `forge/src/gametest` | Live player packet delivery, browser RPC and multi-client paths remain uncovered | Add multiplayer acceptance tests |
| CI | Implemented, awaiting first remote run | GitHub Actions runs the complete Gradle build and Forge GameTest on Temurin Java 17 with read-only repository permissions | `.github/workflows/build.yml` | Workflow has not yet run on GitHub | Confirm the first Actions run after push |

## Storage locations

- TTS addon global config: `<game>/config/abs-tts.toml`
- World library metadata: `<world>/abs_library/<folder-id>/...`
- Core audio cache: `<world>/abs_cache/`
- Command-side TTS cache: `<world>/abs_cache/tts/`
- Speaker TOML: `<world>/abs/speakers/<dimension>/...`
- Controller TOML: `<world>/abs/controllers/<dimension>/...`, with a legacy lookup under `serverconfig/abs/...`
- Block configuration also persists in block-entity NBT.

## Threads and executors

| Owner | Work | Current lifecycle |
|---|---|---|
| `AudioTransferService` scheduled pool (2 daemon threads) | Bounded server audio reads and paced Minecraft chunk sends | Owned and stopped on server stop; at most two active transfers globally and per player |
| `SpeakerAudioManager` fixed pool (2 daemon threads) | Client cache reads, transfer waits and integrity checks | Tasks are tracked and interrupted on stop/disconnect; requests have timeouts |
| `WebRpcService` worker pool (4 daemon threads) | Potentially blocking API dispatch, including Provider and FFmpeg calls | Owned and interrupted on server stop; at most four assembling requests per player |
| `WebRpcService` scheduler (1 daemon thread) | Response chunk pacing and stale-request cleanup | Isolated from blocking API work; owned and stopped on server stop |
| `ClientWebServer` fixed pool (4 daemon threads) | Loopback static files and browser request waits | Starts on `/abs ui`; owned and stopped on client disconnect |
| `WebRpcClient` single scheduled daemon thread | Request chunk pacing and timeout completion | Process-owned daemon; pending requests clear on client disconnect |
| `FfmpegSupport` single daemon thread | Startup capability probe | Owned and stopped on server stop; external probes are time-bounded |
| `LibraryCacheMaintenance` single daemon thread | Root-level orphan Ogg cleanup | Runs once after HTTP startup; owned and stopped before HTTP shutdown |
| Minecraft server thread | Network packet callbacks queued through Architectury | Controller/speaker mutation occurs here |
| External ffmpeg process | WAV-to-Ogg conversion | 90-second timeout with graceful then forced termination |

## TODO/FIXME and hidden stubs

No literal `TODO`, `FIXME`, `UnsupportedOperationException`, or `NotImplemented` markers were found. TOML load helpers remain available for a possible future explicit import command, but BlockEntity NBT is intentionally the only automatic restore source.
