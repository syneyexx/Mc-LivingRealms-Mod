package dev.livingrealms.sim.society;

import dev.livingrealms.sim.util.Mathx;

public record SettlementNeeds(double food, double housing, double safety, double employment, double goods) {
    public SettlementNeeds {
        food=Mathx.clamp(food,0,1); housing=Mathx.clamp(housing,0,1); safety=Mathx.clamp(safety,0,1); employment=Mathx.clamp(employment,0,1); goods=Mathx.clamp(goods,0,1);
    }
    public double satisfaction(){return food*.32+housing*.20+safety*.20+employment*.16+goods*.12;}
}
