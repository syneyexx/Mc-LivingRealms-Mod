package dev.livingrealms.sim.presentation;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.SettlementPlanCache;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.economy.primary.PrimaryEconomyPlanner;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.presentation.CivicChoreographyPlanner.EventKind;
import dev.livingrealms.sim.presentation.CivicChoreographyPlanner.PresentationEvent;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Headless planner→runtime decision layer for civic choreography.
 *
 * <p>Selects bounded representative citizens and site destinations from
 * {@link CivicChoreographyPlanner} cues. Does not pathfind globally, mutate economy,
 * or claim physical work feeds the stockpile.
 */
public final class CivicChoreographyDirector {
    public static final int MAX_ACTIVE_EVENTS_PER_SETTLEMENT = 3;
    public static final int MAX_PARTICIPANTS_PER_EVENT = 6;
    public static final int MAX_PARTICIPANTS_PER_SETTLEMENT = 12;
    public static final long DISPATCH_COOLDOWN_TICKS = 200L;
    public static final double NEAR_PLAYER_RADIUS = 128.0D;

    public record LoadedCitizen(long citizenId, int projectionSlot, CitizenRole role) {
        public LoadedCitizen {
            Objects.requireNonNull(role, "role");
            if (projectionSlot < 0) throw new IllegalArgumentException("slot");
        }
    }

    public record Order(
            long settlementId,
            long factionId,
            long citizenId,
            int projectionSlot,
            EventKind kind,
            StructureRole siteRole,
            double targetX,
            double targetZ,
            double speed,
            String cue
    ) {
        public Order {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(siteRole, "siteRole");
            cue = cue == null ? "" : cue;
            speed = Double.isFinite(speed) ? Math.max(0.5, Math.min(1.2, speed)) : 0.9;
        }
    }

    /** Mutable cooldown book keyed by {@code settlementId:EventKind}. */
    public static final class CooldownBook {
        private final Map<String, Long> lastDispatch = new HashMap<>();

        public boolean ready(long settlementId, EventKind kind, long tick, long cooldownTicks) {
            Objects.requireNonNull(kind, "kind");
            String key = key(settlementId, kind);
            Long last = lastDispatch.get(key);
            return last == null || tick - last >= cooldownTicks;
        }

        public void mark(long settlementId, EventKind kind, long tick) {
            Objects.requireNonNull(kind, "kind");
            lastDispatch.put(key(settlementId, kind), tick);
        }

        public void clear() { lastDispatch.clear(); }

        public int size() { return lastDispatch.size(); }

        private static String key(long settlementId, EventKind kind) {
            return settlementId + ":" + kind.name();
        }
    }

    private CivicChoreographyDirector() {}

    public static List<Order> planOrders(
            SimulationState state,
            Faction faction,
            Settlement settlement,
            List<LoadedCitizen> loadedCitizens,
            long tick,
            CooldownBook cooldowns
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(loadedCitizens, "loadedCitizens");
        Objects.requireNonNull(cooldowns, "cooldowns");
        if (loadedCitizens.isEmpty()) return List.of();

        List<PresentationEvent> events = CivicChoreographyPlanner.planSettlement(state, faction, settlement);
        if (events.isEmpty()) return List.of();

        List<ConstructionIntent> completed = completedIntents(state, faction, settlement);
        List<Order> out = new ArrayList<>();
        Set<Integer> usedSlots = new HashSet<>();
        int eventsUsed = 0;

        for (PresentationEvent event : events) {
            if (eventsUsed >= MAX_ACTIVE_EVENTS_PER_SETTLEMENT) break;
            if (out.size() >= MAX_PARTICIPANTS_PER_SETTLEMENT) break;
            if (!cooldowns.ready(settlement.id(), event.kind(), tick, DISPATCH_COOLDOWN_TICKS)) continue;

            StructureRole siteRole = siteFor(event.kind());
            SimPosition target = resolveSite(settlement, completed, event.kind(), siteRole);
            if (target == null) target = settlement.position();

            EnumSet<CitizenRole> preferred = preferredRoles(event.kind());
            List<LoadedCitizen> candidates = new ArrayList<>(loadedCitizens);
            candidates.sort(Comparator
                    .comparingInt((LoadedCitizen c) -> preferred.contains(c.role()) ? 0 : 1)
                    .thenComparingInt(LoadedCitizen::projectionSlot)
                    .thenComparingLong(LoadedCitizen::citizenId));

            int want = Math.max(1, Math.min(MAX_PARTICIPANTS_PER_EVENT,
                    (int) Math.ceil(event.intensity() * MAX_PARTICIPANTS_PER_EVENT)));
            int taken = 0;
            double speed = 0.82 + event.intensity() * 0.2;
            for (LoadedCitizen citizen : candidates) {
                if (taken >= want) break;
                if (out.size() >= MAX_PARTICIPANTS_PER_SETTLEMENT) break;
                if (!usedSlots.add(citizen.projectionSlot())) continue;
                out.add(new Order(
                        settlement.id(),
                        faction.id(),
                        citizen.citizenId(),
                        citizen.projectionSlot(),
                        event.kind(),
                        siteRole,
                        target.x(),
                        target.z(),
                        speed,
                        event.cue()
                ));
                taken++;
            }
            if (taken > 0) {
                cooldowns.mark(settlement.id(), event.kind(), tick);
                eventsUsed++;
            }
        }
        return List.copyOf(out);
    }

