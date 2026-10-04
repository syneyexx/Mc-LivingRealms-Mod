# Living Realms — Definition of Done matrix

**Checkpoint:** v3.0.0-rc4 autonomous deepen (holy-day festivals + assistance delivery) — see `IMPLEMENTATION_LEDGER.md`. Schema **16**, protocol **14**, ContentRevision **9**. Core suite **34** tests.

A subsystem is **COMPLETE** only when its authoritative model, simulation rules, persistence/migration, Minecraft runtime projection, player feedback/UI, required content/assets, and regression tests are all complete. `CORE VERIFIED` is deliberately not the same as complete.

| Subsystem | Model | Simulation | Save/migrate | MC runtime | UI/feedback | Content/assets | Tests | Status |
|---|---|---|---|---|---|---|---|---|
| World clock/history | ✅ | ✅ | ✅ | ✅ | ✅ dedicated History tab + commands | n/a | ✅ | COMPLETE (singleplayer core; boot smoke pending globally) |
| Factions/kingdoms | ✅ 12 starter monarchies + hidden Wizard Trees theocracy + player-founded realms | ✅ diplomacy/war/trade/growth | ✅ | ✅ bounded citizen + military projection | ✅ Overview/Realms/Map + Join/Leave + found/locate | 🟡 12 citizen variants; bespoke royal art pending | ✅ | PARTIAL (linked runtime smoke pending) |
| Settlements/growth | ✅ + persistent policy + 380+ starter settlements/colonies | ✅ organic planning + immigration + automatic housing growth + density compression | ✅ schema 16 + ContentRevision 8 authored-block ledger | ✅ terrain-aware buildings, streets, sidewalks, provenance-safe construction, Waystone bridge | ✅ management + locate/found commands | 🟡 varied architecture/12 NPC looks; deeper art pass remains | ✅ density/layout/player-realm/construction-integrity gates | PARTIAL (linked runtime smoke pending) |
| Society/needs/unrest | ✅ aggregate + persistent named social citizens | ✅ needs/personality/memory/relationships + rumor diffusion | ✅ schema 12 (schema 11 social + civilization state) | 🟡 physical jobs + street routines + specialist civic routines | ✅ Society tab + free-text NPC dialogue | 🟡 12 citizen looks; deeper role art pending | ✅ society/dialogue/infrastructure gates | PARTIAL (linked dialogue/runtime smoke pending) |
| Government/succession | ✅ | ✅ | ✅ | 🟡 world history only | ✅ Politics-tab ruler/government/stability/legitimacy/corruption/tax presentation | ❌ bespoke ruler/court presentation | ✅ | PARTIAL (assets/runtime smoke pending) |
| Diplomacy/treaties | ✅ | ✅ | ✅ | 🟡 strategic only | ✅ Politics-tab bounded relations/opinion/trade/treaty detail | n/a | ✅ | PARTIAL (runtime smoke pending) |
| War/objectives/sieges | ✅ | ✅ objectives now drive movement/combat/sieges | ✅ | 🟡 bounded military projection | ✅ War tab objectives/siege progress + strategic-map fronts | 🟡 generic troops | ✅ command-routing/codec tests | PARTIAL (runtime smoke/assets pending) |
| Territory/jurisdiction | ✅ | ✅ | ✅* derived | 🟡 runtime queries | ✅ local jurisdiction + strategic claim overlay | n/a | ✅ | PARTIAL (runtime smoke pending) |
| Economy/markets | ✅ | ✅ taxation + scarcity markets + atomic player transactions | ✅ | 🟡 physical emerald/item trade bridge at completed markets; linked smoke pending | ✅ Economy UI + canonical tax controls + server-authoritative BUY/SELL quotes/actions | vanilla commodity/emerald bridge | ✅ transaction replay/stock/treasury/codec/release-audit gates | PARTIAL (linked runtime smoke pending) |
| Trade/logistics | ✅ | ✅ + cumulative liveness counters | ✅ | ✅ caravan projection | ✅ Ops-tab shipment progress/value/parties | 🟡 generic caravan | ✅ TradeLivenessTest | PARTIAL (assets/runtime smoke pending) |
| Transport networks | ✅ + SettlementGeographyProfile | ✅ bounded same-realm route graph; water modes use geography | ✅ geography sidecar outside schema payload | ✅ physical carriageways/sidewalks/bridges + no straight-corridor mountain fallback | ✅ Ops-tab route quality/security/capacity/status | 🟡 vanilla road/rail/bridge projection; dedicated route assets pending | ✅ geography + corridor gates | PARTIAL (assets/runtime smoke pending) |
| Industry/Create | ✅ persistent sites | ✅ production + maintenance + breakdown + repair + starvation | ✅ schema 7 + legacy migration | 🟡 bounded Create machinery yards + sabotage hook; kinetic network boot not verified | ✅ Ops-tab condition/status/utilization/downtime + command fallback | 🟡 Create block projection; powered network polish pending | ✅ | PARTIAL |
| Crime/notoriety | ✅ | ✅ | ✅ | ✅ murder/caravan/property/industrial sabotage hooks | ✅ dedicated Law tab + wanted/reputation/infamy/bounty feedback | n/a | ✅ | PARTIAL (runtime smoke pending) |
| Bounty/custody | ✅ | ✅ | ✅ | ✅ arrest/custody/release/treasury payout runtime | ✅ interactive server-authoritative Law tab + command fallback | n/a | ✅ canonical action/codec tests | PARTIAL (runtime smoke pending) |
| Wildlife ecology | ✅ aggregate cohorts + region centers | ✅ stabilized food-web + colonization | ✅ schema 8 region positions | ✅ bounded terrestrial/aquatic/amphibious/flying LOD | ✅ bounded Ecology dashboard with region/species/health/needs telemetry | 🟡 134 bundled species; bespoke per-species art incomplete | ✅ 3650-day biodiversity + dashboard codec/bounds + projection stress gates | PARTIAL (assets/runtime smoke pending) |
| Animal behaviour | ✅ utility brain + explicit morphology/locomotion | ✅ hunt/flee/defend/migrate/social | ✅ through populations | ✅ ground/water/amphibious/flying navigation | n/a | 🟡 morphology families exist; bespoke animations/textures incomplete | ✅ | PARTIAL |
| Biomes/habitats | ✅ 25 archetypes + spatial ecology regions | ✅ exploration discovery/classification | ✅ schema 8 centers | ✅ overlay mapping for vanilla/BOP/Terralith/modded biomes; no biome-source replacement by design | ✅ Ecology tab | n/a — existing worldgen stack remains authoritative | ✅ classifier + colonization + 3650-day biodiversity gates | COMPLETE (singleplayer core; linked runtime smoke pending globally) |
| Aviation | ✅ | ✅ | ✅ | 🟡 aircraft entity projection | ✅ Forces-tab air-wing status/mission/readiness | 🟡 generic aircraft | ✅ | PARTIAL (assets/runtime smoke pending) |
| Naval systems | ✅ ports/fleets/classes | ✅ production/movement/combat/blockade/raiding/amphibious + geography port discovery | ✅ schema 6 | ✅ bounded ship projection + dock intents + physical loss accounting | ✅ Forces-tab fleet/port/composition/readiness/supply status | 🟡 generic ship visual; dock blueprint present | ✅ CompletionPass port/locate gates | PARTIAL (assets/runtime smoke pending) |
| Player reputation/faction membership | ✅ per-faction reputation/membership/rank/service | ✅ join/leave/promotion/expulsion/crime effects | ✅ schema 6+ | ✅ runtime commands + crime/combat-service hooks | ✅ contextual dashboard Join/Leave + standing feedback | n/a | ✅ authorization/spoof tests | COMPLETE (singleplayer core; boot smoke pending globally) |
| Bounty hunters | ✅ contract/assignment/capture | ✅ | ✅ | ✅ bounded NPC hunter runtime | ✅ interactive Law-tab bounty board + command fallback | 🟡 generic hunter visual | ✅ | PARTIAL (assets/runtime smoke pending) |
| Singleplayer client/integrated-server sync | ✅ bounded immutable dashboard snapshot | ✅ authoritative request/response + canonical action service | server save | 🟡 NeoForge custom payloads implemented; linked integrated-server runtime test pending | ✅ dashboard + Law actions | n/a | ✅ codec/bounds/action authorization; integrated-runtime smoke pending | PARTIAL |
| Config/data packs | ✅ simulation config + 134 strict species JSONs + generator | ✅ catalog validation/colonization + curated presets | ✅ schema 9 config persistence | ✅ atomic species reload with live-world rollback protection | ✅ Settings tab with server-authoritative presets | 🟡 | ✅ bundled-pack + preset persistence/action tests | PARTIAL (runtime smoke pending) |
| Requested modpack compatibility | ✅ expanded target-pack policy | n/a | n/a | 🟡 fixed target-pack registry/API integration + Waystones + foreign village/structure adoption + safe non-load-bearing palette integration | n/a | 🟡 visible RPG/magic/ranged equipment; Guns++ + GamingBarn NPC exclusion | ✅ compatibility/worldgen/release-audit gates | PARTIAL (full linked pack smoke test pending) |
| Performance/LOD | ✅ | ✅ | n/a | ✅ bounded projections | n/a | n/a | ✅ | STRONG, NOT FINAL |
| World map/strategic UI | ✅ bounded dashboard + dedicated M strategic-map/ecology/operations models | n/a | n/a | 🟡 M-map + packet-backed dashboard implemented; linked runtime smoke pending | ✅ responsive dashboard + dedicated M map + K creative catalog | 🟡 vanilla UI only | ✅ protocol-v12 codec/bounds/overflow tests | PARTIAL (runtime smoke/assets pending) |

