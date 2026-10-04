package dev.livingrealms.sim.government;

/** Monumental civic works with multi-resource construction costs. */
public enum GrandProjectType {
    PALACE,CATHEDRAL,UNIVERSITY,AQUEDUCT,CANAL,GRAND_BRIDGE,HARBOR_EXPANSION,FORTRESS,CITY_WALLS,MONUMENTAL_GATE,GRAND_MARKET,ROYAL_ROAD,CIVIC_MONUMENT;

    public double baseCost(){
        return switch(this){
            case PALACE,CATHEDRAL,UNIVERSITY -> 900;
            case AQUEDUCT,CANAL,GRAND_BRIDGE,HARBOR_EXPANSION,FORTRESS -> 700;
            case CITY_WALLS,MONUMENTAL_GATE,GRAND_MARKET,ROYAL_ROAD -> 520;
            case CIVIC_MONUMENT -> 360;
        };
    }
    public double foodNeed(){return baseCost()*.02;}
    public double timberNeed(){return baseCost()*(this==ROYAL_ROAD||this==GRAND_BRIDGE?.12:.08);}
    public double stoneNeed(){return baseCost()*(this==CATHEDRAL||this==CITY_WALLS||this==FORTRESS||this==MONUMENTAL_GATE?.14:.09);}
    public double ironNeed(){return baseCost()*(this==FORTRESS||this==HARBOR_EXPANSION||this==GRAND_BRIDGE?.05:.025);}
    public int laborNeed(){return (int)Math.round(baseCost()*.08);}
}
