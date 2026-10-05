# Architecture depth pass — baseline report

Branch: `cursor/architecture-depth-pass-f4a7`  
Purpose: frozen reference for modular-monolith inventory work (see `docs/ARCHITECTURE_INVENTORY.md`).

---

## Git baseline

| Item | Value |
|------|--------|
| **HEAD at baseline start** | `2180af61be07146ba2fca1894d8489eaabe03e92` |
| **Parent production tip** | `643c2f9ebfc547e091b9909ee01417f6318d5025` (Living Realms production completion 10×, #17) |

### Commits after `643c2f9` (on baseline line)

Only one commit before inventory-only doc work on this branch:

| Commit | Summary |
|--------|---------|
| `2180af61be07146ba2fca1894d8489eaabe03e92` | Fix door-portal survey enclosure for GameTest houses (#18) |

Doc-only inventory commits (`ca7908c`, `333da75`, merges) are out of scope for **simulation/code baseline**; inventory HEAD for class analysis remains `2180af6` as cited in `ARCHITECTURE_INVENTORY.md`.

---

## Verified pins (from source)

Values match `docs/ARCHITECTURE.md` CURRENT PINS and `DocumentationPinTest` constants.

| Pin | Value | Source |
|-----|-------|--------|
| Minecraft | **1.21.1** | `gradle.properties` (`minecraft_version`) |
| NeoForge | **21.1.219** | `gradle.properties` (`neo_version`) |
| Java | **21** | Gradle toolchain / `--release 21` gates |
| Create | **6.0.10-280** | `gradle.properties` (`create_version`) |
| Schema | **20** | `SimulationStateCodec.SCHEMA_VERSION` |
| minSchema | **1** | `SimulationStateCodec.MIN_SUPPORTED_SCHEMA` |
| Protocol | **20** | `RealmDashboardSnapshot.PROTOCOL_VERSION` |
| Network | **16** | `LivingRealmsNetwork` / pin tests |
| contentRevision | **15** | `LivingRealmsSavedData.CONTENT_REVISION` |
| surfaceSettlements | **36** | `SimulationConfig` / documentation pins |
| perRealm | **3** | sparse world policy pins |
| spacing | **2000** | settlement spacing policy pins |

**CURRENT PINS line:** schema 20 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / surfaceSettlements 36 / perRealm 3 / spacing 2000.

---

## Baseline core tests (at `2180af6`)

Recorded gate from release documentation and branch verification before architecture doc consolidation:

| Gate | Result |
|------|--------|
| Core test harness exit code | **EXIT=0** |
| Overall | **All PASS** |
| Long deterministic soak | **3650-day soak PASS** |
| Documentation pin sync | **`DocumentationPinTest` PASS** |

Additional release gates documented in `docs/RELEASE_GATES.md` (migration 1→20, fuzz, compile `-Werror`, etc.) were green on the production tip; baseline delta after `643c2f9` is the door-portal GameTest fix only.

---

## Inventory scope at baseline

Ten high-mass classes documented in `docs/ARCHITECTURE_INVENTORY.md` (merged from inventory commits `ca7908c` and `333da75`), with dependency map, circular-responsibility matrix, and Waves **2–7** extraction order aligned to the architecture depth directive.
