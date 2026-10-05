# LivingRealms Implementation Ledger

CURRENT PINS: schema 20 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / surfaceSettlements 36 / perRealm 3 / spacing 2000

**Authority rule:** source wins over docs. No parallel engines.

## Baseline (source truth)

| Item | Value |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.219 |
| Java | 21 |
| Create | 6.0.10 |
| Save schema | **20** (1–19 readable; underworld contracts + stolen-goods ledger) |
| Dashboard protocol | **20** |
| Network | **16** |
| ContentRevision | **15** |
| Surface settlements | **36** (12 × 3: capital + 1 Spec + 1 rural) |
| Spacing | **2000** blocks |
| Dashboard / map / catalog | F12 / M / K |

## Release authorities
- SimulationState, FactionEngine, DiplomacyEngine, SettlementEconomyEngine, CivilizationEngine, SettlementConstructionMaterializer, SimulationStateCodec.
- SettlementTransfer for capture/rebellion; ContentMigrationPolicy for density/morphology one-shot gates.
- Named roster seeding via SocialPopulationEngine; aggregate population for scale.

## Status
Subsystem completeness in `COMPLETION_MATRIX.md` tracks feature wiring. Overall release claim must follow `docs/RELEASE_GATES.md` vocabulary (`core-green` … `release-ready`) with matching evidence — not core suite alone.

## Geschiedenis
- Earlier ledger rows used schema 16–17, ContentRevision 10–11, 32/realm density, 2000-block spacing, and COMPLETE (core) / EXTERNAL GATE labels. Obsolete for current pins.
