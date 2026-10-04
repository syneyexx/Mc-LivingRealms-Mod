#!/usr/bin/env python3
from pathlib import Path
import json,re,sys
root=Path(__file__).resolve().parents[1]
errors=[]; notes=[]

def fail(msg): errors.append(msg)
def require(cond,msg):
    if not cond: fail(msg)

props={}
for line in (root/'gradle.properties').read_text().splitlines():
    if '=' in line and not line.lstrip().startswith('#'):
        k,v=line.split('=',1);props[k.strip()]=v.strip()
require(props.get('minecraft_version')=='1.21.1','minecraft_version must be 1.21.1')
require(props.get('neo_version')=='21.1.219','NeoForge pin must be 21.1.219')
require(props.get('create_version')=='6.0.10-280','Create pin must be 6.0.10-280')
require(props.get('mod_version')=='3.0.0-rc4','mod_version must match v3.0.0-rc4')

readiness=(root/'docs/PRODUCTION_READINESS.md')
require(readiness.exists(),'production readiness report must exist')
world_contract=root/'docs/WORLD_INTEGRATION_EXPANSION.md'
require(world_contract.exists(),'buildfix10 world-integration acceptance contract must exist')
if readiness.exists():
    readiness_text=readiness.read_text()
    require('v3.0.0-rc4' in readiness_text,'production readiness report must match RC4')
    require('Production candidate, not yet release-complete.' in readiness_text,'readiness report must not overclaim unverified linked/runtime gates')


build_gradle=(root/'build.gradle').read_text()
for repo in ['https://maven.createmod.net','https://mvn.devos.one/snapshots','https://maven.blamejared.com/']:
    require(repo in build_gradle,f'missing Create 1.21.1 build repository: {repo}')
require('maven.ithundxr.dev' not in build_gradle,'obsolete ithundxr snapshots repository must stay removed')

mods=(root/'src/main/templates/META-INF/neoforge.mods.toml').read_text()
require('modId="create"' in mods and 'type="required"' in mods,'Create must be required in mods metadata')
for modid in ['geckolib','curios','ferritecore','skinlayers3d','jei','clumps','shulkerboxtooltip','biomesoplenty','glitchcore','terrablender','waystones','balm','terralith','lithostitched','travelersbackpack']:
    require(f'modId="{modid}"' not in mods,f'{modid} must not become a hard metadata dependency')

require('import net.neoforged.neoforge.event.tick.ClientTickEvent;' not in '\n'.join(p.read_text(errors='replace') for p in (root/'src/main/java').rglob('*.java')),'NeoForge 1.21.1 ClientTickEvent must come from neoforge.client.event')

# Common-side client classloading guard.
for p in (root/'src/main/java/dev/livingrealms').rglob('*.java'):
    rel=p.relative_to(root/'src/main/java/dev/livingrealms').as_posix()
    text=p.read_text(errors='replace')
    if re.search(r'^\s*import\s+net\.minecraft\.client\.',text,re.M) and not rel.startswith('minecraft/client/'):
        fail(f'client import outside client package: {rel}')

# No foreign API compile-time references except Create itself.
foreign_tokens=['software.bernie','top.theillusivec4.curios','mezz.jei','blay09.mods','terrablender','biomesoplenty','lithostitched','travelersbackpack']
for p in (root/'src/main/java').rglob('*.java'):
    text=p.read_text(errors='replace')
    imports='\n'.join(line.strip() for line in text.splitlines() if line.lstrip().startswith('import '))
    for token in foreign_tokens:
        if token in imports: fail(f'unexpected optional-mod hard reference {token}: {p.relative_to(root)}')

# Source tree cleanliness.
for p in (root/'src').rglob('*.class'): fail(f'compiled class inside src: {p.relative_to(root)}')

# Key mappings follow the NeoForge 1.21.1 lazy-registration pattern.
keymap=(root/'src/main/java/dev/livingrealms/minecraft/client/ui/LivingRealmsKeyMappings.java').read_text()
client_events=(root/'src/main/java/dev/livingrealms/minecraft/client/LivingRealmsClientEvents.java').read_text()
client_game=(root/'src/main/java/dev/livingrealms/minecraft/client/ui/LivingRealmsClientGameEvents.java').read_text()
require('Lazy<KeyMapping>' in keymap and 'Lazy.of' in keymap,'client key mapping must be lazily initialized on NeoForge 1.21.1')
require('OPEN_DASHBOARD.get()' in client_events and 'OPEN_DASHBOARD.get().consumeClick()' in client_game,'key mapping users must dereference the lazy mapping')
require('GLFW.GLFW_KEY_M' in keymap and 'OPEN_WORLD_MAP' in keymap and 'requestOpenMap()' in client_game,'M must open the Living Realms world map')
require('GLFW.GLFW_KEY_K' in keymap and 'OPEN_CREATIVE_CATALOG' in keymap and 'CreativeItemCatalogScreen' in client_game,'K must open the creative target-mod catalog')

# Required entity textures exist and are nonempty PNGs.
textures=['wildlife','trade_caravan','faction_citizen','military_unit','aircraft','ship','bounty_hunter']
for name in textures:
    p=root/f'src/main/resources/assets/livingrealms/textures/entity/{name}.png'
    require(p.exists() and p.stat().st_size>32,f'missing/empty texture: {name}.png')
    if p.exists(): require(p.read_bytes()[:8]==b'\x89PNG\r\n\x1a\n',f'invalid PNG header: {name}.png')

# Species pack strict basic validity and count.
species_dir=root/'src/main/resources/data/livingrealms/livingrealms/species'
files=sorted(species_dir.glob('*.json'))
require(len(files)>=134,f'expected >=134 bundled species, found {len(files)}')
ids=set()
for p in files:
    try: d=json.loads(p.read_text())
    except Exception as e: fail(f'invalid species JSON {p.name}: {e}'); continue
    sid=d.get('id'); require(isinstance(sid,str) and sid, f'{p.name}: missing id')
    if isinstance(sid,str):
        require(sid not in ids,f'duplicate species id: {sid}');ids.add(sid)
        require(p.stem==sid,f'species filename/id mismatch: {p.name} vs {sid}')
    for key in ['locomotion','morphology']:
        require(isinstance(d.get(key),str) and d.get(key),f'{p.name}: missing explicit {key}')

