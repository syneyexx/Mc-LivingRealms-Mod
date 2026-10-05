package dev.livingrealms.sim.content;

import java.util.Locale;
import java.util.Objects;

/**
 * Wave 30 — culture-pack naming styles produce distinct given/family names.
 * Deterministic from seed + agent identity; falls back to riverine_english pools.
 */
public final class CulturalNaming {
    private CulturalNaming() {}

    public record NamePair(String given, String family) {
        public NamePair {
            Objects.requireNonNull(given, "given");
            Objects.requireNonNull(family, "family");
            if (given.isBlank() || family.isBlank()) throw new IllegalArgumentException("name");
        }

        public String full() {
            return given + " " + family;
        }
    }

    public static NamePair name(String namingStyle, long salt) {
        String style = namingStyle == null ? "" : namingStyle.trim().toLowerCase(Locale.ROOT);
        String[] given = givenPool(style);
        String[] family = familyPool(style);
        long z = mix(salt ^ style.hashCode() * 0x9E3779B97F4A7C15L);
        return new NamePair(
                given[Math.floorMod((int) z, given.length)],
                family[Math.floorMod((int) (z >>> 32), family.length)]);
    }

    public static NamePair name(CultureDefinition culture, long salt) {
        Objects.requireNonNull(culture, "culture");
        return name(culture.namingStyle(), salt);
    }

    private static String[] givenPool(String style) {
        return switch (style) {
            case "forge_dwarven" -> new String[]{
                    "Thrain", "Bora", "Durin", "Hilda", "Karg", "Sigrid", "Brom", "Ylva", "Grom", "Astrid", "Torvik", "Eira"};
            case "nordic_frost" -> new String[]{
                    "Bjorn", "Ingrid", "Leif", "Sigrid", "Erik", "Freya", "Hakon", "Astrid", "Ragnar", "Solveig", "Tor", "Liv"};
            case "maritime", "stormcoast", "coastal" -> new String[]{
                    "Marin", "Cora", "Tide", "Selene", "Reef", "Anwen", "Gale", "Pearl", "Harbor", "Lir", "Brine", "Nessa"};
            case "golden_court" -> new String[]{
                    "Aurelio", "Celes", "Lucien", "Isolde", "Maxim", "Seraphine", "Julian", "Vespera", "Cassian", "Opaline", "Dorian", "Liora"};
            case "crystal_academic" -> new String[]{
                    "Quill", "Athena", "Orin", "Lyce", "Theo", "Helene", "Archon", "Iris", "Vellum", "Sophia", "Codex", "Elena"};
            case "march_war", "martial_compound" -> new String[]{
                    "Cassius", "Valeria", "Draven", "Mira", "Hadrian", "Sable", "Rex", "Theron", "Voss", "Kara", "Brand", "Nyx"};
            case "sylvan_english", "greenwood" -> new String[]{
                    "Rowan", "Fern", "Ash", "Willow", "Briar", "Hazel", "Oak", "Linden", "Moss", "Ivy", "Thorn", "Elm"};
            case "solar_roman" -> new String[]{
                    "Lucius", "Aurelia", "Marcus", "Julia", "Titus", "Flavia", "Quintus", "Octavia", "Gaius", "Livia", "Severus", "Diana"};
            case "gothic_sable" -> new String[]{
                    "Raven", "Nocturne", "Silas", "Mortis", "Cyril", "Lilith", "Edmund", "Vera", "Draven", "Morrigan", "Aldric", "Nyx"};
            case "arcane_grove" -> new String[]{
                    "Aether", "Myrrh", "Lumen", "Sylph", "Orin", "Vesper", "Rune", "Elowen", "Thistle", "Fael", "Starling", "Moss"};
            case "riverine_english" -> new String[]{
                    "Alden", "Mira", "Tomas", "Elise", "Rowan", "Lena", "Corin", "Nora", "Darian", "Maeve", "Jonas", "Talia"};
            default -> new String[]{
                    "Alden", "Mira", "Tomas", "Elise", "Rowan", "Lena", "Corin", "Nora", "Darian", "Maeve", "Jonas", "Talia",
                    "Bram", "Iris", "Cedric", "Anya", "Lucan", "Freya", "Marek", "Selene", "Garrick", "Lyra", "Owen", "Petra"};
        };
    }

    private static String[] familyPool(String style) {
        return switch (style) {
            case "forge_dwarven" -> new String[]{
                    "Ironvein", "Stonefist", "Deepdelve", "Anvilborn", "Coalmark", "Granite", "Hammerfall", "Orekeep"};
            case "nordic_frost" -> new String[]{
                    "Snowmantle", "Frosthelm", "Iceford", "Wolfsson", "Highcrag", "Winterborn", "Skarn", "Fjeld"};
            case "maritime", "stormcoast", "coastal" -> new String[]{
                    "Saltwind", "Wavebreaker", "Seascale", "Deepcurrent", "Harborwell", "Stormsail", "Kelpson", "Brine"};
            case "golden_court" -> new String[]{
                    "Goldmere", "Silkledger", "Coinwright", "Gildveil", "Marketborn", "Vaultsong", "Aureline", "Prosper"};
            case "crystal_academic" -> new String[]{
                    "Glassquill", "Starledger", "Scrollmere", "Prismwell", "Inkward", "Lumenhall", "Codex", "Scholar"};
            case "march_war", "martial_compound" -> new String[]{
                    "Ironmarch", "Redbanner", "Spearwall", "Warholt", "Bladeholt", "Shieldborn", "Ashfield", "Vanguard"};
            case "sylvan_english", "greenwood" -> new String[]{
                    "Greenbough", "Leafwhisper", "Mossbrook", "Woodsong", "Fernvale", "Rootward", "Glade", "Thicket"};
            case "solar_roman" -> new String[]{
                    "Solenne", "Sunfield", "Aurelian", "Radiance", "Daycrest", "Lumenor", "Flavian", "Helior"};
            case "gothic_sable" -> new String[]{
                    "Nightgrove", "Sablemere", "Ravenholt", "Umberveil", "Duskwatch", "Blackthorn", "Grimshaw", "Nocturne"};
            case "arcane_grove" -> new String[]{
                    "Starroot", "Moonwick", "Spellbough", "Arcaneleaf", "Whisperoak", "Feymere", "Glyphwood", "Aether"};
            case "riverine_english" -> new String[]{
                    "Ashford", "Briar", "Dunwell", "Evermere", "Farrow", "Hart", "Marsh", "Oakley", "Reed", "Thorne", "Vale", "West"};
            default -> new String[]{
                    "Ashford", "Briar", "Crowley", "Dunwell", "Evermere", "Farrow", "Grey", "Hart", "Iverson", "Jensen",
                    "Marsh", "North", "Oakley", "Pryce", "Quill", "Rook", "Stone", "Thorne", "Vale", "West"};
        };
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
