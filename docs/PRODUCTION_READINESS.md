# Living Realms production readiness

CURRENT PINS: schema 20 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / surfaceSettlements 36 / perRealm 3 / spacing 2000

## Status
Provenance for this HEAD should use the release-state vocabulary in `docs/RELEASE_GATES.md`
(`core-green` → `linked-build-green` → `gametest-green` → `runtime-smoke-green` → `release-ready`).
Do not claim `release-ready` from headless core alone. `RELEASE_MANIFEST.json` must match HEAD.
`runtimeSmoke` may only be recorded as **pass** after a real client run — never guessed.

Subsystem feature depth uses maturity levels in `COMPLETION_MATRIX.md`
(FOUNDATION / CANONICAL / PLAYABLE / PHYSICALIZED / DEEP / POLISHED). Maturity ≠ release readiness.

## Green automated gates
- Java 21 `-Xlint:all -Werror` core suite including schema **20** goods, densifier Specs, SettlementTransfer, war capital goals, dialogue tokens, roster-without-projection, documentation pins, deterministic refactor proof.
- Save migration schemas **1→20**, integrity recovery, 768 fuzz cases, 3650-day soak.
- Static release-audit for blur suppression, Create hard-dep, Guns++ player-only, version pins.

## Geschiedenis
- Earlier readiness notes claimed schema 17 / protocol 17 / ContentRevision 11 linked builds. Historical only.
