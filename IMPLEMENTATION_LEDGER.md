# LivingRealms Implementation Ledger

CURRENT PINS: schema 21 / minSchema 1 / protocol 20 / network 16 / contentRevision 18 / starterSettlements 204-300 / perRealm 17-25 / capitalSpacing 3000-4500 / roleAwareSpacing

**Authority rule:** source wins over docs. No parallel engines.

## Baseline (source truth)

| Item | Value |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.219 |
| Java | 21 |
| Create | 6.0.10 |
| Save schema | **21** (1–20 readable; settlement roles + hierarchical world-fabric migration) |
| Dashboard protocol | **20** |
| Network | **16** |
| ContentRevision | **15** |
| Surface starter settlements | **204–300** (12 × 17–25: capital + 10 authored satellites + 6–14 rural hamlets) |
| Spacing | **role-aware**; capital↔capital preferred **3000–4500** blocks |
| Dashboard / map / catalog | F12 / M / K |

## Release authorities
- SimulationState, FactionEngine, DiplomacyEngine, SettlementEconomyEngine, CivilizationEngine, SettlementConstructionMaterializer, SimulationStateCodec.
- SettlementTransfer for capture/rebellion; ContentMigrationPolicy for density/morphology one-shot gates.
- Named roster seeding via SocialPopulationEngine; aggregate population for scale.

## Status
Subsystem maturity in `COMPLETION_MATRIX.md` uses FOUNDATION / CANONICAL / PLAYABLE / PHYSICALIZED / DEEP / POLISHED (not blanket done labels). Overall release claim must follow `docs/RELEASE_GATES.md` vocabulary (`core-green` … `release-ready`) with matching evidence — not core suite alone.

## Geschiedenis
- Earlier ledger rows used schema 16–17, ContentRevision 10–11, 32/realm density, 2000-block spacing, and COMPLETE (core) / EXTERNAL GATE labels. Obsolete for current pins.
