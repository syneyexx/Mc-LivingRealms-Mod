package dev.livingrealms.sim.underworld;

import dev.livingrealms.sim.util.Mathx;

/** Persistent underworld standing for a player/NPC actor — contracts, street cred, bribery. */
public final class UnderworldProfile {
    private final String actorKey;
    private int contractsCompleted;
    private double streetCred;
    private double briberySkill;
    private long lastContractDay = -1;
    private long lastBribeDay = -1;
    private boolean blackMarketAccess;

    public UnderworldProfile(String actorKey) {
        if (actorKey == null || actorKey.isBlank()) throw new IllegalArgumentException("actorKey");
        this.actorKey = actorKey;
    }

    public String actorKey() { return actorKey; }
    public int contractsCompleted() { return contractsCompleted; }
    public double streetCred() { return streetCred; }
    public double briberySkill() { return briberySkill; }
    public long lastContractDay() { return lastContractDay; }
    public long lastBribeDay() { return lastBribeDay; }
    public boolean blackMarketAccess() { return blackMarketAccess; }

    public boolean isBlackMarketEligible() {
        return blackMarketAccess || contractsCompleted >= 2 || streetCred >= 18;
    }

    public void recordContract(long day, double credGain) {
        contractsCompleted = Math.max(0, contractsCompleted + 1);
        streetCred = Mathx.clamp(streetCred + Math.max(0, credGain), 0, 100);
        lastContractDay = Math.max(0, day);
        if (isBlackMarketEligible()) blackMarketAccess = true;
    }

    public void recordBribe(long day, double skillGain) {
        briberySkill = Mathx.clamp(briberySkill + Math.max(0, skillGain), 0, 100);
        streetCred = Mathx.clamp(streetCred + 1.5, 0, 100);
        lastBribeDay = Math.max(0, day);
        if (isBlackMarketEligible()) blackMarketAccess = true;
    }

    public void grantBlackMarketAccess() { blackMarketAccess = true; }

    public void restore(int contractsCompleted, double streetCred, double briberySkill,
                        long lastContractDay, long lastBribeDay, boolean blackMarketAccess) {
        this.contractsCompleted = Math.max(0, contractsCompleted);
        this.streetCred = Mathx.clamp(streetCred, 0, 100);
        this.briberySkill = Mathx.clamp(briberySkill, 0, 100);
        this.lastContractDay = lastContractDay;
        this.lastBribeDay = lastBribeDay;
        this.blackMarketAccess = blackMarketAccess;
    }
}
