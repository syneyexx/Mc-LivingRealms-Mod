package dev.livingrealms.sim.world;

import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * Classifies foreign physical villages/structures against the 2000-block settlement rule.
 *
 * <ul>
 *   <li>Near existing footprint → bind into that settlement (no new settlement)</li>
 *   <li>≥ {@link SettlementDensitySeeder#MIN_SETTLEMENT_SPACING} from every settlement → new FOREIGN_ADOPTED</li>
 *   <li>Inside spacing belt → {@link OutlyingSite} attached to nearest settlement</li>
 * </ul>
 */
public final class ForeignAdoptionClassifier {
    /** Physical footprint radius for "already represented locally". */
    public static final double DUPLICATE_PHYSICAL_SITE_RADIUS = 190.0;
    /** Canonical settlement spacing — same product constant as densifier/founder. */
    public static final double CANONICAL_SETTLEMENT_SPACING = SettlementDensitySeeder.MIN_SETTLEMENT_SPACING;

    public enum Outcome { BOUND_EXISTING, NEW_SETTLEMENT, OUTLYING_SITE, IDEMPOTENT_SITE }

    public record Result(Outcome outcome, Settlement settlement, OutlyingSite site, String reason) {}

    private ForeignAdoptionClassifier() {}

    public static Result classifyAndAdopt(SimulationState state, SimPosition pos, String name,
                                          int population, int housing, OutlyingSite.Type siteType) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(siteType, "siteType");

        Optional<Settlement> nearFootprint = nearestSettlement(state, pos, DUPLICATE_PHYSICAL_SITE_RADIUS);
        if (nearFootprint.isPresent()) {
            return new Result(Outcome.BOUND_EXISTING, nearFootprint.get(), null, "bound_existing_footprint");
        }

        Optional<OutlyingSite> existingSite = state.findOutlyingNear(pos, DUPLICATE_PHYSICAL_SITE_RADIUS);
        if (existingSite.isPresent()) {
            return new Result(Outcome.IDEMPOTENT_SITE, null, existingSite.get(), "site_already_registered");
        }

        Optional<Settlement> nearest = nearestSettlement(state, pos, Double.POSITIVE_INFINITY);
        if (nearest.isEmpty()) {
            return new Result(Outcome.BOUND_EXISTING, null, null, "no_host_faction");
        }

        double dist = nearest.get().position().distanceTo(pos);
        Faction owner = state.findSettlementOwner(nearest.get().id()).orElse(null);
        if (owner == null) return new Result(Outcome.BOUND_EXISTING, null, null, "owner_missing");

        if (dist >= CANONICAL_SETTLEMENT_SPACING) {
            Settlement adopted = new Settlement(state.nextId(), uniqueName(state, name), pos,
                    Math.max(1, population), Math.max(1, housing),
                    SettlementOrigin.FOREIGN_ADOPTED, true, DevelopmentMode.AUTO);
            owner.addSettlement(adopted);
            softProvision(owner, population);
            state.history().add(new WorldEvent(state.clock().day(), "foreign_settlement_adopted",
                    adopted.name() + " adopted as FOREIGN_ADOPTED under " + owner.name()
                            + " at " + Math.round(pos.x()) + "," + Math.round(pos.z())));
            return new Result(Outcome.NEW_SETTLEMENT, adopted, null, "new_foreign_settlement");
        }

        OutlyingSite site = new OutlyingSite(state.nextId(), nearest.get().id(), owner.id(), siteType, pos,
                OutlyingSite.defaultName(siteType, pos), Math.max(1, population),
                Math.max(0, housing * 0.85), true);
        state.addOutlyingSite(site);
        state.history().add(new WorldEvent(state.clock().day(), "foreign_outlying_site",
                site.name() + " attached to " + nearest.get().name()
                        + " (" + Math.round(dist) + "m < " + (int) CANONICAL_SETTLEMENT_SPACING + "m spacing)"));
        return new Result(Outcome.OUTLYING_SITE, nearest.get(), site, "outlying_site");
    }

    private static Optional<Settlement> nearestSettlement(SimulationState state, SimPosition pos, double maxDist) {
        Settlement best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Faction f : state.factions()) {
            for (Settlement s : f.settlements()) {
                double d = s.position().distanceTo(pos);
                if (d <= maxDist && d < bestDist) {
                    bestDist = d;
                    best = s;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static String uniqueName(SimulationState state, String base) {
        String name = base == null || base.isBlank() ? "Foreign Hamlet" : base.strip();
        boolean taken = state.factions().stream().flatMap(f -> f.settlements().stream())
                .anyMatch(s -> s.name().equalsIgnoreCase(name));
        if (!taken) return name;
        return name + " " + Math.floorMod((int) state.peekNextId(), 997);
    }

    private static void softProvision(Faction owner, int population) {
        owner.stockpile().add(ResourceType.GRAIN, Math.max(80, population * 0.4));
        owner.stockpile().add(ResourceType.BREAD, Math.max(60, population * 0.25));
        owner.stockpile().add(ResourceType.WOOD, Math.max(40, population * 0.2));
    }

    public static Faction nearestOwner(SimulationState state, SimPosition pos) {
        return state.factions().stream()
                .min(Comparator.comparingDouble(f -> f.settlements().stream()
                        .mapToDouble(s -> s.position().distanceTo(pos)).min().orElse(Double.POSITIVE_INFINITY)))
                .orElse(null);
    }
}