## Non-negotiable release gates

1. `./scripts/test-core.sh` passes with Java 21, `-Xlint:all -Werror`.
2. Full NeoForge/Create Gradle compile passes with no source errors.
3. Integrated singleplayer server boot test passes.
4. Client boot test passes.
5. Save/load/reload test passes for current schema plus supported migrations. Core decoder coverage now spans schemas 1-12; integrated Minecraft SavedData reload is still a runtime gate.
6. 30/365/3650-day deterministic soak tests pass.
7. Entity projection stress test stays under configured budgets without duplication or canonical-state loss.
8. Singleplayer integrated-server test verifies authoritative actions, client snapshot compatibility, and no canonical-state mutation from UI code.
9. Every gameplay-facing system has a way for the player to perceive and interact with it.
10. No subsystem may be marked COMPLETE while a required row above is yellow/red.

- Container theft runtime: ✅ faction-property detection + open/close delta + witness-gated crime; linked NeoForge smoke test pending.

| Faction storage theft | ✅ authored storage + property crime | ✅ canonical stock is converted to physical goods | n/a | 🟡 PlayerContainer open/close bridge + witness law path | crime feedback | barrel storage + modded item visibility | ✅ storage resolver/delta/law/release-audit gates | PARTIAL (linked runtime smoke test pending) |


**Projection stress coverage:** wildlife cohorts, trade caravans, citizens, armies, aircraft and fleets are flooded above configured budgets; projection IDs/slots must remain unique and reconciliation must remove duplicates/orphans.

**RC4 automated save gates:** fixed historical schema-1/2 fixtures, versioned schema-3..12 migration fixtures, deterministic current-schema encoding, semantic validation, 32 MiB payload/64 KiB string ceilings, strict UTF-8, checksum/schema consistency, corruption/truncation/trailing-data rejection and 768 deterministic mutation-fuzz cases all pass under Java 21 `-Xlint:all -Werror`.
