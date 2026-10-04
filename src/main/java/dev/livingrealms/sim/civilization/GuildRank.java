package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.social.SocialCitizen;

/**
 * Medieval craft ranks derived from {@link SocialCitizen#professionSkill()} — no extra save field.
 * Apprentice → journeyman → master; masters mentor and earn higher wages.
 */
public enum GuildRank {
    APPRENTICE("apprentice", 0.0, 0.50),
    JOURNEYMAN("journeyman", 0.50, 0.75),
    MASTER("master", 0.75, 1.01);

    private final String key;
    private final double minSkill;
    private final double maxSkillExclusive;

    GuildRank(String key, double minSkill, double maxSkillExclusive) {
        this.key = key;
        this.minSkill = minSkill;
        this.maxSkillExclusive = maxSkillExclusive;
    }

    public String key() { return key; }
    public double wageMultiplier() {
        return switch (this) {
            case APPRENTICE -> 0.55;
            case JOURNEYMAN -> 1.00;
            case MASTER -> 1.55;
        };
    }

    public static GuildRank of(double professionSkill) {
        if (professionSkill >= MASTER.minSkill) return MASTER;
        if (professionSkill >= JOURNEYMAN.minSkill) return JOURNEYMAN;
        return APPRENTICE;
    }

    public static GuildRank of(SocialCitizen citizen) {
        return of(citizen.professionSkill());
    }

    /** Roles that historically sit under a craft/trade guild. */
    public static boolean guildedRole(CitizenRole role) {
        return switch (role) {
            case ARTISAN, BUILDER, CARPENTER, BUTCHER, TRADER, MINER, LUMBERJACK,
                    FISHER, SAILOR, DOCKWORKER, HEALER, SCHOLAR, TEACHER -> true;
            default -> false;
        };
    }

    public String titleFor(CitizenRole role) {
        String craft = role.name().toLowerCase().replace('_', ' ');
        return key + " " + craft;
    }
}
