package dev.livingrealms.sim.aviation;

/** Technology-agnostic canonical aircraft classes. Physical addons may map them to custom models. */
public enum AircraftModel {
    LIGHT_FIGHTER(AircraftRole.FIGHTER,900,1400,1.0,.75,0),
    HEAVY_FIGHTER(AircraftRole.INTERCEPTOR,820,1700,1.25,.95,0),
    TACTICAL_BOMBER(AircraftRole.BOMBER,650,2200,1.55,.55,4),
    GROUND_ATTACK(AircraftRole.ATTACK,580,1200,1.35,.65,1),
    TRANSPORT(AircraftRole.TRANSPORT,520,2800,.25,.45,18),
    RECON(AircraftRole.RECON,1050,2500,.15,.85,1);
    private final AircraftRole role;private final double speed;private final double range;private final double attack;private final double agility;private final double cargo;
    AircraftModel(AircraftRole role,double speed,double range,double attack,double agility,double cargo){this.role=role;this.speed=speed;this.range=range;this.attack=attack;this.agility=agility;this.cargo=cargo;}
    public AircraftRole role(){return role;}public double speed(){return speed;}public double range(){return range;}public double attack(){return attack;}public double agility(){return agility;}public double cargo(){return cargo;}
}
