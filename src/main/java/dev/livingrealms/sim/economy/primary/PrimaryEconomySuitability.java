package dev.livingrealms.sim.economy.primary;

import dev.livingrealms.sim.biome.EcoBiome;
import dev.livingrealms.sim.ecology.EcosystemRegion;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimulationState;
import java.util.*;

/** Resolves primary-industry suitability from the nearest discovered ecological region. */
public final class PrimaryEconomySuitability {
    private static final double REGION_INFLUENCE_RADIUS=1800.0;
    private PrimaryEconomySuitability(){}
    public static double score(SimulationState state,Settlement settlement,PrimaryEconomyKind kind){
        Objects.requireNonNull(state,"state");Objects.requireNonNull(settlement,"settlement");Objects.requireNonNull(kind,"kind");
        EcosystemRegion nearest=state.regions().stream().min(Comparator.comparingDouble(r->r.center().distanceTo(settlement.position()))).orElse(null);
        if(nearest==null||nearest.center().distanceTo(settlement.position())>REGION_INFLUENCE_RADIUS)return undiscoveredScore(kind);
        EcoBiome b=nearest.biome();Set<String> t=b.tags();
        return Mathx.clamp(switch(kind){
            case MINE -> (t.contains("mountain")||t.contains("rocky")?1.0:.0)+(t.contains("arid")?.2:0)+(t.contains("forest")?.08:0)+(t.contains("aquatic")?-.65:0)+.28;
            case LUMBER_CAMP -> (t.contains("forest")?.9:0)+(t.contains("scrub")?.35:0)+(t.contains("wetland")?.18:0)+(t.contains("arid")?-.45:0)+(t.contains("aquatic")?-.7:0)+.08;
            case FISHERY -> (t.contains("aquatic")?.9:0)+(t.contains("freshwater")?.65:0)+(t.contains("coast")?.55:0)+(t.contains("wetland")?.38:0)+b.waterAvailability()*.22-(t.contains("arid")?.55:0);
        },0,1);
    }
    /** Stable no-geography score used before any ecological region is discovered. */
    public static double undiscoveredScore(PrimaryEconomyKind kind){
        Objects.requireNonNull(kind,"kind");
        return switch(kind){case MINE->.38;case LUMBER_CAMP->.32;case FISHERY->.12;};
    }
}
