package dev.livingrealms.sim.biome;

import java.util.*;

/** Ecological archetypes used by the strategic simulator; Minecraft biome tags map into these. */
public final class BiomeCatalog {
    private BiomeCatalog() {}
    public static Map<String, EcoBiome> defaults() {
        List<EcoBiome> list = List.of(
            new EcoBiome("temperate_forest", ClimateBand.TEMPERATE, 7000, 100, .85, .90, Set.of("forest","freshwater","temperate")),
            new EcoBiome("temperate_rainforest", ClimateBand.TEMPERATE, 10000, 150, .95, 1.0, Set.of("forest","humid","freshwater","temperate")),
            new EcoBiome("grassland", ClimateBand.TEMPERATE, 5000, 120, .65, .35, Set.of("grassland","open","temperate")),
            new EcoBiome("steppe", ClimateBand.TEMPERATE, 2800, 55, .38, .18, Set.of("grassland","dry","open")),
            new EcoBiome("mediterranean", ClimateBand.SUBTROPICAL, 4200, 65, .42, .48, Set.of("scrub","dry","warm")),
            new EcoBiome("savanna", ClimateBand.TROPICAL, 4500, 80, .45, .35, Set.of("grassland","warm","open")),
            new EcoBiome("rainforest", ClimateBand.TROPICAL, 12000, 180, .95, 1.0, Set.of("forest","humid","warm","freshwater")),
            new EcoBiome("tropical_dry_forest", ClimateBand.TROPICAL, 6000, 80, .48, .72, Set.of("forest","seasonal","warm")),
            new EcoBiome("hot_desert", ClimateBand.ARID, 500, 5, .08, .15, Set.of("arid","warm","open")),
            new EcoBiome("cold_desert", ClimateBand.ARID, 650, 7, .11, .18, Set.of("arid","cold","open")),
            new EcoBiome("taiga", ClimateBand.BOREAL, 5000, 50, .65, .85, Set.of("forest","cold","freshwater")),
            new EcoBiome("tundra", ClimateBand.POLAR, 1000, 15, .35, .20, Set.of("cold","open")),
            new EcoBiome("polar_ice", ClimateBand.POLAR, 120, 1.5, .25, .05, Set.of("polar","ice","coast")),
            new EcoBiome("alpine", ClimateBand.ALPINE, 1200, 20, .55, .45, Set.of("mountain","cold","rocky")),
            new EcoBiome("montane_forest", ClimateBand.ALPINE, 4200, 55, .68, .78, Set.of("mountain","forest","cold")),
            new EcoBiome("wetland", ClimateBand.TEMPERATE, 9000, 140, 1.0, .75, Set.of("wetland","freshwater","humid")),
            new EcoBiome("mangrove", ClimateBand.TROPICAL, 8500, 125, 1.0, .92, Set.of("wetland","coast","brackish","warm")),
            new EcoBiome("freshwater_lake", ClimateBand.AQUATIC, 4200, 95, 1.0, .35, Set.of("lake","freshwater","aquatic")),
            new EcoBiome("river", ClimateBand.AQUATIC, 3500, 80, 1.0, .40, Set.of("river","freshwater","aquatic")),
            new EcoBiome("estuary", ClimateBand.AQUATIC, 7000, 130, 1.0, .52, Set.of("estuary","brackish","coast","aquatic")),
            new EcoBiome("rocky_coast", ClimateBand.AQUATIC, 3000, 60, 1.0, .30, Set.of("coast","saltwater","rocky","aquatic")),
            new EcoBiome("coral_reef", ClimateBand.AQUATIC, 9500, 160, 1.0, .80, Set.of("ocean","saltwater","reef","warm","aquatic")),
            new EcoBiome("continental_shelf", ClimateBand.AQUATIC, 6500, 115, 1.0, .25, Set.of("ocean","saltwater","shelf","aquatic")),
            new EcoBiome("open_ocean", ClimateBand.AQUATIC, 5000, 100, 1.0, .10, Set.of("ocean","saltwater","pelagic","aquatic")),
            new EcoBiome("deep_ocean", ClimateBand.AQUATIC, 1800, 30, 1.0, .08, Set.of("ocean","saltwater","deep","aquatic"))
        );
        Map<String,EcoBiome> out = new LinkedHashMap<>(); list.forEach(b -> out.put(b.id(), b)); return Collections.unmodifiableMap(out);
    }
}
