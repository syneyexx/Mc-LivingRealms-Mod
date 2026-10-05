# Living Realms — Definition of Done matrix

CURRENT PINS: schema 20 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / surfaceSettlements 36 / perRealm 3 / spacing 2000

A subsystem is **COMPLETE** only when model, simulation, persistence/migration, Minecraft runtime projection, player feedback/UI, required content/assets, and regression tests are all real for the pins above.

| Subsystem | Model | Simulation | Save/migrate | MC runtime | UI/feedback | Content/assets | Tests | Status |
|---|---|---|---|---|---|---|---|---|
| World clock/history | ✅ | ✅ | ✅ | ✅ | ✅ History tab + commands | n/a | ✅ | COMPLETE |
| Factions/kingdoms | ✅ 12 + Wizard Trees + player realms | ✅ diplomacy/war/trade/growth + SettlementTransfer | ✅ | ✅ citizen + military projection | ✅ Overview/Realms/Map + Join/Leave + found/locate | ✅ heraldry + citizen skins | ✅ | COMPLETE |
| Settlements/growth | ✅ capital+1 Spec+1 rural (36 surface) + Spec expansion catalog + spacing 2000 | ✅ organic planning + immigration + causal expansion | ✅ schema 20 + ContentRevision 15 | ✅ terrain-aware construction + provenance + Waystone bridge | ✅ management + locate/found + player register building | ✅ hut→mansion + culture geometry families | ✅ density/authored-names/worldgen | COMPLETE |
| Society/needs/unrest | ✅ SocialCitizen roster + SocialMobilityEngine | ✅ needs/memory + roster simulateDay | ✅ schema 20 | ✅ physical jobs as animation of sim authority | ✅ Society tab + tokenized dialogue | ✅ 48 citizen skins | ✅ roster/dialogue/society | COMPLETE |
| Government/succession | ✅ + SovereignDebt + GrandProject | ✅ debt/projects/succession | ✅ | ✅ court projection | ✅ Politics | ✅ heraldry court kits | ✅ | COMPLETE |
| Diplomacy/treaties | ✅ | ✅ typed war goals + peace treaties | ✅ | ✅ strategic | ✅ Politics | n/a | ✅ WarGoal* | COMPLETE |
| War/objectives/sieges | ✅ CampaignPlan + SiegeState | ✅ capital targets + multi-key breach | ✅ | ✅ military + siege equipment | ✅ War tab | ✅ troop/siege art | ✅ SiegeBreach* + FinalProduct | COMPLETE |
| Territory/jurisdiction | ✅ | ✅ | ✅ | ✅ runtime queries | ✅ jurisdiction + claims | n/a | ✅ | COMPLETE |
| Economy/markets | ✅ GRAIN→FLOUR→BREAD + MEAT/ALE/WOOL | ✅ farms/mills/bakeries/breweries/pastures/hinterland truth | ✅ schema 20 | ✅ player market + vanilla item map | ✅ Economy + dialogue OPEN_TRADE | vanilla commodity bridge | ✅ GoodsChain* | COMPLETE |
| Trade/logistics | ✅ TradeShipment | ✅ escort/loss/risk | ✅ | ✅ caravan projection | ✅ Ops | ✅ caravan art | ✅ Trade* | COMPLETE |
| Transport networks | ✅ StreetType + corridors | ✅ same-realm + cross-faction corridors | ✅ | ✅ carriageways/sidewalks | ✅ Ops | ✅ road projection | ✅ | COMPLETE |
| Industry/Create | ✅ IndustrialSite | ✅ production + maintenance + starvation | ✅ | ✅ Create yard projection | ✅ Ops | ✅ Create blocks | ✅ | COMPLETE |
| Crime/notoriety | ✅ | ✅ witness-gated theft | ✅ | ✅ authored storage only | ✅ Law | n/a | ✅ | COMPLETE |
| Bounty/custody | ✅ | ✅ single payout path | ✅ | ✅ arrest/custody/release | ✅ Law | n/a | ✅ | COMPLETE |
| Wildlife ecology | ✅ cohorts + region centers | ✅ food-web + colonization | ✅ | ✅ LOD | ✅ Ecology | ✅ 134 textures | ✅ | COMPLETE |
| Animal behaviour | ✅ utility brain | ✅ hunt/flee/defend | ✅ | ✅ locomotion + attacksHumans | n/a | ✅ morphology | ✅ SpeciesPack* | COMPLETE |
| Biomes/habitats | ✅ 25 archetypes | ✅ discovery | ✅ | ✅ overlay; no biome-source replacement | ✅ Ecology | n/a | ✅ | COMPLETE |
| Aviation | ✅ AirWing | ✅ fuel return | ✅ | ✅ aircraft projection | ✅ Forces | ✅ role textures | ✅ | COMPLETE |
| Naval systems | ✅ ports/fleets | ✅ geography-gated docks | ✅ | ✅ ship projection | ✅ Forces | ✅ class textures | ✅ | COMPLETE |
| Player reputation/membership/influence/careers | ✅ | ✅ join/leave/found/tax/policy | ✅ | ✅ commands + unlocks | ✅ dashboard | n/a | ✅ | COMPLETE |
| Bounty hunters | ✅ | ✅ | ✅ | ✅ hunter runtime | ✅ Law | ✅ | ✅ | COMPLETE |
| Assistance/contracts | ✅ | ✅ verified delivery + shortage contracts | ✅ | ✅ assist runtime | ✅ Ops + dialogue | n/a | ✅ | COMPLETE |
| Singleplayer client sync | ✅ protocol 20 snapshot | ✅ request/response | server save | ✅ NeoForge payloads | ✅ F12/M/K | ✅ | ✅ | COMPLETE |
| Config/data packs | ✅ | ✅ | ✅ | ✅ | ✅ Settings | ✅ | ✅ | COMPLETE |
| Requested modpack compatibility | ✅ Guns++/GamingBarn player-only | n/a | n/a | ✅ Waystones soft + Create hard | n/a | ✅ | ✅ | COMPLETE |
| Performance/LOD | ✅ | ✅ | n/a | ✅ bounded projections | n/a | n/a | ✅ soak/stress | COMPLETE |
| World map/strategic UI | ✅ | n/a | n/a | ✅ terrain base + no vanilla blur | ✅ F12/M/K + a11y | ✅ | ✅ | COMPLETE |

## Non-negotiable release gates

1. `./scripts/test-core.sh` with Java 21 `-Xlint:all -Werror`.
2. Linked NeoForge/Create `clean --no-build-cache build`.
3. `python3 scripts/release-audit.py`.
4. `RELEASE_MANIFEST.json` generated for exact HEAD.
5. Save/load for schema 20 with migrations from schema 1.
6. 30/365/3650-day soak invariants.
7. Projection stress under budgets.
8. No phantom completion keys; no FOOD×mill fountain; capture keeps `citizen.factionId` aligned.

## Geschiedenis
- Pre-release matrices used schema 16–17, ContentRevision 11, 32/realm (~380+), 2000-block spacing, and labels such as COMPLETE (core) / EXTERNAL GATE. Those are historical only.
