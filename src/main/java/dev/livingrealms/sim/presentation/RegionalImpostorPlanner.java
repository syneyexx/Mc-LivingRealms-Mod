package dev.livingrealms.sim.presentation;

import dev.livingrealms.sim.faction.Army;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.naval.Fleet;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Pure planner for cheap regional impostor tokens between physical and regional radii. */
public final class RegionalImpostorPlanner {
    public enum Kind { MILITARY_BANNER, HERD, CARAVAN_DUST, SETTLEMENT_BUSTLE, SAIL, MIGRATION }

    public record Token(Kind kind, long canonicalId, int slot, double x, double z, int rgb) {
        public Token {
            Objects.requireNonNull(kind, "kind");
            if (canonicalId == 0 || slot < 0 || !Double.isFinite(x) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("token");
            }
        }
        public String key() { return kind.name() + ":" + canonicalId + ":" + slot; }
        public SimPosition position() { return new SimPosition(x, z); }
    }

    /**
     * Physical cutoffs per representation kind. A regional token may only exist outside the
     * corresponding full-entity envelope, preventing full entities and impostors from overlapping.
     */
    public record LodBands(
            double settlementPhysicalRadius,
            double militaryPhysicalRadius,
            double herdPhysicalRadius,
            double caravanPhysicalRadius,
            double navalPhysicalRadius,
            double migrationPhysicalRadius,
            double outerRadius,
            int budget
    ) {
        public LodBands {
            double[] radii = {settlementPhysicalRadius, militaryPhysicalRadius, herdPhysicalRadius,
                    caravanPhysicalRadius, navalPhysicalRadius, migrationPhysicalRadius, outerRadius};
            for (double radius : radii) {
                if (!Double.isFinite(radius) || radius < 0) throw new IllegalArgumentException("radius");
            }
            if (!(outerRadius > 0) || budget < 0) throw new IllegalArgumentException("lod bands");
        }
    }

    private RegionalImpostorPlanner() {}

    /**
     * Compatibility overload: one shared physical cutoff for every representation kind.
     * Production should prefer {@link #plan(SimulationState, List, LodBands)}.
     */
    public static List<Token> plan(SimulationState state, List<SimPosition> players,
                                   double innerRadius, double outerRadius, int budget) {
        return plan(state, players, new LodBands(
                innerRadius, innerRadius, innerRadius, innerRadius, innerRadius, innerRadius,
                outerRadius, budget));
    }

    public static List<Token> plan(SimulationState state, List<SimPosition> players, LodBands bands) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(bands, "bands");
        if (players.isEmpty() || bands.budget() <= 0) return List.of();
        List<Token> out = new ArrayList<>();
        for (Faction faction : state.factions()) {
            int color = factionColor(faction.id());
            for (Settlement settlement : faction.settlements()) {
                if (!inBand(players, settlement.position(), bands.settlementPhysicalRadius(), bands.outerRadius())) continue;
                out.add(new Token(Kind.SETTLEMENT_BUSTLE, settlement.id(), 0, settlement.position().x(), settlement.position().z(), color));
            }
            for (Army army : faction.armies()) {
                if (army.destroyed() || !inBand(players, army.position(), bands.militaryPhysicalRadius(), bands.outerRadius())) continue;
                out.add(new Token(Kind.MILITARY_BANNER, army.id(), 0, army.position().x(), army.position().z(), color));
            }
        }
        for (TradeShipment shipment : state.shipments()) {
            if (shipment.arrived() || shipment.lossState() == TradeShipment.LossState.TOTAL) continue;
            SimPosition p = shipment.position();
            if (!inBand(players, p, bands.caravanPhysicalRadius(), bands.outerRadius())) continue;
            out.add(new Token(Kind.CARAVAN_DUST, shipment.id(), 0, p.x(), p.z(), 0xC2A46D));
        }
        for (Fleet fleet : state.fleets()) {
            if (!inBand(players, fleet.position(), bands.navalPhysicalRadius(), bands.outerRadius())) continue;
            out.add(new Token(Kind.SAIL, fleet.id(), 0, fleet.position().x(), fleet.position().z(), 0xD8E6F0));
        }
        state.migrationGroups().stream().filter(g -> g.status() == dev.livingrealms.sim.civilization.MigrationStatus.TRAVELING).forEach(g -> {
            Settlement from = state.findSettlement(g.sourceSettlementId()).orElse(null);
            Settlement to = state.findSettlement(g.targetSettlementId()).orElse(null);
            if (from == null || to == null) return;
            SimPosition p = from.position().lerp(to.position(), g.progress());
            if (!inBand(players, p, bands.migrationPhysicalRadius(), bands.outerRadius())) return;
            out.add(new Token(Kind.MIGRATION, g.id(), 0, p.x(), p.z(), 0xB0A090));
        });
        // Sparse herd tokens near regional ecology centers.
        state.regions().stream().limit(64).forEach(region -> {
            SimPosition p = region.center();
            if (!inBand(players, p, bands.herdPhysicalRadius(), bands.outerRadius())) return;
            if (region.populations().isEmpty()) return;
            out.add(new Token(Kind.HERD, region.id(), 0, p.x(), p.z(), 0x6B8F71));
        });
        out.sort(Comparator.comparingDouble((Token t) -> nearestDist2(players, t.position())).thenComparing(Token::key));
        if (out.size() > bands.budget()) return List.copyOf(out.subList(0, bands.budget()));
        return List.copyOf(out);
    }

    private static boolean inBand(List<SimPosition> players, SimPosition pos,
                                  double physicalRadius, double outerRadius) {
        double best = Double.POSITIVE_INFINITY;
        for (SimPosition p : players) {
            double d = p.distanceTo(pos);
            d = d * d;
            if (d < best) best = d;
        }
        double inner2 = physicalRadius * physicalRadius;
        double outer2 = outerRadius * outerRadius;
        return best > inner2 && best <= outer2;
    }

    private static double nearestDist2(List<SimPosition> players, SimPosition pos) {
        double best = Double.POSITIVE_INFINITY;
        for (SimPosition p : players) best = Math.min(best, p.distanceTo(pos) * p.distanceTo(pos));
        return best;
    }

    private static int factionColor(long factionId) {
        long h = factionId * 0x9E3779B97F4A7C15L;
        int r = 80 + (int) ((h >>> 16) & 0x7F);
        int g = 80 + (int) ((h >>> 8) & 0x7F);
        int b = 80 + (int) (h & 0x7F);
        return (r << 16) | (g << 8) | b;
    }
}
