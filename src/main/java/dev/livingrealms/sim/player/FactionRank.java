package dev.livingrealms.sim.player;

/** Player rank inside a faction. OUTSIDER means there is no active membership. */
public enum FactionRank {
    OUTSIDER(0, -100),
    CITIZEN(0, 10),
    SOLDIER(100, 30),
    OFFICER(300, 55),
    NOBLE(1000, 80),
    RULER(Double.POSITIVE_INFINITY, 101);

    private final double serviceRequired;
    private final double reputationRequired;

    FactionRank(double serviceRequired,double reputationRequired){
        this.serviceRequired=serviceRequired;
        this.reputationRequired=reputationRequired;
    }

    public double serviceRequired(){return serviceRequired;}
    public double reputationRequired(){return reputationRequired;}
}
