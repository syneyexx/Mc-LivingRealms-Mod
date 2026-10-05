package dev.livingrealms.sim.persistence;

import dev.livingrealms.sim.civilization.FactionCivilizationState;
import dev.livingrealms.sim.civilian.AppearanceProfile;
import dev.livingrealms.sim.faction.ConstructionOrigin;
import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.faction.Stockpile;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.world.SimulationState;

/**
 * Explicit schema migration steps applied when decoding payloads older than {@link SimulationStateCodec#SCHEMA_VERSION}.
 * Behavior matches the pre-extraction migratePreV* helpers; schema version is not bumped here.
 */
public final class SaveMigrationRegistry {
    private SaveMigrationRegistry() {}

    /** Applies every migration step required to bring {@code fromVersion} state up to current schema semantics. */
    public static void migrateToCurrent(SimulationState state, int fromVersion) {
        if (fromVersion < 16) migrate15to16(state);
        if (fromVersion < 17) migrate16to17(state);
        if (fromVersion < 18) migrate17to18(state);
        if (fromVersion < 19) migrate18to19(state);
        if (fromVersion < 20) migrate19to20(state);
    }

    /** Pre-schema-16 worlds already receive starter stores from Settlement construction; refresh barn/granary from structures. */
    public static void migrate15to16(SimulationState state) {
        for (Faction f : state.factions()) for (Settlement s : f.settlements()) {
            s.refreshStorageCapacity();
            s.enforceStorageCaps();
        }
    }

    public static void migrate16to17(SimulationState state) {
        for (SocialCitizen c : state.socialCitizens()) {
            int age = Math.max(1, c.ageYears(state.clock().day()));
            AppearanceProfile profile = AppearanceProfile.forCitizen(state.seed(), c.id(), c.role(), age, c.factionId());
            // Prefer deterministic rebuild from identity when packed appearance was constructor-default only.
            c.restoreAppearance(profile.pack());
            FactionCivilizationState civ = state.findFactionCivilization(c.factionId()).orElse(null);
            if (civ != null) {
                if (c.cultureKey() == null || c.cultureKey().isBlank()) c.setCultureKey(civ.cultureName());
                if (c.faithKey() == null || c.faithKey().isBlank()) c.setFaithKey(civ.faithName());
            }
        }
    }

    public static void migrate17to18(SimulationState state) {
        for (Faction f : state.factions()) {
            migrateStockpileToGoods(f.stockpile());
            for (Settlement s : f.settlements()) {
                migrateStockpileToGoods(s.stockpile());
                for (String key : s.completedConstruction()) s.restoreConstructionOrigin(key, ConstructionOrigin.MATERIALIZED);
            }
        }
    }

    /** Schema ≤18 settlements become LEGACY + physicallyAnchored — never assume they lack world geometry. */
    public static void migrate18to19(SimulationState state) {
        for (Faction f : state.factions()) {
            boolean wizard = f.name().equals("Wizard Trees");
            for (Settlement s : f.settlements()) {
                if (wizard) s.restoreProvenance(SettlementOrigin.WIZARD_TREES, true, DevelopmentMode.AUTO);
                else s.restoreProvenance(SettlementOrigin.LEGACY, true, DevelopmentMode.AUTO);
            }
        }
    }

    /**
     * Schema 19→20: underworld contracts / stolen-goods collections default empty when absent from the payload.
     * No in-memory rewrite is required beyond decode skipping the v20 sections.
     */
    public static void migrate19to20(SimulationState state) {
        // Intentionally empty — new collections are already empty on SimulationState construction.
        // Kept as an explicit step so SaveMigrationRegistry documents the 19→20 boundary.
    }

    static void migrateStockpileToGoods(Stockpile stockpile) {
        double food = stockpile.get(ResourceType.FOOD);
        if (food <= 0) {
            // Already seeded as GRAIN/BREAD (fresh constructors) or empty — keep as-is.
            return;
        }
        // Clear constructor seed for post-legacy goods before mapping FOOD so values are not doubled.
        zeroNewGoods(stockpile);
        ResourceType.migrateLegacyFood(stockpile);
    }

    private static void zeroNewGoods(Stockpile stockpile) {
        for (int i = ResourceType.LEGACY_COUNT; i < ResourceType.values().length; i++) stockpile.set(ResourceType.values()[i], 0);
    }
}
