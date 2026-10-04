package dev.livingrealms.sim.law;

/** Crimes understood by faction law. Values are strategic defaults, not currency-specific UI text. */
public enum CrimeType {
    TRESPASS(4, 2, false),
    POACHING(12, 6, false),
    THEFT(18, 8, false),
    BURGLARY(30, 14, false),
    ASSAULT(45, 18, true),
    ROBBERY(60, 25, true),
    SABOTAGE(75, 30, true),
    ARSON(100, 40, true),
    MURDER(250, 100, true),
    REGICIDE(1000, 250, true),
    WAR_CRIME(800, 180, true),
    /** Appended for schema-safe ordinal growth (old saves never wrote these indices). */
    SMUGGLING(40, 16, false),
    HERESY(55, 22, false);

    private final double baseBounty;
    private final double notoriety;
    private final boolean violent;
    CrimeType(double baseBounty,double notoriety,boolean violent){this.baseBounty=baseBounty;this.notoriety=notoriety;this.violent=violent;}
    public double baseBounty(){return baseBounty;} public double notoriety(){return notoriety;} public boolean violent(){return violent;}
}
