## Module-owned Observer UI

- Every new or modified player-facing `Screen`/`Menu` must expose a module-owned,
  read-only semantic Observer mode through the TotemCore provider contract when
  its state is safe to relay. TotemVanillaTweaks coordinates transport only and
  must never copy this module's renderer or draw a lookalike screen.
- Observer rendering is permanently framebuffer-free: never transmit a
  screenshot, framebuffer, video or pixel stream.
- Observer mode must suppress local widget/menu mutation, lifecycle requests and
  action packets. Escape may only stop observing.
- `DiscordConfigScreen` is metadata-only by design. Never relay its worker URL,
  API key, channel draft, channel configuration, credentials, tokens, secrets,
  prompts or any unsent text. Its title metadata must also be redacted.
- UI changes require unit coverage, native-scale Client GameTest screenshots,
  dedicated three-JVM E2E and Production Runtime validation.
- Provider capture/create and handle methods are client-thread-only; GameTests
  must use their client-thread context helpers.
