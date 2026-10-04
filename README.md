# Living Realms

**Living Realms is a simulation-first civilization, society and ecosystem overhaul for Minecraft 1.21.1 on NeoForge.**

The goal is not to make Minecraft feel like a map with a few extra NPCs. The goal is to make the world feel as if an actual civilization exists inside it: people are born, work, trade, remember, migrate, fight, form families, build settlements, spread knowledge, create cultures, suffer shortages and disease, start wars, become rulers, leave ruins behind and create history — whether the player intervenes or not.

Living Realms is designed primarily for **offline singleplayer**. The integrated Minecraft server remains authoritative for simulation state, while the client renders and interacts with bounded physical projections of that world.

Current development checkpoint: **v3.0.0-rc4** (A–Z production completion pass; save schema **16**). See `IMPLEMENTATION_LEDGER.md` and `docs/DETAIL_MATRIX.md` for consolidated A–Z / correction / Claude-masterplan status.

> **End-product vision:** a persistent living world where kingdoms, settlements, people, wildlife, economy, politics, law, culture and history continue to evolve as one connected system instead of as isolated features.

---

## What the finished Living Realms world should feel like

You should be able to leave a town, travel for a while and encounter signs of an inhabited world:

- capitals, cities, towns, villages, hamlets and colonies;
- roads that actually connect population centers;
- farms, mines, fisheries, lumber camps, markets and industry;
- walls, gates, keeps, castles, barracks, temples, clinics, schools and prisons;
- civilians walking between homes, workplaces and civic buildings;
- guards patrolling streets and protecting settlements;
- caravans moving real goods between markets;
- armies traveling toward real objectives;
- wildlife occupying believable habitats;
- rumors and information spreading from place to place;
- refugees moving away from war, hunger or disease;
- monuments and ruins that refer to events that genuinely happened.

A settlement shown on the world map should correspond to a real settlement in the world once that area is physically materialized. A kingdom should not just be a colored circle: it should have a capital, settlements, roads, resources, population, guards, trade and history.

---

## Living civilizations

Living Realms maintains a canonical strategic world made of factions, kingdoms, settlements, infrastructure, resources and people.

The finished system is intended to support:

- multiple surface kingdoms with their own territory and politics;
- a large spawn-region capital with an actual castle/keep and urban core;
- cities, towns, villages, hamlets and specialist settlements;
- player-founded realms using the same systems as AI realms;
- the hidden underground **Wizard Trees** theocracy and its colonies;
- organic settlement growth based on population, wealth, safety, resources and geography;
- urban districts such as residential, market, craft, military, religious, harbor and government areas;
- terrain-aware streets, sidewalks, bridges, plazas and intercity roads;
- dense housing in larger settlements, including multi-storey townhouses/apartment-style buildings;
- settlement specialization such as trade cities, mining towns, agricultural centers, ports, fortresses and religious centers;
- adoption of suitable vanilla and mod-generated villages/structures instead of bulldozing them.

Urban growth is simulation-driven. Population and prosperity create a physical development target, and loaded settlements gradually reconcile toward that state with bounded construction work.

---

## People, not disposable NPCs

Important civilians are backed by persistent canonical identities. Physical Minecraft entities are projections of those people, not the source of truth.

Citizens can carry state such as:

- name and stable identity;
- age and life stage;
- settlement and faction;
- profession and skill;
- household and family relationships;
- health;
- wealth;
- needs;
- personality;
- memories;
- relationships;
- knowledge;
- rumors;
- loyalty and reputation;
- current work/activity.

Living Realms is designed around the idea that civilians have understandable motivations rather than scripted quest markers.

### Needs

Citizens can react to pressures such as:

- hunger;
- safety;
- social contact;
- status;
- comfort.

Those needs feed into real behavior and larger systems. Food shortages can raise prices, damage health, reduce morale, increase migration and make crime or raiding more likely. Unsafe settlements can demand more guards, walls or defenses.

