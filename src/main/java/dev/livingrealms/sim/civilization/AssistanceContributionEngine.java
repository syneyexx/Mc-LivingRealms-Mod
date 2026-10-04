package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.industry.IndustrialSiteStatus;
import dev.livingrealms.sim.player.PlayerStanding;
import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.WorldEvent;
import java.util.Locale;
import java.util.Objects;

/**
 * Server-authoritative player aid against canonical assistance tasks.
 * Inventory verification stays in the Minecraft runtime; this engine only mutates simulation state
 * after a verified physical contribution is reported.
 */
public final class AssistanceContributionEngine {
    public static final double CONTRIBUTION_RADIUS = 96.0;
    public static final double PACKAGE_UNITS = 8.0;

    private AssistanceContributionEngine() {}

    public record Result(boolean success, boolean dirty, String reason, long taskId, AssistanceTaskStatus status, double relief) {
        public Result {
            reason = reason == null ? "" : reason;
            relief = Math.max(0, Double.isFinite(relief) ? relief : 0);
        }
    }

    public static ResourceType requiredResource(AssistanceTaskType type) {
        return switch (type) {
            case FOOD_RELIEF, REFUGEE_SUPPORT, MILITARY_SUPPLY -> ResourceType.FOOD;
            case MEDICAL_AID, WATER_SUPPLY -> ResourceType.TEXTILES;
            case HOUSING_SUPPLIES, BRIDGE_REPAIR -> ResourceType.WOOD;
            case SECURITY_SUPPORT, TRADE_ESCORT, BANDIT_BOUNTY, MISSING_CARAVAN -> ResourceType.IRON;
            case INFRASTRUCTURE_REPAIR, RECONSTRUCTION_AID -> ResourceType.STONE;
        };
    }

    public static Result contributeVerified(SimulationState state, String actorKey, SimPosition position, long taskId, double units) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(position, "position");
        if (actorKey == null || actorKey.isBlank()) return fail(taskId, "invalid_actor");
        if (!(units > 0) || !Double.isFinite(units) || units > 64) return fail(taskId, "invalid_units");
        AssistanceTask task = state.assistanceTasks().stream().filter(t -> t.id() == taskId).findFirst().orElse(null);
        if (task == null) return fail(taskId, "unknown_task");
        if (!task.active()) return fail(taskId, "task_closed");
        Settlement settlement = state.findSettlement(task.settlementId()).orElse(null);
        Faction faction = state.findFaction(task.factionId()).orElse(null);
        if (settlement == null || faction == null) return fail(taskId, "missing_settlement");
        if (position.distanceTo(settlement.position()) > CONTRIBUTION_RADIUS) return fail(taskId, "too_far");
        if (state.activeCustody(actorKey, faction.id()).isPresent()) return fail(taskId, "in_custody");

        ResourceType resource = requiredResource(task.type());
        double delivered = Math.min(units, PACKAGE_UNITS);
        settlement.stockpile().add(resource, delivered);
        faction.stockpile().add(resource, delivered * .25);

        SettlementCivilizationState civ = state.ensureSettlementCivilization(settlement.id(), faction.id());
        double relief = Mathx.clamp(delivered / (PACKAGE_UNITS * 4.0), .04, .28);
        applyRelief(task.type(), settlement, civ, state, relief);
        double pressure = currentPressure(task.type(), settlement, civ, state);
        task.updatePressure(state.clock().day(), pressure);

        PlayerStanding standing = state.playerStanding(actorKey);
        standing.adjustReputation(faction.id(), 2.5 + relief * 8);
        if (standing.isMemberOf(faction.id())) {
            try { state.grantFactionService(actorKey, faction.id(), 4 + relief * 20); }
            catch (RuntimeException ignored) { /* non-member path already handled */ }
        }

