package dev.livingrealms.sim.world;

import dev.livingrealms.sim.civilization.ResourceClaim;
import dev.livingrealms.sim.civilization.ResourceClaimType;
import dev.livingrealms.sim.civilization.RuinSite;
import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.economy.primary.PrimaryEconomyPlanner;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import java.util.Objects;
import java.util.Optional;

/** Pure locate helpers shared by commands and regression tests. */
public final class LocateQuery {
    public record Hit(String label, String name, String type, String factionName, double x, double z, double distance) {
        public Hit {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(factionName, "factionName");
        }
    }

    private LocateQuery() {}

    public static Optional<Hit> nearestSettlement(SimulationState state, SimPosition from, java.util.function.Predicate<Settlement> filter, String label) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(from, "from");
        Settlement nearest = null;
        Faction owner = null;
        double best = Double.POSITIVE_INFINITY;
        for (Faction faction : state.factions()) {
            for (Settlement settlement : faction.settlements()) {
                if (filter != null && !filter.test(settlement)) continue;
                double d = from.distanceTo(settlement.position());
                if (d < best) {
                    best = d;
                    nearest = settlement;
                    owner = faction;
                }
            }
        }
        if (nearest == null) return Optional.empty();
        return Optional.of(new Hit(label, nearest.name(), nearest.tier().name(), owner.name(), nearest.position().x(), nearest.position().z(), best));
    }

    public static Optional<Hit> nearestCity(SimulationState state, SimPosition from) {
        Optional<Hit> city = nearestSettlement(state, from, s -> s.tier().ordinal() >= Settlement.Tier.CITY.ordinal(), "city");
        if (city.isPresent()) return city;
        return nearestSettlement(state, from, s -> true, "city").map(h -> new Hit("city", h.name(), h.type(), h.factionName(), h.x(), h.z(), h.distance()));
    }

    public static Optional<Hit> nearestMine(SimulationState state, SimPosition from) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(from, "from");
        ConstructionIntent nearestIntent = null;
        Settlement settlementFound = null;
        Faction factionFound = null;
        double best = Double.POSITIVE_INFINITY;
        for (Faction faction : state.factions()) {
            for (Settlement settlement : faction.settlements()) {
                for (ConstructionIntent intent : PrimaryEconomyPlanner.plan(state, faction, settlement)) {
                    if (intent.role() != StructureRole.MINE) continue;
                    double d = from.distanceTo(intent.center());
                    if (d < best) {
                        best = d;
                        nearestIntent = intent;
                        settlementFound = settlement;
                        factionFound = faction;
                    }
                }
                for (String key : settlement.completedConstruction()) {
                    if (!key.startsWith("mine:")) continue;
                    double d = from.distanceTo(settlement.position());
                    if (d < best) {
                        best = d;
                        nearestIntent = null;
                        settlementFound = settlement;
                        factionFound = faction;
                    }
                }
            }
        }
        for (ResourceClaim claim : state.resourceClaims()) {
            if (!claim.active() || claim.type() != ResourceClaimType.MINE) continue;
            double d = from.distanceTo(claim.position());
            if (d < best) {
                best = d;
                nearestIntent = null;
                settlementFound = state.findSettlement(claim.settlementId()).orElse(null);
                factionFound = state.findFaction(claim.factionId()).orElse(null);
            }
        }
        // Global fallback: nearest rocky/highland settlement still counts as a mine destination
        // so far-world locate never reports "not found" when civilization exists somewhere.
        if (settlementFound == null) {
            for (Faction faction : state.factions()) {
                for (Settlement settlement : faction.settlements()) {
                    if (settlement.geography().miningPotential() < .35) continue;
                    double d = from.distanceTo(settlement.position());
                    if (d < best) {
                        best = d;
                        settlementFound = settlement;
                        factionFound = faction;
                    }
                }
            }
        }
        if (settlementFound == null || factionFound == null) {
            return nearestSettlement(state, from, s -> true, "mine");
        }
        double x = nearestIntent != null ? nearestIntent.center().x() : settlementFound.position().x();
        double z = nearestIntent != null ? nearestIntent.center().z() : settlementFound.position().z();
        for (ResourceClaim claim : state.resourceClaims()) {
            if (claim.active() && claim.type() == ResourceClaimType.MINE && claim.settlementId() == settlementFound.id()
                    && Math.abs(from.distanceTo(claim.position()) - best) < 1e-6) {
                x = claim.position().x();
                z = claim.position().z();
                break;
            }
        }
        return Optional.of(new Hit("mine", settlementFound.name(), "MINE", factionFound.name(), x, z, best));
    }

    public static Optional<Hit> nearestKingdom(SimulationState state, SimPosition from) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(from, "from");
        Faction bestFaction = null;
        Settlement capital = null;
        double best = Double.POSITIVE_INFINITY;
        for (Faction faction : state.factions()) {
            if (WizardTreesSeeder.isWizardTrees(faction)) continue;
            Settlement cap = faction.settlements().stream().max(java.util.Comparator.comparingInt(Settlement::population).thenComparingLong(Settlement::id)).orElse(null);
            if (cap == null) continue;
            double d = from.distanceTo(cap.position());
            if (d < best) {
                best = d;
                bestFaction = faction;
                capital = cap;
            }
        }
        if (bestFaction == null || capital == null) return Optional.empty();
        return Optional.of(new Hit("kingdom", bestFaction.name(), "KINGDOM", bestFaction.name(), capital.position().x(), capital.position().z(), best));
    }

    public static Optional<Hit> nearestMarket(SimulationState state, SimPosition from) {
        return nearestSettlement(state, from, s -> s.completedConstruction().stream().anyMatch(k -> k.startsWith("market:"))
                || s.tier().ordinal() >= Settlement.Tier.VILLAGE.ordinal(), "market");
    }

    public static Optional<Hit> nearestPort(SimulationState state, SimPosition from) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(from, "from");
        dev.livingrealms.sim.naval.PortState nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (dev.livingrealms.sim.naval.PortState port : state.ports()) {
            double d = from.distanceTo(port.position());
            if (d < best) {
                best = d;
                nearest = port;
            }
        }
        if (nearest == null) return Optional.empty();
        Faction faction = state.findFaction(nearest.factionId()).orElse(null);
        Settlement settlement = state.findSettlement(nearest.settlementId()).orElse(null);
        String name = settlement != null ? settlement.name() : "Port";
        String factionName = faction != null ? faction.name() : "Unknown";
        return Optional.of(new Hit("port", name, "PORT", factionName, nearest.position().x(), nearest.position().z(), best));
    }

    public static Optional<Hit> nearestWizardTrees(SimulationState state, SimPosition from) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(from, "from");
        for (Faction faction : state.factions()) {
            if (!WizardTreesSeeder.isWizardTrees(faction) || faction.settlements().isEmpty()) continue;
            Settlement nearest = faction.settlements().stream().min(java.util.Comparator.comparingDouble(s -> from.distanceTo(s.position()))).orElse(null);
            if (nearest == null) continue;
            return Optional.of(new Hit("wizardtrees", nearest.name(), "WIZARD_TREES", faction.name(), nearest.position().x(), nearest.position().z(), from.distanceTo(nearest.position())));
        }
        return Optional.empty();
    }

    public static Optional<Hit> nearestRuin(SimulationState state, SimPosition from) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(from, "from");
        RuinSite nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (RuinSite ruin : state.ruinSites()) {
            double d = from.distanceTo(ruin.position());
            if (d < best) {
                best = d;
                nearest = ruin;
            }
        }
        if (nearest == null) return Optional.empty();
        Faction faction = state.findFaction(nearest.originalFactionId()).orElse(null);
        return Optional.of(new Hit("ruin", nearest.originalName(), "RUIN", faction != null ? faction.name() : "Unknown", nearest.position().x(), nearest.position().z(), best));
    }
}
