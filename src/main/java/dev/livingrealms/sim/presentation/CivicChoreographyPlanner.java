package dev.livingrealms.sim.presentation;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimulationState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Non-authoritative presentation planner. Reads canonical truth and emits bounded choreography
 * cues for nearby Minecraft projection. Never mutates economy/politics.
 */
public final class CivicChoreographyPlanner {
    public static final int MAX_EVENTS_PER_SETTLEMENT = 8;

    public enum EventKind {
        MARKET_DAY, HOLY_DAY, EPIDEMIC_CARE, FAMINE_QUEUE, JUSTICE_PROCESSION,
        ARMY_MUSTER, SUCCESSION_COURT, CONSTRUCTION_WORK
    }

    public record PresentationEvent(
            EventKind kind,
            long settlementId,
            long factionId,
            String cue,
            double intensity
    ) {
        public PresentationEvent {
            Objects.requireNonNull(kind, "kind");
            cue = cue == null ? "" : cue;
            intensity = Double.isFinite(intensity) ? Math.max(0, Math.min(1, intensity)) : 0;
        }
    }

    private CivicChoreographyPlanner() {}

    public static List<PresentationEvent> planSettlement(SimulationState state, Faction faction, Settlement settlement) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        List<PresentationEvent> out = new ArrayList<>();
        long day = state.clock().day();
        long sid = settlement.id();
        long fid = faction.id();

        // Market day: every 7th day in settlements with a market role / village+.
        if (settlement.tier().ordinal() >= Settlement.Tier.VILLAGE.ordinal() && day % 7 == 0) {
            out.add(new PresentationEvent(EventKind.MARKET_DAY, sid, fid,
                    "merchants gather at the market", 0.7));
        }
        // Holy day: temple-bearing towns on day-of-week aligned to settlement id.
        if (settlement.tier().ordinal() >= Settlement.Tier.TOWN.ordinal()
                && Math.floorMod(day + sid, 14) == 0) {
            out.add(new PresentationEvent(EventKind.HOLY_DAY, sid, fid,
                    "congregation walks to the temple", 0.65));
        }
        if (settlement.foodSecurity() < 0.35) {
            out.add(new PresentationEvent(EventKind.FAMINE_QUEUE, sid, fid,
                    "citizens queue at the granary", 1.0 - settlement.foodSecurity()));
        }
        boolean epidemic = state.epidemics().stream()
                .anyMatch(e -> e.settlementId() == sid && e.active());
        if (epidemic) {
            out.add(new PresentationEvent(EventKind.EPIDEMIC_CARE, sid, fid,
                    "healers attend the clinic", 0.8));
        }
        boolean justice = state.justiceCases().stream()
                .anyMatch(c -> c.settlementId() == sid && c.active());
        if (justice) {
            out.add(new PresentationEvent(EventKind.JUSTICE_PROCESSION, sid, fid,
                    "guards escort a case to court", 0.55));
        }
        boolean mustering = state.objectives().stream()
                .anyMatch(o -> !o.complete() && o.ownerFactionId() == fid);
        if (mustering && settlement.tier().ordinal() >= Settlement.Tier.TOWN.ordinal()) {
            out.add(new PresentationEvent(EventKind.ARMY_MUSTER, sid, fid,
                    "soldiers assemble at the gate", 0.75));
        }
        boolean succession = state.history().recent(8).stream()
                .anyMatch(e -> e.type().contains("succession") || e.type().contains("abdicate")
                        || e.type().contains("coronation"));
        if (succession) {
            out.add(new PresentationEvent(EventKind.SUCCESSION_COURT, sid, fid,
                    "court gathers for succession", 0.7));
        }
        if (!settlement.activeConstructionKey().isBlank()
                || !settlement.priorityLandmarks().isEmpty()) {
            out.add(new PresentationEvent(EventKind.CONSTRUCTION_WORK, sid, fid,
                    "builders work the active site", 0.5));
        }

        out.sort(Comparator.comparing((PresentationEvent e) -> e.intensity()).reversed()
                .thenComparing(e -> e.kind().name()));
        if (out.size() > MAX_EVENTS_PER_SETTLEMENT) {
            return List.copyOf(out.subList(0, MAX_EVENTS_PER_SETTLEMENT));
        }
        return List.copyOf(out);
    }
}