# Versioned state/dashboard contracts — parse from source so audit cannot drift from constants.
codec=(root/'src/main/java/dev/livingrealms/sim/persistence/SimulationStateCodec.java').read_text()
snap=(root/'src/main/java/dev/livingrealms/sim/ui/RealmDashboardSnapshot.java').read_text()
net=(root/'src/main/java/dev/livingrealms/minecraft/network/LivingRealmsNetwork.java').read_text()
saved_data_for_rev=(root/'src/main/java/dev/livingrealms/minecraft/LivingRealmsSavedData.java').read_text()
def int_const(text,name):
    m=re.search(rf'\b{re.escape(name)}\s*=\s*(\d+)\b',text)
    require(m is not None,f'missing constant {name}')
    return int(m.group(1)) if m else -1
def str_const(text,name):
    m=re.search(rf'\b{re.escape(name)}\s*=\s*"([^"]+)"',text)
    require(m is not None,f'missing string constant {name}')
    return m.group(1) if m else ''
schema_version=int_const(codec,'SCHEMA_VERSION')
min_schema=int_const(codec,'MIN_SUPPORTED_SCHEMA')
dashboard_protocol=int_const(snap,'PROTOCOL_VERSION')
network_version=str_const(net,'NETWORK_VERSION')
content_revision=int_const(saved_data_for_rev,'CONTENT_REVISION')
require(schema_version==17,f'save schema must be 17 (source currently {schema_version})')
require(min_schema==1,f'min supported schema must remain 1 (source {min_schema})')
require(dashboard_protocol==17,f'dashboard protocol must be 17 (source {dashboard_protocol})')
require(network_version=='14',f'network registration version must be 14 (source {network_version!r})')
require(content_revision==12,f'content revision must be 12 (source {content_revision})')



# NeoForge 1.21.1 EntityType.Builder uses build(String); ResourceKey overload is not available.
entities=(root/'src/main/java/dev/livingrealms/minecraft/entity/ModEntities.java').read_text()
require('DeferredRegister.Entities' not in entities and 'createEntities(' not in entities,'NeoForge 1.21.1 must use generic DeferredRegister<EntityType<?>>; specialized Entities helper is not part of the 1.21.1 API')
require('DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE' in entities,'entity register must target Registries.ENTITY_TYPE through generic DeferredRegister')
require('.build(ResourceKey.create' not in entities,'NeoForge 1.21.1 entity builders must not use ResourceKey build overload')
for entity_id in ['wildlife','trade_caravan','faction_citizen','military_unit','aircraft','ship','bounty_hunter']:
    require(f'.build(LivingRealms.MOD_ID + ":{entity_id}")' in entities,f'entity {entity_id} must use the 1.21.1 build(String) signature')

# Production runners share one canonical core-suite list; never maintain duplicated test arrays.
core_list_path=root/'scripts/core-tests.list'
require(core_list_path.exists(),'scripts/core-tests.list must be the single source of truth for the core suite')
def parse_core_list(text):
    out=[]
    for raw in text.splitlines():
        line=raw.strip()
        if not line or line.startswith('#'): continue
        out.append(line)
    return out
canonical_tests=parse_core_list(core_list_path.read_text())
require(len(canonical_tests)>=20,f'core-tests.list too small ({len(canonical_tests)})')
for main in canonical_tests:
    simple=main.rsplit('.',1)[-1]
    path=root/f'src/testCore/java/dev/livingrealms/{simple}.java'
    require(path.exists(),f'core suite entry missing source: {main}')
required_suite={
    'dev.livingrealms.CoreSimulationTest',
    'dev.livingrealms.ConstructionIntegrityTest',
    'dev.livingrealms.SpeciesPackAuditTest',
    'dev.livingrealms.SystemCompletenessTest',
    'dev.livingrealms.SettlementEconomyTest',
    'dev.livingrealms.ProjectionStressTest',
    'dev.livingrealms.SaveMigrationMatrixTest',
    'dev.livingrealms.SaveIntegrityTest',
    'dev.livingrealms.SaveMutationFuzzTest',
    'dev.livingrealms.ProductionHardeningTest',
    'dev.livingrealms.LivingWorldDensityTest',
    'dev.livingrealms.WorldgenQualityTest',
    'dev.livingrealms.ProductionQualityTest',
    'dev.livingrealms.SocietyDialogueTest',
    'dev.livingrealms.RumorNetworkTest',
    'dev.livingrealms.SocietyInfrastructureTest',
    'dev.livingrealms.CivilizationLayerTest',
    'dev.livingrealms.FaithAndInfrastructureTest',
    'dev.livingrealms.BanditAndPriceRumorTest',
    'dev.livingrealms.TradeHouseholdEcologyTest',
    'dev.livingrealms.TradeLivenessTest',
    'dev.livingrealms.HumanityLifecycleTest',
    'dev.livingrealms.CitizenConversationTest',
    'dev.livingrealms.ResourceDominanceTest',
    'dev.livingrealms.CartographicKnowledgeTest',
    'dev.livingrealms.PlayerRulershipTest',
    'dev.livingrealms.MobileCivilizationProjectionTest',
    'dev.livingrealms.WizardTreesTest',
    'dev.livingrealms.LongRunSoakTest',
}
missing_required=sorted(required_suite-set(canonical_tests))
require(not missing_required,f'core-tests.list missing required gates: {missing_required}')

production_sh=(root/'build-production.sh').read_text()
production_ps=(root/'build-production.ps1').read_text()
test_script=(root/'scripts/test-core.sh').read_text()
require('scripts/core-tests.list' in test_script or 'core-tests.list' in test_script,'test-core.sh must execute scripts/core-tests.list')
require('core-tests.list' in production_ps,'Windows production build must execute scripts/core-tests.list')
require('./scripts/test-core.sh' in production_sh,'Linux production build must invoke scripts/test-core.sh')
require("Get-ChildItem -Path" in production_ps,'Windows production build must use explicit -Path source discovery')
require("clean build" in production_ps,'Windows production build must perform a clean linked Gradle build')
require('python3 ./scripts/release-audit.py' in production_sh,'Linux production build must run release source audit')
require('clean build' in production_sh,'Linux production build must perform a clean linked Gradle build')
require('write-release-manifest.py' in production_sh and 'write-release-manifest.py' in production_ps,'production builds must emit RELEASE_MANIFEST.json')
require('--no-build-cache' in production_sh,'Linux production linked build must disable Gradle build cache as release evidence')
require((root/'scripts/write-release-manifest.py').exists(),'release manifest writer must exist')
prod_sh=production_sh
prod_ps1=production_ps
for build_script,name in [(prod_sh,'build-production.sh'),(prod_ps1,'build-production.ps1')]:
    require('31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26' in build_script,f'{name} must verify the official Gradle 8.10.2 binary checksum')

