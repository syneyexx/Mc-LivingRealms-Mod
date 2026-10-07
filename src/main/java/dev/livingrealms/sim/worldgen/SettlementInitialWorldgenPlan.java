package dev.livingrealms.sim.worldgen;

import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.CultureArchitecture;
import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.economy.primary.PrimaryEconomyPlanner;
import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.faction.SettlementRole;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable day-zero physical settlement plan shared by Minecraft world generation.
 *
 * <p>It materializes no blocks and mutates no canonical state. Temporary faction/settlement objects
 * exist only while deriving the already-existing graph-first {@link SettlementPlanner} output.</p>
 */
public record SettlementInitialWorldgenPlan(
        String stableKey,
        String realmId,
        long factionId,
        long settlementId,
        String settlementName,
        SettlementRole role,
        Settlement.Tier tier,
        SimPosition center,
        CultureArchitecture architecture,
        List<ConstructionIntent> intents
) {
    public SettlementInitialWorldgenPlan {
        if (stableKey == null || stableKey.isBlank()) throw new IllegalArgumentException("stableKey");
        if (realmId == null || realmId.isBlank()) throw new IllegalArgumentException("realmId");
        if (factionId <= 0 || settlementId <= 0) throw new IllegalArgumentException("owner ids");
        if (settlementName == null || settlementName.isBlank()) throw new IllegalArgumentException("settlementName");
        role = Objects.requireNonNull(role, "role");
        tier = Objects.requireNonNull(tier, "tier");
        center = Objects.requireNonNull(center, "center");
        architecture = Objects.requireNonNull(architecture, "architecture");
        intents = List.copyOf(Objects.requireNonNull(intents, "intents"));
    }

    public static List<SettlementInitialWorldgenPlan> buildAll(
            StarterCivilizationLayoutPlanner.Layout layout) {
        Objects.requireNonNull(layout, "layout");
        List<SettlementInitialWorldgenPlan> out = new ArrayList<>();
        for (StarterCivilizationLayoutPlanner.RealmPlan realmPlan : layout.realms()) {
            Faction faction = new Faction(
                    realmPlan.factionId(),
                    realmPlan.definition().displayName(),
                    realmPlan.definition().rulerSeedName());
            faction.restoreTechnology(realmPlan.definition().technology());
            faction.restoreTreasury(realmPlan.definition().treasury());

            List<Settlement> settlements = new ArrayList<>();
            for (StarterCivilizationLayoutPlanner.SettlementPlan starter : realmPlan.settlements()) {
                Settlement settlement = new Settlement(
                        starter.id(), starter.name(), starter.position(),
                        starter.population(), starter.housing(),
                        SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO, starter.role());
                faction.addSettlement(settlement);
                settlements.add(settlement);
            }

            for (int i = 0; i < settlements.size(); i++) {
                Settlement settlement = settlements.get(i);
                StarterCivilizationLayoutPlanner.SettlementPlan starter = realmPlan.settlements().get(i);
                out.add(buildOne(faction, realmPlan, starter, settlement));
            }
        }
        return List.copyOf(out);
    }

    /**
     * Derives one settlement's day-zero physical plan without walking every other starter
     * settlement. Used by chunk-lazy Minecraft worldgen.
     */
    public static SettlementInitialWorldgenPlan buildOne(
            StarterCivilizationLayoutPlanner.RealmPlan realmPlan,
            StarterCivilizationLayoutPlanner.SettlementPlan starter) {
        Objects.requireNonNull(realmPlan, "realmPlan");
        Objects.requireNonNull(starter, "starter");
        Faction faction = new Faction(
                realmPlan.factionId(),
                realmPlan.definition().displayName(),
                realmPlan.definition().rulerSeedName());
        faction.restoreTechnology(realmPlan.definition().technology());
        faction.restoreTreasury(realmPlan.definition().treasury());

        // Preserve the faction context used by buildAll because boundary/culture planning may
        // inspect sibling settlements, but derive heavy physical intents only for the requested one.
        Settlement requested = null;
        for (StarterCivilizationLayoutPlanner.SettlementPlan sibling : realmPlan.settlements()) {
            StarterCivilizationLayoutPlanner.SettlementPlan materialized =
                    sibling.id() == starter.id() ? starter : sibling;
            Settlement settlement = new Settlement(
                    materialized.id(), materialized.name(), materialized.position(),
                    materialized.population(), materialized.housing(),
                    SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO, materialized.role());
            faction.addSettlement(settlement);
            if (materialized.id() == starter.id()) requested = settlement;
        }
        if (requested == null) {
            throw new IllegalArgumentException("starter settlement is not part of realm");
        }
        return buildOne(faction, realmPlan, starter, requested);
    }

    private static SettlementInitialWorldgenPlan buildOne(
            Faction faction,
            StarterCivilizationLayoutPlanner.RealmPlan realmPlan,
            StarterCivilizationLayoutPlanner.SettlementPlan starter,
            Settlement settlement) {
        SettlementPlanner.WorldgenPlan physical = SettlementPlanner.planWorldgen(faction, settlement);
        List<ConstructionIntent> dayZero = new ArrayList<>(physical.intents());
        dayZero.addAll(PrimaryEconomyPlanner.planStarterBaseline(faction, settlement, dayZero));
        SettlementInitialWorldgenPlan plan = new SettlementInitialWorldgenPlan(
                starter.stableKey(), realmPlan.definition().id(), faction.id(), settlement.id(),
                settlement.name(), settlement.role(), settlement.tier(), settlement.position(),
                physical.architecture(), dayZero);
        StarterSettlementWorldgenContract.requireComplete(plan);
        return plan;
    }
}
