# LivingRealms Implementation Ledger

CURRENT PINS: schema 19 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / surfaceSettlements 36 / perRealm 3 / spacing 2000

**Authority rule:** source wins over docs. No parallel engines.

## Baseline (source truth)

| Item | Value |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.219 |
| Java | 21 |
| Create | 6.0.10 |
| Save schema | **18** (1–17 readable; goods chain GRAIN/FLOUR/BREAD/MEAT/ALE/WOOL) |
| Dashboard protocol | **18** |
| Network | **14** |
| ContentRevision | **14** |
| Surface settlements | **36** (12 × 3: capital + 1 Spec + 1 rural) |
| Spacing | **800** blocks |
| Dashboard / map / catalog | F12 / M / K |

## Release authorities
- SimulationState, FactionEngine, DiplomacyEngine, SettlementEconomyEngine, CivilizationEngine, SettlementConstructionMaterializer, SimulationStateCodec.
- SettlementTransfer for capture/rebellion; ContentMigrationPolicy for density/morphology one-shot gates.
- Named roster seeding via SocialPopulationEngine; aggregate population for scale.

## Status
All A–R release-ready subsystems tracked in `COMPLETION_MATRIX.md` are COMPLETE against the pins above when core suite, release-audit, and linked build are green.

## Geschiedenis
- Earlier ledger rows used schema 16–17, ContentRevision 10–11, 32/realm density, 2000-block spacing, and COMPLETE (core) / EXTERNAL GATE labels. Obsolete for current pins.
