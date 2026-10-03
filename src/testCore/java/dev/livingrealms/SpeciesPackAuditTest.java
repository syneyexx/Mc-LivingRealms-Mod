package dev.livingrealms;

import dev.livingrealms.sim.data.SpeciesJsonCodec;
import dev.livingrealms.sim.biome.BiomeClassifier;
import dev.livingrealms.sim.biome.BiomeSignalNormalizer;
import dev.livingrealms.sim.ecology.*;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.world.*;
import java.nio.file.*;
import java.util.*;

/** Full bundled content audit, separate from the small headless fallback catalog. */
public final class SpeciesPackAuditTest {
    private SpeciesPackAuditTest() {}

    public static void main(String[] args) throws Exception {
        Path dir=Path.of("src/main/resources/data/livingrealms/livingrealms/species");
        Map<String,SpeciesDefinition> all=new LinkedHashMap<>();
        try(var paths=Files.list(dir)){
            for(Path path:paths.filter(p->p.toString().endsWith(".json")).sorted().toList()){
                String raw=Files.readString(path);
                for(String required:List.of("\"morphology\"","\"locomotion\"","\"swimSpeedFactor\"","\"flightSpeedFactor\""))
                    if(!raw.contains(required))throw new AssertionError(path.getFileName()+" missing explicit "+required);
                SpeciesDefinition sp=SpeciesJsonCodec.decode(raw);
                String file=path.getFileName().toString().replaceFirst("\\.json$","");
                if(!file.equals(sp.id()))throw new AssertionError("file/id mismatch "+path);
                if(all.put(sp.id(),sp)!=null)throw new AssertionError("duplicate species "+sp.id());
            }
        }
        if(all.size()<90)throw new AssertionError("bundled content regression: only "+all.size()+" species");
        SpeciesCatalogValidator.Report report=SpeciesCatalogValidator.validate(all);report.throwIfInvalid();
        if(!report.warnings().isEmpty())throw new AssertionError("species pack warnings: "+report.warnings());
        for(LocomotionMode mode:LocomotionMode.values())if(all.values().stream().noneMatch(s->s.locomotion()==mode))throw new AssertionError("missing locomotion family "+mode);
        for(MorphologyFamily morphology:MorphologyFamily.values())if(all.values().stream().noneMatch(s->s.morphology()==morphology))throw new AssertionError("missing morphology family "+morphology);

        SimulationState state=new SimulationState(99112233L);DemoSeeder.seed(state);
        int before=(int)state.regions().stream().flatMap(r->r.populations().stream()).count();
        state.replaceSpeciesCatalog(all);
        int after=(int)state.regions().stream().flatMap(r->r.populations().stream()).count();
        if(after<=before)throw new AssertionError("expanded datapack did not colonize compatible existing regions");

        EcosystemRegion ocean=state.ensureEcosystemRegion("open_ocean",new SimPosition(12000,9000),128,256);
        if(ocean.populations().size()<5)throw new AssertionError("ocean region seeded too few species: "+ocean.populations().size());
        for(PopulationGroup group:ocean.populations()){
            SpeciesDefinition sp=all.get(group.speciesId());
            if(sp==null)throw new AssertionError("unknown seeded species");
            if(sp.locomotion()==LocomotionMode.TERRESTRIAL)throw new AssertionError("terrestrial species seeded in open ocean: "+sp.id());
        }
        var same=state.ensureEcosystemRegion("open_ocean",new SimPosition(12080,9040),128,256);
        if(same.id()!=ocean.id())throw new AssertionError("nearby matching biome cell should merge");

        byte[] save=SimulationStateCodec.encode(state);
        SimulationState restored=SimulationStateCodec.decode(save,all);
        boolean rejectedMissingCatalog=false;
        try { SimulationStateCodec.decode(save,SpeciesCatalog.starter()); }
        catch (java.io.UncheckedIOException expected) { rejectedMissingCatalog=true; }
        if(!rejectedMissingCatalog)throw new AssertionError("save with live datapack species must reject a catalog that no longer defines them");
        EcosystemRegion restoredOcean=restored.regions().stream().filter(r->r.id()==ocean.id()).findFirst().orElseThrow();
        if(restoredOcean.center().distanceTo(ocean.center())>1e-9)throw new AssertionError("schema8 ecosystem center round-trip");
        if(SimulationStateCodec.SCHEMA_VERSION!=15)throw new AssertionError("unexpected schema version");
        if(!BiomeClassifier.classifyId(BiomeSignalNormalizer.observation("minecraft:deep_frozen_ocean",List.of("minecraft:is_ocean"),.05,.5)).equals("deep_ocean"))throw new AssertionError("deep ocean mapping");
        if(!BiomeClassifier.classifyId(BiomeSignalNormalizer.observation("minecraft:mangrove_swamp",List.of("c:is_wetland"),.8,.9)).equals("mangrove"))throw new AssertionError("mangrove mapping");
        if(!BiomeClassifier.classifyId(BiomeSignalNormalizer.observation("modded:ancient_mountain_forest",List.of("c:is_mountain","c:is_forest"),.35,.7)).equals("montane_forest"))throw new AssertionError("modded tagged biome mapping");
        System.out.println("PASS bundled species pack: "+all.size()+" species + explicit physics + colonization + schema8 ecosystem positions + biome normalization");
    }
}
