# Living Realms — Release Overhaul Plan (feedback wave)

Expert plan addressing the ten player feedback points without removing systems.
Inspired by Kingdom: Civilization Expansion (live HUD, clear founding, readable map)
and BuildPaste vernacular house categories (medieval / fantasy / mediterranean / desert /
japanese / modern / victorian).

## Goals

1. **Roads feel authored, not stamped.** Habitable cores keep street grids; countryside is a single dirt path; water crossings are bridges; wall crossings are gates.
2. **Live simulation on screen.** The world *is* the sim — continuous physical projection + Kingdom-style live HUD (realm, treasury, people, military, standing).
3. **Smooth ticks.** Time-slice construction/projection; no feature deletion; budgets stay intact.
4. **Readable people & wildlife.** Original citizen skins + overlays; species textures re-authored to morphology.
5. **Culture architecture.** Eight BuildPaste-inspired families drive massing + interiors (full beds).
6. **Usable map & menus.** F12 Map is geographic; Settings fit the panel; founding is guided with 2000-block spacing.

## Architecture decisions

| Area | Decision |
|---|---|
| Settlement spacing | Hard floor **2000 blocks** between any two settlements (player + seeded). |
| Starter density | Sparse network: capital + distant satellites (≥2000). Far-world via frontier seeder at 2000+. |
| Urban roads | Full hierarchy (lanes → boulevards) only TOWN+. |
| Rural roads | FOOTPATH / dirt, width 1–3, **no sidewalk fences**. |
| Intercity CARAVAN | Materialize as narrow dirt lanes (no stone curb). |
| Water | Deck + railings + pillars (settlement streets + intercity). |
| Walls | Curtain skips gate axis; GATE terrain-follows with portal AIR. |
| Culture | `CultureArchitecture` wired into planner sizing + house blueprints. |
| Beds | Two-block HEAD/FOOT placement (Minecraft bed contract). |
| Live HUD | Client overlay from cached dashboard snapshot (Kingdom-style strip). |
| Map | F12 Map draws claims/routes/labels + opens M world map. |
| Perf | Stagger construction/transport/urban/industry across tick phases. |

## Non-goals / safety

- Do not delete kingdoms, species, diplomacy, or construction systems.
- Densifier remains **add-only** for migrated dense saves (new worlds get sparse 2000 layout).
- No third-party BuildPaste schematics copied — vernacular grammar only.

## Verification

- Core suite (`scripts/test-core.sh`) including `LivingWorldDensityTest` updated for 2000 spacing.
- Manual: founding far enough succeeds; countryside shows one dirt path; water shows bridges; gates open; HUD visible; F12 Map usable.
