package dev.livingrealms;

import dev.livingrealms.sim.dialogue.DialogueIntent;
import dev.livingrealms.sim.dialogue.NaturalLanguageDialogueEngine;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.industry.IndustrialSite;
import dev.livingrealms.sim.industry.IndustryKind;
import dev.livingrealms.sim.naval.PortState;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.world.SettlementTransfer;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** F1/F2: capture and rebellion move citizens, claims, industry and ports. */
public final class SettlementTransferTest {
    private SettlementTransferTest() {}

    public static void main(String[] args) {
        captureMovesSociety();
        rebellionUsesSameTransfer();
        System.out.println("PASS settlement transfer: capture + rebellion migrate society");
    }

    private static void captureMovesSociety() {
        SimulationState state = new SimulationState(0xF1F1L);
        Faction attacker = new Faction(state.nextId(), "Attackers", "Warlord");
        Faction defender = new Faction(state.nextId(), "Defenders", "Warden");
        Settlement town = new Settlement(state.nextId(), "Border Town", new SimPosition(100, 100), 400, 450);
        defender.addSettlement(town);
        defender.addSettlement(new Settlement(state.nextId(), "Keep", new SimPosition(400, 100), 800, 900));
        attacker.addSettlement(new Settlement(state.nextId(), "Camp", new SimPosition(-100, 100), 200, 220));
        state.addFaction(attacker);
        state.addFaction(defender);
        state.ensureNamedRosters();
        SocialCitizen citizen = state.socialCitizens().stream()
                .filter(c -> c.settlementId() == town.id() && c.alive()).findFirst().orElseThrow();
        IndustrialSite site = new IndustrialSite(state.nextId(), defender.id(), town.id(), IndustryKind.SAWMILL, 1);
        state.addIndustrialSite(site);
        PortState port = new PortState(state.nextId(), defender.id(), town.id(), town.position(), 1);
        state.addPort(port);

        SettlementTransfer.transfer(state, town, defender, attacker);

        check(attacker.settlements().stream().anyMatch(s -> s.id() == town.id()), "attacker owns town");
        check(defender.settlements().stream().noneMatch(s -> s.id() == town.id()), "defender lost town");
        SocialCitizen moved = state.findSocialCitizen(citizen.id()).orElseThrow();
        check(moved.factionId() == attacker.id(), "citizen faction follows settlement");
        check(site.factionId() == attacker.id(), "industry follows settlement");
        check(port.factionId() == attacker.id(), "port follows settlement");
    }

    private static void rebellionUsesSameTransfer() {
        SimulationState state = new SimulationState(0xF2F2L);
        Faction parent = new Faction(state.nextId(), "Parent Realm", "King");
        Settlement capital = new Settlement(state.nextId(), "Capital", new SimPosition(0, 0), 2000, 2200);
        Settlement rebelTown = new Settlement(state.nextId(), "Rebel Town", new SimPosition(800, 0), 500, 560);
        rebelTown.adjustUnrest(0.95);
        parent.addSettlement(capital);
        parent.addSettlement(rebelTown);
        state.addFaction(parent);
        state.ensureNamedRosters();
        SocialCitizen before = state.socialCitizens().stream()
                .filter(c -> c.settlementId() == rebelTown.id() && c.alive()).findFirst().orElseThrow();
        for (int i = 0; i < 80; i++) {
            rebelTown.adjustUnrest(0.99);
            parent.government().restore(0.05, parent.government().legitimacy(), parent.government().corruption(),
                    parent.government().taxRate(), parent.government().lawEnforcement(), parent.government().yearsInPower());
            state.advanceDays(1);
            if (state.factions().size() > 1) break;
        }
        check(state.factions().size() > 1, "rebellion should create splinter");
        SocialCitizen after = state.findSocialCitizen(before.id()).orElseThrow();
        Faction owner = state.factions().stream()
                .filter(f -> f.settlements().stream().anyMatch(s -> s.id() == rebelTown.id()))
                .findFirst().orElseThrow();
        check(after.factionId() == owner.id(), "citizen faction matches settlement owner after rebellion");
        if (after.factionId() != parent.id()) {
            var engine = new NaturalLanguageDialogueEngine();
            var context = new dev.livingrealms.sim.dialogue.DialogueContext();
            var answer = engine.respond(state, after, "player:test", "welk koninkrijk is dit?", context);
            check(answer.intent() == DialogueIntent.ASK_FACTION, "dialogue intent after rebellion");
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
