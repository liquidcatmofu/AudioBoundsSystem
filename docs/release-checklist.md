# ABS / TTS Addon Release Checklist

Use Java 17 for every release-gate command. Record the tested commit, loader versions and Provider versions with the result.

## Automated gate

- [ ] `./gradlew clean test build` succeeds.
- [ ] `./gradlew :forge:runGameTestServer` reports all required tests passed.
- [ ] `git diff --check` succeeds.
- [ ] The release worktree contains no unintended files.
- [ ] The first GitHub Actions run succeeds with read-only repository permissions.

## Single-player smoke test

- [ ] Forge starts with ABS Core alone and generates its Core configuration.
- [ ] Forge starts with ABS Core plus TTS Addon and generates `config/abs-tts.toml`.
- [x] Fabric starts with the same module combinations.
- [x] `/abs ui` opens the loopback WebUI without requiring a separately opened port.
- [x] Uploading an audio file registers one library entry and plays from a Speaker.
- [x] Replaying the same entry uses the client disk cache without another audio transfer.
- [x] Subtitle display is disabled on a newly placed Speaker.
- [x] Enabling subtitles shows the configured title or library display name, never an internal UUID reference.
- [x] Subtitle display duration follows the audio duration.

## Dedicated server and two clients

For a local Forge test, start these in separate terminals. The client run
configurations use separate directories and the usernames `PlayerOne` and
`PlayerTwo`.

```bash
./gradlew forge:runServer
./gradlew forge:runClientOne
./gradlew forge:runClientTwo
```

Fabric provides the equivalent `fabric:runServer`, `fabric:runClientOne`, and
`fabric:runClientTwo` tasks.

On the first local server run, accept the generated `eula.txt` and set
`online-mode=false` in `server.properties`. This is only for local development
with the two fixed offline test identities; do not use that setting for a
public server.

- [x] Both clients can authenticate their own WebUI sessions.
- [x] Owner, shared-player, non-owner and operator permissions match the documented library policy.
- [x] One Speaker playback reaches both nearby clients.
- [ ] Each client receives a player-bound transfer token; another player cannot consume or discard it.
- [ ] Concurrent playback respects the two-transfer server limit and retries transient busy responses.
- [ ] A cached client does not download again and its unused token is promptly discarded.
- [ ] Disconnecting during download clears the client operation and does not block later transfers.
- [x] Disconnecting and reconnecting normally leaves WebUI and playback functional.
- [ ] Stopping and restarting the dedicated server leaves no stale Web RPC, transfer, probe or maintenance task.

## TTS and failure handling

- [ ] Each configured Provider discovers engines and speakers.
- [x] Repeating an identical synthesis request returns immediately from cache and creates no duplicate library entry.
- [ ] Changing engine, speaker, text or a numeric parameter creates a distinct cache identity.
- [ ] A non-default local or remote Provider URL works after restart.
- [ ] An unavailable Provider returns a bounded user-facing error without hanging a Web RPC worker.
- [ ] An invalid FFmpeg path reports an actionable error.
- [ ] A valid non-default FFmpeg path successfully transcodes.
- [ ] TTS cache eviction respects the configured capacity.

## Playback behavior

- [ ] Listener movement reaches silence at and outside every configured bounds shape.
- [ ] LEVEL mode starts and stops with redstone state.
- [ ] PULSE mode triggers only on a rising edge.
- [ ] Controller queues preserve order, sequence delays and STOP/RESTART behavior.
- [ ] Stopping playback clears audio and subtitle state on every receiving client.

## Release record

- Commit:
- Java runtime:
- Forge result:
- Fabric result:
- Dedicated-server/two-client result:
- TTS Providers tested:
- Known limitations confirmed: fixed block sources only; no moving 3D source, separate 2D playback API or multi-cue timed subtitle track.
