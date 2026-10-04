package dev.livingrealms.sim.economy;

import dev.livingrealms.sim.civilization.CivilizationCalendar;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import java.util.Locale;

/**
 * Medieval crop / livestock mix derived from settlement identity, season and terrain proxies.
 * Still stores output as {@code ResourceType.FOOD}/{@code TEXTILES} (codec-safe); the mix changes yields.
 */
public final class AgrarianProfile {
    public enum Crop { GRAIN, RYE, BARLEY, OATS, VEGETABLES, FLAX, GRAPES, HOPS }
    public enum Livestock { CATTLE, SHEEP, PIGS, CHICKENS, HORSES, OXEN }

    private AgrarianProfile() {}

    public record Mix(Crop primaryCrop, Crop secondaryCrop, Livestock primaryStock, double yieldMul, double textileMul, String label) {}

    public static Mix of(Settlement settlement, CivilizationCalendar.Season season) {
        long mixSeed = settlement.id() * 0x9E3779B97F4A7C15L ^ settlement.name().toLowerCase(Locale.ROOT).hashCode();
        Crop[] crops = Crop.values();
        Livestock[] stock = Livestock.values();
        Crop primary = crops[Math.floorMod((int) (mixSeed >>> 8), crops.length)];
        Crop secondary = crops[Math.floorMod((int) (mixSeed >>> 16), crops.length)];
        if (secondary == primary) secondary = crops[Math.floorMod(primary.ordinal() + 3, crops.length)];
        Livestock animals = stock[Math.floorMod((int) (mixSeed >>> 24), stock.length)];

        // Terrain / name proxies for regional specialization.
        String n = settlement.name().toLowerCase(Locale.ROOT);
        SimPosition p = settlement.position();
        if (n.contains("vine") || n.contains("grape") || Math.abs(p.z()) < 800) primary = Crop.GRAPES;
        else if (n.contains("hop") || n.contains("brew")) primary = Crop.HOPS;
        else if (n.contains("flax") || n.contains("linen")) primary = Crop.FLAX;
        else if (n.contains("rye") || Math.abs(p.z()) > 4_500) primary = Crop.RYE;
        else if (n.contains("oat") || n.contains("highland") || n.contains("hill")) primary = Crop.OATS;
        else if (n.contains("barley") || n.contains("malt")) primary = Crop.BARLEY;
        if (n.contains("sheep") || n.contains("wool") || n.contains("fold")) animals = Livestock.SHEEP;
        else if (n.contains("horse") || n.contains("mare")) animals = Livestock.HORSES;
        else if (n.contains("ox") || n.contains("plough") || n.contains("plow")) animals = Livestock.OXEN;

        double yield = switch (primary) {
            case GRAIN -> 1.08;
            case RYE -> season == CivilizationCalendar.Season.WINTER ? 0 : 1.02;
            case BARLEY -> 1.05;
            case OATS -> 0.98;
            case VEGETABLES -> 0.94;
            case FLAX -> 0.90;
            case GRAPES -> season == CivilizationCalendar.Season.AUTUMN ? 1.15 : 0.88;
            case HOPS -> season == CivilizationCalendar.Season.SUMMER || season == CivilizationCalendar.Season.AUTUMN ? 1.10 : 0.86;
        };
        double textile = primary == Crop.FLAX || animals == Livestock.SHEEP ? 1.35 : secondary == Crop.FLAX ? 1.12 : 1.0;
        if (animals == Livestock.SHEEP) textile *= 1.15;
        if (animals == Livestock.OXEN) yield *= 1.08; // draft animals raise plow yield
        if (animals == Livestock.HORSES) yield *= 1.04;
        // Braak / three-field applied by caller via rotationMultiplier(day).
        String label = primary.name().toLowerCase(Locale.ROOT) + "/" + secondary.name().toLowerCase(Locale.ROOT)
                + " with " + animals.name().toLowerCase(Locale.ROOT);
        return new Mix(primary, secondary, animals, yield, textile, label);
    }

    /** Fallow / three-field drag for the current calendar year (braak). */
    public static double rotationMultiplier(long day) {
        int year = CivilizationCalendar.year(day);
        // Year mod 3: 0 rich, 1 average, 2 fallow-heavy.
        return switch (Math.floorMod(year, 3)) {
            case 0 -> 1.08;
            case 1 -> 1.00;
            default -> 0.86;
        };
    }
}
