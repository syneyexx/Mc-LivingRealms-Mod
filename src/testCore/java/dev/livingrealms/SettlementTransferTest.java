package dev.livingrealms;

import dev.livingrealms.sim.dialogue.DialogueIntent;
import dev.livingrealms.sim.dialogue.NaturalLanguageDialogueEngine;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.industry.IndustrialSite;
import dev.livingrealms.sim.industry.IndustryKind;
import dev.livingrealms.sim.naval.PortState;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.society.RebellionEngine;
import dev.livingrealms.sim.util.DeterministicRng;
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
        rebelTown.adjustUnrest(0.99);
        parent.addSettlement(capital);
        parent.addSettlement(rebelTown);
        state.addFaction(parent);
        state.ensureNamedRosters();
        SocialCitizen before = state.socialCitizens().stream()
                .filter(c -> c.settlementId() == rebelTown.id() && c.alive()).findFirst().orElseThrow();
        // Same registration+transfer path RebellionEngine uses (avoids multi-day migration relocating the citizen).
        Faction splinter = new Faction(state.nextId(), rebelTown.name() + " Free State", "Council of " + rebelTown.name());
        state.addFaction(splinter);
        Settlement moved = SettlementTransfer.transfer(state, rebelTown, parent, splinter);
        check(moved != null, "rebellion transfer must move settlement");
        splinter.relationWith(parent.id()).declareWar();
        parent.relationWith(splinter.id()).declareWar();
        // Prove RebellionEngine also invokes that path under forced unrest.
        SimulationState engineState = new SimulationState(0xF2F3L);
        Faction p2 = new Faction(engineState.nextId(), "Parent2", "King");
        Settlement c2 = new Settlement(engineState.nextId(), "Cap2", new SimPosition(0, 0), 2000, 2200);
        Settlement r2 = new Settlement(engineState.nextId(), "Rebel2", new SimPosition(900, 0), 600, 650);
        r2.adjustUnrest(0.99);
        p2.addSettlement(c2);
        p2.addSettlement(r2);
        p2.government().restore(0.05, 0.2, 0.4, 0.2, 0.2, 1);
        engineState.addFaction(p2);
        boolean rebelled = false;
        for (long seed = 1; seed < 2_000 && !rebelled; seed++) {
            SimulationState trial = new SimulationState(0xF2F3L);
            Faction tp = new Faction(trial.nextId(), "Parent2", "King");
            Settlement tc = new Settlement(trial.nextId(), "Cap2", new SimPosition(0, 0), 2000, 2200);
            Settlement tr = new Settlement(trial.nextId(), "Rebel2", new SimPosition(900, 0), 600, 650);
            tr.adjustUnrest(0.99);
            tp.addSettlement(tc);
            tp.addSettlement(tr);
            tp.government().restore(0.05, 0.2, 0.4, 0.2, 0.2, 1);
            trial.addFaction(tp);
            trial.ensureNamedRosters();
            SocialCitizen marked = trial.socialCitizens().stream()
                    .filter(c -> c.settlementId() == tr.id() && c.alive()).findFirst().orElseThrow();
            new RebellionEngine().simulateDay(trial, new DeterministicRng(seed), 0.5);
            if (trial.factions().size() > 1) {
                SocialCitizen afterEngine = trial.findSocialCitizen(marked.id()).orElseThrow();
                Faction ownerEngine = trial.factions().stream()
                        .filter(f -> f.settlements().stream().anyMatch(s -> s.id() == tr.id()))
                        .findFirst().orElseThrow();
                check(afterEngine.factionId() == ownerEngine.id(), "RebellionEngine citizen faction");
                check(afterEngine.settlementId() == tr.id(), "RebellionEngine citizen settlement");
                rebelled = true;
            }
        }
        check(rebelled, "RebellionEngine must secede under high unrest within seed search");

        SocialCitizen after = state.findSocialCitizen(before.id()).orElseThrow();
        Faction owner = state.factions().stream()
                .filter(f -> f.settlements().stream().anyMatch(s -> s.id() == rebelTown.id()))
                .findFirst().orElseThrow();
        check(after.factionId() == owner.id(), "citizen faction matches settlement owner after rebellion");
        check(after.settlementId() == rebelTown.id(), "citizen remains in rebel settlement");
        var engine = new NaturalLanguageDialogueEngine();
        var context = new dev.livingrealms.sim.dialogue.DialogueContext();
        var answer = engine.respond(state, after, "player:test", "welk koninkrijk is dit?", context);
        check(answer.intent() == DialogueIntent.ASK_FACTION, "dialogue intent after rebellion");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