### Personality and memory

Personality traits influence risk, trade, aggression, loyalty, greed, caution, betrayal and social behavior.

Citizens can remember meaningful events such as:

- being helped;
- being attacked or robbed;
- losing relatives;
- surviving a raid;
- seeing a crime;
- hearing a rumor;
- having their settlement conquered;
- receiving a gift;
- being rescued by the player.

Those memories can affect relationships, dialogue, trust and future decisions.

---

## Natural-language NPC conversations without an LLM

Living Realms deliberately does **not** require OpenAI, Ollama, an external AI service or a local language model.

The dialogue system is a deterministic local Natural Language Dialogue Engine.

Players can type free-form questions such as:

- “Have you seen bandits?”
- “Where did they go?”
- “Who told you that?”
- “Why is food so expensive?”
- “Who rules this kingdom?”
- “Are people sick here?”
- “Where can I buy iron?”
- “What happened yesterday?”
- “What do you think of me?”
- “Can I help?”

The system performs intent recognition, entity/topic recognition, synonyms, time/location parsing, conversation context and follow-up resolution.

NPC knowledge is intentionally limited. A farmer should not know secret military plans simply because those plans exist somewhere in the simulation. Guards, traders, rulers, healers, priests and scholars know different things.

Vanilla villagers and compatible civilian NPCs adopted into a Living Realms settlement are intended to enter the same persistent citizen/dialogue layer rather than remaining separate “silent” villagers.

---

## Families, generations and society

The long-term civilization model is designed to support:

- births and deaths;
- aging;
- households;
- parents and children;
- partnership and marriage;
- adoption and orphans;
- dynasties and succession;
- regencies and succession crises;
- political marriages;
- migration and refugees;
- education and apprenticeships;
- social mobility;
- long-term generational history.

Aggregate population remains authoritative for scale, while important named citizens provide the persistent human layer the player can actually know.

---

## Work and production

When a settlement is physically loaded, representative civilians can visibly perform work tied to the strategic economy.

Examples include:

- farmers harvesting and replanting crops;
- lumberjacks harvesting natural trees;
- miners working natural ore/resource sites;
- fishers working near water;
- hunters interacting with the ecology system;
- builders supporting settlement construction;
- traders moving between markets;
- guards patrolling settlements;
- healers, priests, teachers and scholars visiting relevant civic buildings.

Physical work feeds the same canonical resource/economy state used by the strategic simulation.

---

## Economy, markets and trade

Goods do not simply teleport between kingdoms.

Living Realms models:

- settlement stockpiles;
- scarcity-driven prices;
- player buy/sell transactions;
- taxation;
- resource production;
- trade agreements;
- persistent trade shipments;
- physical caravan projections;
- route security;
- industry;
- warehouses;
- resource claims;
- shortages and surpluses;
- economic specialization.

A mine discovery can therefore influence trade, migration, prices, diplomacy and eventually war.

Create integration is used for bounded physical industrial projection while the canonical economy remains authoritative even when chunks are unloaded.

---

## Government, diplomacy and politics

Kingdoms are intended to have governments rather than just faction names.

The political layer includes or targets:

- rulers and governments;
- legitimacy;
- stability;
- corruption;
- taxation;
- succession;
- dynasties;
- diplomacy;
- alliances;
- trade agreements;
- non-aggression arrangements;
- tributary relationships;
- political marriage;
- intelligence networks;
- propaganda;
- rebellions.

Political changes are supposed to arise from world conditions, not arbitrary random events.

---

## War, raids and conquest

Wars can grow out of border disputes, resource pressure, raids, broken relations, succession problems or larger diplomatic escalation.

Military systems include:

- armies;
- ranks;
- morale;
- supply;
- objectives;
- sieges;
- conquest;
- occupation;
- raids;
- desertion;
- banditry;
- naval forces;
- aviation;
- piracy;
- pirate hideouts.

