# Architecture Decisions

## ADR-001: Preserve the existing vertical slice

Status: Accepted

The existing Core HTTP API, `TTSBridge`, provider implementation, library registration and speaker playback flow are the compatibility baseline. Stabilization work will add seams, adapters and tests rather than replacing this flow.

## ADR-002: Core owns generic audio; addon owns provider details

Status: Accepted

ABS Core owns library registration, delivery, playback, bounds, subtitles and generic bridge DTOs. The TTS addon owns engine endpoints, speakers, tuning parameters, synthesis and transcode orchestration. VOICEVOX-specific types must not be added to Core.

## ADR-003: Keep synchronous bridge v1 compatible

Status: Accepted for stabilization

`TTSBridge.synthesize` currently returns `byte[]` synchronously. Removing or changing it would break the current addon boundary. Async/cancellable behavior should initially be introduced behind the HTTP handler or through an additional compatible API, not by removing the v1 method.

## ADR-004: Runtime reports and automated verification are distinct

Status: Accepted

The handoff states that WebUI configuration, synthesis, registration and playback work. Those features are marked “Verified working” with that evidence, while code-only features remain “Implemented but untested.” A successful Gradle build is not considered runtime verification.

## ADR-005: Forge 1.20.1 / Java 17 is the release gate

Status: Accepted

Fabric compatibility is preserved, but release readiness is evaluated first on Forge 1.20.1 with a Java 17 runtime. Builds performed with a newer host JDK do not satisfy that gate by themselves.

## ADR-006: Core owns generic FFmpeg process execution

Status: Accepted

Audio transcoding is useful without TTS because ABS Core supports uploaded audio files. The generic ffmpeg execution, timeout, process termination, diagnostic limit and temporary-file cleanup therefore live in Core. The TTS addon retains its existing `tts.transcode.FfmpegTranscoder` entry point as a compatibility adapter and passes its configured ffmpeg path to the Core service. Provider-specific synthesis remains in the addon.

## Open decisions

### TOML authority

The project writes comment-preserving TOML and NBT, but no code currently invokes the TOML load helpers. Before enabling those calls, decide precedence and conflict handling between NBT, TOML and WebUI updates.

### Bounds falloff semantics

Current code attenuates from the source center to the boundary. The original specification describes full volume inside the bounds and a configurable fade outside the boundary. Changing this affects existing worlds and needs an explicit migration/compatibility choice.

### Remote audio endpoint

Status: Partially resolved

The client now derives the HTTP hostname from the active Minecraft connection and falls back to localhost for an integrated/local connection. This preserves the existing play packet. The fixed port still needs a compatibility design for proxies, NAT and configurable HTTP ports.
