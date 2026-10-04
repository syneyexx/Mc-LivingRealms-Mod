package dev.livingrealms.sim.society;

import dev.livingrealms.sim.civilization.AssistanceTask;
import dev.livingrealms.sim.civilization.AssistanceTaskType;
import dev.livingrealms.sim.civilization.EpidemicRecord;
import dev.livingrealms.sim.civilization.SettlementCivilizationState;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.military.SiegeState;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.world.SimulationState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Turns canonical pressures into short player-facing causal explanations.
 * Does not invent causes — only reports measurable simulation state.
 */
public final class WorldCauseExplainer {
    private WorldCauseExplainer() {}

    public static String settlementPressureCause(SimulationState state, Faction faction, Settlement settlement) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        SocietyAssessment assessment = SocietyDiagnostics.assess(faction, settlement);
        return switch (assessment.primaryPressure()) {
            case FOOD -> foodCause(state, faction, settlement, assessment);
            case HOUSING -> housingCause(settlement);
            case SAFETY -> safetyCause(state, faction, settlement);
            case EMPLOYMENT -> employmentCause(assessment);
            case GOODS -> goodsCause(state, faction, settlement);
            case BALANCED -> "Conditions are stable enough that no single shortage dominates.";
        };
    }

    public static String constructionPauseCause(Faction faction, Settlement settlement) {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        if (settlement.stockpile().get(ResourceType.WOOD) < 8 && faction.stockpile().get(ResourceType.WOOD) < 20) {
            return "Construction paused: insufficient timber in local stockpile and treasury.";
        }
        if (settlement.stockpile().get(ResourceType.STONE) < 6 && faction.stockpile().get(ResourceType.STONE) < 16) {
            return "Construction paused: stone and masonry stores are too low.";
        }
        if (faction.treasury() < 25) {
            return "Construction paused: the treasury cannot fund wages and materials.";
        }
        if (settlement.unrest() > 0.72) {
            return "Construction slowed: unrest keeps builders off site.";
        }
        return "Construction continues when sites, labor and materials are available.";
    }

    public static String crimeCause(Settlement settlement, SocietyAssessment assessment, double banditPressure) {
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(assessment, "assessment");
        List<String> parts = new ArrayList<>();
        if (assessment.needs().employment() < 0.55) parts.add("unemployment");
        if (assessment.needs().food() < 0.5) parts.add("food scarcity");
        if (assessment.needs().safety() < 0.55) parts.add("weak enforcement");
        if (banditPressure > 0.45) parts.add("bandit activity");
        if (settlement.unrest() > 0.55) parts.add("unrest");
        if (parts.isEmpty()) return "Crime pressure is low while work, food and order hold.";
        return "Crime pressure rises from " + join(parts) + ".";
    }

    public static List<String> activeAssistanceReasons(SimulationState state, Settlement settlement) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(settlement, "settlement");
        return state.assistanceTasks().stream()
                .filter(AssistanceTask::active)
                .filter(t -> t.settlementId() == settlement.id())
                .sorted(Comparator.comparingDouble(AssistanceTask::remainingPressure).reversed())
                .limit(3)
                .map(WorldCauseExplainer::taskReason)
                .toList();
    }

    private static String foodCause(SimulationState state, Faction faction, Settlement settlement, SocietyAssessment assessment) {
        if (underSiege(state, settlement)) {
            return "Food prices and hunger rise because " + settlement.name() + " is under siege.";
        }
        if (hasEpidemic(state, settlement)) {
            return "Food security is strained while disease keeps fields and markets quiet.";
        }
        long blocked = state.routes().stream()
                .filter(r -> r.fromSettlementId() == settlement.id() || r.toSettlementId() == settlement.id())
                .filter(r -> r.security() < 0.35 || !r.operational())
                .count();
        if (blocked > 0) {
            return "Food prices are high because " + blocked + " nearby trade route"
                    + (blocked == 1 ? " is" : "s are") + " unsafe or inactive.";
        }
        boolean inboundShort = state.shipments().stream()
                .noneMatch(s -> !s.arrived() && nearDestination(s, settlement));
        if (inboundShort && settlement.stockpile().get(ResourceType.FOOD) < settlement.population() * 0.4) {
            return "Granaries are thin and few inbound food shipments are on the road.";
        }
        if (assessment.needs().food() < 0.4) {
            return "Harvest and local stores cannot cover current population demand.";
        }
        return "Food pressure is elevated relative to local supply days.";
    }

    private static String housingCause(Settlement settlement) {
        int shortage = Math.max(0, settlement.population() - settlement.housing());
        if (shortage > 40) {
            return "Housing is short by about " + shortage + " people; new wards are still under construction.";
        }
        if (shortage > 0) {
            return "Population outgrew finished homes; denser housing is being planned.";
        }
        return "Housing satisfaction is low even though capacity looks adequate — crowding and quality drag it down.";
    }

    private static String safetyCause(SimulationState state, Faction faction, Settlement settlement) {
        if (underSiege(state, settlement)) {
            return "Public order collapses while the settlement is besieged.";
        }
        SettlementCivilizationState civ = state.ensureSettlementCivilization(settlement.id(), faction.id());
        if (civ.banditPressure() > 0.55) {
            return "Bandit pressure and weak patrols are undermining public order.";
        }
        if (faction.government().lawEnforcement() < 0.4) {
            return "Law enforcement capacity is too low for the current unrest.";
        }
        return "Safety suffers from unrest and insufficient guard coverage.";
    }

    private static String employmentCause(SocietyAssessment assessment) {
        if (assessment.needs().employment() < 0.45) {
            return "Too few workplaces relative to working-age population; workshops and fields are saturated.";
        }
        return "Employment is the weakest need even though other conditions are closer to balance.";
    }

    private static String goodsCause(SimulationState state, Faction faction, Settlement settlement) {
        long insecure = state.routes().stream()
                .filter(r -> r.fromSettlementId() == settlement.id() || r.toSettlementId() == settlement.id())
                .filter(r -> r.security() < 0.4)
                .count();
        if (insecure > 0) {
            return "Tools and textiles are scarce because insecure routes disrupt manufactured goods.";
        }
        if (faction.stockpile().get(ResourceType.TOOLS) + settlement.stockpile().get(ResourceType.TOOLS) < 12) {
            return "Craft goods are scarce: tool stocks are nearly exhausted.";
        }
        return "Market goods access is the binding constraint on local prosperity.";
    }

    private static String taskReason(AssistanceTask task) {
        return switch (task.type()) {
            case FOOD_RELIEF -> "Food relief requested after shortage pressure.";
            case MEDICAL_AID -> "Medical aid requested during disease or clinic strain.";
            case HOUSING_SUPPLIES -> "Housing supplies requested under shelter pressure.";
            case SECURITY_SUPPORT -> "Security support requested against bandits or unrest.";
            case REFUGEE_SUPPORT -> "Refugee support requested for displaced arrivals.";
            case WATER_SUPPLY -> "Water supply aid requested under sanitation or drought pressure.";
            case TRADE_ESCORT -> "Trade escort requested after route insecurity.";
            case INFRASTRUCTURE_REPAIR -> "Infrastructure repair requested after route wear or damaged industry.";
            case BANDIT_BOUNTY -> "Bandit bounty opened after rising outlaw pressure.";
            case BRIDGE_REPAIR -> "Bridge repair requested for river access.";
            case MILITARY_SUPPLY -> "Military supply requested while the realm is at war.";
            case RECONSTRUCTION_AID -> "Reconstruction aid requested after unrest and infrastructure loss.";
            case MISSING_CARAVAN -> "Missing caravan investigation after trade interception.";
        };
    }

    private static boolean underSiege(SimulationState state, Settlement settlement) {
        for (SiegeState siege : state.sieges()) {
            if (siege.settlementId() == settlement.id() && siege.active()) return true;
        }
        return false;
    }

    private static boolean hasEpidemic(SimulationState state, Settlement settlement) {
        for (EpidemicRecord epidemic : state.epidemics()) {
            if (epidemic.settlementId() == settlement.id() && epidemic.active()) return true;
        }
        return false;
    }

    private static boolean nearDestination(TradeShipment shipment, Settlement settlement) {
        double dx = shipment.destination().x() - settlement.position().x();
        double dz = shipment.destination().z() - settlement.position().z();
        return dx * dx + dz * dz <= 48 * 48;
    }

    private static String join(List<String> parts) {
        if (parts.size() == 1) return parts.get(0);
        if (parts.size() == 2) return parts.get(0) + " and " + parts.get(1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(i == parts.size() - 1 ? ", and " : ", ");
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    public static String debtCause(SimulationState state, Faction faction) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(faction, "faction");
        long active = state.debts().stream().filter(d -> d.active() && d.debtorFactionId() == faction.id()).count();
        long defaults = state.debts().stream().filter(d -> d.defaulted() && d.debtorFactionId() == faction.id()).count();
        if (defaults > 0) return "Sovereign default has weakened legitimacy and merchant confidence.";
        if (active > 0 && faction.treasury() < 80) return "Debt service is straining a thin treasury.";
        if (active > 0) return "The realm carries " + active + " active sovereign debt instrument" + (active == 1 ? "" : "s") + ".";
        return "The treasury is not currently bound by active sovereign debt.";
    }

    public static String grandProjectCause(SimulationState state, Faction faction) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(faction, "faction");
        var project = state.grandProjects().stream().filter(p -> p.active() && p.sponsorFactionId() == faction.id()).findFirst().orElse(null);
        if (project == null) return "No active grand project is consuming treasury and labor.";
        if (!project.pauseReason().isBlank()) return "The " + project.type().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " is paused: " + project.pauseReason() + ".";
        return "The " + project.type().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " advances with available materials and labor.";
    }

    public static String campaignCause(SimulationState state, Faction faction) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(faction, "faction");
        var plan = state.campaignPlans().stream().filter(p -> p.active() && p.factionId() == faction.id())
                .max(Comparator.comparingInt(dev.livingrealms.sim.military.CampaignPlan::priority)).orElse(null);
        if (plan == null) return "No active wartime campaign plan directs the field armies.";
        return "Campaign posture is " + plan.type().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " at priority " + plan.priority() + ".";
    }

    /** Compact cause for dashboard packets (bounded length). */
    public static String compact(String cause) {
        if (cause == null || cause.isBlank()) return "";
        String trimmed = cause.trim();
        if (trimmed.length() <= 140) return trimmed;
        return trimmed.substring(0, 137) + "...";
    }
}