# Release test suite must retain exact long-run and projection stress gates.
soak_test=(root/'src/testCore/java/dev/livingrealms/LongRunSoakTest.java').read_text()
stress_test=root/'src/testCore/java/dev/livingrealms/ProjectionStressTest.java'
migration_test=root/'src/testCore/java/dev/livingrealms/SaveMigrationMatrixTest.java'
integrity_test=root/'src/testCore/java/dev/livingrealms/SaveIntegrityTest.java'
fuzz_test=root/'src/testCore/java/dev/livingrealms/SaveMutationFuzzTest.java'
hardening_test=root/'src/testCore/java/dev/livingrealms/ProductionHardeningTest.java'
density_test=root/'src/testCore/java/dev/livingrealms/LivingWorldDensityTest.java'
worldgen_quality_test=(root/'src/testCore/java/dev/livingrealms/WorldgenQualityTest.java')
require(worldgen_quality_test.exists(),'worldgen quality regression gate must exist')
require(stress_test.exists(),'ProjectionStressTest.java must exist')
require(migration_test.exists(),'SaveMigrationMatrixTest.java must exist')
require(integrity_test.exists(),'SaveIntegrityTest.java must exist')
require(fuzz_test.exists(),'SaveMutationFuzzTest.java must exist')
require(hardening_test.exists(),'ProductionHardeningTest.java must exist')
require(density_test.exists(),'LivingWorldDensityTest.java must exist')
density_text=density_test.read_text()
require(('12 kingdoms + Wizard Trees' in density_text or 'twelve kingdoms plus Wizard Trees' in density_text) and 'Wizard Trees' in density_text,'living-world gate must retain twelve kingdoms plus the hidden Wizard Trees faction')
founder=(root/'src/main/java/dev/livingrealms/sim/player/PlayerSettlementFounder.java').read_text()
require('Realm of ' in founder and 'assumeRule' in founder and 'relationWith' in founder,'player-founded settlements must enter canonical government/membership/diplomacy as the actual ruler')
saved_data=(root/'src/main/java/dev/livingrealms/minecraft/LivingRealmsSavedData.java').read_text()
require('ContentRevision' in saved_data and 'contentRevision < CONTENT_REVISION' in saved_data,'density content migration must remain one-shot and persisted')
require('resetConstructionCompletion' in saved_data and 'contentRevision < 10' in saved_data and 'CONTENT_REVISION = 12' in saved_data,'content revision 12 keeps morphology rebuild gate from revision 10 and sparse-density migration without schema-only presentation debt')
require('contentRevision<6||contentRevision<12' in saved_data.replace(' ',''),'revision 12 densifier gate must remain one-shot for pre-12 saves')
require((root/'LICENSE').exists() and 'MIT License' in (root/'LICENSE').read_text(),'MIT LICENSE file must exist at repo root (matches mod_license)')
require((root/'scripts/runtime-smoke.sh').exists(),'runtime smoke script must exist')
require('dev.livingrealms.RuntimeSmokeTest' in canonical_tests,'core suite must include headless runtime smoke gate')
escort_mat=(root/'src/main/java/dev/livingrealms/minecraft/entity/CaravanEscortMaterializer.java').read_text()
military_ent=(root/'src/main/java/dev/livingrealms/minecraft/entity/MilitaryUnitEntity.java').read_text()
require('initializeEscortProjection' in escort_mat and 'initializeEscortProjection' in military_ent,'caravan escorts must use dedicated escort projection initializer')
require('isEscort()' in military_ent and 'escortShipmentId' in military_ent,'military units must expose escort identity namespace')
mil_mat=(root/'src/main/java/dev/livingrealms/minecraft/entity/MilitaryUnitMaterializer.java').read_text()
require('if (e.isEscort()) continue' in mil_mat or 'if(e.isEscort())continue' in mil_mat.replace(' ',''),'army materializer must not dematerialize escort projections')
events_text_early=(root/'src/main/java/dev/livingrealms/minecraft/LivingRealmsEvents.java').read_text()
screen_early=(root/'src/main/java/dev/livingrealms/minecraft/client/ui/RealmDashboardScreen.java').read_text()
net_early=(root/'src/main/java/dev/livingrealms/minecraft/network/LivingRealmsNetwork.java').read_text()
require('Commands.literal("home")' in events_text_early and 'locateOwnSettlement' in events_text_early,'player home/own settlement locate command must be registered')
require('F12 dashboard' in screen_early,'overview tip must say F12 dashboard, not J')
require('OnboardedPlayers' in saved_data and 'markOnboarded' in saved_data,'onboarding greet state must persist in SavedData')
require('onboarding.catalog' in (root/'src/main/java/dev/livingrealms/minecraft/player/PlayerOnboardingRuntime.java').read_text(),'onboarding must send the catalog lang key when creative')
require('FOUND_SETTLEMENT' in (root/'src/main/java/dev/livingrealms/sim/ui/DashboardActionCommand.java').read_text() and 'argument' in (root/'src/main/java/dev/livingrealms/sim/ui/DashboardActionCommand.java').read_text(),'dashboard found must support a custom settlement name argument')
require('ruler_cannot_leave' in (root/'src/main/java/dev/livingrealms/sim/ui/DashboardActionService.java').read_text(),'dashboard leave must reject rulers honestly')
require('insufficient_reputation' in net_early and 'Requires reputation' in screen_early,'join failures/tooltips must explain reputation/bounty thresholds')
require((root/'src/main/java/dev/livingrealms/sim/construction/SettlementMorphology.java').exists(),'geography-derived SettlementMorphology must exist')
require('COASTAL_PORT' in (root/'src/main/java/dev/livingrealms/sim/construction/SettlementMorphology.java').read_text() and 'HILL_TOWN' in (root/'src/main/java/dev/livingrealms/sim/construction/SettlementMorphology.java').read_text(),'morphology catalog must include coastal/hill patterns')
cause_explainer=root/'src/main/java/dev/livingrealms/sim/society/WorldCauseExplainer.java'
require(cause_explainer.exists() and 'settlementPressureCause' in cause_explainer.read_text(),'player-facing WorldCauseExplainer must exist')
require('causeSummary' in (root/'src/main/java/dev/livingrealms/sim/ui/RealmDashboardSnapshot.java').read_text(),'dashboard must expose settlement cause summaries')
require('AssistanceTaskView' in (root/'src/main/java/dev/livingrealms/sim/ui/RealmDashboardSnapshot.java').read_text(),'dashboard protocol must expose assistance task board')
require('case PLAZA' in (root/'src/main/java/dev/livingrealms/sim/construction/StructureBlueprintFactory.java').read_text() or 'PLAZA ->' in (root/'src/main/java/dev/livingrealms/sim/construction/StructureBlueprintFactory.java').read_text(),'civic plazas must have blueprints')
blueprint_factory=(root/'src/main/java/dev/livingrealms/sim/construction/StructureBlueprintFactory.java').read_text()
require('PaletteSlot.LIGHT' in blueprint_factory and ('street.lighting()' in blueprint_factory or 'road_' in blueprint_factory),'roads must place street lighting on sidewalks')
require('AuthoredBlockLedger' in saved_data or 'authoredBlocks' in saved_data,'SavedData must persist authored construction provenance outside schema payload')
construction_runtime_text=(root/'src/main/java/dev/livingrealms/minecraft/construction/SettlementConstructionMaterializer.java').read_text()
require('OBSTRUCTED_PROTECTED' in construction_runtime_text and 'AuthoredBlockLedger' in construction_runtime_text,'construction materializer must use provenance-aware obstruction results')
require('ConstructionRetryKey' in construction_runtime_text,'construction retry/backoff must use globally stable settlement-scoped keys')
require('WorldMutationGuard' in construction_runtime_text,'settlement construction must use shared WorldMutationGuard')
require('hasChunkAt(center)) continue' in construction_runtime_text or 'hasChunkAt(center))continue' in construction_runtime_text.replace(' ',''),'unloaded construction intents must continue to later loaded work, not break the settlement scan')
require('physicallyComplete' in (root/'src/main/java/dev/livingrealms/sim/construction/ConstructionJob.java').read_text(),'construction jobs must expose physical completion distinct from cursor completion')
require((root/'src/main/java/dev/livingrealms/sim/construction/AuthoredOwnerType.java').exists(),'typed provenance owner classes must exist')
require((root/'src/main/java/dev/livingrealms/minecraft/construction/WorldMutationGuard.java').exists(),'shared WorldMutationGuard must exist')
require((root/'src/main/java/dev/livingrealms/minecraft/construction/SettlementGeographyDiscoveryRuntime.java').exists(),'Minecraft geography discovery runtime must exist')
require((root/'src/main/java/dev/livingrealms/minecraft/gametest/LivingRealmsGameTests.java').exists(),'NeoForge GameTest foundation must exist')
require('RegisterGameTestsEvent' in (root/'src/main/java/dev/livingrealms/minecraft/gametest/LivingRealmsGameTests.java').read_text(),'GameTests must register via RegisterGameTestsEvent')
require((root/'src/main/java/dev/livingrealms/sim/construction/SettlementDistrictPlan.java').exists(),'settlement district plan overlay must exist')
require((root/'src/main/java/dev/livingrealms/sim/social/HouseholdHomeBinder.java').exists(),'household home binder must exist')
require('discoverCrossFactionCorridors' in (root/'src/main/java/dev/livingrealms/sim/transport/TransportNetworkEngine.java').read_text(),'transport must discover inter-faction trade corridors')
require('dev.livingrealms.WorldQualityPassTest' in canonical_tests,'core suite must include world-quality P1 gate')
require('return List.of()' in (root/'src/main/java/dev/livingrealms/sim/transport/TerrainCorridorPlanner.java').read_text() and 'straight(' not in (root/'src/main/java/dev/livingrealms/sim/transport/TerrainCorridorPlanner.java').read_text(),'terrain corridor must not fall back to destructive straight roads')
events_text=(root/'src/main/java/dev/livingrealms/minecraft/LivingRealmsEvents.java').read_text()
require('SettlementGeographyDiscoveryRuntime.tick' in events_text,'server tick must run settlement geography discovery from loaded chunks')
require('Commands.literal("setday")' in events_text and 'advanceToDay(target)' in events_text,'absolute setday command must run canonical simulation progression')
require('Commands.literal("locate")' in events_text and 'Commands.literal("city")' in events_text,'Living Realms locate commands must remain registered')
require('Commands.literal("found")' in events_text and 'PlayerSettlementFounder.found' in events_text,'player-founded realm command must remain registered')
require('Commands.literal("mine")' in events_text,'player-owned settlement locate command must remain registered')
require('LocateQuery.nearestMine' in events_text and 'LocateQuery.nearestCity' in events_text,'mine/city locate commands must use dedicated canonical locators')
require('Commands.literal("kingdom")' in events_text and 'Commands.literal("wizardtrees")' in events_text,'locate must cover kingdom/wizardtrees as well as city/mine')
require('AbstractVillager' in events_text and 'DialogueSessionRuntime.open(player,villager)' in events_text,'vanilla/villager-derived NPCs must be adopted into Living Realms dialogue')
require('CivilianNpcAdoption' in events_text,'allowlisted foreign civilians must share the dialogue adoption bridge')
keymap=(root/'src/main/java/dev/livingrealms/minecraft/client/ui/LivingRealmsKeyMappings.java').read_text()
require('GLFW.GLFW_KEY_F12' in keymap and 'GLFW.GLFW_KEY_M' in keymap,'dashboard must use F12 and world map must remain on M')
world_map=(root/'src/main/java/dev/livingrealms/minecraft/client/ui/RealmWorldMapScreen.java').read_text()
require('renderTerrainBase' in world_map and 'LivingRealmsScreens.clearBackground' in world_map,'M map must render an always-visible terrain base without vanilla blur')
require('ClientTerrainMapCache' in world_map,'M map must use cached client surface samples when chunks are loaded')
waystone=(root/'src/main/java/dev/livingrealms/minecraft/compat/WaystoneSettlementRuntime.java').read_text()
require('WaystonesAPI' in waystone and 'placeWaystone' in waystone and 'GLOBAL' in waystone,'settlements must retain optional named global Waystone integration')
require('WaystoneSettlementRuntime.tick' in events_text,'Waystone settlement maintenance must run from server tick')
require('nearestSettlementId' in waystone and 'allWaystones' in waystone,'Waystones must be deduplicated by owning settlement rather than a small fixed radius')
require('recordWaystone' in waystone or 'recordWaystone' in saved_data,'Living Realms Waystones must persist provenance so player stones are never destroyed')
require('WaystoneProvenance' in waystone or 'LR · ' in waystone,'authored Waystones must be marked with Living Realms provenance naming')
palette=(root/'src/main/java/dev/livingrealms/minecraft/construction/FactionBlockPalette.java').read_text()
require('Blocks.OAK_DOOR' in palette and 'doors come in detail pass later' not in palette,'DOOR palette must place real door blocks, not deferred AIR stubs')
require('PhysicalDevelopmentReconciler' in (root/'src/main/java/dev/livingrealms/minecraft/construction/SettlementConstructionMaterializer.java').read_text(),'day-jump catch-up must use physical development reconciliation')
require('TerrainCorridorPlanner' in (root/'src/main/java/dev/livingrealms/minecraft/construction/TransportNetworkMaterializer.java').read_text(),'intercity roads must use terrain-cost corridor planning')
dialogue_ui=(root/'src/main/java/dev/livingrealms/minecraft/client/ui/NpcDialogueScreen.java').read_text()
require('LivingRealmsScreens.clearBackground' in dialogue_ui,'NPC dialogue must not use vanilla world blur')
require('Trailing bytes after Living Realms state' in codec,'save decoder must reject trailing payload data')
require('MAX_STATE_BYTES = 32 * 1024 * 1024' in codec and 'MAX_STRING_BYTES = 64 * 1024' in codec,'save codec must retain global payload/string resource ceilings')
require('CodingErrorAction.REPORT' in codec,'save codec must reject malformed UTF-8 instead of replacement decoding')
require('SimulationStateCodec.MIN_SUPPORTED_SCHEMA' in migration_test.read_text() and 'SimulationStateCodec.SCHEMA_VERSION' in migration_test.read_text(),'save migration matrix must span the decoder-supported schema range')
civilization_test=(root/'src/testCore/java/dev/livingrealms/CivilizationLayerTest.java')
rumor_network_test=(root/'src/testCore/java/dev/livingrealms/RumorNetworkTest.java')
require(civilization_test.exists(),'buildfix12 civilization layer regression gate must exist')
require(rumor_network_test.exists(),'buildfix12 route-aware rumor regression gate must exist')
require('FINAL_DAY = 3650' in soak_test,'long-run soak must end at exactly 3650 days')
for checkpoint in ['day == 30','day == 365','day == FINAL_DAY']:
    require(checkpoint in soak_test,f'long-run soak missing checkpoint expression: {checkpoint}')

