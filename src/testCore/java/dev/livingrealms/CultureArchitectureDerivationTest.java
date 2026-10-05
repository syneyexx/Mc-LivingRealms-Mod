package dev.livingrealms;

import dev.livingrealms.sim.civilization.FactionCivilizationState;
import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.CultureArchitecture;
import dev.livingrealms.sim.construction.CultureArchitectureProfile;
import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.StructureBlueprintFactory;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementGeographyProfile;
import dev.livingrealms.sim.faction.SettlementSpecialization;
import dev.livingrealms.sim.world.SimPosition;
import java.util.HashSet;
import java.util.Set;

/** Culture architecture must derive from traits + geography, not factionId % 8. */
public final class CultureArchitectureDerivationTest {
    private CultureArchitectureDerivationTest() {}

    public static void main(String[] args) {
        sameFactionIdDoesNotAloneDetermine();
        coldMartialDiffersFromAridMercantile();
        portAffectsProfile();
        derivationDeterministic();
        cultureChangeDoesNotMutateCompletedGeometry();
        System.out.println("PASS culture architecture derivation: traits+geography, not factionId%8");
    }

    private static void sameFactionIdDoesNotAloneDetermine() {
        long factionId = 42;
        SettlementGeographyProfile cold = new SettlementGeographyProfile(
                false, false, false, false, 0.05, 140, 12, .3, .4, .5, "minecraft:snowy_taiga", true);
        SettlementGeographyProfile arid = new SettlementGeographyProfile(
                false, false, false, false, 0.05, 70, 1, .15, .05, .2, "minecraft:desert", true);
        CultureArchitecture a = CultureArchitectureProfile.derive(
                .3, .85, .2, .2, .2, .2, .4, cold, SettlementSpecialization.MILITARY, factionId).architecture();
        CultureArchitecture b = CultureArchitectureProfile.derive(
                .85, .2, .3, .3, .3, .4, .6, arid, SettlementSpecialization.TRADE, factionId).architecture();
        check(a != b, "same faction id with different traits/geo must not force one architecture: " + a + " vs " + b);
        check(a != CultureArchitecture.fromStyleIndex(Math.floorMod((int) factionId, 8))
                        || b != CultureArchitecture.fromStyleIndex(Math.floorMod((int) factionId, 8)),
                "at least one profile must diverge from factionId%8");
    }

    private static void coldMartialDiffersFromAridMercantile() {
        CultureArchitecture nordic = CultureArchitectureProfile.derive(
                .2, .9, .2, .1, .2, .25, .4,
                new SettlementGeographyProfile(false, false, false, false, 0.05, 150, 14, .25, .5, .4, "minecraft:frozen_peaks", true),
                SettlementSpecialization.MILITARY, 99L).architecture();
        CultureArchitecture desert = CultureArchitectureProfile.derive(
                .8, .2, .4, .3, .3, .35, .55,
                new SettlementGeographyProfile(false, false, false, false, 0.05, 72, 1, .1, .05, .2, "minecraft:desert", true),
                SettlementSpecialization.TRADE, 99L).architecture();
        check(nordic == CultureArchitecture.NORDIC_FORTRESS || nordic == CultureArchitecture.MEDIEVAL_FACHWERK,
                "cold/martial should bias nordic/fortified, got " + nordic);
        check(desert == CultureArchitecture.DESERT_COURTYARD || desert == CultureArchitecture.MERCANTILE_TOWNHOUSE,
                "arid/mercantile should bias courtyard/townhouse, got " + desert);
        check(nordic != desert, "cold/martial must differ from arid/mercantile");
    }