Sieges are designed around persistent material and progress rather than a simple timer. Defenses, fortifications, supply and siege equipment matter.

Conquered settlements can affect population, culture, migration, economy, government and future unrest.

---

## Law, crime and custody

Living Realms contains an authoritative law/crime layer.

Systems include:

- jurisdiction;
- theft;
- murder/assault consequences;
- property crime;
- wanted state;
- notoriety;
- bounties;
- bounty hunters;
- custody;
- prisons;
- fines and punishment;
- exile;
- faction reputation.

Player-facing law actions are server-authoritative.

---

## Culture, religion, knowledge and information

Civilizations should become recognizable over time.

Living Realms models or is structured to model:

- culture;
- faith;
- dialect/identity;
- festivals and civic events;
- monuments;
- education;
- technology/knowledge;
- propaganda;
- intelligence;
- rumors;
- cartographic knowledge;
- historical memory.

Rumors can travel through people and trade/transport connections with source tracking and reliability loss instead of instantly becoming global knowledge.

---

## History that leaves physical traces

The world history system records important events such as:

- settlement foundations;
- rulers;
- wars;
- conquests;
- rebellions;
- disasters;
- migrations;
- discoveries;
- major player actions;
- legendary individuals.

History is meant to remain visible through:

- monuments;
- memorials;
- graves;
- ruins;
- hidden caches;
- abandoned infrastructure;
- local stories and rumors.

The objective is for a player to be able to discover something old and learn *why it exists*.

---

## Wildlife and ecology

Living Realms also simulates a persistent ecosystem rather than treating animals as unrelated random spawns.

The current bundled ecology catalog contains **134 species** across terrestrial, aerial, freshwater, coastal and open-ocean environments.

The ecology model supports:

- habitat suitability;
- food webs;
- predators and prey;
- births and mortality;
- hunger/thirst;
- group behavior;
- hunting;
- fleeing;
- defending;
- migration;
- biodiversity pressure;
- physical LOD projection near the player.

The simulation uses 25 ecological biome archetypes and can normalize vanilla and compatible modded biomes into that ecology without replacing the active biome source.

Bespoke art for every species is separate presentation work; the ecology simulation itself is data-driven.

---

## World integration

Living Realms is designed to coexist with the world instead of replacing it.

Key rules:

- do not replace the active biome source;
- do not blindly overwrite foreign block entities or machines;
- do not treat player builds as generic natural resources;
- adopt compatible existing villages/structures instead of erasing them;
- build only in loaded/approved areas;
- keep strategic simulation independent from chunk loading;
- use terrain-aware construction;
- keep physical projection bounded.

This allows Living Realms to coexist with biome/worldgen stacks such as **Regions Unexplored, Biomes O' Plenty, Terralith, TerraBlender and Lithostitched**.

---

## Strategic world map

Press **M** to open the Living Realms strategic world map.

The end-state map is intended to show a sharp, readable terrain/biome base by default, with simulation layers rendered above it.

Layers include or are designed to include:

- kingdoms and territory;
- cities, towns, villages, hamlets and colonies;
- roads and transport routes;
- mines/resource sites;
- markets and ports;
- armies;
- raids;
- sieges/fronts;
- caravans;
- fleets/air units;
- ecology/biomes;
- ruins and historic sites;
- known pirate hideouts;
- discovered hidden information.

The terrain base itself should not require discovery. Intelligence/discovery is reserved for information that is actually supposed to be secret.

---

## Dashboard and controls

Default controls in the current RC4 direction:

| Key | Function |
| --- | --- |
| **M** | Strategic world map |
| **F12** | Living Realms dashboard |
| **K** | Creative-only registered mod-item catalog |

The dashboard exposes bounded, server-authoritative views of realms, settlements, society, economy, politics, war, law, forces, ecology, operations, history and settings.

---

## Useful commands

Current player/admin tooling includes:

