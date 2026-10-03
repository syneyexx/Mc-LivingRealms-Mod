package dev.livingrealms.sim.biome;

import java.util.Map;
import java.util.Objects;

/** Deterministic classifier from Minecraft-style biome facts into Living Realms ecology archetypes. */
public final class BiomeClassifier {
    private BiomeClassifier() {}

    public static EcoBiome classify(BiomeObservation o,Map<String,EcoBiome> catalog){
        Objects.requireNonNull(o,"observation");Objects.requireNonNull(catalog,"catalog");
        String id=classifyId(o);
        EcoBiome biome=catalog.get(id);
        if(biome==null)throw new IllegalArgumentException("Missing ecology biome archetype: "+id);
        return biome;
    }

    public static String classifyId(BiomeObservation o){
        if(o.has("deep_ocean"))return "deep_ocean";
        if(o.has("ocean")){
            if(o.has("warm")||o.has("coral"))return "coral_reef";
            if(o.has("coast")||o.has("shelf"))return "continental_shelf";
            return "open_ocean";
        }
        if(o.has("river"))return "river";
        if(o.has("lake"))return "freshwater_lake";
        if(o.has("estuary")||o.has("brackish"))return "estuary";
        if(o.has("mangrove"))return "mangrove";
        if(o.has("swamp")||o.has("wetland"))return "wetland";
        if(o.has("beach")||o.has("coast"))return "rocky_coast";
        if(o.has("frozen")||o.has("ice"))return o.has("barren")?"polar_ice":"tundra";
        if(o.has("mountain")||o.has("peak"))return o.has("forest")?"montane_forest":"alpine";
        if(o.has("jungle")||o.has("rainforest"))return o.humidity()>=.72?"rainforest":"tropical_dry_forest";
        if(o.has("savanna"))return "savanna";
        if(o.has("desert"))return o.temperature()<.35?"cold_desert":"hot_desert";
        if(o.has("badlands"))return o.humidity()<.18?"hot_desert":"steppe";
        if(o.has("taiga")||o.has("boreal"))return "taiga";
        if(o.has("forest")){
            if(o.temperature()<.30)return "taiga";
            if(o.temperature()>1.05)return o.humidity()>.65?"rainforest":"tropical_dry_forest";
            if(o.humidity()>.82)return "temperate_rainforest";
            if(o.humidity()<.32&&o.temperature()>.65)return "mediterranean";
            return "temperate_forest";
        }
        if(o.has("plains")||o.has("meadow")||o.has("grassland")){
            if(o.temperature()>1.0)return "savanna";
            if(o.humidity()<.28)return "steppe";
            return "grassland";
        }
        if(o.temperature()<.20)return "tundra";
        if(o.temperature()>1.25&&o.humidity()<.22)return "hot_desert";
        if(o.humidity()>.75)return o.temperature()>1.0?"rainforest":"temperate_forest";
        return o.humidity()<.30?"steppe":"grassland";
    }
}
