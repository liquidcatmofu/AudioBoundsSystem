# ABS / TTS Addon Implementation Status

Last audited: 2026-07-13

## Audit basis

This document records what is present in the repository and what was verified during the audit. A successful build proves compilation and packaging only. Runtime claims for the WebUI-to-playback path are based on the existing handoff report and were not independently reproduced during this audit.

Status terms follow `handoff_v2/02_ABS_codex_implementation_addendum.md`.

## Build and modules

- Target: Minecraft 1.20.1, Architectury 9.2.14, Forge 47.4.10, Fabric Loader 0.19.3.
- Source and target compatibility: Java 17.
- Audit host JDK: Temurin 25.0.3. A Java 17 runtime build remains to be run.
- Modules: `common`, `fabric`, `forge`, `tts-addon`, `tts-addon-fabric`, `tts-addon-forge`.
- `./gradlew build` succeeded on 2026-07-13 for all six modules.
- Every Gradle test task reported `NO-SOURCE`; no CI workflow is present.

## Feature inventory

| Feature | Status | Evidence | Main classes | Risks | Next action |
|---|---|---|---|---|---|
| Web dashboard and authentication | Verified working | Handoff runtime report; static WebUI and HTTP routes exist | `ABSHttpServer`, `WebUIHandler`, `AuthApiHandler`, `WebSessionStore` | No explicit CSRF token; server binds all interfaces; no configurable port | Add request/security tests and document deployment assumptions |
| WebUI library configuration | Verified working | Handoff runtime report; CRUD routes and browser UI exist | `LibraryApiHandler`, `ABSLibrary`, `webui/app.js` | Several request bodies are unbounded; create operations use view access in some routes | Add validation and authorization tests before changing behavior |
| TTS provider discovery | Implemented but untested | Five providers are registered and exposed through the bridge | `TTSAddon`, `TTSProviderRegistry`, `AddonTTSBridge` | Availability calls are synchronous and repeated; registry has no lifecycle reset | Add fake-provider tests and bounded probing |
| VOICEVOX-compatible synthesis | Verified working | Handoff runtime report; `/audio_query` and `/synthesis` implementation exists | `VoiceVoxCompatibleProvider` | Blocking calls occupy an HTTP worker; response size is unbounded; coarse error types | Add transport seam, response limits, and error classification |
| WAV-to-Ogg transcoding | Verified working | Handoff runtime report; ProcessBuilder implementation exists | `tts-addon.transcode.FfmpegTranscoder` | No timeout/cancellation; process output can be unbounded; cleanup may fail if process remains | Introduce a process runner and lifecycle tests |
| TTS library registration | Verified working | Handoff runtime report; Ogg and JSON metadata are written | `LibraryTts`, `TtsEntry` | Direct non-atomic writes; no content validation or deduplication | Add atomic write and corruption tests |
| Audio upload and library storage | Implemented but untested | Upload/transcode and JSON metadata paths exist | `LibraryAudio`, `ABSLibrary`, `FfmpegTranscoder` | Upload is fully read before the 64 MiB check; no capacity policy | Stream with an enforced limit and add temporary-directory tests |
| Core/Add-on boundary | Implemented but untested | TTS addon implements the Core-owned bridge interface | `TTSBridge`, `TTSBridgeRegistry`, `AddonTTSBridge` | Synchronous byte-array API hides cancellation and typed failures; no compatibility version | Document v1 contract, then add compatible adapters only as needed |
| One-time audio delivery | Implemented but untested | Per-player tokens are generated and consumed once | `TokenStore`, `AudioRequestHandler`, `ABSHttpServer` | Full-file memory reads; token path is not constrained to cache root | Add expiry/single-use/path-validation tests |
| Client download and playback | Verified working | Handoff runtime report; dynamic sound store and mixin exist | `SpeakerAudioManager`, `ABSDynamicSoundStore`, `SoundBufferLibraryMixin` | URL is hard-coded to `localhost:25566`; no timeout/cancellation; common-pool tasks are unmanaged | Fix server endpoint derivation without changing packet compatibility |
| Fixed speaker bounds audio | Implemented but untested | Tickable sound reads speaker bounds and computes volume | `ABSSpeakerSoundInstance`, `AudioBounds`, `FalloffCurve` | Uses `Attenuation.NONE`; no configured outer falloff width; some curves do not match the original formulas | Add pure geometry/curve tests and reconcile semantics in an ADR |
| Moving 3D source | Missing | No entity-following sound implementation | — | Original specification is not fulfilled | Keep out of the stabilization milestones |
| 2D/UI playback | Missing | No separate 2D playback API or sound instance | — | Original specification is not fulfilled | Keep out of the stabilization milestones |
| Playback sequence | Implemented but untested | Sequence metadata and controller expansion are present | `LibrarySequence`, `AudioControllerBlockEntity` | No automated queue/retrigger tests | Extract pure queue planning logic for tests |
| Subtitle HUD | Partial | A single title/subtitle overlay is rendered | `SubtitleOverlayManager`, `SubtitleHudRenderer` | Not a timed `SubtitleTrack`; packet duration is fixed at 100 ticks | Test existing overlay, then define a compatible timed-track extension |
| Speaker/controller TOML | Partial | Comment-preserving save/load helpers exist | `SpeakerTomlConfig`, `AudioControllerTomlConfig` | Load helpers have no callers; NBT is currently the effective restore path | Decide whether TOML is authoritative or export-only and test that decision |
| TTS configuration | Stub | In-memory defaults and URL lookup exist | `TTSConfig` | No disk/WebUI integration; no validation | Preserve defaults and add a versioned config loader later |
| TTS cache | Partial | A command-side cache helper exists | `TTSAudioCache` | WebUI path bypasses it; legacy key omits engine and tuning parameters; no atomic writes/eviction | Add a deterministic full-request key and integrate after regression tests |
| Server lifecycle | Partial | HTTP server and sessions stop on server stopping | `ABSServerLifecycle`, `ABSHttpServer` | HTTP executor is not retained/shut down; bridge/providers have no shutdown hook | Add owned executors and idempotent lifecycle tests |
| Client lifecycle | Partial | Active sound/subtitle state clears on player quit | `AudioBoundsSystemClient`, `SpeakerAudioManager` | In-flight downloads are not cancelled; HTTP clients are recreated | Own downloads/client and cancel them on disconnect |
| Authorization | Partial | Sessions, folder access, and speaker owner checks exist | `WebSessionStore`, `ABSLibrary`, `ABSNetwork` | Controller save/test/stop packets lack owner/OP checks | Add permission tests and enforce a controller policy |
| Error handling | Partial | Failures are caught at HTTP/provider boundaries | Multiple | Most errors collapse to HTTP 502 or null; diagnostic playback logs use WARN | Add typed failures and rate-limited/user-facing diagnostics |
| Automated tests | Partial | 10 pure-Java bridge/cache/bounds tests pass | `AddonTTSBridgeTest`, `TTSAudioCacheTest`, `AudioBoundsTest` | HTTP, registration, playback, lifecycle and loader paths remain uncovered | Continue Milestone 1 |
| CI | Missing | No `.github/workflows` files | — | Regressions are not automatically detected | Add after local tests are stable |

