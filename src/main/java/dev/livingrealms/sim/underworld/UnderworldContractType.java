package dev.livingrealms.sim.underworld;

import dev.livingrealms.sim.law.CrimeType;
import java.util.Objects;

/** Underworld job kinds — each maps to a real {@link CrimeType} that must be committed to complete. */
public enum UnderworldContractType {
    THEFT(CrimeType.THEFT),
    BURGLARY(CrimeType.BURGLARY),
    SABOTAGE(CrimeType.SABOTAGE),
    SMUGGLING(CrimeType.SMUGGLING),
    CARAVAN_HEIST(CrimeType.ROBBERY),
    ASSASSINATION(CrimeType.MURDER);

    private final CrimeType crimeType;

    UnderworldContractType(CrimeType crimeType) {
        this.crimeType = Objects.requireNonNull(crimeType);
    }

    public CrimeType crimeType() {
        return crimeType;
    }

    public boolean depositsStolenGoods() {
        return this == THEFT || this == BURGLARY || this == SMUGGLING || this == CARAVAN_HEIST;
    }
}