```text
/livingrealms status
/livingrealms locate settlement
/livingrealms locate city
/livingrealms locate town
/livingrealms locate village
/livingrealms locate hamlet
/livingrealms locate mine
/livingrealms found <name>
/livingrealms wanted
/livingrealms setday <day>
/livingrealms advance <days>
```

The locate system is intended to resolve real canonical/physical sites rather than arbitrary labels.

---

## Spawn kingdom

The real Overworld spawn is required to fall inside Living Realms civilization coverage.

The target experience is a recognizable capital region with:

- city-scale population;
- a real castle/keep;
- walls and gates;
- roads and sidewalks;
- housing;
- markets;
- barracks;
- guards;
- civic buildings;
- farms/resource infrastructure;
- a single settlement Waystone;
- surrounding connected settlements.

The spawn kingdom is meant to immediately demonstrate what Living Realms is, not appear as a tiny village with a “kingdom” label.

---

## Wizard Trees

**Wizard Trees** is a special hidden underground theocracy.

It is designed as a real civilization, not a dungeon:

- underground colonies;
- excavated halls and tunnels;
- homes;
- redstone-lit growing chambers;
- magic-oriented roles;
- its own culture and faith;
- population;
- economy;
- diplomacy;
- history.

It participates in the same canonical simulation architecture as surface realms while using dedicated underground physical construction.

---

## Modpack integration philosophy

The requested singleplayer modpack is treated as a fixed compatibility target.

Important integrations include:

- **Create** — industrial physical projection;
- **Waystones** — one generated settlement Waystone, deduplicated by canonical settlement;
- **Better Villages / vanilla villages** — adoption into Living Realms;
- **Regions Unexplored / Biomes O' Plenty / Terralith** — biome/ecology/settlement coexistence;
- **Macaw's building mods** — safe non-load-bearing visual variety;
- **SecurityCraft** — suitable secure civic/military contexts where safe;
- **Iron's Spells / Spell Engine / Elemental Wizards** — eligible magic-oriented equipment/content paths;
- compatible RPG/ranged/armor mods — visible faction equipment where allowed.

**NPC equipment rule:** Guns++ and GamingBarn's Guns remain player-only and are hard-denied for civilian, guard and military loadouts.

See [docs/MODPACK_COMPATIBILITY.md](docs/MODPACK_COMPATIBILITY.md) for the detailed compatibility contract.

---

## Simulation architecture

Living Realms uses one canonical world state and three simulation scales:

### ABSTRACT
Far away from the player.

Cheap strategic simulation for population, economy, diplomacy, ecology, war, trade and society.

### REGIONAL
Relevant nearby regions.

More detailed movement and regional activity.

### PHYSICAL
Near loaded players.

Actual Minecraft entities, buildings, visible work, combat, conversations and physical projection.

This separation is critical.

A chunk unloading must **not** kill citizens, destroy shipments, delete wildlife populations or erase relationships. Physical despawn is not canonical death.

The client also never becomes authoritative simply because it is rendering the world.

---

## Performance principles

Living Realms is deliberately not “one entity per simulated person”.

The project uses:

- aggregate populations;
- bounded representative citizens;
- bounded wildlife projection;
- projection identity/reconciliation;
- construction budgets;
- loaded-chunk-only physical work;
- deterministic strategic simulation;
- bounded memories/rumors/history;
- no forced global chunk loading for simulation.

The ambition is a world with a very large simulated population without requiring Minecraft to physically tick every individual at all times.

Singleplayer immersion profiles (F12 → Settings): **Performance**, **Balanced**, **Immersive**, **Cinematic**, and **Showcase**. Showcase is the high-end target profile for strong single-GPU machines (RTX 16GB-class + 32GB RAM): denser citizens, wildlife, caravans and construction near the player while canonical LOD stays bounded. See `docs/HIGH_END_SYSTEM_CONCEPT.md`.

---

## Save safety and determinism

World persistence is treated as a core feature.

The current RC4 line includes:

