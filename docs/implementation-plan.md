# ABS / TTS Addon Stabilization Plan

## Constraints

- Preserve the reported working WebUI → synthesis → registration → playback path.
- Do not replace the WebUI framework, provider model, package layout, or loader architecture.
- Forge 1.20.1 and Java 17 are the release baseline; Fabric remains buildable.
- Railway integrations and new playback modes are outside this stabilization effort.

## Milestone 0 — Audit

- Keep `implementation-status.md` synchronized with code evidence.
- Record build/test commands and environment in `testing.md`.
- Record compatibility-sensitive choices in `architecture-decisions.md`.
- Resolve whether TOML is authoritative configuration or a comment-preserving export.

Exit: feature states, storage, threads, lifecycle and known risks are documented.

## Milestone 1 — Regression protection

1. Enable JUnit 5 for pure-Java tests.
2. Add fake-provider tests for `AddonTTSBridge` request forwarding and unknown engines.
3. Test provider parameter schemas and deterministic full-request cache keys.
4. Test bounds geometry and falloff curves, including invalid/clamped inputs.
5. Test token expiry/single use through a controllable clock or package seam.
6. Add temporary-directory tests for metadata and atomic cache operations where Minecraft bootstrap is not required.
7. Separate tests that require a Minecraft runtime from ordinary unit tests.

Exit: current public behavior is protected without launching Minecraft, and remaining manual tests are listed.

## Milestone 2 — Stability fixes

Apply only issues demonstrated by the audit/tests:

- Own and shut down Web RPC workers, schedulers and client transfer tasks.
- Bound Provider HTTP calls, track Minecraft transfers, and cancel on disconnect.
- Keep audio and WebUI traffic on Minecraft networking so no additional public port is required.
- Add ffmpeg timeout, forced termination and bounded diagnostic capture.
- Enforce request/text/upload limits while streaming.
- Add deterministic cache keys covering engine, speaker, text and sorted parameters.
- Use temporary files plus atomic move for Ogg/metadata commits.
- Add typed synthesis/transcode/cache errors while retaining existing HTTP response compatibility.
- Replace unconditional WARN diagnostics with appropriate debug-level diagnostics.

Exit: failure, timeout, cancellation and shutdown tests pass; no external process or executor remains after stop.

## Milestone 3 — API boundary

- Treat the current `TTSBridge` classes as compatibility version 1.
- Document which classes are public to addons.
- Prefer default methods/adapters when adding async or typed-result APIs.
- Prevent provider-specific types from entering ABS Core.
- Add API compatibility checks before any signature removal.

Exit: the Core/Add-on contract and compatibility policy are explicit and tested.

## Milestone 4 — Release readiness

- Run with a Java 17 runtime on Forge 1.20.1.
- Manually verify WebUI settings, synthesis, registration and playback.
- Verify dedicated-server remote clients, multiple clients, single player and provider-down behavior.
- Add CI for the reproducible unit/build suite.
- Publish known limitations: no moving 3D source, no 2D playback, and no timed subtitle track.
