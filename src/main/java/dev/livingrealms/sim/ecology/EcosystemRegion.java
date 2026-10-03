package dev.livingrealms.sim.ecology;

import dev.livingrealms.sim.biome.EcoBiome;
import dev.livingrealms.sim.world.SimPosition;
import java.util.*;

public final class EcosystemRegion {
    private final long id;
    private final EcoBiome biome;
    private final double areaKm2;
    private SimPosition center;
    private double plantBiomass;
    private final List<PopulationGroup> populations = new ArrayList<>();

    public EcosystemRegion(long id, EcoBiome biome, double areaKm2) {
        this(id, biome, areaKm2, new SimPosition(0, 0));
    }

    public EcosystemRegion(long id, EcoBiome biome, double areaKm2, SimPosition center) {
        if (areaKm2 <= 0) throw new IllegalArgumentException("areaKm2");
        this.id=id; this.biome=Objects.requireNonNull(biome); this.areaKm2=areaKm2; this.center=Objects.requireNonNull(center);
        this.plantBiomass=biome.basePlantBiomass()*areaKm2;
    }
    public long id(){return id;} public EcoBiome biome(){return biome;} public double areaKm2(){return areaKm2;} public SimPosition center(){return center;} public double plantBiomass(){return plantBiomass;}
    public List<PopulationGroup> populations(){return Collections.unmodifiableList(populations);} public void add(PopulationGroup p){populations.add(Objects.requireNonNull(p));}
    public void restoreCenter(SimPosition value){center=Objects.requireNonNull(value);} 
    public void regrowPlants(double days){ plantBiomass = Math.min(biome.basePlantBiomass()*areaKm2*1.5, plantBiomass + biome.plantRegrowthPerDay()*areaKm2*days); }
    public double consumePlants(double requested){ double eaten=Math.min(Math.max(0,requested),plantBiomass); plantBiomass-=eaten; return eaten; }
    public void restorePlantBiomass(double value){plantBiomass=Math.max(0,value);}
    public void purgeExtinct(){populations.removeIf(PopulationGroup::extinct);}
}
