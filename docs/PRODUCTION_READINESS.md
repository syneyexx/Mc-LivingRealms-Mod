# Living Realms production readiness

CURRENT PINS: schema 18 / minSchema 1 / protocol 18 / network 14 / contentRevision 14 / surfaceSettlements 156 / perRealm 13 / spacing 800

## Status
Release-ready candidate against the pins above. Headless core suite and release-audit must be green; linked NeoForge/Create `clean --no-build-cache build` must be green; `RELEASE_MANIFEST.json` must match HEAD. `runtimeSmoke` is `pass` only after a real client run.

## Green automated gates
- Java 21 `-Xlint:all -Werror` core suite including schema **18** goods, densifier Specs, SettlementTransfer, war capital goals, dialogue tokens, roster-without-projection.
- Save migration schemas 1→18, integrity, 768 fuzz cases, 3650-day soak.
- Static release-audit for blur suppression, Create hard-dep, Guns++ player-only, version pins.

## Geschiedenis
- Earlier readiness notes claimed schema 17 / protocol 17 / ContentRevision 11 linked builds. Historical only.
