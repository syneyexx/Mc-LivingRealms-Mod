# Living Realms — singleplayer release profile

Living Realms is now scoped as a **singleplayer-first/offline** mod. Minecraft singleplayer still runs an integrated logical server, so the existing server-authoritative simulation and client snapshot boundary remain intentional. They prevent UI code from mutating canonical world state and make saves deterministic.

## Required release target

- Minecraft 1.21.1
- NeoForge 21.1.219
- Create 6.0.10
- Java 21
- Singleplayer integrated-server boot and play test
- Offline operation after dependencies/mod files are installed

## Explicitly not required

- Dedicated-server support as a release gate
- Internet-hosted multiplayer
- Remote-client authority/anti-cheat hardening
- Multi-user packet stress testing

## Still required despite singleplayer scope

- Client/integrated-server packet compatibility for the dashboard and actions
- Save/load migration and corruption safety
- Bounded entity materialization and LOD
- Create compatibility
- Client boot test
- Integrated-server boot test
- Long deterministic simulation soak
- Gameplay-facing UI/feedback
