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

    private RegionalImpostorPlanner() {}

    public static List<Token> plan(SimulationState state, List<SimPosition> players, double innerRadius, double outerRadius, int budget) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(players, "players");
        if (players.isEmpty() || budget <= 0 || !(outerRadius > innerRadius)) return List.of();
        double inner2 = innerRadius * innerRadius, outer2 = outerRadius * outerRadius;
        List<Token> out = new ArrayList<>();
        for (Faction faction : state.factions()) {
            int color = factionColor(faction.id());
            for (Settlement settlement : faction.settlements()) {
                if (!inBand(players, settlement.position(), inner2, outer2)) continue;
                out.add(new Token(Kind.SETTLEMENT_BUSTLE, settlement.id(), 0, settlement.position().x(), settlement.position().z(), color));
            }
            for (Army army : faction.armies()) {
                if (army.destroyed() || !inBand(players, army.position(), inner2, outer2)) continue;
                out.add(new Token(Kind.MILITARY_BANNER, army.id(), 0, army.position().x(), army.position().z(), color));
            }
        }
        for (TradeShipment shipment : state.shipments()) {
            if (shipment.arrived() || shipment.lossState() == TradeShipment.LossState.TOTAL) continue;
            SimPosition p = shipment.position();
            if (!inBand(players, p, inner2, outer2)) continue;
            out.add(new Token(Kind.CARAVAN_DUST, shipment.id(), 0, p.x(), p.z(), 0xC2A46D));
        }
        for (Fleet fleet : state.fleets()) {
            if (!inBand(players, fleet.position(), inner2, outer2)) continue;
            out.add(new Token(Kind.SAIL, fleet.id(), 0, fleet.position().x(), fleet.position().z(), 0xD8E6F0));
        }
        state.migrationGroups().stream().filter(g -> g.status() == dev.livingrealms.sim.civilization.MigrationStatus.TRAVELING).forEach(g -> {
            Settlement from = state.findSettlement(g.sourceSettlementId()).orElse(null);
            Settlement to = state.findSettlement(g.targetSettlementId()).orElse(null);
            if (from == null || to == null) return;
            SimPosition p = from.position().lerp(to.position(), g.progress());
            if (!inBand(players, p, inner2, outer2)) return;
            out.add(new Token(Kind.MIGRATION, g.id(), 0, p.x(), p.z(), 0xB0A090));
        });
        // Sparse herd tokens near regional ecology centers.
        state.regions().stream().limit(64).forEach(region -> {
            SimPosition p = region.center();
            if (!inBand(players, p, inner2, outer2)) return;
            if (region.populations().isEmpty()) return;
            out.add(new Token(Kind.HERD, region.id(), 0, p.x(), p.z(), 0x6B8F71));
        });
        out.sort(Comparator.comparingDouble((Token t) -> nearestDist2(players, t.position())).thenComparing(Token::key));
        if (out.size() > budget) return List.copyOf(out.subList(0, budget));
        return List.copyOf(out);
    }

    private static boolean inBand(List<SimPosition> players, SimPosition pos, double inner2, double outer2) {
        double best = Double.POSITIVE_INFINITY;
        for (SimPosition p : players) {
            double d = p.distanceTo(pos);
            d = d * d;
            if (d < best) best = d;
        }
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
