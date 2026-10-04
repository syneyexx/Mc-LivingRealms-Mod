package dev.livingrealms.sim.player;

import dev.livingrealms.sim.faction.Army;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.government.GrandProject;
import dev.livingrealms.sim.government.GrandProjectType;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.WorldEvent;
import java.util.Objects;

/**
 * Server-authoritative unlocks for player institutional influence.
 * Reputation remains separate; these actions spend/require influence with the matching institution.
 */
public final class PlayerInfluenceActions {
    private PlayerInfluenceActions() {}

    public enum Unlock {
        REQUEST_AUDIENCE(InfluenceInstitution.CROWN, 20),
        PROPOSE_PROJECT(InfluenceInstitution.CROWN, 35),
        REQUEST_MILITARY_SUPPORT(InfluenceInstitution.MILITARY, 40),
        PETITION_TRADE(InfluenceInstitution.MERCHANTS, 25),
        PETITION_CLERGY(InfluenceInstitution.CLERGY, 25);

        private final InfluenceInstitution institution;
        private final double threshold;

        Unlock(InfluenceInstitution institution, double threshold) {
            this.institution = institution;
            this.threshold = threshold;
        }

        public InfluenceInstitution institution() { return institution; }
        public double threshold() { return threshold; }
    }

    public static boolean can(PlayerStanding standing, long factionId, Unlock unlock) {
        Objects.requireNonNull(standing, "standing");
        Objects.requireNonNull(unlock, "unlock");
        if (factionId <= 0) return false;
        if (unlock == Unlock.PROPOSE_PROJECT) {
            return standing.influenceWith(factionId, InfluenceInstitution.CROWN) >= Unlock.PROPOSE_PROJECT.threshold()
                    || standing.influenceWith(factionId, InfluenceInstitution.SCHOLARS) >= 30;
        }
        return standing.influenceWith(factionId, unlock.institution()) >= unlock.threshold();
    }

    public static Result apply(SimulationState state, String actorKey, long factionId, Unlock unlock) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(unlock, "unlock");
        if (actorKey == null || actorKey.isBlank() || factionId <= 0) return Result.fail("invalid_actor");
        PlayerStanding standing = state.findPlayerStanding(actorKey).orElse(null);
        if (standing == null) return Result.fail("no_standing");
        Faction faction = state.findFaction(factionId).orElse(null);
        if (faction == null) return Result.fail("faction_missing");
        if (!can(standing, factionId, unlock)) return Result.fail("insufficient_influence");

        return switch (unlock) {
            case REQUEST_AUDIENCE -> {
                standing.adjustInfluence(factionId, InfluenceInstitution.CROWN, -2);
                standing.adjustReputation(factionId, 1.5);
                faction.government().adjustLegitimacy(.002);
                state.history().add(new WorldEvent(state.clock().day(), "player_audience",
                        "actor=" + actorKey + ", faction=" + factionId));
                yield Result.ok("audience_granted");
            }
            case PROPOSE_PROJECT -> {
                if (state.grandProjects().stream().anyMatch(p -> p.active() && p.sponsorFactionId() == factionId)) {
                    yield Result.fail("project_already_active");
                }
                if (state.grandProjects().size() >= SimulationState.MAX_GRAND_PROJECTS) {
                    yield Result.fail("project_cap");
                }
                Settlement site = faction.settlements().stream()
                        .max(java.util.Comparator.comparingInt(Settlement::population)).orElse(null);
                if (site == null || site.population() < 80) yield Result.fail("no_suitable_site");
                if (faction.treasury() < 120) yield Result.fail("treasury_low");
                GrandProjectType type = standing.influenceWith(factionId, InfluenceInstitution.SCHOLARS)
                        >= standing.influenceWith(factionId, InfluenceInstitution.CROWN)
                        ? GrandProjectType.UNIVERSITY : GrandProjectType.CIVIC_MONUMENT;
                GrandProject project = new GrandProject(state.nextId(), factionId, site.id(), type, state.clock().day());
                state.addGrandProject(project);
                standing.adjustInfluence(factionId, InfluenceInstitution.CROWN, -6);
                standing.adjustInfluence(factionId, InfluenceInstitution.SCHOLARS, -2);
                standing.grantCareerService(CareerTrack.POLITICAL, 25);
                state.history().add(new WorldEvent(state.clock().day(), "player_proposed_project",
                        "actor=" + actorKey + ", faction=" + factionId + ", type=" + type + ", settlement=" + site.id()));
                yield Result.ok("project_proposed");
            }
            case REQUEST_MILITARY_SUPPORT -> {
                Army army = faction.armies().stream().findFirst().orElse(null);
                if (army == null) yield Result.fail("no_army");
                army.adjustMorale(.04);
                standing.adjustInfluence(factionId, InfluenceInstitution.MILITARY, -5);
                standing.grantCareerService(CareerTrack.MILITARY, 20);
                state.history().add(new WorldEvent(state.clock().day(), "player_military_support",
                        "actor=" + actorKey + ", faction=" + factionId + ", army=" + army.id()));
                yield Result.ok("military_support");
            }
            case PETITION_TRADE -> {
                boolean improved = false;
                for (TransportRoute route : state.routes()) {
                    if (route.ownerFactionId() != factionId) continue;
                    route.adjustSecurity(.05);
                    improved = true;
                    break;
                }
                standing.adjustInfluence(factionId, InfluenceInstitution.MERCHANTS, -4);
                standing.grantCareerService(CareerTrack.ECONOMIC, 18);
                if (!improved) {
                    for (Settlement settlement : faction.settlements()) {
                        settlement.adjustProsperity(.01);
                        break;
                    }
                }
                state.history().add(new WorldEvent(state.clock().day(), "player_trade_petition",
                        "actor=" + actorKey + ", faction=" + factionId + ", route_improved=" + improved));
                yield Result.ok(improved ? "trade_security_improved" : "trade_petition_noted");
            }
            case PETITION_CLERGY -> {
                standing.adjustInfluence(factionId, InfluenceInstitution.CLERGY, -3);
                standing.grantCareerService(CareerTrack.RELIGIOUS, 16);
                var civ = state.ensureFactionCivilization(factionId);
                civ.approach(civ.culturalInfluence(), Math.min(1, civ.religiousInfluence() + .015),
                        civ.education(), civ.propaganda(), civ.intelligence(), .01);
                for (Settlement settlement : faction.settlements()) {
                    settlement.adjustUnrest(-.01);
                    break;
                }
                state.history().add(new WorldEvent(state.clock().day(), "player_clergy_petition",
                        "actor=" + actorKey + ", faction=" + factionId));
                yield Result.ok("clergy_petition");
            }
        };
    }

    public record Result(boolean success, boolean dirty, String reason) {
        public static Result ok(String reason) { return new Result(true, true, reason == null ? "" : reason); }
        public static Result fail(String reason) { return new Result(false, false, reason == null ? "" : reason); }
    }
}
