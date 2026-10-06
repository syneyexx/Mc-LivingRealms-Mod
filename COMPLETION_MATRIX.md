# Living Realms — Definition of Done matrix

CURRENT PINS: schema 21 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / starterSettlements 204-300 / perRealm 17-25 / capitalSpacing 3000-4500 / roleAwareSpacing

## Maturity levels (documentation truth)

Blanket “done” labels are retired. Each subsystem is graded on the deepest level it honestly reaches for the pins above:

| Level | Meaning |
|---|---|
| **FOUNDATION** | Types / data model exist; not yet a living authority path |
| **CANONICAL** | Simulation mutates authoritative state deterministically |
| **PLAYABLE** | Player (or grounded UI/commands) can trigger the verb |
| **PHYSICALIZED** | Minecraft projection / materialization reflects canonical truth |
| **DEEP** | Cross-domain feedback (economy↔war↔society↔ecology) is wired |
| **POLISHED** | UX clarity + focused regression tests cover the loop |

A subsystem may be **CANONICAL** without being **PHYSICALIZED**, or **PLAYABLE** without being **POLISHED**. Do not treat any single column as release-ready; release claims follow `docs/RELEASE_GATES.md`.

| Subsystem | Model | Simulation | Save/migrate | MC runtime | UI/feedback | Content/assets | Tests | Maturity |
|---|---|---|---|---|---|---|---|---|
| World clock/history | ✅ | ✅ | ✅ | ✅ | ✅ History tab + commands | n/a | ✅ | POLISHED |
| Factions/kingdoms | ✅ 12 + Wizard Trees + player realms | ✅ diplomacy/war/trade/growth + SettlementTransfer | ✅ | ✅ citizen + military projection | ✅ Overview/Realms/Map + Join/Leave + found/locate | ✅ heraldry + citizen skins | ✅ | DEEP |
| Settlements/growth | ✅ capital + 10 authored satellites + 6–14 rural hamlets (204–300 surface) + role-aware spacing | ✅ organic planning + immigration + causal expansion | ✅ schema 21 + ContentRevision 15 | ✅ terrain-aware construction + provenance + Waystone bridge | ✅ management + locate/found + player register building | ✅ hut→mansion + culture geometry families | ✅ density/authored-names/worldgen | DEEP |
| Society/needs/unrest | ✅ SocialCitizen roster + SocialMobilityEngine | ✅ needs/memory + roster simulateDay | ✅ schema 21 | ✅ physical jobs as animation of sim authority | ✅ Society tab + tokenized dialogue | ✅ 48 citizen skins | ✅ roster/dialogue/society | DEEP |
| Government/succession | ✅ + SovereignDebt + GrandProject | ✅ debt/projects/succession | ✅ | ✅ court projection | ✅ Politics | ✅ heraldry court kits | ✅ | DEEP |
| Diplomacy/treaties | ✅ | ✅ typed war goals + peace treaties | ✅ | ✅ strategic | ✅ Politics | n/a | ✅ WarGoal* | DEEP |
| War/objectives/sieges | ✅ CampaignPlan + SiegeState | ✅ capital targets + multi-key breach | ✅ | ✅ military + siege equipment | ✅ War tab + War Room | ✅ troop/siege art | ✅ SiegeBreach* + FinalProduct | PLAYABLE |
| Territory/jurisdiction | ✅ | ✅ | ✅ | ✅ runtime queries | ✅ jurisdiction + claims | n/a | ✅ | CANONICAL |
| Economy/markets | ✅ GRAIN→FLOUR→BREAD + MEAT/ALE/WOOL | ✅ farms/mills/bakeries/breweries/pastures/hinterland truth | ✅ schema 21 | ✅ player market + vanilla item map | ✅ Economy + dialogue OPEN_TRADE | vanilla commodity bridge | ✅ GoodsChain* | DEEP |
| Trade/logistics | ✅ TradeShipment | ✅ escort/loss/risk | ✅ | ✅ caravan projection | ✅ Ops | ✅ caravan art | ✅ Trade* | PHYSICALIZED |
| Transport networks | ✅ StreetType + corridors | ✅ same-realm + cross-faction corridors | ✅ | ✅ carriageways/sidewalks | ✅ Ops | ✅ road projection | ✅ | PHYSICALIZED |
| Industry/Create | ✅ IndustrialSite | ✅ production + maintenance + starvation | ✅ | ✅ Create yard projection | ✅ Ops | ✅ Create blocks | ✅ | PHYSICALIZED |
| Crime/notoriety | ✅ | ✅ witness-gated theft | ✅ | ✅ authored storage only | ✅ Law | n/a | ✅ | DEEP |
| Bounty/custody | ✅ | ✅ single payout path | ✅ | ✅ arrest/custody/release | ✅ Law | n/a | ✅ | PLAYABLE |
| Wildlife ecology | ✅ cohorts + region centers | ✅ food-web + colonization | ✅ | ✅ LOD | ✅ Ecology | ✅ 134 textures | ✅ | DEEP |
| Animal behaviour | ✅ utility brain | ✅ hunt/flee/defend | ✅ | ✅ locomotion + attacksHumans | n/a | ✅ morphology | ✅ SpeciesPack* | PHYSICALIZED |
| Biomes/habitats | ✅ 25 archetypes | ✅ discovery | ✅ | ✅ overlay; no biome-source replacement | ✅ Ecology | n/a | ✅ | CANONICAL |
| Aviation | ✅ AirWing | ✅ fuel return | ✅ | ✅ aircraft projection | ✅ Forces | ✅ role textures | ✅ | PHYSICALIZED |
| Naval systems | ✅ ports/fleets | ✅ geography-gated docks | ✅ | ✅ ship projection | ✅ Forces | ✅ class textures | ✅ | PHYSICALIZED |
| Player reputation/membership/influence/careers | ✅ | ✅ join/leave/found/tax/policy | ✅ | ✅ commands + unlocks | ✅ dashboard | n/a | ✅ | PLAYABLE |
| Bounty hunters | ✅ | ✅ | ✅ | ✅ hunter runtime | ✅ Law | ✅ | ✅ | PLAYABLE |
| Assistance/contracts | ✅ | ✅ verified delivery + shortage contracts | ✅ | ✅ assist runtime | ✅ Ops + dialogue | n/a | ✅ | DEEP |
| Underworld board | ✅ contracts + stolen-goods ledger | ✅ matcher never fabricates | ✅ schema 21 | ✅ | ✅ Underworld Accept/Bribe/Fence | n/a | ✅ Underworld* | PLAYABLE |
| Singleplayer client sync | ✅ protocol 20 snapshot | ✅ request/response | server save | ✅ NeoForge payloads | ✅ F12/M/K | ✅ | ✅ | POLISHED |
| Config/data packs | ✅ | ✅ | ✅ | ✅ | ✅ Settings | ✅ | ✅ | POLISHED |
| Requested modpack compatibility | ✅ Guns++/GamingBarn player-only | n/a | n/a | ✅ Waystones soft + Create hard | n/a | ✅ | ✅ | POLISHED |
| Performance/LOD | ✅ | ✅ | n/a | ✅ bounded projections + RuntimeScheduler | n/a | n/a | ✅ soak/stress/scheduler | DEEP |
| World map/strategic UI | ✅ | n/a | n/a | ✅ terrain base + no vanilla blur | ✅ F12/M/K + a11y | ✅ | ✅ | POLISHED |
| Dialogue (no-LLM) | ✅ interpreter/knowledge/planner/style/realizer | ✅ epistemic gating | ✅ via citizens | ✅ conversation screen | ✅ | phrase tables | ✅ Dialogue* | DEEP |
| Demography / migration / health | ✅ civ state + cohorts | ✅ MigrationEngine + EpidemicEngine; demography still in CivilizationEngine | ✅ | bounded projections | Society / history | n/a | ✅ Migration*/Epidemic* | CANONICAL |

## Non-negotiable release gates

1. `./scripts/test-core.sh` with Java 21 `-Xlint:all -Werror`.
2. Linked NeoForge/Create `clean --no-build-cache build`.
3. `python3 scripts/release-audit.py`.
4. `RELEASE_MANIFEST.json` generated for exact HEAD.
5. Save/load for schema 21 with migrations from schema 1.
6. 30/365/3650-day soak invariants.
7. Projection stress under budgets.
8. No phantom completion keys; no FOOD×mill fountain; capture keeps `citizen.factionId` aligned.

## Geschiedenis
- Pre-release matrices used schema 16–17, ContentRevision 11, 32/realm (~380+), 2000-block spacing, and labels such as COMPLETE (core) / EXTERNAL GATE. Those are historical only.
- Wave 23/40 replaced blanket COMPLETE status cells with FOUNDATION→POLISHED maturity levels.