        state.history().add(new WorldEvent(state.clock().day(), "assistance_contribution",
                "task=" + task.id() + ", actor=" + actorKey + ", type=" + task.type()
                        + ", resource=" + resource + ", units=" + String.format(Locale.ROOT, "%.1f", delivered)
                        + ", status=" + task.status()));
        return new Result(true, true, "contributed", task.id(), task.status(), relief);
    }

    private static void applyRelief(AssistanceTaskType type, Settlement settlement, SettlementCivilizationState civ,
                                    SimulationState state, double relief) {
        switch (type) {
            case FOOD_RELIEF, MILITARY_SUPPLY -> {
                settlement.setFoodSecurity(Math.min(1, settlement.foodSecurity() + relief));
                settlement.adjustProsperity(relief * .15);
            }
            case MEDICAL_AID -> {
                civ.adjustDisease(-relief);
                civ.approach(Math.min(1, civ.sanitation() + relief * .35), civ.diseasePressure(), civ.education(),
                        civ.waterSecurity(), civ.refugeePressure(), civ.banditPressure(), civ.culturalCohesion(),
                        civ.assimilation(), civ.resourcePressure(), .12);
            }
            case SECURITY_SUPPORT, TRADE_ESCORT, BANDIT_BOUNTY, MISSING_CARAVAN -> {
                civ.adjustBanditPressure(-relief);
                settlement.setPublicOrder(Math.min(1, settlement.publicOrder() + relief * .65));
                state.routes().stream()
                        .filter(r -> r.fromSettlementId() == settlement.id() || r.toSettlementId() == settlement.id())
                        .forEach(r -> r.adjustSecurity(relief * .2));
            }
            case REFUGEE_SUPPORT -> {
                civ.adjustRefugeePressure(-relief);
                settlement.setFoodSecurity(Math.min(1, settlement.foodSecurity() + relief * .35));
            }
            case WATER_SUPPLY -> civ.approach(civ.sanitation(), civ.diseasePressure(), civ.education(),
                    Math.min(1, civ.waterSecurity() + relief), civ.refugeePressure(), civ.banditPressure(),
                    civ.culturalCohesion(), civ.assimilation(), civ.resourcePressure(), .18);
            case HOUSING_SUPPLIES -> {
                settlement.addHousing(Math.max(1, (int) Math.round(relief * 6)));
                settlement.adjustProsperity(relief * .08);
            }
            case INFRASTRUCTURE_REPAIR, BRIDGE_REPAIR, RECONSTRUCTION_AID -> {
                settlement.improveInfrastructure(relief * 2.5);
                settlement.adjustProsperity(relief * .1);
                settlement.adjustUnrest(-relief * .15);
                state.routes().stream()
                        .filter(r -> r.fromSettlementId() == settlement.id() || r.toSettlementId() == settlement.id())
                        .forEach(r -> {
                            r.improve(relief * .35);
                            if (!r.operational() && r.quality() > .35) r.setOperational(true);
                        });
                state.industrialSites().stream()
                        .filter(i -> i.settlementId() == settlement.id())
                        .forEach(i -> i.repair(relief * .45));
            }
        }
    }

    private static double currentPressure(AssistanceTaskType type, Settlement settlement, SettlementCivilizationState civ, SimulationState state) {
        return switch (type) {
            case FOOD_RELIEF, MILITARY_SUPPLY -> Mathx.clamp((.48 - settlement.foodSecurity()) / .48, 0, 1);
            case MEDICAL_AID -> Mathx.clamp(Math.max(civ.diseasePressure(), 1 - civ.sanitation()) - .34, 0, 1);
            case SECURITY_SUPPORT, BANDIT_BOUNTY -> Mathx.clamp(Math.max(civ.banditPressure(), 1 - settlement.publicOrder()) - .30, 0, 1);
            case REFUGEE_SUPPORT -> Mathx.clamp(civ.refugeePressure() - .28, 0, 1);
            case WATER_SUPPLY -> Mathx.clamp(.50 - civ.waterSecurity(), 0, 1) * 2;
            case HOUSING_SUPPLIES -> {
                double ratio = settlement.housing() <= 0 ? 1 : Mathx.clamp(settlement.population() / (double) settlement.housing(), 0, 2);
                yield Mathx.clamp(ratio - .84, 0, 1);
            }
            case TRADE_ESCORT, MISSING_CARAVAN -> {
                double insecure = state.routes().stream()
                        .filter(r -> r.operational())
                        .filter(r -> r.fromSettlementId() == settlement.id() || r.toSettlementId() == settlement.id())
                        .mapToDouble(r -> Math.max(0, .55 - r.security()))
                        .max().orElse(0);
                yield Mathx.clamp(insecure * 1.7, 0, 1);
            }
            case INFRASTRUCTURE_REPAIR, BRIDGE_REPAIR, RECONSTRUCTION_AID -> infrastructurePressure(state, settlement);
        };
    }

    public static double infrastructurePressure(SimulationState state, Settlement settlement) {
        double routeWear = state.routes().stream()
                .filter(r -> r.fromSettlementId() == settlement.id() || r.toSettlementId() == settlement.id())
                .mapToDouble(r -> Math.max(0, .55 - r.quality()) + (r.operational() ? 0 : .35))
                .max().orElse(0);
        double industryWear = state.industrialSites().stream()
                .filter(i -> i.settlementId() == settlement.id())
                .mapToDouble(i -> Math.max(0, .55 - i.condition())
                        + (i.status() == IndustrialSiteStatus.OFFLINE
                        || i.status() == IndustrialSiteStatus.DAMAGED
                        || i.status() == IndustrialSiteStatus.REPAIRING ? .4 : 0))
                .max().orElse(0);
        double infra = Math.max(0, .55 - settlement.infrastructure() / Math.max(1, settlement.population() * .02));
        return Mathx.clamp(Math.max(routeWear, Math.max(industryWear, infra)), 0, 1);
    }

    private static Result fail(long taskId, String reason) {
        return new Result(false, false, reason, taskId, AssistanceTaskStatus.CANCELLED, 0);
    }
}
