# Player Experience Matrix (Waves 12–14)

Shared product maturity (subsystem depth) uses FOUNDATION / CANONICAL / PLAYABLE / PHYSICALIZED / DEEP / POLISHED in `COMPLETION_MATRIX.md`.

This matrix grades **player-facing agency loops** with experience-specific labels that map onto that scale:

| Experience label | Maps to | Meaning |
|-------|---------|---------|
| **CANONICAL** | CANONICAL | Simulation state mutates on the authoritative path |
| **VISIBLE** | PLAYABLE (observe) | Dashboard / dialogue / history surfaces the change |
| **INTERACTIVE** | PLAYABLE | Player can trigger the verb from UI or grounded chat |
| **CONSEQUENCE WIRED** | DEEP | Stock, mood, memory, rumor, reputation, pressure hooks fire |
| **POLISHED** | POLISHED | UX clarity + regression tests cover the loop |

CURRENT PINS: schema 20 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / surfaceSettlements 36 / perRealm 3 / spacing 2000

## Agency audit (deepened, not invented)

| Action | Canonical engines | Observable consequences | Maturity |
|--------|-------------------|-------------------------|----------|
| **Found settlement** | `PlayerSettlementFounder` | Realm appears on map/dashboard; rank RULER | CONSEQUENCE WIRED |
| **Aid / famine help** | `AssistanceContributionEngine` → stock, food security, task progress, reputation | Mood / HELPED_BY memory / rumor subject `aid` / refugee pressure softener / Ops tab progress | CONSEQUENCE WIRED |
| **Trading (market)** | `MarketTransactionEngine` | Prices / stock on Economy tab | INTERACTIVE + VISIBLE |
| **Theft / crime** | `CrimeEngine` + `PropertyCrimeEngine` + `UnderworldActions.observeCrime` | Property loss from settlement stock, public order, heat/bounty, victim STOLEN_FROM, witness CRIME_WITNESS, stolen-goods ledger provenance | CONSEQUENCE WIRED |
| **Murder** | `CrimeType.MURDER` via `reportCrime` | Wanted escalation, reputation, underworld assassination contract match | CANONICAL + VISIBLE |
| **War declare / orders** | `PlayerAgencyActions` + `WarRoomOptionsBuilder` | Wars tab goal select, army identity, escort gated | CONSEQUENCE WIRED |
| **Tax / policy** | Dashboard tax & settlement priority | RealmView + Cities tab | INTERACTIVE + VISIBLE |
| **Diplomacy** | Peace / trade pact agency | Politics tab buttons | INTERACTIVE + VISIBLE |
| **Building register** | `PlayerStructureRegistration` + `HousingCapacity` | Capacity, migration pressure, NPC LOCAL_EVENT, Cities UI registered count | CONSEQUENCE WIRED |
| **Building demolish / invalidate** | `PlayerStructureRegistration.invalidate` | Housing reconcile, unrest, NPC grumble, refugee pressure | CONSEQUENCE WIRED |
| **Housing / expansion** | HousingCapacity + SettlementExpansionEngine | Cities deficit / verified housing | VISIBLE + CANONICAL |
| **Underworld** | `UnderworldActions` + board engine + dashboard | Accept / bribe / fence; crime matcher never fabricates | CONSEQUENCE WIRED |

## Aid / famine chain

```
Player delivers verified goods (assist runtime / command)
  → AssistanceContributionEngine.contributeVerified
      → settlement + faction stockpile
      → foodSecurity / order / infrastructure relief
      → assistance task pressure + progress (Ops tab)
      → reputation / service
      → PlayerAgencyConsequences.onAssistance
          → citizen needs (mood)
          → HELPED_BY memories
          → refugee/bandit pressure softener
      → history `assistance_contribution` (settlement=…)
          → RumorEngine diffusion
```

## Crime chain

```
Real CrimeIncident (witnessed or not)
  → CrimeEngine.report
      → (witnessed) bounty / heat / notoriety
      → PlayerAgencyConsequences.onCrime
          → nearest settlement stockpile loss
          → public order / unrest
          → STOLEN_FROM victim memory + CRIME_WITNESS
  → (if registered) UnderworldActions.observeCrime
      → stolen-goods ledger deposit
      → ACCEPTED contract match → COMPLETED + street cred
  → ReputationEngine.onCrime
  → LawEnforcementEngine / bounty board (Law tab)
```

## Building register / demolish chain

```
REGISTER_BUILDING (survey runtime) / invalidate on revalidation fail
  → HousingCapacity.reconcileCanonical (Cities housing fields)
  → PlayerAgencyConsequences.onBuildingRegistered|Invalidated
      → NPC LOCAL_EVENT mentions
      → mood + migration pressure
  → history → RumorEngine
```

## Underworld player surface (Wave 13)

- Dashboard **Underworld** tab: contract board (type, jurisdiction, non-secret target, reward, days, status)
- Interactive widgets: **Accept** (AVAILABLE), **Bribe officials** (local jurisdiction), **Fence** (stolen lots when eligible)
- Accepted contracts show an inspectable objective line; completion stays **CrimeIncident → matcher only**
- Tavern / trader NPC tip when corrupt jurisdiction has AVAILABLE contracts (points at same board)

## War Room player surface (Wave 14)

- Wars tab: contextual goal cycle (CONQUEST / LIBERATION / REPARATIONS / HUMILIATION …), enemy declare/petition with suggested settlement
- Army cards: identity, strength, morale, supply, home, objective
- **Escort target cycle** over `WarRoomOptionsBuilder` escort list only (friendly settlements / owned shipments — never enemy war targets)