- versioned save schemas;
- backward migrations;
- semantic validation;
- bounded payload/string sizes;
- strict UTF-8 handling;
- corruption/truncation/trailing-data rejection;
- checksum/schema consistency checks;
- deterministic mutation fuzzing;
- canonical ID high-watermark repair;
- long deterministic soak testing.

The attached/current development line uses **save schema 16** (schemas 1–15 remain readable; settlement barn/granary/local stockpiles) with older schemas retained through migration support.

---

## Current development status

The repository is currently on the **v3.0.0-RC4** development line.

The latest local checkpoint represented by this README direction is based on the worldgen/city/map/NPC correction pass and includes work such as:

- dense starter civilization;
- 12 surface kingdoms plus Wizard Trees;
- 380+ starter settlements/colonies in the current density gate (32/realm + rural hamlets);
- settlement streets, sidewalks and denser housing;
- capital/spawn city and castle planning;
- terrain-aware route correction;
- terrain-backed M-map without background blur;
- F12 dashboard binding;
- villager/civilian dialogue adoption;
- Waystone deduplication;
- dedicated city/mine locate logic;
- 134-species ecology;
- society/civilization state;
- no-LLM dialogue;
- persistent migration/history/law/economy/war systems;
- long deterministic simulation and migration gates.

### Important release status

**RC4 is still a release candidate, not a final production release.**

Before declaring the mod production-complete, the project still requires the real linked/runtime verification matrix to be clean, including:

- full NeoForge/Create Gradle compilation;
- integrated singleplayer-server boot;
- client boot;
- real Minecraft save/close/reopen/reload;
- real dashboard packet/action round-trip;
- Create kinetic-network verification;
- full requested modpack smoke testing;
- chunk load/unload and teleport projection stress in-game;
- long-world performance and playtesting;
- final visual/content polish where generic assets remain.

A green core test is not enough by itself.

---

## Definition of Done

Living Realms considers a gameplay subsystem complete only when the relevant pieces are complete:

1. authoritative model;
2. simulation behavior;
3. persistence/migration;
4. Minecraft runtime projection;
5. player interaction and feedback;
6. cross-system integration;
7. required content/presentation;
8. bounded performance behavior;
9. regression/runtime verification.

That rule is intentionally strict.

The target is not a collection of impressive internal systems that the player cannot see. The target is a world where all those systems visibly combine into one coherent civilization.

---

## Development documents

For deeper technical and design detail:

- [Architecture](docs/ARCHITECTURE.md)
- [Living Society](docs/LIVING_SOCIETY.md)
- [World Integration](docs/WORLD_INTEGRATION_EXPANSION.md)
- [Modpack Compatibility](docs/MODPACK_COMPATIBILITY.md)
- [Production Readiness](docs/PRODUCTION_READINESS.md)
- [Release Gates](docs/RELEASE_GATES.md)
- [Roadmap](docs/ROADMAP.md)
- [Definition of Done Matrix](COMPLETION_MATRIX.md)
- [Detail Matrix](docs/DETAIL_MATRIX.md)
- [Implementation Ledger](IMPLEMENTATION_LEDGER.md)
- [Project State](PROJECT_STATE.md)

---

## Production stack

- **Minecraft:** 1.21.1
- **Mod loader:** NeoForge 21.1.219
- **Java:** 21
- **Create:** 6.0.10
- **Primary target:** offline singleplayer / integrated server

---

## The core idea

If the player does nothing, the world should still have a story.

A farmer should harvest because food is needed.  
A caravan should travel because another settlement is short on goods.  
Bandits should appear because people became desperate or deserted.  
A road should matter because trade actually uses it.  
A war should have a reason.  
A city should grow because people moved there.  
A refugee should have somewhere they came from.  
A ruin should exist because something once stood there.  
An NPC should remember what the player did.  
A kingdom should be able to rise, change and fall.

And if the player intervenes, history should genuinely take a different path.

**That is Living Realms.**