# Save startup must never decode against the 20-species starter fallback before datapacks are ready.
species_registry=(root/'src/main/java/dev/livingrealms/minecraft/SpeciesDataRegistry.java').read_text()
simulation_runtime=(root/'src/main/java/dev/livingrealms/minecraft/SimulationRuntime.java').read_text()
codec_text=(root/'src/main/java/dev/livingrealms/sim/persistence/SimulationStateCodec.java').read_text()
require('public static boolean ready()' in species_registry and 'ready = true' in species_registry,'species registry must expose/install readiness')
require('if (!SpeciesDataRegistry.ready())' in simulation_runtime,'canonical SavedData access must wait for datapack species readiness')
require('Save contains live species missing from active catalog' in codec_text,'save decoder must reject removed live species definitions')
saved_data=(root/'src/main/java/dev/livingrealms/minecraft/LivingRealmsSavedData.java').read_text()
require('PayloadCrc32Plus1' in saved_data and 'SimulationStateCodec.integrityToken(payload)' in saved_data,'Minecraft SavedData must persist/verify a payload integrity token')
require('innerSchema != outerSchema' in saved_data,'Minecraft SavedData must reject outer/inner schema mismatch')
require('outerSchema != SimulationStateCodec.SCHEMA_VERSION || expectedIntegrity == 0L' in saved_data and 'loaded.setDirty()' in saved_data,'legacy/pre-checksum SavedData must be marked dirty for rewrite')
require('SimulationValidator.validate(state).throwIfInvalid()' in codec_text,'current state must be semantically validated before encoding')
require('state.repairNextIdWatermark()' in codec_text,'save migration must repair stale canonical ID watermarks')

