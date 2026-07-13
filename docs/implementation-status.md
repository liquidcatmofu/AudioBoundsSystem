# ABS / TTS Addon Implementation Status

Last audited: 2026-07-13

## Audit basis

This document records what is present in the repository and what was verified during the audit. A successful build proves compilation and packaging only. Runtime claims for the WebUI-to-playback path are based on the existing handoff report and were not independently reproduced during this audit.

Status terms follow `handoff_v2/02_ABS_codex_implementation_addendum.md`.

## Build and modules

- Target: Minecraft 1.20.1, Architectury 9.2.14, Forge 47.4.10, Fabric Loader 0.19.3.
- Source and target compatibility: Java 17.
- Initial audit host JDK: Temurin 25.0.3. The release-gate build now also passes on Microsoft OpenJDK 17.0.19.
- Modules: `common`, `fabric`, `forge`, `tts-addon`, `tts-addon-fabric`, `tts-addon-forge`.
- `./gradlew build` succeeded on 2026-07-13 for all six modules.
- Ordinary unit tests now run in `common` and `tts-addon`; no CI workflow is present.

## Feature inventory

| Feature | Status | Evidence | Main classes | Risks | Next action |
|---|---|---|---|---|---|
| Web dashboard and authentication | Verified working | Handoff runtime report; static WebUI and HTTP routes exist | `ABSHttpServer`, `WebUIHandler`, `AuthApiHandler`, `WebSessionStore` | Server binds all interfaces; no configurable port; CSRF header requires runtime browser verification | Document deployment assumptions and verify session/CSRF manually |
| WebUI library configuration | Implemented but untested | Original flow was runtime-verified; bounded request and authorization changes have unit coverage only | `LibraryApiHandler`, `RequestBodyReader`, `ABSLibrary`, `webui/app.js` | 64 KiB JSON and 64 MiB upload limits are fixed constants | Run WebUI mutation/upload acceptance tests |
| TTS provider discovery | Implemented but untested | Five providers are registered; fake-provider bridge, parameter validation and five-second discovery-cache tests pass | `TTSAddon`, `TTSProviderRegistry`, `AddonTTSBridge` | A cache miss still probes synchronously; registry has no lifecycle reset | Add an asynchronous refresh path if runtime latency remains visible |
| VOICEVOX-compatible synthesis | Verified working | Handoff runtime report; `/audio_query` and `/synthesis` implementation exists; bounded-reader tests cover declared and streamed response sizes | `VoiceVoxCompatibleProvider` | Blocking calls occupy an HTTP worker; coarse error types; no live HTTP fixture test | Add a transport seam and error classification |
| WAV/audio-to-Ogg transcoding | Verified working | User confirmed operation after Core/TTS consolidation; identical-PCM investigation verified bit-exact Ogg output locally | `audio.FfmpegTranscoder`, `tts-addon.transcode.FfmpegTranscoder` | 90-second timeout is fixed; automated external-process coverage remains | Add a process-runner test seam |
| TTS library registration | Implemented but untested | Original path was runtime-verified; same-request lookup now occurs before Provider synthesis and again under the synchronized registration path | `LibraryTts`, `TtsEntry`, `AtomicFiles`, `AudioContent` | Group commit is metadata-last rather than a filesystem transaction; existing duplicate development entries are not removed automatically | Run create/re-synthesis/restart integration tests |
| Audio upload and library storage | Implemented but untested | Upload is bounded; Ogg/source/metadata use metadata-last atomic file commits | `LibraryAudio`, `ABSLibrary`, `FfmpegTranscoder`, `AtomicFiles` | No capacity or eviction policy; full integration test remains | Run upload/failure/restart tests and define capacity policy |
| Core/Add-on boundary | Implemented but untested | TTS addon implements the Core-owned bridge interface | `TTSBridge`, `TTSBridgeRegistry`, `AddonTTSBridge` | Synchronous byte-array API hides cancellation and typed failures; no compatibility version | Document v1 contract, then add compatible adapters only as needed |
| One-time audio delivery | Implemented but untested | Per-player tokens authorize one Minecraft transfer request; content hashes are sent with new entries; metadata paths remain constrained to root-level `abs_cache` files | `TokenStore`, `AudioTransferService`, `SpeakerBlockEntity` | Cached clients leave their one-time token unused until expiry | Add expiry/single-use integration tests and avoid issuing tokens on confirmed hits later |
| Client download and playback | Implemented but untested | Bounded ordered-chunk assembly and the 128 MiB disk cache have unit coverage | `SpeakerAudioManager`, `ChunkedTransferAssembler`, `ClientAudioCache`, `ABSDynamicSoundStore` | Minecraft transfer needs multiplayer runtime verification; pre-release hashless data cannot verify content identity | Verify repeated and concurrent playback on two clients |
| Fixed speaker bounds audio | Implemented but untested | Tickable sound reads speaker bounds and computes volume | `ABSSpeakerSoundInstance`, `AudioBounds`, `FalloffCurve` | Uses `Attenuation.NONE`; no configured outer falloff width; some curves do not match the original formulas | Add pure geometry/curve tests and reconcile semantics in an ADR |
| Moving 3D source | Missing | No entity-following sound implementation | — | Original specification is not fulfilled | Keep out of the stabilization milestones |
| 2D/UI playback | Missing | No separate 2D playback API or sound instance | — | Original specification is not fulfilled | Keep out of the stabilization milestones |
| Playback sequence | Implemented but untested | Sequence metadata and controller expansion are present | `LibrarySequence`, `AudioControllerBlockEntity` | No automated queue/retrigger tests | Extract pure queue planning logic for tests |
| Subtitle HUD | Partial | A single title/subtitle overlay is rendered | `SubtitleOverlayManager`, `SubtitleHudRenderer` | Not a timed `SubtitleTrack`; packet duration is fixed at 100 ticks | Test existing overlay, then define a compatible timed-track extension |
| Speaker/controller TOML | Partial | Comment-preserving save/load helpers exist | `SpeakerTomlConfig`, `AudioControllerTomlConfig` | Load helpers have no callers; NBT is currently the effective restore path | Decide whether TOML is authoritative or export-only and test that decision |
| TTS configuration | Implemented but untested | `config/abs-tts.toml` is generated and loads validated cache capacity, FFmpeg path and five engine base URLs at startup | `TTSConfig`, `TTSAddon` | No live reload or WebUI editing; URL changes require restart | Verify a non-default local/remote engine URL at runtime |
| TTS cache | Implemented but untested | The addon checks a deterministic full-request cache before Provider synthesis; validated atomic entries use a 128 MiB access-time LRU and same-key requests are serialized | `AddonTTSBridge`, `TTSAudioCache`, `DiskCachePruner` | Cache key has no explicit synthesis-format version | Verify eviction and cache hits with a real Provider; define version policy before changing output format |
| Server lifecycle | Implemented but untested | HTTP, ffmpeg-probe and cache-maintenance executors are owned and stopped on server stopping | `ABSServerLifecycle`, `ABSHttpServer`, `FfmpegSupport`, `LibraryCacheMaintenance` | Requires runtime stop/restart verification; provider bridge has no shutdown hook | Add lifecycle integration tests |
| Client lifecycle | Implemented but untested | Active sound/subtitle state and in-flight download tasks clear on player quit | `AudioBoundsSystemClient`, `SpeakerAudioManager` | Download executor lives for the client process, although its threads are daemon threads | Add disconnect-during-download integration test |
| Authorization | Implemented but untested | Sessions, mutation header, folder mutation policy, speaker checks and controller owner/OP/distance checks exist | `WebSessionStore`, `WebAuthHelper`, `ABSLibrary`, `ABSNetwork` | Existing ownerless controllers are claimed on first save; Minecraft packet paths lack integration tests | Run owner/non-owner/OP multiplayer tests |
| Error handling | Partial | Failures are caught at HTTP/provider boundaries; Provider JSON/WAV/error bodies are bounded | Multiple | Most errors collapse to HTTP 502 or null; diagnostic playback logs use WARN | Add typed failures and rate-limited/user-facing diagnostics |
| Automated tests | Partial | 39 pure-Java bridge/cache/bounds/endpoint/request/security/integrity/client-cache/maintenance/config/provider/transfer tests pass | Test sources under `common` and `tts-addon` | Full registration, actual playback, lifecycle and loader paths remain uncovered | Continue stabilization regression coverage |
| CI | Missing | No `.github/workflows` files | — | Regressions are not automatically detected | Add after local tests are stable |

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
| `ABSHttpServer` fixed pool (8 daemon threads) | WebUI/API, synthesis call, file reads/writes | Temporary until WebUI RPC migration; owned and stopped on server stop |
| `AudioTransferService` scheduled pool (2 daemon threads) | Bounded server audio reads and paced Minecraft chunk sends | Owned and stopped on server stop; at most two active transfers globally and per player |
| `SpeakerAudioManager` fixed pool (2 daemon threads) | Client cache reads, transfer waits and integrity checks | Tasks are tracked and interrupted on stop/disconnect; requests have timeouts |
| `FfmpegSupport` single daemon thread | Startup capability probe | Owned and stopped on server stop; external probes are time-bounded |
| `LibraryCacheMaintenance` single daemon thread | Root-level orphan Ogg cleanup | Runs once after HTTP startup; owned and stopped before HTTP shutdown |
| Minecraft server thread | Network packet callbacks queued through Architectury | Controller/speaker mutation occurs here |
| External ffmpeg process | WAV-to-Ogg conversion | 90-second timeout with graceful then forced termination |

## TODO/FIXME and hidden stubs

No literal `TODO`, `FIXME`, `UnsupportedOperationException`, or `NotImplemented` markers were found. There are nevertheless functional stubs: `TTSConfig` explicitly describes a future NightConfig phase, and both block entities contain TOML-ready flags without invoking their TOML load helpers.
