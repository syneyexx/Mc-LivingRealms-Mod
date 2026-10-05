# Living Realms production readiness

CURRENT PINS: schema 20 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / surfaceSettlements 36 / perRealm 3 / spacing 2000

## Status
Release-ready candidate against the pins above for automated gates. Headless core suite and release-audit must be green; linked NeoForge/Create `clean --no-build-cache build` must be green; `RELEASE_MANIFEST.json` must match HEAD. `runtimeSmoke` may only be recorded as **pass** after a real client run — never guessed.

## Green automated gates
- Java 21 `-Xlint:all -Werror` core suite including schema **20** goods, densifier Specs, SettlementTransfer, war capital goals, dialogue tokens, roster-without-projection.
- Save migration schemas 1→18, integrity, 768 fuzz cases, 3650-day soak.
- Static release-audit for blur suppression, Create hard-dep, Guns++ player-only, version pins.

## Geschiedenis
- Earlier readiness notes claimed schema 17 / protocol 17 / ContentRevision 11 linked builds. Historical only.
