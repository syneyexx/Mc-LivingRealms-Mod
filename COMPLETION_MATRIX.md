# Living Realms — Definition of Done matrix

**Checkpoint:** final-product wave — see `docs/AUTONOMOUS_COMPLETION_CHECKPOINT.md` / `docs/WAVE0_SYSTEM_INVENTORY.md`. Schema **17**, dashboard protocol **17**, network **14**, ContentRevision **11**. Core suite includes `FinalProductSystemsTest` + 3650-day soak.

A subsystem is **COMPLETE** only when its authoritative model, simulation rules, persistence/migration, Minecraft runtime projection, player feedback/UI, required content/assets, and regression tests are all complete. `CORE VERIFIED` is deliberately not the same as complete. Do not treat this matrix as finished until Wave 227 rebuild is done against final HEAD.

| Subsystem | Model | Simulation | Save/migrate | MC runtime | UI/feedback | Content/assets | Tests | Status |
|---|---|---|---|---|---|---|---|---|
| World clock/history | ✅ | ✅ | ✅ | ✅ | ✅ dedicated History tab + commands | n/a | ✅ | COMPLETE (singleplayer core; boot smoke pending globally) |
| Factions/kingdoms | ✅ 12 starter + Wizard Trees + player realms + heraldry | ✅ diplomacy/war/trade/growth | ✅ | ✅ bounded citizen + military projection | ✅ Overview/Realms/Map + Join/Leave + found/locate | 🟡 heraldry banners + 48 citizen skins; deeper royal art pending | ✅ | PARTIAL (linked runtime smoke pending) |
| Settlements/growth | ✅ + growth layers + BuildingCondition + 380+ starters | ✅ organic planning + immigration + housing growth | ✅ schema 17 + ContentRevision 11 | ✅ terrain-aware buildings, streets, sidewalks, provenance-safe construction, Waystone bridge | ✅ management + locate/found + onboarding | 🟡 hut→mansion + apartments + interiors; 8 culture architecture families | ✅ density/layout/worldgen/construction gates | PARTIAL |
| Society/needs/unrest | ✅ SocialCitizen + SocialMobilityEngine | ✅ needs/memory/relationships + mobility + workplaces | ✅ schema 12/13/17 | 🟡 physical jobs + street routines + specialist civic routines | ✅ Society tab + free-text NPC dialogue | 🟡 48 citizen looks; layered clothing deepen | ✅ society/dialogue/FinalProduct gates | PARTIAL |
| Government/succession | ✅ + SovereignDebt + GrandProject | ✅ debt/projects/succession | ✅ schema 4/13/17 | 🟡 court projection + history | ✅ Politics + debts/projects on overview | 🟡 heraldry-dyed court kits (not gold armor) | ✅ | PARTIAL |
| Diplomacy/treaties | ✅ | ✅ | ✅ | 🟡 strategic only | ✅ Politics-tab bounded relations/opinion/trade/treaty detail | n/a | ✅ | PARTIAL |
| War/objectives/sieges | ✅ + CampaignPlan + SiegeState equipment | ✅ campaign AI + siege breach/authored damage | ✅ | ✅ military + siege equipment + column/line/guard formations | ✅ War tab + campaigns | 🟡 troop/siege textures present; formations active | ✅ FinalProduct + soak | PARTIAL |
| Territory/jurisdiction | ✅ | ✅ | ✅* derived | 🟡 runtime queries | ✅ local jurisdiction + strategic claim overlay | n/a | ✅ | PARTIAL |
| Economy/markets | ✅ | ✅ taxation + scarcity markets + atomic player transactions | ✅ | 🟡 physical emerald/item trade bridge | ✅ Economy UI + tax controls | vanilla commodity/emerald bridge | ✅ | PARTIAL |
| Trade/logistics | ✅ + shipment logistics/escort/loss | ✅ + partial loss + escort risk | ✅ schema 17 | ✅ caravan projection + wagon/pack modes + escorts | ✅ Ops-tab shipment progress/value/parties | 🟡 caravan tex; escort NPCs | ✅ Trade* + FinalProduct | PARTIAL |
| Transport networks | ✅ + StreetType hierarchy + growth layers | ✅ bounded same-realm route graph | ✅ geography sidecar | ✅ physical carriageways/sidewalks/bridges | ✅ Ops-tab route quality/security/capacity/status | 🟡 vanilla road/rail/bridge projection | ✅ geography + corridor gates | PARTIAL |
| Industry/Create | ✅ persistent sites | ✅ production + maintenance + breakdown + repair + starvation | ✅ schema 7 | 🟡 bounded Create machinery yards | ✅ Ops-tab condition/status | 🟡 Create block projection | ✅ | PARTIAL |
| Crime/notoriety | ✅ | ✅ | ✅ | ✅ murder/caravan/property/industrial sabotage hooks | ✅ Law tab | n/a | ✅ | PARTIAL |
| Bounty/custody | ✅ | ✅ | ✅ | ✅ arrest/custody/release/treasury payout runtime | ✅ Law tab | n/a | ✅ | PARTIAL |
| Wildlife ecology | ✅ aggregate cohorts + region centers | ✅ stabilized food-web + colonization | ✅ schema 8 | ✅ bounded LOD | ✅ Ecology dashboard | 🟡 134 species textures; animation QA pending | ✅ | PARTIAL |
| Animal behaviour | ✅ utility brain + morphology/locomotion | ✅ hunt/flee/defend/migrate/social | ✅ | ✅ ground/water/amphibious/flying navigation | n/a | 🟡 morphology + species tex; intent-driven anim profiles | ✅ | PARTIAL |
| Biomes/habitats | ✅ 25 archetypes + spatial ecology regions | ✅ exploration discovery/classification | ✅ schema 8 | ✅ overlay mapping; no biome-source replacement | ✅ Ecology tab | n/a | ✅ | COMPLETE (singleplayer core; linked runtime smoke pending globally) |
| Aviation | ✅ | ✅ | ✅ | 🟡 aircraft entity projection | ✅ Forces tab | 🟡 class textures | ✅ | PARTIAL |
| Naval systems | ✅ ports/fleets/classes | ✅ + geography port discovery | ✅ schema 6 | ✅ bounded ship projection + docks | ✅ Forces tab | 🟡 class textures | ✅ | PARTIAL |
| Player reputation/membership/influence/careers | ✅ reputation + influence + careers | ✅ join/leave/promotion + service | ✅ schema 6/17 | ✅ runtime commands + crime/combat-service hooks | ✅ dashboard influence/career lines + unlock actions | n/a | ✅ FinalProduct | PARTIAL |
| Bounty hunters | ✅ contract/assignment/capture | ✅ | ✅ | ✅ bounded NPC hunter runtime | ✅ Law tab | 🟡 hunter texture | ✅ | PARTIAL |
| Assistance/contracts | ✅ AssistanceTask + ProductionContract | ✅ expanded pressure types | ✅ schema 13 | ✅ assist runtime | ✅ Ops + dialogue | n/a | ✅ Assistance + FinalProduct | PARTIAL |
| Singleplayer client sync | ✅ bounded immutable dashboard snapshot | ✅ authoritative request/response | server save | 🟡 NeoForge payloads; linked smoke pending | ✅ dashboard + Law actions + panel texture | 🟡 | ✅ protocol-v17 codec | PARTIAL |
| Config/data packs | ✅ simulation config + 134 species JSONs | ✅ catalog validation | ✅ schema 9 | ✅ atomic species reload | ✅ Settings tab | 🟡 | ✅ | PARTIAL |
| Requested modpack compatibility | ✅ expanded target-pack policy | n/a | n/a | 🟡 Waystones + foreign adoption + safe palette | n/a | 🟡 RPG/magic/ranged; gun deny | ✅ | PARTIAL |
| Performance/LOD | ✅ | ✅ | n/a | ✅ bounded projections | n/a | n/a | ✅ | STRONG, NOT FINAL |
| World map/strategic UI | ✅ bounded dashboard + M map | n/a | n/a | 🟡 M-map + packet-backed dashboard | ✅ F12/M/K + onboarding + influence unlocks | 🟡 panel/icons + settlement ambience VFX | ✅ protocol 17 | PARTIAL |

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
