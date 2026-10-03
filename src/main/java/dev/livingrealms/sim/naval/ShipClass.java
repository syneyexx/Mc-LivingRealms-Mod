package dev.livingrealms.sim.naval;

/** Strategic ship classes. Values are simulation units, not real-world measurements. */
public enum ShipClass {
    PATROL_BOAT(.25, 8, 4, 32, 160, 0, 8, 4, 4),
    CORVETTE(.45, 18, 12, 28, 240, 0, 20, 12, 8),
    FRIGATE(.70, 34, 26, 24, 360, 0, 42, 28, 16),
    DESTROYER(1.00, 58, 42, 22, 430, 0, 70, 48, 24),
    CRUISER(1.30, 95, 88, 18, 520, 0, 120, 90, 40),
    CARGO_SHIP(.35, 3, 18, 16, 600, 900, 48, 18, 12),
    LANDING_SHIP(.65, 12, 30, 15, 420, 260, 65, 38, 18);

    private final double techRequired;
    private final double attack;
    private final double defense;
    private final double speed;
    private final double range;
    private final int cargoCapacity;
    private final double machineryCost;
    private final double ironCost;
    private final double fuelCapacity;

    ShipClass(double techRequired,double attack,double defense,double speed,double range,int cargoCapacity,double machineryCost,double ironCost,double fuelCapacity){
        this.techRequired=techRequired;this.attack=attack;this.defense=defense;this.speed=speed;this.range=range;this.cargoCapacity=cargoCapacity;this.machineryCost=machineryCost;this.ironCost=ironCost;this.fuelCapacity=fuelCapacity;
    }
    public double techRequired(){return techRequired;} public double attack(){return attack;} public double defense(){return defense;} public double speed(){return speed;} public double range(){return range;} public int cargoCapacity(){return cargoCapacity;} public double machineryCost(){return machineryCost;} public double ironCost(){return ironCost;} public double fuelCapacity(){return fuelCapacity;}
}
