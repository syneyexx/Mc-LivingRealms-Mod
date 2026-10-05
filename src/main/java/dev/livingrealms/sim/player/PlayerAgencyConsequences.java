package dev.livingrealms.sim.player;

import dev.livingrealms.sim.civilization.AssistanceTask;
import dev.livingrealms.sim.civilization.AssistanceTaskType;
import dev.livingrealms.sim.civilization.SettlementCivilizationState;
import dev.livingrealms.sim.construction.RegisteredPlayerStructure;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.law.CrimeIncident;
import dev.livingrealms.sim.law.CrimeType;
import dev.livingrealms.sim.social.CitizenMemory;
import dev.livingrealms.sim.social.MemoryType;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimulationState;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Wires observable feedback loops for key player actions that already exist:
 * aid, crime, and building register/demolish. Does not invent new verbs —
 * only connects stock, mood, rumor, memory, reputation, and migration pressure.
 */
public final class PlayerAgencyConsequences {
    private static final double MEMORY_RADIUS = 220.0;

    private PlayerAgencyConsequences() {}

    /** After verified aid: mood, NPC gratitude, rumor subject, migration pressure softener. */
    public static void onAssistance(SimulationState state, String actorKey, Settlement settlement,
                                    Faction faction, AssistanceTask task, double relief) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(task, "task");
        if (actorKey == null || actorKey.isBlank()) return;
        double r = Mathx.clamp(relief, 0, 1);
        SettlementCivilizationState civ = state.ensureSettlementCivilization(settlement.id(), faction.id());
        // Aid lowers outbound migration pressure (food/refugee/bandit) beyond the primary relief path.
        if (task.type() == AssistanceTaskType.FOOD_RELIEF || task.type() == AssistanceTaskType.REFUGEE_SUPPORT) {
            civ.adjustRefugeePressure(-r * .35);
        }
        if (task.type() == AssistanceTaskType.SECURITY_SUPPORT || task.type() == AssistanceTaskType.BANDIT_BOUNTY) {
            civ.adjustBanditPressure(-r * .2);
        }
        settlement.adjustUnrest(-r * .08);
        String summary = "A traveler brought " + task.type().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                + " to " + settlement.name() + ".";
        seedSettlementMemory(state, settlement.id(), MemoryType.HELPED_BY, "aid:" + task.id(),
                actorKey, summary, settlement.position(), .45 + r * .4, .75 + r * .15, 6);
        bumpCitizenMood(state, settlement.id(), r * .12, r * .08, r * .05);
    }

    /**
     * After a registered crime: property loss from nearest settlement stock, public-order hit,
     * victim/witness memories, and black-market provenance already handled by UnderworldActions.
     */
    public static void onCrime(SimulationState state, CrimeIncident incident) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(incident, "incident");
        Faction jurisdiction = state.findFaction(incident.jurisdictionFactionId()).orElse(null);
        if (jurisdiction == null) return;
        Settlement nearest = jurisdiction.settlements().stream()
                .min(Comparator.comparingDouble(s -> s.position().distanceTo(incident.position())))
                .orElse(null);
        if (nearest == null) return;
        applyPropertyLoss(nearest, incident);
        nearest.setPublicOrder(Math.max(0, nearest.publicOrder() - Math.min(.12, incident.type().notoriety() * .004
                + (incident.witnessed() ? .02 : 0))));
        nearest.adjustUnrest(Math.min(.08, incident.type().notoriety() * .0025));
        if (!incident.victimKey().isBlank()) {
            seedVictimMemory(state, nearest, incident);
        }
        if (incident.witnessed() && incident.witnessCount() > 0) {
            String summary = "Someone saw a " + incident.type().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                    + " near " + nearest.name() + ".";
            seedSettlementMemory(state, nearest.id(), MemoryType.CRIME_WITNESS, "crime:" + incident.id(),
                    "witness", summary, incident.position(), .55 + Math.min(.3, incident.witnessCount() * .04),
                    .7, Math.min(8, 2 + incident.witnessCount()));
            bumpCitizenMood(state, nearest.id(), -0.04, -0.1, -0.02);
        }
    }

    /** Building registered: housing already reconciled; NPCs notice capacity and migration eases. */
    public static void onBuildingRegistered(SimulationState state, Settlement settlement, Faction faction,
                                            RegisteredPlayerStructure.Role role, int capacity) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(settlement, "settlement");
        if (faction != null) {
            SettlementCivilizationState civ = state.ensureSettlementCivilization(settlement.id(), faction.id());
            if (role == RegisteredPlayerStructure.Role.HOUSE || capacity > 0) {
                civ.adjustRefugeePressure(-Math.min(.12, capacity * .008));
            }
        }
        settlement.adjustUnrest(-.015);
        String summary = "New " + role.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                + " work finished in " + settlement.name()
                + (capacity > 0 ? " (+" + capacity + " capacity)." : ".");
        seedSettlementMemory(state, settlement.id(), MemoryType.LOCAL_EVENT, "structure:" + role.name(),
                "town works", summary, settlement.position(), .42, .8, 5);
        bumpCitizenMood(state, settlement.id(), .04, .03, .06);
    }

    /** Demolish / invalidate: capacity already reconciled; NPCs grumble and migration pressure rises. */
    public static void onBuildingInvalidated(SimulationState state, Settlement settlement, Faction faction,
                                             RegisteredPlayerStructure.Role role) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(settlement, "settlement");
        if (faction != null) {
            SettlementCivilizationState civ = state.ensureSettlementCivilization(settlement.id(), faction.id());
            if (role == RegisteredPlayerStructure.Role.HOUSE) {
                civ.adjustRefugeePressure(.06);
            }
        }
        String summary = "A " + role.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                + " in " + settlement.name() + " was lost or condemned.";
        seedSettlementMemory(state, settlement.id(), MemoryType.LOCAL_EVENT, "structure_lost:" + role.name(),
                "town works", summary, settlement.position(), .5, .78, 5);
        bumpCitizenMood(state, settlement.id(), -.03, -.05, -.08);
    }

    private static void applyPropertyLoss(Settlement settlement, CrimeIncident incident) {
        CrimeType type = incident.type();
        boolean loot = type == CrimeType.THEFT || type == CrimeType.BURGLARY || type == CrimeType.ROBBERY
                || type == CrimeType.SMUGGLING || type == CrimeType.POACHING;
        if (!loot || incident.stolenOrDamageValue() <= 0) return;
        ResourceType resource = switch (type) {
            case POACHING -> ResourceType.FOOD;
            case SMUGGLING -> ResourceType.TEXTILES;
            case BURGLARY -> ResourceType.WOOD;
            case ROBBERY -> ResourceType.IRON;
            default -> ResourceType.FOOD;
        };
        double loss = Math.min(incident.stolenOrDamageValue() * .35, 40);
        settlement.stockpile().take(resource, loss);
        if (resource == ResourceType.FOOD) {
            settlement.setFoodSecurity(Math.max(0, settlement.foodSecurity() - Math.min(.08, loss * .002)));
        }
    }

    private static void seedVictimMemory(SimulationState state, Settlement settlement, CrimeIncident incident) {
        // Prefer a citizen whose key-ish name matches; else any local trader/official as proxy victim.
        List<SocialCitizen> locals = state.socialCitizens().stream()
                .filter(SocialCitizen::alive)
                .filter(c -> c.settlementId() == settlement.id())
                .toList();
        if (locals.isEmpty()) return;
        SocialCitizen victim = locals.stream()
                .filter(c -> incident.victimKey().contains(c.name())
                        || ("citizen:" + c.id()).equals(incident.victimKey()))
                .findFirst()
                .orElse(locals.stream()
                        .filter(c -> c.role().name().contains("TRADER") || c.role().name().contains("OFFICIAL"))
                        .findFirst()
                        .orElse(locals.getFirst()));
        String summary = "I was the victim of a " + incident.type().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                + " (loss ~" + Math.round(incident.stolenOrDamageValue()) + ").";
        victim.remember(new CitizenMemory(incident.day(), MemoryType.STOLEN_FROM, "crime:" + incident.id(),
                incident.actorKey(), summary, incident.position(), .7, .95));
        victim.needs().approach(victim.needs().hunger(), Math.max(0, victim.needs().safety() - .15),
                victim.needs().social(), Math.max(0, victim.needs().status() - .08),
                victim.needs().comfort(), .35);
    }

    private static void seedSettlementMemory(SimulationState state, long settlementId, MemoryType type,
                                             String subject, String source, String summary,
                                             dev.livingrealms.sim.world.SimPosition pos,
                                             double importance, double confidence, int limit) {
        List<SocialCitizen> locals = state.socialCitizens().stream()
                .filter(SocialCitizen::alive)
                .filter(c -> c.settlementId() == settlementId)
                .sorted(Comparator.comparingLong(SocialCitizen::id))
                .limit(Math.max(1, limit))
                .toList();
        long day = state.clock().day();
        for (SocialCitizen c : locals) {
            if (pos != null && c.settlementId() == settlementId
                    && state.findSettlement(settlementId)
                    .map(s -> s.position().distanceTo(pos) > MEMORY_RADIUS).orElse(false)) {
                continue;
            }
            if (c.latestMemory(m -> m.subjectKey().equals(subject) && m.day() >= day - 2).isPresent()) continue;
            c.remember(new CitizenMemory(day, type, subject, source, summary, pos, importance, confidence));
        }
    }

    private static void bumpCitizenMood(SimulationState state, long settlementId,
                                        double statusDelta, double safetyDelta, double comfortDelta) {
        for (SocialCitizen c : state.socialCitizens()) {
            if (!c.alive() || c.settlementId() != settlementId) continue;
            c.needs().approach(
                    c.needs().hunger(),
                    Mathx.clamp(c.needs().safety() + safetyDelta, 0, 1),
                    c.needs().social(),
                    Mathx.clamp(c.needs().status() + statusDelta, 0, 1),
                    Mathx.clamp(c.needs().comfort() + comfortDelta, 0, 1),
                    .25);
        }
    }
}
