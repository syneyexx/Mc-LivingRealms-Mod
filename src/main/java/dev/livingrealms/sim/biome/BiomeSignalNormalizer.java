package dev.livingrealms.sim.biome;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Normalizes vanilla/common/modded biome ids and tag paths into loader-neutral ecological signals. */
public final class BiomeSignalNormalizer {
    private BiomeSignalNormalizer() {}

    public static BiomeObservation observation(String sourceId, Collection<String> rawTagPaths, double temperature, double humidity) {
        // Minecraft's vanilla climate values are normally finite and downfall is near [0,1],
        // but modded biome stacks can expose NaN/infinite/out-of-range values. Treat this
        // adapter as a trust boundary: normalize external climate signals before they enter
        // the strict canonical simulation model instead of crashing the server tick.
        double safeTemperature=Double.isFinite(temperature)?temperature:.8D;
        double safeHumidity=Double.isFinite(humidity)?Math.max(0D,Math.min(1.5D,humidity)):.4D;
        LinkedHashSet<String> tags=new LinkedHashSet<>();
        addSignals(tags,sourceId);
        for(String raw:rawTagPaths)addSignals(tags,raw);
        if(safeTemperature<=.18)tags.add("frozen");
        if(safeTemperature<.35)tags.add("cold");
        if(safeTemperature>1.0)tags.add("warm");
        if(safeHumidity>=.75)tags.add("humid");
        if(safeHumidity<=.22)tags.add("dry");
        return new BiomeObservation(sourceId,Set.copyOf(tags),safeTemperature,safeHumidity);
    }

    static void addSignals(Set<String> out,String raw){
        if(raw==null||raw.isBlank())return;
        String path=raw.toLowerCase(Locale.ROOT).replace('\\','/');
        int colon=path.indexOf(':');if(colon>=0)path=path.substring(colon+1);
        int slash=path.lastIndexOf('/');if(slash>=0)path=path.substring(slash+1);
        out.add(path);
        if(path.startsWith("is_"))out.add(path.substring(3));
        if(path.contains("deep")&&path.contains("ocean"))out.add("deep_ocean");
        signal(out,path,"ocean","ocean");signal(out,path,"river","river");signal(out,path,"lake","lake");
        signal(out,path,"estuary","estuary");signal(out,path,"brackish","brackish");
        signal(out,path,"mangrove","mangrove");
        if(path.contains("swamp")||path.contains("marsh"))out.add("wetland");
        signal(out,path,"swamp","swamp");signal(out,path,"wetland","wetland");
        if(path.contains("beach")||path.contains("shore"))out.add("coast");
        signal(out,path,"beach","beach");signal(out,path,"coast","coast");signal(out,path,"shelf","shelf");signal(out,path,"coral","coral");
        signal(out,path,"forest","forest");signal(out,path,"taiga","taiga");signal(out,path,"boreal","boreal");
        if(path.contains("redwood")||path.contains("woodland")||path.contains("orchard")||path.contains("grove"))out.add("forest");
        signal(out,path,"jungle","jungle");signal(out,path,"rainforest","rainforest");
        signal(out,path,"savanna","savanna");signal(out,path,"desert","desert");signal(out,path,"badlands","badlands");
        if(path.contains("steppe")||path.contains("heath")||path.contains("lavender"))out.add("grassland");
        if(path.contains("chaparral")){out.add("scrub");out.add("dry");}
        if(path.contains("bog")||path.contains("fen")||path.contains("bayou")){out.add("wetland");out.add("freshwater");}
        if(path.contains("oasis")){out.add("freshwater");out.add("warm");}
        signal(out,path,"plains","plains");signal(out,path,"meadow","meadow");signal(out,path,"grassland","grassland");
        signal(out,path,"mountain","mountain");signal(out,path,"peak","peak");signal(out,path,"alpine","mountain");
        if(path.contains("highland")||path.contains("plateau")){out.add("mountain");out.add("grassland");}
        if(path.contains("rocky")||path.contains("canyon")||path.contains("volcanic")){out.add("rocky");out.add("open");}
        signal(out,path,"frozen","frozen");signal(out,path,"ice","ice");signal(out,path,"warm","warm");signal(out,path,"cold","cold");
    }

    private static void signal(Set<String> out,String path,String needle,String signal){if(path.contains(needle))out.add(signal);}
}