    public static StructureRole siteFor(EventKind kind) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case MARKET_DAY -> StructureRole.MARKET;
            case HOLY_DAY -> StructureRole.TEMPLE;
            case FAMINE_QUEUE -> StructureRole.WAREHOUSE;
            case EPIDEMIC_CARE -> StructureRole.CLINIC;
            case JUSTICE_PROCESSION -> StructureRole.COURTHOUSE;
            case ARMY_MUSTER -> StructureRole.GATE;
            case SUCCESSION_COURT -> StructureRole.KEEP;
            case CONSTRUCTION_WORK -> StructureRole.TOWN_HALL;
        };
    }

    public static EnumSet<CitizenRole> preferredRoles(EventKind kind) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case MARKET_DAY -> EnumSet.of(CitizenRole.TRADER, CitizenRole.BUTCHER, CitizenRole.OFFICIAL, CitizenRole.FARMER);
            case HOLY_DAY -> EnumSet.of(CitizenRole.PRIEST, CitizenRole.OFFICIAL, CitizenRole.TEACHER, CitizenRole.SCHOLAR);
            case FAMINE_QUEUE -> EnumSet.of(CitizenRole.FARMER, CitizenRole.TRADER, CitizenRole.OFFICIAL, CitizenRole.DOCKWORKER);
            case EPIDEMIC_CARE -> EnumSet.of(CitizenRole.HEALER, CitizenRole.PRIEST, CitizenRole.OFFICIAL);
            case JUSTICE_PROCESSION -> EnumSet.of(CitizenRole.GUARD, CitizenRole.OFFICIAL, CitizenRole.SPY);
            case ARMY_MUSTER -> EnumSet.of(CitizenRole.GUARD, CitizenRole.OFFICIAL, CitizenRole.HUNTER);
            case SUCCESSION_COURT -> EnumSet.of(CitizenRole.OFFICIAL, CitizenRole.PRIEST, CitizenRole.GUARD, CitizenRole.SCHOLAR);
            case CONSTRUCTION_WORK -> EnumSet.of(CitizenRole.BUILDER, CitizenRole.CARPENTER, CitizenRole.LUMBERJACK, CitizenRole.MINER);
        };
    }

    private static List<ConstructionIntent> completedIntents(SimulationState state, Faction faction, Settlement settlement) {
        List<ConstructionIntent> all = new ArrayList<>(SettlementPlanCache.plan(faction, settlement));
        all.addAll(PrimaryEconomyPlanner.plan(state, faction, settlement));
        return all.stream().filter(i -> settlement.isConstructionCompleted(i.key())).toList();
    }

    private static SimPosition resolveSite(
            Settlement settlement,
            List<ConstructionIntent> completed,
            EventKind kind,
            StructureRole preferred
    ) {
        if (kind == EventKind.CONSTRUCTION_WORK) {
            String active = settlement.activeConstructionKey();
            if (active != null && !active.isBlank()) {
                for (ConstructionIntent intent : completed) {
                    if (intent.key().equals(active)) return intent.center();
                }
            }
            for (String landmark : settlement.priorityLandmarks()) {
                for (ConstructionIntent intent : completed) {
                    if (intent.key().equals(landmark)) return intent.center();
                }
            }
        }
        ConstructionIntent match = firstRole(completed, preferred);
        if (match != null) return match.center();
        // Soft fallbacks when the preferred civic shell is not yet materialized.
        return switch (kind) {
            case MARKET_DAY -> centerOrNull(firstRole(completed, StructureRole.PLAZA),
                    firstRole(completed, StructureRole.WAREHOUSE));
            case HOLY_DAY -> centerOrNull(firstRole(completed, StructureRole.KEEP),
                    firstRole(completed, StructureRole.TOWN_HALL));
            case FAMINE_QUEUE -> centerOrNull(firstRole(completed, StructureRole.MARKET),
                    firstRole(completed, StructureRole.MILL));
            case EPIDEMIC_CARE -> centerOrNull(firstRole(completed, StructureRole.TEMPLE),
                    firstRole(completed, StructureRole.HOUSE));
            case JUSTICE_PROCESSION -> centerOrNull(firstRole(completed, StructureRole.KEEP),
                    firstRole(completed, StructureRole.PRISON));
            case ARMY_MUSTER -> centerOrNull(firstRole(completed, StructureRole.BARRACKS),
                    firstRole(completed, StructureRole.WALL));
            case SUCCESSION_COURT -> centerOrNull(firstRole(completed, StructureRole.TOWN_HALL),
                    firstRole(completed, StructureRole.PLAZA));
            case CONSTRUCTION_WORK -> centerOrNull(firstRole(completed, StructureRole.KEEP),
                    firstRole(completed, StructureRole.HOUSE));
        };
    }

    private static ConstructionIntent firstRole(List<ConstructionIntent> completed, StructureRole role) {
        for (ConstructionIntent intent : completed) {
            if (intent.role() == role) return intent;
        }
        return null;
    }

    private static SimPosition centerOrNull(ConstructionIntent a, ConstructionIntent b) {
        if (a != null) return a.center();
        if (b != null) return b.center();
        return null;
    }
}