# Runtime profile wiring must remain real, not UI-only.
events=(root/'src/main/java/dev/livingrealms/minecraft/LivingRealmsEvents.java').read_text()
require('if (!SpeciesDataRegistry.ready()) return;' in events,'server tick must wait for datapack species readiness')
wildlife_runtime=(root/'src/main/java/dev/livingrealms/minecraft/entity/WildlifeMaterializer.java').read_text()
caravan_runtime=(root/'src/main/java/dev/livingrealms/minecraft/entity/TradeCaravanMaterializer.java').read_text()
aircraft_runtime=(root/'src/main/java/dev/livingrealms/minecraft/entity/AircraftMaterializer.java').read_text()
require('advanceDays(data.state().config().strategicDaysPerStep())' in events,'strategicDaysPerStep must drive the daily runtime tick')
require('DashboardActionLimiter.remove(event.getEntity().getUUID())' in events and 'DashboardActionLimiter.clear()' in events,'dashboard action limiter must reset on logout/server stop')
require('RuntimeProjectionPolicy.wildlife(state.config())' in wildlife_runtime,'wildlife runtime must use persisted projection profile')
require('RuntimeProjectionPolicy.caravans(state.config())' in caravan_runtime,'caravan runtime must use persisted projection profile')
require('RuntimeProjectionPolicy.aircraftRadiusBlocks(config)' in aircraft_runtime and 'RuntimeProjectionPolicy.aircraftBudget(config)' in aircraft_runtime,'aircraft runtime must use persisted projection profile')
require('MaterializationConfig.defaults()' not in wildlife_runtime,'wildlife runtime must not silently use default materialization settings')
require('SPAWN_RADIUS_SQR' not in caravan_runtime,'caravan runtime must not use a hardcoded spawn radius')
construction_runtime=(root/'src/main/java/dev/livingrealms/minecraft/construction/SettlementConstructionMaterializer.java').read_text()
require('config().constructionBlockOpsPerTick()' in construction_runtime,'settlement construction budget must follow persisted runtime config')
require('BLOCK_OPERATIONS_PER_TICK' not in construction_runtime,'settlement construction must not retain a hardcoded operation budget')
require('createTerrainAwareJob' in construction_runtime and 'findBuildSite' in construction_runtime and 'terrainFollowingOperations' in construction_runtime,'settlement construction must remain terrain-aware')
require('oldForeignConstructionMaterial' in construction_runtime,'old foreign structural-block artifacts must remain replaceable during authored rebuilds')
transport_runtime=(root/'src/main/java/dev/livingrealms/minecraft/construction/TransportNetworkMaterializer.java').read_text()
require('placeSidewalk' in transport_runtime and 'Blocks.DIRT_PATH' in transport_runtime,'regional routes must physically project carriageways and sidewalks')
animal_entity=(root/'src/main/java/dev/livingrealms/minecraft/entity/LivingRealmsAnimalEntity.java').read_text()
military_entity=(root/'src/main/java/dev/livingrealms/minecraft/entity/MilitaryUnitEntity.java').read_text()
require('.add(Attributes.FLYING_SPEED' in animal_entity,'flying wildlife must register vanilla flying_speed to prevent FlyingMoveControl crashes')
require('noCollision(this)' in military_entity and 'MOTION_BLOCKING_NO_LEAVES' in military_entity,'military projections must self-rescue from terrain/construction suffocation')

