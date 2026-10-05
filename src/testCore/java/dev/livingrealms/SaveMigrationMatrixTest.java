package dev.livingrealms;

import dev.livingrealms.sim.config.SimulationConfig;
import dev.livingrealms.sim.civilization.*;
import dev.livingrealms.sim.diplomacy.TreatyType;
import dev.livingrealms.sim.ecology.PopulationGroup;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.government.GovernmentType;
import dev.livingrealms.sim.government.SuccessionLaw;
import dev.livingrealms.sim.industry.IndustrialSiteStatus;
import dev.livingrealms.sim.industry.IndustryKind;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.construction.ConstructionPhase;
import dev.livingrealms.sim.government.GrandProjectType;
import dev.livingrealms.sim.military.CampaignPlanType;
import dev.livingrealms.sim.player.CareerTrack;
import dev.livingrealms.sim.player.FactionRank;
import dev.livingrealms.sim.player.InfluenceInstitution;
import dev.livingrealms.sim.world.SimClock;
import dev.livingrealms.sim.world.SimulationState;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

/** Regression matrix for every historical save schema that the production decoder claims to support. */
public final class SaveMigrationMatrixTest {
    private static final int MAGIC = 0x4C52534D;
    private static final long SEED = 0x1234ABCDL;
    private static final long TICKS = 17L * SimClock.TICKS_PER_DAY + 321L;
    private static final long NEXT_ID = 9000L;
    private static final long FACTION_ID = 200L;
    private static final long SETTLEMENT_ID = 300L;

    private SaveMigrationMatrixTest() {}

    public static void main(String[] args) throws Exception {
        Map<String, dev.livingrealms.sim.ecology.SpeciesDefinition> catalog = dev.livingrealms.sim.ecology.SpeciesCatalog.starter();
        for (int schema = SimulationStateCodec.MIN_SUPPORTED_SCHEMA; schema <= SimulationStateCodec.SCHEMA_VERSION; schema++) {
            if (schema <= 2) continue; // Historical binary fixtures for schemas 1 and 2 are exercised by CoreSimulationTest.
            byte[] fixture = fixture(schema);
            SimulationState state = SimulationStateCodec.decode(fixture, catalog);
            verify(schema, state);
        }
        System.out.println("PASS save migration matrix: schemas 1-2 fixed fixtures + schemas 3-" + SimulationStateCodec.SCHEMA_VERSION + " versioned compatibility fixtures");
    }

