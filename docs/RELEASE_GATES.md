# Living Realms v3.0 release gates

## Automated headless gate
Run from the project root with JDK 21:

```bash
./scripts/test-core.sh
python3 ./scripts/release-audit.py
```

The core gate must compile with `--release 21 -Xlint:all -Werror` and pass:
- deterministic core replay;
- bundled 134-species audit;
- strategic completeness;
- projection stress and reconciliation identity checks;
- save migration matrix across every supported schema;
- save corruption/truncation/trailing-data integrity checks, bounded payload/string resources and strict UTF-8;
- deterministic save mutation fuzzing plus production-hardening invariants;
- exact 3650-day deterministic soak with day 30/365/3650 persistence/invariant checkpoints.

## Full linked build
Use `build-production.sh` on Linux/macOS-like shells or `build-production.ps1` on Windows. The production runner executes the headless gates and source audit before the linked Gradle build. Gradle 8.10.2 bootstrap bytes are accepted only after matching the official binary SHA-256.

Required result:
- Java 21;
- Gradle 8.10.2 bootstrap or equivalent installed Gradle;
- NeoForge 21.1.219 resolves;
- Create 6.0.10-280 and its pinned dependencies resolve;
- `clean build` completes with no source/resource errors;
- final mod JAR appears under `build/libs`.

A network/bootstrap failure is not a passing linked build and is not evidence of a source compile failure. Record it separately as an environment block.

## Integrated singleplayer smoke matrix
Use a fresh test world plus one existing migrated world.

1. **Boot** — client reaches title screen, fresh singleplayer world starts, no Living Realms classloading/registry/datapack errors.
2. **Save lifecycle** — create world, advance simulation, save/quit, reopen, reload datapacks, save/quit again; canonical day, factions, ecology, policies and player standing remain consistent.
3. **Dashboard** — open with `F12`; every tab renders; server snapshot protocol is accepted; no mutable canonical object is exposed to client code.
4. **Actions** — exercise faction join/leave, bounty accept/abandon, tax change, settlement policy, settings preset and physical market buy/sell. Verify server-side rejection for spoofed/remote/invalid actions.
5. **Projection lifecycle** — move/teleport across LOD boundaries and unload/reload chunks containing wildlife, citizens, caravans, armies, aircraft and fleets. No duplicate projection IDs, orphan entities or canonical loss.
6. **Property crime** — open authored foreign faction storage, remove goods with/without witness, verify only net removed authored storage is treated as theft; own-faction storage and Traveler's Backpack remain excluded.
7. **Worldgen coexistence** — traverse vanilla, Biomes O' Plenty/Terralith and other target worldgen terrain. Living Realms classifies/overlays ecology and does not replace the biome source.
8. **Create industry** — locate projected industrial yards, verify expected Create blocks form a valid kinetic network where applicable, break/sabotage projected machinery and verify canonical site damage without making loaded chunks production authority.
9. **Markets** — completed Living Realms market required; fixed 8-unit package uses server quote, physical emerald/items and exact canonical stockpile/treasury accounting.
10. **Target modpack** — load the fixed requested pack, confirm target registries/APIs are detected, Create Deep Seas/Aeronautics remain excluded, Guns++ and GamingBarn's Guns are never selected for NPC equipment; eligible non-gun RPG/ranged/magic equipment remains available.

## Promotion rule
Do not label v3.0 release-complete or promote to v4.0 while any linked/runtime gate above remains unverified. Headless success is necessary but not sufficient.

## Headless runtime smoke
```bash
./scripts/runtime-smoke.sh
```
Covers founding (command + dashboard custom name), ruler leave rejection, join reputation gate, escort identity namespace, save/load, one-day advance, and dashboard snapshot build. Linked Minecraft boot/F12/M remain an external gate before `runtimeSmoke=pass` in `RELEASE_MANIFEST.json`.

## Current content-revision smoke additions
- Load an older RC4 save and verify one-shot migrations up through `ContentRevision=12` (schema **17** payload, sparse densifier, Waystone provenance, typed authored-block ledger, persisted onboarding). Revision-6/10 construction rebuild and revision-4/6/12 density expansion must not re-trigger after the world has been rewritten.
- Press **M** and verify the dedicated world map renders discovered biome/ecology cells, kingdoms/claims, settlements, roads/routes, armies and war fronts. Pan/scale polish may be iterative, but snapshot bounds and server authority must hold.
- Press **K** in creative, search for items from several target mods, spawn 1/16/64 items, and verify non-creative/invalid-id packets are rejected server-side.
- Verify actual world spawn lies within a Living Realms kingdom/city or receives the bounded Crownspawn fallback.
- Visit vanilla/Better Villages/other qualifying generated settlements or structures. Adoption must preserve their existing blocks and add Living Realms growth later rather than immediately overwriting them.
- Observe lumberjack, farmer, miner, fisher and hunter citizens. Physical work must feed canonical resources while avoiding arbitrary player structures, block entities and non-ore mining.
- Observe civilians commuting on completed street/road intents and settlement-to-settlement routes; entity counts must stay within projection budgets.
- Observe guards/soldiers with compatible target-mod armor/weapons. Armor and held items must be visible. Guns++ and GamingBarn's Guns must never be assigned to NPCs.
- Traverse several modded biomes (including Regions Unexplored/BOP/Terralith where available) and verify settlement/wildlife materialization remains terrain-safe and biome-agnostic.
- Confirm 134-species datapack load and multi-minute flying-wildlife stability, including Common Raven.

## Buildfix9 living-world smoke additions
- Load an older RC4 save and verify the one-shot `ContentRevision=3` migration does not repeatedly reseed/rebuild on later reloads.
- Visit multiple settlements and verify terrain-safe varied buildings, proper roads/sidewalks and no repeating foreign-block towers/floating shells.
- With Waystones installed, approach a loaded settlement and verify exactly one named settlement Waystone is created and remains valid after save/reload.
- Run `/livingrealms locate city`, `/livingrealms locate mine`, `/livingrealms setday 50` and `/livingrealms advance 10` and verify canonical state changes rather than clock-only changes.
- Found a distant settlement with `/livingrealms found <name>` and verify membership, civilians, physical construction/growth, bilateral relations and participation in ordinary diplomacy/war systems.
- Observe civilians long enough to see deterministic names, multiple visual variants and street-route traversal without runaway entity counts.
- Observe a Common Raven or other flying Living Realms wildlife for multiple minutes; there must be no `generic.flying_speed` / `FlyingMoveControl` exception.
- Observe military/guards with the target modpack loaded; compatible non-denied mod weapons may be equipped. Guns++ and GamingBarn's Guns remain NPC-forbidden.