# Industry projection must have exactly one canonical block writer.
industry_runtime=(root/'src/main/java/dev/livingrealms/minecraft/construction/IndustrialSiteMaterializer.java').read_text()
require(not (root/'src/main/java/dev/livingrealms/minecraft/construction/IndustryMachineMaterializer.java').exists(),'obsolete second industry block writer must stay removed')
require('IndustryMachineMaterializer.tick' not in events,'server tick must not invoke a second industry writer')
require('data.state().industrialSites()' in industry_runtime,'industry projection must be driven by canonical IndustrialSite records')
require('data.state().config().physicalRadiusBlocks()' in industry_runtime,'industry projection radius must follow persisted runtime config')
require('CreateBlockLookup.orElse("gearbox"' in industry_runtime and 'CreateBlockLookup.orElse("shaft"' in industry_runtime,'single industry writer must retain Create drivetrain projection')

# Faction-property theft must stay scoped to authored Living Realms storage, never arbitrary nearby containers.
theft_runtime=(root/'src/main/java/dev/livingrealms/minecraft/law/FactionContainerTheftRuntime.java').read_text()
property_rights=(root/'src/main/java/dev/livingrealms/sim/property/PropertyRightsEngine.java').read_text()
blueprints=(root/'src/main/java/dev/livingrealms/sim/construction/StructureBlueprintFactory.java').read_text()
require('PropertyRightsEngine.resolveStorage' in theft_runtime,'container theft must resolve authored storage slots')
require('Blocks.BARREL' in theft_runtime,'faction storage runtime must be restricted to authored barrel storage')
require('PropertyRightsEngine.resolve(state.factions()' not in theft_runtime,'container theft must not claim every container inside a faction structure')
require('PaletteSlot.STORAGE' in property_rights and 'PaletteSlot.STORAGE' in blueprints,'authored faction storage slots must remain explicit')
require('stockFromFaction' in theft_runtime and 'faction.stockpile().take' in theft_runtime,'physical faction storage must consume canonical stock')

# Visible target-pack content integration must remain registry-only and respect player-only exclusions.
content_runtime=(root/'src/main/java/dev/livingrealms/minecraft/compat/CompatibleContentRuntime.java').read_text()
palette_runtime=(root/'src/main/java/dev/livingrealms/minecraft/construction/FactionBlockPalette.java').read_text()
citizen_runtime=(root/'src/main/java/dev/livingrealms/minecraft/entity/FactionCitizenEntity.java').read_text()
require('BuiltInRegistries.BLOCK' in content_runtime and 'BuiltInRegistries.ITEM' in content_runtime,'compatible content must be discovered from registries')
require('CompatibleContentRuntime.decorativeBlock' in palette_runtime,'faction palette must expose compatible building content')
require('CompatibleContentRuntime.equipCitizen' in citizen_runtime,'faction citizens must expose compatible equipment content')
citizen_identity=(root/'src/main/java/dev/livingrealms/sim/civilian/CitizenIdentity.java').read_text()
citizen_renderer=(root/'src/main/java/dev/livingrealms/minecraft/client/FactionCitizenRenderer.java').read_text()
require('CitizenIdentity.forProjection' in citizen_runtime and 'SKIN_VARIANT' in citizen_runtime,'citizens must keep deterministic names and appearance variants')
require('new ResourceLocation[48]' in citizen_renderer or 'ResourceLocation[48]' in citizen_renderer or 'MAX_SKIN' in citizen_renderer or '48' in citizen_renderer,'citizen renderer must keep forty-eight stable skin variants')
for i in range(48): require((root/f'src/main/resources/assets/livingrealms/textures/entity/faction_citizen_{i}.png').exists(),f'missing citizen skin variant {i}')
# Final-product authorities must remain present.
require((root/'src/main/java/dev/livingrealms/sim/social/SocialMobilityEngine.java').exists(),'SocialMobilityEngine must exist')
require((root/'src/main/java/dev/livingrealms/sim/military/SiegeMaterializationPlanner.java').exists(),'siege equipment planner must exist')
require((root/'src/main/java/dev/livingrealms/sim/civilization/ProductionContract.java').exists(),'ProductionContract facade must exist')
require((root/'src/main/java/dev/livingrealms/minecraft/entity/SiegeEquipmentEntity.java').exists(),'siege equipment entity must exist')
require('SIEGE_EQUIPMENT' in (root/'src/main/java/dev/livingrealms/minecraft/entity/ModEntities.java').read_text(),'siege equipment must be registered')
require((root/'src/main/resources/assets/livingrealms/textures/entity/siege_ram.png').exists(),'siege ram texture must exist')
require((root/'src/main/resources/assets/livingrealms/textures/gui/dashboard_panel.png').exists(),'dashboard panel texture must exist')
animal_renderer=(root/'src/main/java/dev/livingrealms/minecraft/client/LivingRealmsAnimalRenderer.java').read_text()
require('FAMILY_TEXTURES' in animal_renderer and 'wildlife_' in animal_renderer,'wildlife renderer must select morphology-family textures')
for morph in ('small_quadruped','ungulate','predator_quadruped','bear','large_mammal','crocodilian','fish','cetacean','pinniped','bird'):
    require((root/f'src/main/resources/assets/livingrealms/textures/entity/wildlife_{morph}.png').exists(),f'missing wildlife morphology texture {morph}')
require('path.contains("gun")' in content_runtime and 'equipMilitary' in content_runtime,'faction equipment must discover gun-class mod weapons and military loadouts')
policy_text=(root/'src/main/java/dev/livingrealms/sim/compat/ModCompatibilityPolicy.java').read_text()
require('\"Guns++\",\"mr_guns\",Category.COMBAT,Strategy.PLAYER_ONLY,false' in policy_text,'Guns++ must remain player-only/NPC-forbidden')
require('gamingbarn' in policy_text.lower() and 'PLAYER_ONLY' in policy_text,'GamingBarn guns must remain player-only/NPC-forbidden')
require((root/'src/main/java/dev/livingrealms/minecraft/SpawnKingdomRuntime.java').exists(),'spawn kingdom runtime must exist')
foreign_village=root/'src/main/java/dev/livingrealms/minecraft/ForeignSettlementDiscoveryRuntime.java'
foreign_structure=root/'src/main/java/dev/livingrealms/minecraft/ForeignStructureDiscoveryRuntime.java'
foreign_bootstrap=root/'src/main/java/dev/livingrealms/minecraft/ForeignSettlementBootstrap.java'
require(foreign_village.exists(),'foreign village adoption runtime must exist')
require(foreign_structure.exists(),'foreign modded-structure adoption runtime must exist')
require(foreign_bootstrap.exists(),'foreign settlement preservation bootstrap must exist')
require('SpawnKingdomRuntime.ensure' in events and 'ForeignSettlementDiscoveryRuntime.tick' in events and 'ForeignStructureDiscoveryRuntime.tick' in events,'server tick must run spawn-kingdom plus village/structure adoption')
if foreign_structure.exists():
    fs=foreign_structure.read_text()
    require('startsForStructure' in fs and 'SCANNED_CHUNKS' in fs,'foreign structure adoption must use loaded structure starts with bounded per-session scanning')
    require('pathContainsOnlyWeakTokens' in fs or 'MIN_SETTLEMENT_AREA' in fs,'foreign structure adoption must reject lone house/building/tower classifications')
