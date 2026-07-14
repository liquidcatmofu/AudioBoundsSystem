# TTS Bridge API v1

## Scope

The supported Core/Add-on boundary is the `io.github.liquidcatmofu.abs.ttsbridge` package in ABS Core. ABS TTS Addon implements this API; Provider endpoints, Provider JSON models and FFmpeg configuration remain addon internals.

Public v1 types:

- `TTSBridge`: availability, engine discovery and synchronous Ogg synthesis.
- `TTSBridgeRegistry`: one process-wide optional bridge registration slot.
- `TTSSynthesisRequest`: engine ID, speaker ID, text and numeric parameters.
- `TTSEngine`, `TTSSpeaker`, `TTSParam`: discovery DTOs used by Core and WebUI serialization.
- `TTSSynthesisException`: optional typed failure details understood by Core.

`TTSBridge.synthesize` returns a complete Ogg Vorbis byte array. Implementations must either return a valid Ogg result or throw; `null` is not a valid result. Calls may block and Core must invoke them away from the Minecraft server thread.

## Compatibility policy

The project has not released v1 yet, so incompatible cleanup may still occur before the first release when recorded in the architecture decisions. Once v1 is released:

- Existing public classes, methods and public DTO fields will not be removed or renamed within the v1 line.
- New DTO fields and interface default methods may be added when older implementations can continue working.
- Async, streaming or cancellable synthesis must be introduced as an additional interface or adapter. It must not replace the synchronous `synthesize` method in v1.
- New typed failures may extend the existing failure model, but callers must continue to handle an unspecified `Exception` from third-party v1 implementations.
- Provider-specific request or response types must not enter this Core package.
- A breaking contract requires a new package or explicit bridge version and a compatibility adapter where practical.

## Registration and lifecycle

The addon registers its bridge during initialization with `TTSBridgeRegistry.set`. Absence means that no TTS addon is installed. Core owns Minecraft/WebUI request scheduling; the addon owns Provider communication and its synthesis cache. Registry replacement and live addon unload are not supported in v1.

## Parameter and identity rules

`engineId`, `speakerId` and `text` are required synthesis identity fields. Parameter keys correspond to the selected engine's `TTSParam.key`. Implementations may reject unknown, non-finite or out-of-range values. Cache and duplicate identity must include sorted parameter keys so map iteration order cannot change the result.

This contract does not expose Minecraft classes, loader-specific classes, VOICEVOX JSON types, filesystem paths or HTTP connection objects.
