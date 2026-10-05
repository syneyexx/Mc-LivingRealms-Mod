# Living Society / Civilization Layer

CURRENT PINS: schema 20 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / surfaceSettlements 36 / perRealm 3 / spacing 2000

## Product goal

Living Realms must simulate a society, not a collection of scripted NPCs. A visible citizen is a persistent person with needs, personality, memory, relationships, knowledge and a place in an existing canonical faction/settlement/economy/law/war system. The physical Minecraft entity is only a projection of that person. Off-screen simulation remains aggregate and bounded so the feature does not require an LLM, GPU VRAM or thousands of always-loaded entities.

This layer is additive. It reuses the existing canonical systems for settlements, factions, government, diplomacy, economy, markets, routes, trade shipments, crime, wanted state, bounties, war, rebellion, ecology, construction, industry, player standing and world history. It must not create a second competing economy, diplomacy model or population count.

## Core invariants

1. Canonical state lives on the integrated server; the client only receives bounded snapshots/dialogue responses.
2. NPC knowledge is epistemic: an NPC may only say what its memory, profession, faction role, local observations or received rumors plausibly contain.
3. Free text dialogue is deterministic and local. No LLM, remote service or model runtime is required.
4. Important people survive physical despawn/reprojection through persistent `SocialCitizen` identity.
5. Memories, relationships, conversation context and rumor spread are bounded.
6. Aggregate population remains authoritative; named/physical citizens are a bounded representative layer.
7. Loaded-chunk materialization never determines off-screen economic or political truth.
8. Foreign vanilla/modded settlements are adopted and extended; Living Realms does not bulldoze their existing infrastructure.
9. Guns++ (`mr_guns`) and GamingBarn's Guns are permanently excluded from NPC/guard/military loadouts. They remain player content/creative-catalog content.

## Human layer

Every persistent social citizen has five needs: hunger, safety, social contact, status and comfort. Needs influence health, routine pressure and dialogue. Personality uses aggression, trade affinity, caution, greed, loyalty and treachery. A bounded memory store records important personal experiences and received information. A bounded relationship graph tracks friendship, hostility, romance, rivalry, trust and family bonds.

Stable identity includes name, age/birth day, role, settlement, faction and skin variant. Physical citizen entities bind to the same social-citizen id and restore that identity after LOD despawn/reprojection.

## No-LLM Natural Language Dialogue Engine

Players type normal text. The dialogue pipeline performs normalization, intent recognition, topic/entity recognition, synonym matching, short conversation context and pronoun/follow-up resolution. Responses are assembled dynamically from small composable clauses and real facts rather than selecting one giant hard-coded response.

Supported/foundation intents include greetings, danger/bandits, direction follow-ups, source attribution, ruler, food, recent events, opinion of player, help, trade, gifts, threats, insults and goodbye. The system is designed to add law, family, politics, religion, work, war, quests, locations, history and rumor intents without replacing the parser.

Dialogue actions are server-authoritative. A statement cannot fabricate an item transfer or quest completion. Actions can open trade, mark known locations, alert guards, offer tasks, change reputation and apply a real gift only after the runtime verifies the held item and consumes it.

Profession constrains knowledge. Farmers are local; guards know more about crime/security; traders receive route/market rumors; officials know institutional politics; future healers, priests and scholars receive medical, religious and knowledge-domain facts respectively. Source follow-ups retain provenance when a rumor has one.

## Information and rumors

World events do not instantly become global omniscience. Institutional events can reach guards/officials; trade information reaches traders; local events reach residents. Citizens share memories locally with confidence decay. Rumors retain source, confidence, day and optional location. Later phases add distortion, deliberate propaganda, spies and regional dialect rendering.

## Visible settlements and society buildings

The normal settlement planner remains the only general civic planner. Growth appends structures; it does not replace the settlement with a second city system. Current civic vocabulary includes houses, farms, streets, markets, warehouses, workshops/smithies, barracks, walls, factories, airfields, docks, mines, lumber camps, fisheries, wells, taverns, temples, clinics, schools, courthouses, prisons, orphanages, city gates, monuments and observatories.

Civic infrastructure affects people: wells/clinics improve comfort/health pressure; taverns/temples improve social satisfaction; specialist buildings create meaningful destinations for healers, priests and scholars. Existing roads/sidewalks remain commuting targets.

## Wizard Trees

`Wizard Trees` is a distinct hidden theocratic faction, not an ordinary surface kingdom. It owns persistent underground colonies. Its physical construction is routed through a dedicated underground planner instead of the ordinary settlement materializer. Complexes consist of excavated halls, homes, tunnels and redstone-lit grow chambers. Priests/scholars may visibly carry eligible magic/staff content from the target modpack, while actual spell casting must use a verified isolated runtime adapter rather than fake generic attacks.

The underground materializer uses the normal bounded construction queue and only excavates safe natural terrain; it does not overwrite block entities or arbitrary player/mod machinery.

## Economy, work and survival