if foreign_bootstrap.exists():
    fb=foreign_bootstrap.read_text()
    require('preserveExistingInfrastructure' in fb and 'markConstructionCompleted' in fb,'adopted villages/structures must preserve their existing physical infrastructure before future growth')
    require('foreign:adopted_footprint' in fb,'foreign adoption must record an explicit adopted-footprint marker')
    require('PrimaryEconomyPlanner' not in fb or 'Intentionally do NOT mark PrimaryEconomyPlanner' in fb,'foreign adoption must not auto-complete primary economy mines/fisheries/lumber camps')
require(all(token in citizen_runtime for token in ['workLumber','workFarm','workMine','workFish','huntWildlife']),'civilian runtime must retain physical lumber/farm/mine/fish/hunt work loops')
require('stockpile().add' not in citizen_runtime,'physical workers must not add canonical stockpile resources from loaded-chunk projection')
require('findAuthoredWorksite' in citizen_runtime,'physical workers must bind to Living Realms-authored worksites instead of free-radius destruction')
transport_text=(root/'src/main/java/dev/livingrealms/minecraft/construction/TransportNetworkMaterializer.java').read_text()
industry_text=(root/'src/main/java/dev/livingrealms/minecraft/construction/IndustrialSiteMaterializer.java').read_text()
historical_text=(root/'src/main/java/dev/livingrealms/minecraft/construction/HistoricalSiteMaterializer.java').read_text()
require('WorldMutationGuard' in transport_text and 'AuthoredOwnerType.INTERCITY_ROUTE' in transport_text,'transport projection must use typed provenance mutation guard')
require('WorldMutationGuard' in industry_text and 'AuthoredOwnerType.INDUSTRIAL_SITE' in industry_text,'industrial projection must use typed provenance mutation guard')
require('AuthoredOwnerType.HIDDEN_CACHE' in historical_text and 'AuthoredOwnerType.HISTORICAL_RUIN' in historical_text,'historical sites must persist typed provenance')
festival_mat = root/'src/main/java/dev/livingrealms/minecraft/construction/CivicFestivalMaterializer.java'
festival_plan = root/'src/main/java/dev/livingrealms/sim/civilization/CivicFestivalDecorationPlanner.java'
assist_engine = root/'src/main/java/dev/livingrealms/sim/civilization/AssistanceContributionEngine.java'
require(festival_mat.exists(),'civic festival materializer must exist')
require(festival_plan.exists(),'civic festival decoration planner must exist')
require(assist_engine.exists(),'assistance contribution engine must exist')
require('CIVIC_FESTIVAL' in festival_mat.read_text(encoding='utf-8'),'festival materializer must use CIVIC_FESTIVAL provenance')
require('CIVIC_FESTIVAL' in (root/'src/main/java/dev/livingrealms/sim/construction/AuthoredOwnerType.java').read_text(encoding='utf-8'),'AuthoredOwnerType must define CIVIC_FESTIVAL')
require('contributeVerified' in assist_engine.read_text(encoding='utf-8'),'assistance contributions must be server-verified')
require('HumanoidArmorLayer' in citizen_renderer and 'ItemInHandLayer' in citizen_renderer,'citizen renderer must visibly render compatible armor and held tools/weapons')
world_map=root/'src/main/java/dev/livingrealms/minecraft/client/ui/RealmWorldMapScreen.java'
creative_catalog=root/'src/main/java/dev/livingrealms/minecraft/client/ui/CreativeItemCatalogScreen.java'
require(world_map.exists(),'M world-map screen must exist')
require(creative_catalog.exists(),'creative mod-item catalog must exist')
if world_map.exists():
    wm=world_map.read_text()
    require('ecology().regions()' in wm and 'map.settlements()' in wm and 'map.routes()' in wm and 'map.armies()' in wm,'world map must render biome/ecology, settlements, routes and armies from server snapshots')
if creative_catalog.exists():
    cc=creative_catalog.read_text()
    require('EditBox' in cc and 'BuiltInRegistries.ITEM' in cc and 'CreativeSpawnItemPayload' in cc,'creative catalog must be searchable, registry-backed and server-authoritative')
compat_runtime=(root/'src/main/java/dev/livingrealms/minecraft/compat/ModCompatibilityRuntime.java').read_text()
require('detectionIds().stream().anyMatch' in compat_runtime,'runtime target-pack detection must honor alternate mod ids')
require('removedFromTargetPack()' in compat_runtime,'runtime must warn when excluded Create addons are loaded')

# Compatibility contract stays explicit.
compat=(root/'src/main/java/dev/livingrealms/sim/compat/ModCompatibilityPolicy.java').read_text()
for modid in ['create','geckolib','curios','ferritecore','skinlayers3d','jei','clumps','shulkerboxtooltip','biomesoplenty','glitchcore','terrablender','waystones','balm','terralith','lithostitched','travelersbackpack','irons_lib','gunsplusplus']:
    require(f'"{modid}"' in compat,f'compatibility policy missing {modid}')
require('Set.of("create_deep_seas","createdeepseas","create_aeronautics","createaeronautics")' in compat,'removed Create addons must remain explicitly excluded')
require('\"Guns++\",\"mr_guns\",Category.COMBAT,Strategy.PLAYER_ONLY,false' in compat,'Guns++ must remain explicitly NPC-forbidden')
require('id.equals(\"mr_guns\")' in content_runtime and 'ModCompatibilityPolicy.isPlayerOnly(namespace)' in content_runtime,'runtime equipment discovery must reject Guns++ and all player-only namespaces')
require('mayReplaceBiomeSource(){return false;}' in compat,'biome-source replacement must remain disabled')
require('mayOverwriteBlockEntities(){return false;}' in compat,'foreign block entity overwrite must remain disabled')

