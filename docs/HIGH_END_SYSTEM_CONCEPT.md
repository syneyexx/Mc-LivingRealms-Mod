# Living Realms — High-End System Concept (Showcase)

Target machine for this profile:

| Resource | Spec | Role |
|---|---|---|
| Primary GPU | RTX-class, **16 GB** VRAM | Minecraft render + Embeddium + NeoCulus/shaders + dense entity meshes |
| Secondary GPU | ASUS card, **6 GB** VRAM | Not used by Minecraft rendering |
| System RAM | **32 GB** | JVM + OS + texture/disk cache + Discord/browser |

This document is the product contract for the **Showcase** experience: the densest Living Realms singleplayer feel that still respects canonical LOD, projection budgets and save determinism.

---

## 1. What “top experience” means here

Living Realms is not supposed to feel like a quiet Minecraft world with a few extras. On this hardware the player should feel an inhabited civilization the moment they enter a loaded area.

### First 10 minutes
1. Spawn near or inside a real capital/city core — streets, sidewalks, keep, market, civilians.
2. Hear/see life immediately: commuting citizens, guards, traders, wildlife at the edge.
3. Open **M** and understand that the map is a living strategic world, not a decoration.
4. Talk to one NPC in free text (“Why is food expensive?”, “Have you seen bandits?”) and get local, profession-limited knowledge.
5. Leave town on a real road and encounter a caravan, refugees, or wildlife that belongs to the ecology — not random filler.

### Ongoing session feel
- Capitals feel crowded; hamlets feel sparse.
- Wars, prices, rumors and migration change towns you revisit.
- Construction is visible near the player without freezing the game.
- Far-away kingdoms keep evolving without being physically loaded.
- Shaders/high render distance stay smooth because entity/projection budgets remain hard-capped.

---

## 2. Hardware truth (important)

### Dual GPU
Minecraft / NeoForge / Java rendering uses **one** GPU. There is no useful “split the living world across two cards” path.

Correct allocation:

- **RTX 16 GB = primary display adapter** for the Minecraft window. All shaders, chunk meshing, entity meshes and UI go here. 16 GB is comfortable for a dense modpack + NeoCulus.
- **ASUS 6 GB = secondary / idle** for this product. Optional non-Minecraft uses only: OBS encode on the weak card, a second-monitor browser/Discord, or leave it unused. Do **not** expect GPUBooster/GPUTape or Embeddium to magically harness both cards for Living Realms.

If Windows has the game on the wrong GPU, force the RTX via Windows Graphics Settings / NVIDIA Control Panel. Running Living Realms on the 6 GB card while the RTX idles is the fastest way to ruin the Showcase feel.

### 32 GB RAM split
Recommended singleplayer launch envelope:

| Bucket | Allocation | Why |
|---|---|---|
| JVM (`-Xms12G -Xmx14G`) | 12–14 GB | Heavy modpack + Create + Living Realms canonical state + chunk working set |
| OS + drivers + Discord/browser | ~10–12 GB | Avoids Windows reclaim stutter |
| Disk / texture / shader cache headroom | remainder | Prevents hard paging during exploration |

Do **not** set `-Xmx24G` or higher on a 32 GB machine. Minecraft does not benefit from starving the OS; hitching gets worse.

Suggested JVM extras (G1, Java 21):

```text
-Xms12G -Xmx14G
-XX:+UseG1GC
-XX:+ParallelRefProcEnabled
-XX:MaxGCPauseMillis=200
-XX:+UnlockExperimentalVMOptions
-XX:+DisableExplicitGC
-XX:G1NewSizePercent=30
-XX:G1MaxNewSizePercent=40
-XX:G1HeapRegionSize=8M
-XX:G1ReservePercent=20
-XX:InitiatingHeapOccupancyPercent=15
```

---

## 3. Experience stack (layers)

Showcase is a coordinated stack, not one slider.

```text
┌─────────────────────────────────────────────────────────┐
│ Client spectacle                                        │
│ Embeddium + NeoCulus/shaders + high render distance     │
│ RTX 16 GB VRAM                                          │
├─────────────────────────────────────────────────────────┤
│ Physical projection (near player)                       │
│ citizens / guards / wildlife / caravans / armies / ships│
│ SHOWCASE budgets                                        │
├─────────────────────────────────────────────────────────┤
│ Regional detail                                         │
│ routes, construction intents, nearby movement           │
├─────────────────────────────────────────────────────────┤
│ Canonical civilization (always on, cheap)               │
│ kingdoms, economy, war, ecology, society, history       │
└─────────────────────────────────────────────────────────┘
```

Non-negotiable: canonical simulation stays authoritative. Showcase only raises **how much** of that world is physically projected near the player. Unloading a chunk still never kills a citizen or deletes a caravan.

---

## 4. Living Realms preset ladder

