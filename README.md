# Zombie AI Overhaul — modified blind/hearing-first build (Forge 1.20.1)

## Changes in this modified source
- Default zombie vision range is 1 block (still configurable in `config/zombieai-common.toml`).
- Hearing-triggered close-range target acquisition: after a sound reaches a zombie, it can target any valid player within 5 blocks without line of sight for 10 seconds (both values configurable).
- Hearing triggers are independent of visual acquisition and are checked every AI tick; normal visual acquisition remains limited to the configured 1-block range.
- Sound source priority: one-shot high-priority events (e.g. the existing Zombie Awareness sound entities generated for TACZ gunfire) override ongoing speech; quieter/ongoing sources are compared using effective audibility (range minus distance and wall attenuation). A high-priority sound briefly resists being overridden by ordinary speech.
- Optional Simple Radio integration, using the inspected Forge 1.20.1 API shape: `SimpleRadioApi` -> `speakers()` -> `get()`, active `RadioSpeaker.getSpeakingTime()`, and `getLocation()`. Reflection keeps Simple Radio optional. Active speaker output is treated as a high-priority sound at the speaker location because server-side Simple Radio does not expose microphone amplitude for its transmitted voice frames. If the optional API cannot be read, the mod logs one warning and keeps all other AI working.
- Existing Zombie Awareness/TACZ attraction is not duplicated: the current sound-entity integration remains responsible for those gunfire events.
- Other non-player targeting, retaliation, blood-scent behavior, navigation, random attributes, and zombie scaling are retained.

## New config defaults
- `vision.sightDistance = 1.0`
- `hearing.triggerAttackRange = 5.0`
- `hearing.triggeredAttackSeconds = 10`
- `hearing.simpleRadioEnabled = true`
- `hearing.simpleRadioRange = 48.0`

## Build status
This archive is source code only. A compiled JAR has not been produced or runtime-tested in this environment: there is no Gradle executable/wrapper in the provided project or environment, and the build distribution cannot be fetched because outbound DNS/network access is unavailable. Build with JDK 17 and Gradle/ForgeGradle in a network-enabled development environment, then test with Forge 47.3.0, Simple Voice Chat, Simple Radio, and the user's existing gunfire-attraction mod.