    private static void verify(int schema, SimulationState state) {
        check(state.seed() == SEED, "schema " + schema + " seed");
        check(state.clock().gameTicks() == TICKS, "schema " + schema + " clock");
        check(state.peekNextId() == NEXT_ID, "schema " + schema + " nextId");
        check(state.regions().size() == 1, "schema " + schema + " region count");
        var region = state.regions().getFirst();
        check(close(region.plantBiomass(), 777.0), "schema " + schema + " plant biomass");
        check(region.populations().size() == 1, "schema " + schema + " population count");
        PopulationGroup population = region.populations().getFirst();
        check(population.speciesId().equals("rabbit") && close(population.population(), 55.0), "schema " + schema + " population identity");
        check(close(population.health(), .8) && close(population.hunger(), .2) && close(population.reproductiveFraction(), .6), "schema " + schema + " base population fields");
        if (schema >= 4) {
            check(close(population.thirst(), .3) && close(population.diseasePressure(), .1) && close(population.injuryPressure(), .05), "schema " + schema + " physiology");
            check(close(population.averageAgeDays(), 120.0) && close(population.migrationPressure(), .4), "schema " + schema + " age/migration");
        } else {
            check(close(population.thirst(), 0) && close(population.diseasePressure(), 0) && close(population.migrationPressure(), 0), "schema " + schema + " legacy population defaults");
        }
        if (schema >= 8) check(close(region.center().x(), 100) && close(region.center().z(), 200), "schema " + schema + " persisted ecology center");
        else check(close(region.center().x(), 11) && close(region.center().z(), 22), "schema " + schema + " derived ecology center");

        check(state.factions().size() == 2, "schema " + schema + " faction count");
        var faction = state.factions().getFirst();
        check(faction.id() == FACTION_ID && faction.name().equals("Legacy Realm"), "schema " + schema + " faction identity");
        check(faction.settlements().size() == 1, "schema " + schema + " settlement count");
        var settlement = faction.settlements().getFirst();
        check(settlement.id() == SETTLEMENT_ID && settlement.population() == 400 && settlement.housing() == 500, "schema " + schema + " settlement identity");
        if (schema >= 2) check(settlement.isConstructionCompleted("keep:legacy"), "schema " + schema + " construction completion");
        if (schema >= 4) {
            check(faction.government().type() == GovernmentType.FEUDAL_MONARCHY, "schema " + schema + " government type");
            check(faction.government().successionLaw() == SuccessionLaw.HEREDITARY, "schema " + schema + " succession law");
            check(close(faction.government().taxRate(), .18) && faction.government().yearsInPower() == 9, "schema " + schema + " government state");
            check(close(settlement.prosperity(), .7) && close(settlement.publicOrder(), .9), "schema " + schema + " society state");
            check(state.treaties().size() == 1 && state.treaties().getFirst().type() == TreatyType.NON_AGGRESSION, "schema " + schema + " strategic state");
        }
        check(state.shipments().size() == 1, "schema " + schema + " shipment state");
        if (schema >= 5) check(state.custody().size() == 1 && state.custody().getFirst().actorKey().equals("player:legacy-prisoner"), "schema " + schema + " custody state");
        if (schema >= 6) {
            check(state.ports().size() == 1 && state.ports().getFirst().settlementId() == SETTLEMENT_ID, "schema " + schema + " port state");
            var standing = state.findPlayerStanding("player:legacy-member").orElseThrow();
            check(standing.memberFactionId() == FACTION_ID && standing.rank() == FactionRank.SOLDIER && close(standing.reputationWith(FACTION_ID), 45), "schema " + schema + " player standing");
        }
        if (schema >= 7) {
            check(state.industrialSites().size() == 1, "schema " + schema + " industry count");
            var site = state.industrialSites().getFirst();
            check(site.kind() == IndustryKind.METALWORKS && site.status() == IndustrialSiteStatus.STARVED && close(site.condition(), .76), "schema " + schema + " industry state");
        }
        if (schema >= 11) {
            check(state.socialCitizens().size() == 1, "schema " + schema + " social citizen count");
            var citizen = state.socialCitizens().getFirst();
            check(citizen.name().equals("Legacy Citizen") && citizen.role() == CitizenRole.TRADER, "schema " + schema + " social citizen identity");
            check(citizen.memories().size() == 1 && citizen.relationships().size() == 1, "schema " + schema + " social memory/relations");
        } else check(state.socialCitizens().isEmpty(), "schema " + schema + " legacy social default");
        if (schema >= 12) {
            check(state.settlementCivilizations().size() == 1, "schema " + schema + " settlement civilization");
            var civ = state.settlementCivilizations().get(SETTLEMENT_ID);
            check(civ != null && close(civ.diseasePressure(), .22) && close(civ.education(), .61), "schema " + schema + " civilization metrics");
            var fc = state.factionCivilizations().get(FACTION_ID);
            check(fc != null && fc.cultureName().equals("Legacy Culture") && close(fc.spyStrength(999L), .44), "schema " + schema + " faction civilization");
            check(state.resourceClaims().size() == 1 && state.resourceClaims().getFirst().type() == ResourceClaimType.WATER, "schema " + schema + " resource claim");
            check(state.raids().size() == 1 && state.raids().getFirst().targetSettlementId() == 301L, "schema " + schema + " raid");
            check(state.legends().size() == 1 && state.legends().getFirst().title().equals("Legacy Stand"), "schema " + schema + " legend");
        } else {
            check(state.settlementCivilizations().isEmpty() && state.factionCivilizations().isEmpty() && state.resourceClaims().isEmpty() && state.raids().isEmpty() && state.legends().isEmpty(), "schema " + schema + " legacy civilization defaults");
        }
        if (schema >= 13) {
            check(state.households().size()==1 && state.socialCitizens().getFirst().householdId()==8400L && close(state.socialCitizens().getFirst().professionSkill(),.57), "schema 13 household/skill linkage");
            check(close(state.settlementCivilizations().get(SETTLEMENT_ID).knowledge(KnowledgeDomain.MEDICINE), .20+.03*KnowledgeDomain.MEDICINE.ordinal()), "schema 13 knowledge");
        } else check(state.households().isEmpty(), "schema " + schema + " legacy household default");
        if (schema >= 9) {
            SimulationConfig config = state.config();
            check(close(config.physicalRadiusBlocks(), 444) && close(config.regionalRadiusBlocks(), 2666), "schema " + schema + " config radii");
            check(config.maxPhysicalWildlife() == 211 && config.maxPhysicalCaravans() == 33 && config.strategicDaysPerStep() == 2, "schema " + schema + " config budgets");
        } else {
            check(state.config().equals(SimulationConfig.defaults()), "schema " + schema + " legacy config default");
        }
        check(state.history().all().size() == 1 && state.history().all().getFirst().message().equals("legacy migration marker"), "schema " + schema + " history");
        if (schema >= 16) {
            check(close(settlement.barnCapacity(), 512.0) && close(settlement.granaryCapacity(), 640.0), "schema " + schema + " barn/granary capacity");
            if (schema >= 18) {
                check(close(settlement.stockpile().get(ResourceType.FOOD), 222.0), "schema " + schema + " local food stockpile");
            } else {
                // Schema 16–17 FOOD is split into GRAIN+BREAD by the v18 goods migration.
                check(close(settlement.stockpile().get(ResourceType.GRAIN) + settlement.stockpile().get(ResourceType.BREAD), 222.0),
                        "schema " + schema + " local food migrated to grain/bread");
            }
            check(close(settlement.stockpile().get(ResourceType.WOOD), 88.0), "schema " + schema + " local wood stockpile");
        } else {
            check(settlement.barnCapacity() > 0 && settlement.granaryCapacity() > 0, "schema " + schema + " migrated storage capacity");
            check(settlement.edibleStock() > 0, "schema " + schema + " migrated starter food");
        }
        if (schema >= 17) {
            var citizen = state.socialCitizens().getFirst();
            check(citizen.appearancePacked() != 0 && citizen.cultureKey().equals("Legacy Culture"), "schema 17 citizen appearance/culture");
            check(citizen.socialClass() == SocialClass.MERCHANT && citizen.employmentStatus() == EmploymentStatus.EMPLOYED, "schema 17 citizen social class");
            var standing = state.findPlayerStanding("player:legacy-member").orElseThrow();
            check(close(standing.influenceWith(FACTION_ID, InfluenceInstitution.MILITARY), 22.0), "schema 17 influence");
            check(standing.careerTrack() == CareerTrack.MILITARY && standing.careerRankIndex() == 2, "schema 17 career");
            check(state.debts().size() == 1 && state.grandProjects().size() == 1 && state.campaignPlans().size() == 1, "schema 17 debt/project/plan");
            check(state.shipments().getFirst().originSettlementId() == SETTLEMENT_ID && close(state.shipments().getFirst().risk(), .33), "schema 17 shipment logistics");
        }
    }