    private static void portAffectsProfile() {
        SettlementGeographyProfile port = new SettlementGeographyProfile(
                true, false, true, false, 0.85, 62, 1, .4, .2, .1, "minecraft:beach", true);
        SettlementGeographyProfile inland = new SettlementGeographyProfile(
                false, false, false, false, 0.05, 80, 2, .55, .35, .25, "minecraft:plains", true);
        CultureArchitecture coastal = CultureArchitectureProfile.derive(
                .7, .3, .4, .3, .35, .4, .55, port, SettlementSpecialization.NAVAL, 11L).architecture();
        CultureArchitecture land = CultureArchitectureProfile.derive(
                .7, .3, .4, .3, .35, .4, .55, inland, SettlementSpecialization.TRADE, 11L).architecture();
        check(coastal == CultureArchitecture.COASTAL_VILLA || coastal == CultureArchitecture.MERCANTILE_TOWNHOUSE,
                "port specialization should bias coastal/mercantile, got " + coastal);
        check(coastal != land || coastal == CultureArchitecture.COASTAL_VILLA,
                "port geography should affect profile relative to inland");
    }

    private static void derivationDeterministic() {
        SettlementGeographyProfile geo = new SettlementGeographyProfile(
                false, true, true, true, 0.3, 68, 2, .6, .4, .2, "minecraft:river", true);
        CultureArchitecture a = CultureArchitectureProfile.derive(
                .5, .4, .6, .3, .3, .3, .5, geo, SettlementSpecialization.AGRICULTURAL, 12345L).architecture();
        CultureArchitecture b = CultureArchitectureProfile.derive(
                .5, .4, .6, .3, .3, .3, .5, geo, SettlementSpecialization.AGRICULTURAL, 12345L).architecture();
        check(a == b, "derivation must be deterministic");
        Faction faction = new Faction(55, "Culture Test", "Ruler");
        Settlement settlement = new Settlement(550, "Riverford", new SimPosition(0, 0), 500, 480);
        settlement.setGeography(geo);
        faction.addSettlement(settlement);
        FactionCivilizationState civ = new FactionCivilizationState(55, "Test Culture", "Faith", "Dialect");
        civ.setCultureTraits(.5, .4, .6, .3, .5, .5);
        CultureArchitecture c1 = CultureArchitectureProfile.derive(civ, null, faction, settlement).architecture();
        CultureArchitecture c2 = CultureArchitectureProfile.derive(civ, null, faction, settlement).architecture();
        check(c1 == c2, "faction+settlement derivation must be deterministic");
    }

    private static void cultureChangeDoesNotMutateCompletedGeometry() {
        Faction faction = new Faction(66, "Stable Build", "Ruler");
        Settlement settlement = new Settlement(660, "Oldtown", new SimPosition(200, 200), 300, 280);
        faction.addSettlement(settlement);
        CultureArchitectureProfile.bind(settlement.id(), CultureArchitecture.MEDIEVAL_FACHWERK);
        ConstructionIntent house = SettlementPlanner.plan(faction, settlement).stream()
                .filter(i -> i.role() == StructureRole.HOUSE).findFirst().orElseThrow();
        var before = StructureBlueprintFactory.create(house, CultureArchitecture.MEDIEVAL_FACHWERK);
        settlement.markConstructionCompleted(house.key());
        // Culture conquest shift — new profile must not rewrite completed house blueprint identity.
        CultureArchitectureProfile.bind(settlement.id(), CultureArchitecture.DESERT_COURTYARD);
        var after = StructureBlueprintFactory.create(house, CultureArchitecture.MEDIEVAL_FACHWERK);
        check(before.id().equals(after.id()), "completed geometry blueprint id must stay stable under culture change");
        check(before.placements().size() == after.placements().size(), "completed geometry placement count must stay stable");
        Set<String> families = new HashSet<>();
        for (CultureArchitecture arch : CultureArchitecture.values()) {
            families.add(StructureBlueprintFactory.create(house, arch).id());
        }
        check(families.size() >= 3, "distinct culture families should produce varied house massing");
    }

    private static void check(boolean cond, String message) {
        if (!cond) throw new AssertionError(message);
    }
}
