package dev.livingrealms.sim.ecology;

import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.util.Mathx;

/** Abstract population cohort used when animals are outside the player's materialization radius. */
public final class PopulationGroup {
    private final long id;
    private final String speciesId;
    private String biomeId;
    private SimPosition position;
    private double population;
    private double health = 1.0;
    private double hunger;
    private double thirst;
    private double diseasePressure;
    private double injuryPressure;
    private double averageAgeDays;
    private double migrationPressure;
    private double ageStructure = 0.55;

    public PopulationGroup(long id, String speciesId, String biomeId, SimPosition position, double population) {
        if (id<=0) throw new IllegalArgumentException("id");
        if(speciesId==null||speciesId.isBlank())throw new IllegalArgumentException("speciesId");
        if(biomeId==null||biomeId.isBlank())throw new IllegalArgumentException("biomeId");
        if(position==null)throw new IllegalArgumentException("position");
        if (population < 0 || !Double.isFinite(population)) throw new IllegalArgumentException("population");
        this.id = id; this.speciesId = speciesId; this.biomeId = biomeId; this.position = position; this.population = population;
    }
    public long id(){return id;} public String speciesId(){return speciesId;} public String biomeId(){return biomeId;}
    public SimPosition position(){return position;} public double population(){return population;} public double health(){return health;}
    public double hunger(){return hunger;} public double thirst(){return thirst;} public double diseasePressure(){return diseasePressure;} public double injuryPressure(){return injuryPressure;} public double averageAgeDays(){return averageAgeDays;} public double migrationPressure(){return migrationPressure;} public double reproductiveFraction(){return ageStructure;}
    public void setBiomeId(String v){if(v==null||v.isBlank())throw new IllegalArgumentException("biomeId");biomeId=v;} public void setPosition(SimPosition v){if(v==null)throw new IllegalArgumentException("position");position=v;}
    public void setPopulation(double v){if(!Double.isFinite(v))throw new IllegalArgumentException("population");population=Math.max(0,v);} public void addPopulation(double v){setPopulation(population+v);}
    public void addHunger(double v){setHunger(hunger+v);} public void setHunger(double v){hunger=Mathx.clamp(v,0,1);}
    public void addThirst(double v){setThirst(thirst+v);} public void setThirst(double v){thirst=Mathx.clamp(v,0,1);}
    public void setDiseasePressure(double v){diseasePressure=Mathx.clamp(v,0,1);} public void setInjuryPressure(double v){injuryPressure=Mathx.clamp(v,0,1);}
    public void setAverageAgeDays(double v){averageAgeDays=Math.max(0,v);} public void setMigrationPressure(double v){migrationPressure=Mathx.clamp(v,0,1);}
    public void setHealth(double v){health=Mathx.clamp(v,0,1);} public void setReproductiveFraction(double v){ageStructure=Mathx.clamp(v,0,1);}
    public boolean extinct(){return population < .5;}
}
