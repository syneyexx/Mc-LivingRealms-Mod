# Living Realms architecture

## Authority boundaries (non-negotiable)

| Concern | Authority | Projection / presentation |
|---|---|---|
| Population, households, dynasties | `SimulationState` / social engines | Bounded citizen entities |
| Economy, stockpiles, markets, industry | Faction/settlement stock + engines | Create yards, market stalls (visual) |
| Construction intents / completion | Settlement completion keys + receipts | `SettlementConstructionMaterializer` |
| Block ownership | Typed `AuthoredBlockLedger` via `WorldMutationGuard` | Minecraft blocks |
| Trade shipments / routes | Logistics + transport engines | Caravan entities / road blocks |
| Crime / custody / justice | Law engines | Guard/prisoner projections |
| Ecology | Ecosystem regions / groups | Wildlife entities |
| Geography | `SettlementGeographyProfile` (discovery runtime) | Biome/terrain samples (loaded chunks only) |
| Client UI (F12 / M / K) | Server snapshots + actions | Screens |

Physical workers, Create networks, and loaded-chunk side effects must never become a second simulation authority. Chunk unload ≠ canonical death or economic shutdown.

## Non-negotiable rule: simulation != rendering

A living world at this scale cannot keep every citizen, wolf, fish and army unit as a loaded Minecraft Entity. Living Realms therefore uses two representations:

- **Canonical simulation state**: cheap Java objects representing populations, armies, settlements, stockpiles and relationships.
- **Physical projection**: Minecraft entities/blocks/contraptions instantiated around players.

The canonical state remains authoritative. Materialized entities are a projection and feed changes back into the canonical state when despawned/unloaded.

## LOD

- PHYSICAL: <= 320 blocks from an interested player. Individual entity AI, combat, animation, collisions.
- REGIONAL: 320-2048 blocks. Groups, routes, tactical outcomes, construction intents.
- ABSTRACT: > 2048 blocks. Population/economy/ecology equations and strategic movement.

## Tick budgets

Do not run civilization AI every game tick. Target schedules:

- physical entity steering: vanilla tick cadence, only loaded entities
- materialization bookkeeping: 1-5 Hz
- regional route/tactical updates: every 1-5 seconds
- strategic military planning: every 10-30 seconds
- economic simulation: every Minecraft day or substeps when necessary
- diplomacy: every several simulated days plus event triggers
- ecology: daily aggregate step; local physical predation remains real-time

## Create integration

Create is used for visible industry near players. A faction's canonical industry model owns production capacity. When its factory is physical, throughput is reconciled with actual Create inventory/kinetic state. When unloaded, the aggregate model continues at a conservative efficiency based on the last validated factory configuration.

## Species scale

Species definitions belong in data files and are validated at reload. Java implements reusable behaviours (herbivore grazer, ambush predator, pack hunter, scavenger, filter feeder, etc.). A species composes parameters + behaviour tags. This lets the catalog grow to thousands without thousands of Java classes.

## Animal decision layer (v0.2)

`AnimalBrain` is deliberately independent from Minecraft pathfinding. It receives normalized sensory inputs and emits a high-level intent. The Minecraft entity layer will translate an intent into navigation, animation, attack or feeding goals. This keeps species behavior reproducible and testable and avoids one Java goal class per species.

## Materialization budget (v0.2)

`MaterializationPlanner` decides which aggregate cohorts deserve physical entities around players. It applies both per-cohort and global player budgets, prioritizing the closest populations. It does not mutate canonical population counts. `MaterializationReconciler` now owns stable slot-level projection transitions so animals cannot duplicate or be double-materialized.

## Species data files (v0.2)

Species now also ship as strict JSON records under `data/livingrealms/species`. The dependency-free `SpeciesJsonCodec` is tested against every starter species, so the format is not merely documentation. A future NeoForge datapack reload adapter can feed these exact records into the canonical catalog, including third-party species packs, without changing ecological code.


## Projection reconciliation (v0.3)

Each aggregate population gets zero or more deterministic physical slots (`0..N-1`) while inside PHYSICAL LOD. A Minecraft entity will persist its cohort id, species id and slot. Reconciliation keeps at most one valid occupant per desired slot, creates missing slots and removes duplicates, species mismatches, orphaned cohorts and excess slots. Removal by reconciliation is **dematerialization**, not death. Only a real physical death is permitted to call `SimulationState.recordPhysicalAnimalDeath`, preventing chunk unloads and LOD changes from shrinking wildlife populations.
