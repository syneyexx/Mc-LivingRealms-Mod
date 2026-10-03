package dev.livingrealms.sim.government;

import dev.livingrealms.sim.util.Mathx;

public final class RulerProfile {
    private final long id;
    private final String name;
    private int ageYears;
    private double health;
    private double diplomacy;
    private double stewardship;
    private double martial;
    private double legitimacy;

    public RulerProfile(long id, String name, int ageYears, double health, double diplomacy, double stewardship, double martial, double legitimacy) {
        if (id <= 0) throw new IllegalArgumentException("id");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name");
        this.id = id; this.name = name; this.ageYears = Math.max(0, ageYears);
        this.health = Mathx.clamp(health, 0, 1); this.diplomacy = Mathx.clamp(diplomacy, 0, 1);
        this.stewardship = Mathx.clamp(stewardship, 0, 1); this.martial = Mathx.clamp(martial, 0, 1);
        this.legitimacy = Mathx.clamp(legitimacy, 0, 1);
    }

    public long id(){return id;} public String name(){return name;} public int ageYears(){return ageYears;} public double health(){return health;}
    public double diplomacy(){return diplomacy;} public double stewardship(){return stewardship;} public double martial(){return martial;} public double legitimacy(){return legitimacy;}
    public void ageYear(){ageYears++; if(ageYears>55) health=Mathx.clamp(health-.012-(ageYears-55)*.0006,0,1);}
    public void adjustHealth(double v){health=Mathx.clamp(health+v,0,1);} public void adjustLegitimacy(double v){legitimacy=Mathx.clamp(legitimacy+v,0,1);}
    public boolean dead(){return health<=.001;}
}