| Preset | Intent | When to use |
|---|---|---|
| PERFORMANCE | Lowest physical density | Weak CPU / troubleshooting |
| BALANCED | Default / safe | First boot, unknown hardware |
| IMMERSIVE | Rich towns | Mid-high PCs |
| CINEMATIC | Dense cinematic sessions | Strong single GPU, cautious RAM |
| **SHOWCASE** | Maximum intended product feel | **This target machine** |

### SHOWCASE numeric contract

| Knob | Value | Player-facing effect |
|---|---|---|
| Physical radius | **640** blocks | Towns stay “alive” farther as you approach/leave |
| Regional radius | **4096** blocks | Broader regional activity around the player |
| Wildlife budget | **480** | Ecology is visible, not a rumor |
| Caravan budget | **72** | Trade routes feel real on the road |
| Military budget | **256** | Armies/guards read as forces, not tokens |
| Naval budget | **96** | Ports/coasts show ships without unbounded fleets |
| Construction ops/tick | **420** | Cities grow visibly while you watch |
| Citizen ceiling | **320** (policy) | Capitals feel populated |
| Migration ceiling | **48** (policy) | Refugees/settlers appear as sparse travelers |

Strategic day step stays **1**. Showcase densifies space around the player; it does not fast-forward history.

Apply in-game via **F12 → Settings → Showcase**.

---

## 5. Client / modpack spectacle settings

These are recommendations for the fixed target pack (Embeddium, NeoCulus, GPU helpers, Create, biomes, structures, combat content). They are outside canonical save state.

### Embeddium / render
- Render distance: **16–20** chunks for daily play; **24** only if FPS stays ≥60 outside capitals.
- Simulation distance: keep **≤ render distance**; prefer **12–16** so the integrated server is not overloaded.
- Entity distance: high, but trust Living Realms budgets — do not also install unbounded mob-cap multipliers.
- Smooth lighting / fancy leaves / particles: Fancy is fine on the RTX 16 GB.

### NeoCulus / shaders
- Prefer a shader that stays stable with Create contraptions and dense entity counts.
- Shadows: medium/high, not pathological contact-hardening cascades.
- If a capital dips hard: lower shadow resolution / volumetric quality before touching Living Realms SHOWCASE budgets. Spectacle is GPU; living density is mostly CPU + entity tick.

### Create / industry
- Enjoy kinetic factories in loaded towns; canonical industry continues when unloaded.
- Avoid building enormous always-loaded contraption megabases next to a capital if you want both Showcase density and 60 FPS.

### Audio / UI
- Keep dashboard (**F12**) and map (**M**) as first-class: no world blur, readable contrast.
- Dialogue remains no-LLM and offline — Showcase never depends on an external AI service.

---

## 6. Session modes (same machine, different jobs)

| Mode | Profile | Render distance | Goal |
|---|---|---|---|
| **Daily living** | SHOWCASE | 16–18 | Best default for this PC |
| **Cinema walk** | SHOWCASE | 20–24 + nicer shader | Recording / sightseeing a capital |
| **War watch** | SHOWCASE or CINEMATIC | 16 | Follow armies without starving CPU |
| **Troubleshoot** | PERFORMANCE | 12 | Isolate stutter / isolate a bad mod |

If FPS collapses only inside capitals: lower shader shadows first, then drop render distance, then fall back CINEMATIC. Do not jump straight to PERFORMANCE unless diagnosing.

---

## 7. What Showcase deliberately does *not* do

- No dual-GPU rendering path.
- No “one Minecraft entity per simulated person”.
- No forced global chunk loading to keep kingdoms alive.
- No LLM cloud dialogue.
- No unbounded citizen/wildlife spawn that can melt the integrated server.
- No save-format change for the preset (same schema; config already persisted).

---

## 8. Verification checklist for this hardware

1. Fresh singleplayer world, set **Showcase** in F12 Settings.
2. Confirm Windows/NVIDIA assigned the **RTX 16 GB** to Minecraft.
3. Walk a capital street: citizens + guards + market activity visible; no multi-second tick freezes.
4. Leave on a road: caravan or travelers within a few minutes of travel.
5. Open **M**: kingdoms, settlements, routes readable.
6. Talk to a farmer/guard: answers stay local and role-limited.
7. `/setday` catch-up then return: town still coherent, no duplicated projection citizens.
8. Long session (~1h): RAM steady under ~14 GB JVM, no OS paging thrash.

Pass criteria for “top experience”: dense, readable, stable — not maximum slider chaos.

---

## 9. Implementation map

| Piece | Status |
|---|---|
| `SimulationPreset.SHOWCASE` | shipped with this concept |
| Dashboard action `CONFIG_SHOWCASE` | shipped |
| Raised citizen/migration ceilings for Showcase radii | shipped |
| This hardware/playbook document | shipped |
| External full-modpack smoke on the user’s Windows box | still an external gate |

Canonical simulation density (kingdoms/settlements/ecology) is already large. Showcase is the player-facing projection dial that finally matches this machine.