    private static int resourceCount(int schema) {
        return schema >= 18 ? ResourceType.values().length : ResourceType.LEGACY_COUNT;
    }

    private static void writeResources(DataOutputStream out, int schema, java.util.function.ToDoubleFunction<ResourceType> values) throws IOException {
        for (int i = 0; i < resourceCount(schema); i++) out.writeDouble(values.applyAsDouble(ResourceType.values()[i]));
    }

    private static byte[] fixture(int schema) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(bytes))) {
            out.writeInt(MAGIC);
            out.writeInt(schema);
            out.writeLong(SEED);
            out.writeLong(TICKS);
            out.writeLong(NEXT_ID);
            writeRegions(out, schema);
            writeFactions(out, schema);
            if (schema >= 3) writeShipments(out);
            if (schema >= 4) writeStrategic(out);
            if (schema >= 5) writeCustody(out);
            if (schema >= 6) writeNavalAndPlayers(out);
            if (schema >= 7) writeIndustry(out);
            if (schema >= 9) writeConfig(out);
            if (schema >= 11) writeSocial(out);
            if (schema >= 12) writeCivilization(out);
            if (schema >= 13) writeHumanity(out, schema);
            if (schema >= 14) out.writeInt(0); // pirate hideouts
            if (schema >= 15) out.writeInt(0); // siege equipment extensions
            if (schema >= 16) writeSettlementEconomy(out, schema);
            if (schema >= 17) writeFinalProduct(out);
            if (schema >= 18) writeGoodsOrigins(out);
            if (schema >= 19) writeProvenanceAndSites(out);
            writeHistory(out);
        }
        return bytes.toByteArray();
    }

    private static void writeFinalProduct(DataOutputStream out) throws IOException {
        out.writeInt(1); // citizen social extensions
        out.writeLong(850L);out.writeLong(12345L);writeString(out,"Legacy Culture");writeString(out,"Legacy Faith");
        out.writeInt(SocialClass.MERCHANT.ordinal());out.writeDouble(.42);writeString(out,"market");
        out.writeInt(SocialClass.MERCHANT.ordinal());out.writeDouble(.35);writeString(out,"trader");out.writeInt(EmploymentStatus.EMPLOYED.ordinal());
        out.writeInt(1); // player influence/career
        writeString(out,"player:legacy-member");
        out.writeInt(1);out.writeLong(FACTION_ID);out.writeInt(1);out.writeInt(InfluenceInstitution.MILITARY.ordinal());out.writeDouble(22.0);
        out.writeInt(CareerTrack.MILITARY.ordinal());out.writeInt(2);out.writeDouble(150.0);
        out.writeInt(1); // debts
        out.writeLong(8601L);out.writeLong(FACTION_ID);writeString(out,"merchant:legacy_house");out.writeDouble(100.0);out.writeDouble(.08);out.writeDouble(90.0);out.writeLong(10L);out.writeLong(200L);out.writeDouble(.4);out.writeBoolean(false);out.writeBoolean(true);
        out.writeInt(1); // grand projects
        out.writeLong(8602L);out.writeLong(FACTION_ID);out.writeLong(SETTLEMENT_ID);out.writeInt(GrandProjectType.CIVIC_MONUMENT.ordinal());out.writeLong(12L);out.writeDouble(.25);out.writeInt(ConstructionPhase.FOUNDATION.ordinal());writeString(out,"");out.writeBoolean(false);out.writeBoolean(true);out.writeDouble(40.0);out.writeDouble(5.0);out.writeDouble(8.0);out.writeDouble(6.0);out.writeDouble(1.0);out.writeInt(20);out.writeBoolean(false);out.writeBoolean(false);out.writeBoolean(false);out.writeBoolean(false);
        out.writeInt(1); // campaign plans
        out.writeLong(8603L);out.writeLong(FACTION_ID);out.writeLong(500L);out.writeInt(CampaignPlanType.DEFEND_BORDER.ordinal());out.writeLong(SETTLEMENT_ID);out.writeInt(80);out.writeLong(14L);out.writeBoolean(true);
        out.writeInt(1); // shipment logistics
        out.writeLong(400L);out.writeLong(SETTLEMENT_ID);out.writeLong(301L);out.writeLong(0L);out.writeInt(0);out.writeLong(16L);out.writeLong(20L);out.writeDouble(.33);out.writeDouble(.5);out.writeInt(0);out.writeInt(0);
    }

    private static void writeSettlementEconomy(DataOutputStream out, int schema) throws IOException {
        out.writeInt(2);
        out.writeLong(SETTLEMENT_ID);
        out.writeDouble(512.0);
        out.writeDouble(640.0);
        writeResources(out, schema, type -> type == ResourceType.FOOD ? 222.0 : type == ResourceType.WOOD ? 88.0 : 0.0);
        out.writeLong(301L);
        out.writeDouble(400.0);
        out.writeDouble(500.0);
        writeResources(out, schema, type -> type == ResourceType.FOOD ? 111.0 : 0.0);
    }

    private static void writeGoodsOrigins(DataOutputStream out) throws IOException {
        out.writeInt(2);
        out.writeLong(SETTLEMENT_ID);out.writeInt(1);writeString(out,"keep:legacy");out.writeInt(0);
        out.writeLong(301L);out.writeInt(0);
    }

    private static void writeProvenanceAndSites(DataOutputStream out) throws IOException {
        // Settlement provenance for both fixture settlements.
        out.writeInt(2);
        out.writeLong(SETTLEMENT_ID);out.writeInt(0);out.writeBoolean(false);out.writeInt(0); // AUTHORED_SEED, not anchored, AUTO
        out.writeLong(301L);out.writeInt(0);out.writeBoolean(false);out.writeInt(0);
        out.writeInt(0); // outlying sites
        out.writeInt(0); // citizen journeys
        out.writeInt(0); // roadside sites
        out.writeInt(0); // underworld profiles
    }

    private static void writeRegions(DataOutputStream out, int schema) throws IOException {
        out.writeInt(1);
        out.writeLong(100L);
        writeString(out, "temperate_forest");
        out.writeDouble(42.5);
        if (schema >= 8) writePosition(out, 100, 200);
        out.writeDouble(777.0);
        out.writeInt(1);
        out.writeLong(101L);
        writeString(out, "rabbit");
        writeString(out, "temperate_forest");
        writePosition(out, 11, 22);
        out.writeDouble(55.0);
        out.writeDouble(.8);
        out.writeDouble(.2);
        out.writeDouble(.6);
        if (schema >= 4) {
            out.writeDouble(.3);
            out.writeDouble(.1);
            out.writeDouble(.05);
            out.writeDouble(120.0);
            out.writeDouble(.4);
        }
    }

    private static void writeFactions(DataOutputStream out, int schema) throws IOException {
        out.writeInt(2);
        out.writeLong(FACTION_ID);
        writeString(out, "Legacy Realm");
        writeString(out, "Legacy Ruler");
        out.writeDouble(500.0);
        out.writeDouble(1.25);
        if (schema >= 4) writeGovernment(out);
        writeResources(out, schema, type -> 12.0 + type.ordinal());
        out.writeInt(1);
        out.writeLong(SETTLEMENT_ID);
        writeString(out, "Legacy City");
        writePosition(out, 30, 40);
        out.writeInt(400);
        out.writeInt(500);
        out.writeDouble(.8);
        if (schema >= 4) {
            out.writeDouble(.7);
            out.writeDouble(.1);
            out.writeDouble(.8);
            out.writeDouble(.9);
            out.writeDouble(.85);
        }
        if (schema >= 2) {
            out.writeInt(1);
            writeString(out, "keep:legacy");
        }
        if (schema >= 10) out.writeInt(0);
        out.writeInt(0); // armies
        out.writeInt(0); // relations

        out.writeLong(999L);
        writeString(out, "Legacy Counterparty");
        writeString(out, "Counterparty Ruler");
        out.writeDouble(450.0);
        out.writeDouble(.9);
        if (schema >= 4) writeGovernment(out);
        writeResources(out, schema, type -> 8.0 + type.ordinal());
        out.writeInt(1);
        out.writeLong(301L);
        writeString(out, "Counterparty City");
        writePosition(out, 130, 140);
        out.writeInt(350);
        out.writeInt(420);
        out.writeDouble(.7);
        if (schema >= 4) {
            out.writeDouble(.65);
            out.writeDouble(.12);
            out.writeDouble(.75);
            out.writeDouble(.82);
            out.writeDouble(.78);
        }
        if (schema >= 2) out.writeInt(0);
        if (schema >= 10) out.writeInt(0);
        out.writeInt(0); // armies
        out.writeInt(0); // relations
    }

    private static void writeGovernment(DataOutputStream out) throws IOException {
        out.writeInt(GovernmentType.FEUDAL_MONARCHY.ordinal());
        out.writeInt(SuccessionLaw.HEREDITARY.ordinal());
        out.writeLong(201L);
        writeString(out, "Ruler Profile");
        out.writeInt(44);
        out.writeDouble(.9);
        out.writeDouble(.7);
        out.writeDouble(.8);
        out.writeDouble(.6);
        out.writeDouble(.75);
        out.writeDouble(.66);
        out.writeDouble(.77);
        out.writeDouble(.11);
        out.writeDouble(.18);
        out.writeDouble(.55);
        out.writeLong(9L);
    }

    private static void writeShipments(DataOutputStream out) throws IOException {
        out.writeInt(1);
        out.writeLong(400L);
        out.writeLong(FACTION_ID);
        out.writeLong(999L);
        out.writeInt(ResourceType.IRON.ordinal());
        out.writeDouble(24.0);
        out.writeDouble(88.0);
        writePosition(out, 30, 40);
        writePosition(out, 130, 140);
        out.writeDouble(.35);
    }

    private static void writeStrategic(DataOutputStream out) throws IOException {
        out.writeInt(1); // treaties
        out.writeLong(500L);
        out.writeLong(FACTION_ID);
        out.writeLong(999L);
        out.writeInt(TreatyType.NON_AGGRESSION.ordinal());
        out.writeLong(10L);
        out.writeLong(120L);
        out.writeBoolean(true);
        out.writeInt(0); // wars
        out.writeInt(0); // routes
        out.writeInt(0); // objectives
        out.writeInt(0); // sieges
        out.writeInt(0); // air wings
        out.writeInt(0); // bounties
        out.writeInt(0); // wanted profiles
        out.writeInt(0); // crime incidents
    }

    private static void writeCustody(DataOutputStream out) throws IOException {
        out.writeInt(1);
        out.writeLong(600L);
        writeString(out, "player:legacy-prisoner");
        out.writeLong(FACTION_ID);
        out.writeLong(15L);
        out.writeLong(25L);
        out.writeDouble(125.0);
        writeString(out, "legacy sentence");
        out.writeBoolean(true);
    }

    private static void writeNavalAndPlayers(DataOutputStream out) throws IOException {
        out.writeInt(1); // ports
        out.writeLong(700L);
        out.writeLong(FACTION_ID);
        out.writeLong(SETTLEMENT_ID);
        writePosition(out, 35, 45);
        out.writeInt(2);
        out.writeDouble(.82);
        out.writeDouble(.71);
        out.writeBoolean(true);
        out.writeInt(0); // fleets
        out.writeInt(1); // player standings
        writeString(out, "player:legacy-member");
        out.writeInt(1);
        out.writeLong(FACTION_ID);
        out.writeDouble(45.0);
        out.writeLong(FACTION_ID);
        out.writeInt(FactionRank.SOLDIER.ordinal());
        out.writeLong(8L);
        out.writeDouble(140.0);
        out.writeInt(1);
    }

    private static void writeIndustry(DataOutputStream out) throws IOException {
        out.writeInt(1);
        out.writeLong(800L);
        out.writeLong(FACTION_ID);
        out.writeLong(SETTLEMENT_ID);
        out.writeInt(IndustryKind.METALWORKS.ordinal());
        out.writeInt(3);
        out.writeDouble(.76);
        out.writeInt(IndustrialSiteStatus.STARVED.ordinal());
        out.writeInt(4);
        out.writeInt(2);
        out.writeInt(6);
        out.writeDouble(.63);
    }

    private static void writeConfig(DataOutputStream out) throws IOException {
        out.writeDouble(444.0);
        out.writeDouble(2666.0);
        out.writeInt(211);
        out.writeInt(33);
        out.writeInt(121);
        out.writeInt(47);
        out.writeInt(231);
        out.writeInt(2);
        out.writeDouble(1.1);
        out.writeDouble(.17);
        out.writeDouble(.73);
        out.writeDouble(14.0);
    }

    private static void writeSocial(DataOutputStream out) throws IOException {
        out.writeInt(1);
        out.writeLong(850L);out.writeLong(FACTION_ID);out.writeLong(SETTLEMENT_ID);out.writeInt(0);writeString(out,"Legacy Citizen");out.writeInt(3);out.writeInt(CitizenRole.TRADER.ordinal());out.writeLong(-30L*365L);out.writeDouble(.88);out.writeDouble(14.0);out.writeBoolean(true);
        out.writeDouble(.8);out.writeDouble(.7);out.writeDouble(.6);out.writeDouble(.5);out.writeDouble(.65);
        out.writeDouble(.2);out.writeDouble(.8);out.writeDouble(.4);out.writeDouble(.3);out.writeDouble(.75);out.writeDouble(.1);
        out.writeInt(1);out.writeLong(16L);out.writeInt(MemoryType.RUMOR.ordinal());writeString(out,"bandits");writeString(out,"merchant:Tess");writeString(out,"Bandits were seen by the old road.");writePosition(out,80,90);out.writeDouble(.7);out.writeDouble(.6);
        out.writeInt(1);writeString(out,"player:legacy-member");out.writeDouble(.4);out.writeDouble(.1);out.writeDouble(0);out.writeDouble(.05);out.writeDouble(.7);out.writeInt(FamilyBond.NONE.ordinal());
    }

    private static void writeCivilization(DataOutputStream out) throws IOException {
        out.writeInt(1); // settlement profiles
        out.writeLong(SETTLEMENT_ID);out.writeLong(FACTION_ID);out.writeDouble(.71);out.writeDouble(.22);out.writeDouble(.61);out.writeDouble(.84);out.writeDouble(.18);out.writeDouble(.27);out.writeDouble(.73);out.writeDouble(.31);out.writeDouble(.42);out.writeLong(3L);out.writeLong(7L);
        out.writeInt(1); // faction profiles
        out.writeLong(FACTION_ID);writeString(out,"Legacy Culture");writeString(out,"Legacy Faith");writeString(out,"Legacy Speech");out.writeDouble(.72);out.writeDouble(.55);out.writeDouble(.61);out.writeDouble(.21);out.writeDouble(.48);out.writeLong(0L);out.writeDouble(0);out.writeInt(1);out.writeLong(999L);out.writeDouble(.44);
        out.writeInt(1); // claims
        out.writeLong(8100L);out.writeLong(FACTION_ID);out.writeLong(SETTLEMENT_ID);out.writeInt(ResourceClaimType.WATER.ordinal());writePosition(out,35,45);out.writeDouble(.75);out.writeLong(999L);out.writeBoolean(true);
        out.writeInt(1); // raids
        out.writeLong(8200L);out.writeLong(FACTION_ID);out.writeLong(SETTLEMENT_ID);out.writeLong(301L);out.writeLong(12L);out.writeInt(14);out.writeDouble(.65);out.writeDouble(.4);out.writeBoolean(true);out.writeBoolean(false);
        out.writeInt(1); // legends
        out.writeLong(8300L);out.writeLong(12L);out.writeLong(FACTION_ID);out.writeLong(SETTLEMENT_ID);writeString(out,"battle:legacy");writeString(out,"Legacy Stand");writeString(out,"The city held its ground.");out.writeDouble(.8);out.writeBoolean(true);
    }


    private static void writeHumanity(DataOutputStream out, int schema) throws IOException {
        out.writeInt(1); // citizen household links
        out.writeLong(850L);out.writeLong(8400L);out.writeDouble(.57);
        out.writeInt(1); // settlement civilization extensions
        out.writeLong(SETTLEMENT_ID);out.writeDouble(.23);for(KnowledgeDomain d:KnowledgeDomain.values())out.writeDouble(.20+.03*d.ordinal());
        out.writeInt(1); // faction civilization extensions
        out.writeLong(FACTION_ID);out.writeDouble(.62);out.writeDouble(.51);out.writeDouble(.58);out.writeDouble(.42);out.writeDouble(.67);out.writeDouble(.61);out.writeDouble(.56);out.writeDouble(.72);out.writeDouble(.64);
        out.writeInt(1); // households
        out.writeLong(8400L);out.writeLong(FACTION_ID);out.writeLong(SETTLEMENT_ID);out.writeLong(3L);out.writeBoolean(true);out.writeDouble(33.0);writeString(out,"house:legacy");out.writeInt(1);out.writeLong(850L);out.writeInt(0);
        out.writeInt(0); // epidemics
        out.writeInt(0); // migrations
        out.writeInt(0); // justice
        out.writeInt(0); // hidden caches — empty (resource-count schema sensitive when non-empty)
        out.writeInt(0); // pirates
        out.writeInt(0); // diplomatic marriages
        out.writeInt(0); // dynasties
        out.writeInt(0); // civic events
        out.writeInt(0); // intelligence operations
        out.writeInt(0); // propaganda campaigns
        out.writeInt(0); // ruin sites
        out.writeInt(0); // assistance tasks
    }
    private static void writeHistory(DataOutputStream out) throws IOException {
        out.writeInt(1);
        out.writeLong(17L);
        writeString(out, "migration_fixture");
        writeString(out, "legacy migration marker");
    }

    private static void writePosition(DataOutputStream out, double x, double z) throws IOException {
        out.writeDouble(x);
        out.writeDouble(z);
    }

    private static void writeString(DataOutput out, String value) throws IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static boolean close(double a, double b) { return Math.abs(a - b) <= 1.0e-9; }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