# Structural target-pack content should be visible without unsafe context-sensitive placements.
require('slot==PaletteSlot.GLASS||slot==PaletteSlot.FENCE||slot==PaletteSlot.PATH||slot==PaletteSlot.LIGHT' in palette_runtime,'foreign content must stay out of load-bearing settlement geometry')
require('PaletteSlot.DOOR' not in content_runtime.split('supportsForeignPalette',1)[1].split('};',1)[0],'automatic foreign palette must not place context-sensitive two-block doors')
require('path.contains(\"crossbow\")' in content_runtime and 'path.contains(\"spellbook\")' in content_runtime,'RPG/ranged/magic equipment discovery must remain enabled')

# Player market must remain canonical + physical-inventory bridged; client never computes its own buy/sell quote.
market_core=(root/'src/main/java/dev/livingrealms/sim/economy/MarketTransactionEngine.java').read_text()
market_runtime=(root/'src/main/java/dev/livingrealms/minecraft/economy/PlayerMarketRuntime.java').read_text()
screen=(root/'src/main/java/dev/livingrealms/minecraft/client/ui/RealmDashboardScreen.java').read_text()
require('stockAtQuote' in market_core and 'treasuryAtQuote' in market_core,'market quote must be state-bound against replay/duplication')
require('nearCompletedMarket' in market_runtime and 'Items.EMERALD' in market_runtime,'player market must require a physical market and use physical emerald settlement')
require('MarketTransactionEngine.commit' in market_runtime,'physical market must commit through canonical market engine')
require('marketBuyCosts()' in screen and 'marketSellPayouts()' in screen,'market UI must display server-provided buy/sell quotes')
require('BUY_SPREAD' not in screen and 'SELL_SPREAD' not in screen,'client must not calculate authoritative market prices')

# Living Society / no-LLM dialogue foundation must remain canonical, bounded and server-authoritative.
social_citizen=root/'src/main/java/dev/livingrealms/sim/social/SocialCitizen.java'
dialogue_engine=root/'src/main/java/dev/livingrealms/sim/dialogue/NaturalLanguageDialogueEngine.java'
dialogue_screen=root/'src/main/java/dev/livingrealms/minecraft/client/ui/NpcDialogueScreen.java'
dialogue_session=root/'src/main/java/dev/livingrealms/minecraft/network/DialogueSessionRuntime.java'
society_test=root/'src/testCore/java/dev/livingrealms/SocietyDialogueTest.java'
require(social_citizen.exists() and dialogue_engine.exists(),'persistent social citizens and deterministic dialogue engine must exist')
require(dialogue_screen.exists() and dialogue_session.exists(),'free-text NPC dialogue screen/session runtime must exist')
if social_citizen.exists():
    sc=social_citizen.read_text()
    require('MAX_MEMORIES=48' in sc and 'MAX_RELATIONSHIPS=32' in sc,'social memory/relationship collections must remain bounded')
if dialogue_engine.exists():
    de=dialogue_engine.read_text()
    require('DialogueContext' in de and 'latestMemory' in de and 'ASK_SOURCE' in de,'dialogue engine must retain context, personal knowledge and source-followups')
if dialogue_screen.exists():
    ds=dialogue_screen.read_text()
    require('EditBox' in ds and 'NpcDialogueClientState.send' in ds,'NPC dialogue must keep free-text input and route messages through client state')
    client_dialogue=(root/'src/main/java/dev/livingrealms/minecraft/client/ui/NpcDialogueClientState.java').read_text()
    require('DialogueMessagePayload' in client_dialogue,'NPC dialogue client state must send the bounded dialogue payload')
require('DialogueSessionRuntime.reply' in (root/'src/main/java/dev/livingrealms/minecraft/network/LivingRealmsNetwork.java').read_text(),'dialogue messages must be handled on the integrated server')
require(society_test.exists() and 'dev.livingrealms.SocietyDialogueTest' in canonical_tests,'society/dialogue regression gate must remain in the core suite')
require(all(t in canonical_tests for t in ['dev.livingrealms.SocietyDialogueTest','dev.livingrealms.SocietyInfrastructureTest','dev.livingrealms.WizardTreesTest']),'Windows/Linux production runners must execute all Living Society gates via core-tests.list')

# Wizard Trees must remain a dedicated hidden underground faction rather than a surface reskin.
wizard_seed=root/'src/main/java/dev/livingrealms/sim/world/WizardTreesSeeder.java'
wizard_plan=root/'src/main/java/dev/livingrealms/sim/construction/WizardTreesPlanner.java'
construction_runtime=(root/'src/main/java/dev/livingrealms/minecraft/construction/SettlementConstructionMaterializer.java').read_text()
require(wizard_seed.exists() and wizard_plan.exists(),'Wizard Trees seed/planner must exist')
if wizard_seed.exists(): require('FACTION_NAME="Wizard Trees"' in wizard_seed.read_text() and 'GovernmentType.THEOCRACY' in wizard_seed.read_text(),'Wizard Trees identity/government must remain explicit')
if wizard_plan.exists(): require('WIZARD_GROVE' in wizard_plan.read_text() and 'WIZARD_TUNNEL' in wizard_plan.read_text(),'Wizard Trees must retain underground grove/tunnel planning')
require('WizardTreesPlanner.pending' in construction_runtime and 'safeWizardExcavate' in construction_runtime,'Minecraft construction must route Wizard Trees through bounded underground excavation')
require('REDSTONE_LIGHT' in (root/'src/main/java/dev/livingrealms/sim/construction/StructureBlueprintFactory.java').read_text(),'Wizard Trees grow chamber must retain redstone-light semantics')
require((root/'src/testCore/java/dev/livingrealms/WizardTreesTest.java').exists() and 'dev.livingrealms.WizardTreesTest' in canonical_tests,'Wizard Trees regression gate must remain enabled')

# Minecraft 1.21.1 Slot.container is package-private; use the public inventory identity API.
for java_file in (root/'src/main/java').rglob('*.java'):
    text_java = java_file.read_text(encoding="utf-8", errors="replace")
    if "slot.container" in text_java:
        fail(f"Direct Slot.container access is not 1.21.1-safe: {java_file.relative_to(root)}")

# Minecraft 1.21.1 Heightmap lives in net.minecraft.world.level.levelgen.
for java_file in (root/'src/main/java').rglob('*.java'):
    text_java = java_file.read_text(encoding="utf-8", errors="replace")
    if 'import net.minecraft.world.level.Heightmap;' in text_java:
        fail(f"Wrong Minecraft 1.21.1 Heightmap package: {java_file.relative_to(root)}")

if errors:
    print('RELEASE AUDIT FAILED')
    for e in errors: print(' -',e)
    sys.exit(1)

print(f'PASS release audit: metadata + side safety + optional-mod isolation + {len(files)} species JSONs + assets + schema{schema_version}/protocol{dashboard_protocol}/net{network_version}/content{content_revision} + {len(canonical_tests)} core tests')