Existing stockpiles, markets, scarcity pricing, trade shipments and routes remain canonical. Physical workers bridge loaded-world actions into that state: lumberjacks harvest natural trees, farmers mature crops/replant, miners take natural ores, fishers produce bounded catches near water and hunters target wildlife. Further work layers should include crafted goods, storage logistics, butcheries, seasonal agriculture, soil/water pressure and profession learning.

Famine is produced from food pressure, not scripted quests. The next demographic layer connects prolonged hunger to fertility, migration, disease susceptibility and mortality.

## Politics, diplomacy and war

Existing governments, taxes, bilateral relations, treaties, war state, armies, sieges/objectives, conquest and rebellion remain authoritative. Society extensions attach named leaders, soldiers and families to those systems instead of duplicating them.

Planned extensions include rank/morale/desertion, raiding parties, bandit camps, tributary states, political marriages, spies, propaganda, courts/sentences, prisons/exile, dynasties and succession crises. These must write through existing law/government/war records.

## Culture and long-term history

Culture is a regional/faction layer that will drive architecture palettes, clothing, religion, festivals, taboos, naming/dialect, law preferences and assimilation. It should evolve from repeated events and player influence rather than be a cosmetic random label.

World history already records canonical events. The civilization layer will promote important events/people into dynasties, heroes, legends, monuments, graves/ruins, hidden caches and historical map knowledge. Physical monuments must refer back to a real event id/person/faction.

## Demography, family and health roadmap

The social model is intended to grow into real generations: aging, death, birth, partnership, marriage, parent/child/sibling bonds, adoption and orphans. Education transfers profession/technology knowledge. Health adds disease outbreaks, sanitation, healers and epidemics. Migration creates refugees/refugee camps and later permanent settlements where conditions support them.

These features require bounded cohorts/off-screen aggregation; they must not materialize every canonical inhabitant as a Minecraft entity.

## Infrastructure, geography and resources roadmap

Settlements progressively upgrade footpaths into roads, bridges, signs, gates and regional connections. Water becomes claimable infrastructure through wells, irrigation and later aqueducts. Mines/resources become territorial interests and conflict causes. Coastal/river settlements extend trade into shipping and piracy. Cartography records roads, mines, threats and hostile territory; astronomy/calendars affect seasons/festivals/agriculture once the calendar layer exists.

## Day/night society

Routines are time-aware: workers commute/work by day, guards patrol, and later night schedules enable taverns, thieves, secret meetings, watch rotations and curfews. Night behavior must remain derived from role, law, needs and events rather than spawn arbitrary scripted encounters.

## Player agency

The player may found and rule a real settlement/faction, trade, help, threaten, steal, join wars, mediate, become wanted, support rebellions or influence culture/technology. Consequences flow through the same canonical systems. The player is powerful but not omniscient: maps, rumors, dialogue and espionage determine what information is available.

## Delivery phases

Maturity for society systems today: named citizens / dialogue / migration / epidemics sit at **CANONICAL→DEEP**; full named-person inheritance and physical refugee camps remain next-depth work (see `COMPLETION_MATRIX.md`).

### Implemented through build fix12 development
- persistent named social citizens;
- needs/personality/memory/relationships;
- schema-11 persistent social citizens plus schema-12 civilization-state persistence and migration compatibility;
- bounded rumor diffusion;
- no-LLM free-text dialogue core + client/server conversation screen;
- physical entity ↔ persistent person binding;
- social civic buildings and healer/priest/scholar routines;
- Wizard Trees canonical faction + dedicated underground construction path;
- explicit dual gun-mod NPC deny-list.

### Implemented strategic civilization layer in build fix12
- aggregate births/deaths, settlement attraction and push-pull migration/refugees (`MigrationEngine`);
- sanitation, water security, disease pressure and epidemic consequences (`EpidemicEngine`);
- education and culture/faith/dialect identity;
- cultural cohesion and conquest assimilation;
- resource claims and contest pressure;
- raids/bandit pressure and army desertion pressure;
- espionage networks, propaganda and tribute;
- festivals and emergent legend/monument promotion;
- route-aware rumor propagation;
- broad no-LLM dialogue coverage for these systems (interpreter / knowledge / planner / style / realizer).

### Next depth layers
- named-person inheritance/adoption and full household economics;
- named-person profession learning, inventory and wealth transfer;
- deeper courts/prison/exile and taxation at named-person level;
- political marriage/dynasties and succession consequences;
- physical refugee camps, irrigation/aqueduct construction and piracy;
- hidden caches/treasure creation tied to threats;
- state-backed generated task records rather than scripted quests;
- extract remaining demography helpers from `CivilizationEngine` into a dedicated engine when the day-loop split is proven.

### Release evidence (not feature maturity)
Linked NeoForge 1.21.1 / full-target-pack compile and in-game smoke on a real client remain required before any `release-ready` claim (`docs/RELEASE_GATES.md`). Headless core maturity alone is insufficient.
