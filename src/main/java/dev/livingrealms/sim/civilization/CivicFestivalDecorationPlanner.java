package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.content.CultureDefinition;
import dev.livingrealms.sim.content.SettlementIdentityProfile;
import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure planner for temporary festival decorations.
 * Positions are deterministic from event id so reload/cleanup never invents orphan geometry.
 * Wave 30 — culture material palettes bias decoration kinds.
 */
public final class CivicFestivalDecorationPlanner {
    public enum Kind { BANNER, TORCH, CARPET, FLOWER, STALL }

    public record Decoration(
            long eventId,
            long settlementId,
            CivicEventType eventType,
            Kind kind,
            int offsetX,
            int offsetZ,
            int slot
    ) {}

    private CivicFestivalDecorationPlanner() {}

    public static List<Decoration> planActive(SimulationState state) {
        Objects.requireNonNull(state, "state");
        long day = state.clock().day();
        List<Decoration> out = new ArrayList<>();
        List<CivicEvent> active = state.civicEvents().stream()
                .filter(CivicEvent::active)
                .filter(e -> e.startDay() <= day && e.endDay() >= day)
                .sorted(Comparator.comparingLong(CivicEvent::id))
                .toList();
        for (CivicEvent event : active) {
            Settlement settlement = state.findSettlement(event.settlementId()).orElse(null);
            Faction faction = state.findFaction(event.factionId()).orElse(null);
            if (settlement == null || faction == null) continue;
            Optional<CultureDefinition> culture = SettlementIdentityProfile.cultureOf(faction);
            SimPosition anchor = festivalAnchor(faction, settlement, event.type());
            int budget = decorationBudget(event, culture.orElse(null));
            int[][] ring = {
                    {2, 0}, {-2, 0}, {0, 2}, {0, -2},
                    {3, 2}, {-3, -2}, {2, -3}, {-2, 3},
                    {4, 1}, {-4, -1}, {1, 4}, {-1, -4},
                    {5, 0}, {-5, 0}, {0, 5}, {0, -5}
            };
            for (int i = 0; i < budget && i < ring.length; i++) {
                int idx = Math.floorMod(i + Long.hashCode(event.id()), ring.length);
                Kind kind = kindFor(event.type(), i, culture.orElse(null));
                out.add(new Decoration(event.id(), settlement.id(), event.type(), kind,
                        (int) Math.floor(anchor.x()) + ring[idx][0] - (int) Math.floor(settlement.position().x()),
                        (int) Math.floor(anchor.z()) + ring[idx][1] - (int) Math.floor(settlement.position().z()),
                        i));
            }
        }
        return List.copyOf(out);
    }

    /** Absolute world XZ for a decoration relative to settlement center. */
    public static SimPosition absolutePosition(Settlement settlement, Decoration decoration) {
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(decoration, "decoration");
        return new SimPosition(settlement.position().x() + decoration.offsetX(), settlement.position().z() + decoration.offsetZ());
    }

    private static SimPosition festivalAnchor(Faction faction, Settlement settlement, CivicEventType type) {
        StructureRole preferred = switch (type) {
            case RELIGIOUS_RITUAL, MOURNING, CORONATION -> StructureRole.TEMPLE;
            case MARKET_FAIR, HARVEST_FESTIVAL -> StructureRole.MARKET;
            case VICTORY_FEAST, WEDDING_FEAST -> StructureRole.TAVERN;
        };
        return SettlementPlanner.plan(faction, settlement).stream()
                .filter(i -> i.role() == preferred && settlement.isConstructionCompleted(i.key()))
                .map(i -> i.center())
                .findFirst()
                .or(() -> SettlementPlanner.plan(faction, settlement).stream()
                        .filter(i -> (i.role() == StructureRole.MARKET || i.role() == StructureRole.TEMPLE || i.role() == StructureRole.KEEP)
                                && settlement.isConstructionCompleted(i.key()))
                        .map(i -> i.center())
                        .findFirst())
                .orElse(settlement.position());
    }

    private static int decorationBudget(CivicEvent event, CultureDefinition culture) {
        int base = switch (event.type()) {
            case RELIGIOUS_RITUAL, CORONATION -> 10;
            case MARKET_FAIR, HARVEST_FESTIVAL, VICTORY_FEAST, WEDDING_FEAST -> 12;
            case MOURNING -> 6;
        };
        if (culture != null && culture.artisticTendency() > 0.6) base += 2;
        if (culture != null && culture.economicTendency() > 0.7 && event.type() == CivicEventType.MARKET_FAIR) base += 1;
        return Math.max(4, Math.min(16, (int) Math.round(base * (.55 + .45 * event.intensity()))));
    }

    private static Kind kindFor(CivicEventType type, int slot, CultureDefinition culture) {
        if (culture != null && !culture.materialPalette().isEmpty()) {
            String material = culture.materialPalette().get(slot % culture.materialPalette().size()).toLowerCase(Locale.ROOT);
            if (material.contains("gold") || material.contains("silk") || material.contains("banner")) {
                if (slot % 2 == 0) return Kind.BANNER;
            }
            if (material.contains("flower") || material.contains("thatch") || material.contains("oak")) {
                if (slot % 3 == 0) return Kind.FLOWER;
            }
            if (material.contains("torch") || material.contains("basalt") || material.contains("coal")) {
                if (slot % 3 == 1) return Kind.TORCH;
            }
        }
        if (type == CivicEventType.MOURNING) {
            return switch (slot % 3) { case 0 -> Kind.TORCH; case 1 -> Kind.FLOWER; default -> Kind.CARPET; };
        }
        if (type == CivicEventType.RELIGIOUS_RITUAL || type == CivicEventType.CORONATION) {
            return switch (slot % 4) { case 0 -> Kind.BANNER; case 1 -> Kind.TORCH; case 2 -> Kind.FLOWER; default -> Kind.CARPET; };
        }
        return switch (slot % 5) {
            case 0 -> Kind.STALL;
            case 1 -> Kind.BANNER;
            case 2 -> Kind.TORCH;
            case 3 -> Kind.CARPET;
            default -> Kind.FLOWER;
        };
    }
}
