package dev.livingrealms.sim.society;

import dev.livingrealms.sim.civilian.CitizenRole;

/** Coarse societal professions used by diagnostics and presentation. Maps from CitizenRole. */
public enum Profession {
    FARMER, FORESTER, MINER, FISHER, BUILDER, ARTISAN, MACHINIST, MERCHANT, SOLDIER, GUARD, SCHOLAR, HEALER, PILOT, SAILOR, UNEMPLOYED;

    public static Profession fromRole(CitizenRole role) {
        if (role == null) return UNEMPLOYED;
        return switch (role) {
            case FARMER -> FARMER;
            case LUMBERJACK, CARPENTER -> FORESTER;
            case MINER -> MINER;
            case FISHER, DOCKWORKER -> FISHER;
            case BUILDER -> BUILDER;
            case ARTISAN, BUTCHER -> ARTISAN;
            case TRADER -> MERCHANT;
            case GUARD, SPY -> GUARD;
            case SCHOLAR, TEACHER, OFFICIAL -> SCHOLAR;
            case HEALER, PRIEST -> HEALER;
            case SAILOR -> SAILOR;
            case HUNTER -> SOLDIER;
        };
    }
}