## Storage locations

- World library metadata: `<world>/abs_library/<folder-id>/...`
- Core audio cache: `<world>/abs_cache/`
- Command-side TTS cache: `<world>/abs_cache/tts/`
- Speaker TOML: `<world>/abs/speakers/<dimension>/...`
- Controller TOML: `<world>/abs/controllers/<dimension>/...`, with a legacy lookup under `serverconfig/abs/...`
- Block configuration also persists in block-entity NBT.

## Threads and executors

| Owner | Work | Current lifecycle |
|---|---|---|
| `ABSHttpServer` fixed pool (8 threads) | WebUI/API, synthesis call, file reads/writes | Executor is not retained or explicitly shut down |
| `CompletableFuture` common pool | Client audio HTTP fetch | Futures are not tracked or cancelled |
| Minecraft server thread | Network packet callbacks queued through Architectury | Controller/speaker mutation occurs here |
| External ffmpeg process | WAV-to-Ogg conversion | Waits indefinitely; no cancellation/forced termination |

## TODO/FIXME and hidden stubs

No literal `TODO`, `FIXME`, `UnsupportedOperationException`, or `NotImplemented` markers were found. There are nevertheless functional stubs: `TTSConfig` explicitly describes a future NightConfig phase, and both block entities contain TOML-ready flags without invoking their TOML load helpers.
