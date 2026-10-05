package dev.livingrealms;

import dev.livingrealms.sim.civilization.AssistanceContributionEngine;
import dev.livingrealms.sim.civilization.AssistanceTask;
import dev.livingrealms.sim.civilization.AssistanceTaskType;
import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.faction.Army;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.law.CrimeType;
import dev.livingrealms.sim.player.PlayerSettlementFounder;
import dev.livingrealms.sim.social.CitizenPersonality;
import dev.livingrealms.sim.social.MemoryType;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.ui.DashboardActionCommand;
import dev.livingrealms.sim.ui.DashboardActionService;
import dev.livingrealms.sim.ui.RealmDashboardBuilder;
import dev.livingrealms.sim.ui.RealmDashboardCodec;
import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import dev.livingrealms.sim.underworld.UnderworldActions;
import dev.livingrealms.sim.underworld.UnderworldContractType;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** Wave 12–14: aid/crime feedback loops + underworld/war-room dashboard surfaces. */
public final class PlayerExperienceSurfaceTest {
    private PlayerExperienceSurfaceTest() {}

    public static void main(String[] args) {
        aidDeepensMoodMemoryAndStock();
        crimeDrainsPropertyAndSeedsVictimMemory();
        underworldDashboardAcceptAndCodec();
        warRoomSnapshotEscortNeverEnemy();
        System.out.println("PASS player experience surfaces: aid/crime feedback + underworld/war-room protocol-20 sections");
    }

    private static void aidDeepensMoodMemoryAndStock() {
        SimulationState state = new SimulationState(0xA1D12L);
        Faction faction = new Faction(state.nextId(), "Aidland", "Mayor");
        Settlement town = new Settlement(state.nextId(), "Hungerford", new SimPosition(0, 0), 180, 120);
        town.setFoodSecurity(.12);
        faction.addSettlement(town);
        state.addFaction(faction);
        SocialCitizen citizen = new SocialCitizen(state.nextId(), faction.id(), town.id(), 0, "Mira", 0,
                CitizenRole.FARMER, 0, new CitizenPersonality(.5, .5, .5, .5, .5, .5));
        state.addSocialCitizen(citizen);
        double statusBefore = citizen.needs().status();
        AssistanceTask task = new AssistanceTask(state.nextId(), faction.id(), town.id(), 0, 45,
                AssistanceTaskType.FOOD_RELIEF, "pressure:food_relief", .9);
        state.addAssistanceTask(task);
        double foodStock = town.stockpile().get(ResourceType.FOOD);
        var ok = AssistanceContributionEngine.contributeVerified(state, "player:helper", town.position(), task.id(), 8);
        check(ok.success(), "aid ok: " + ok.reason());
        check(town.stockpile().get(ResourceType.FOOD) > foodStock, "stock rises");
        check(citizen.needs().status() >= statusBefore - 1e-9, "mood/status not worse after aid");
        check(citizen.memories().stream().anyMatch(m -> m.type() == MemoryType.HELPED_BY), "helped memory");
        check(state.history().all().stream().anyMatch(e -> e.type().equals("assistance_contribution")
                && e.message().contains("settlement=" + town.id())), "history carries settlement for rumor");
    }

    private static void crimeDrainsPropertyAndSeedsVictimMemory() {
        SimulationState state = new SimulationState(0xC81E01L);
        Faction faction = new Faction(state.nextId(), "Lawland", "Sheriff");
        Settlement town = new Settlement(state.nextId(), "Watchford", new SimPosition(10, 10), 200, 180);
        town.stockpile().add(ResourceType.FOOD, 80);
        faction.addSettlement(town);
        state.addFaction(faction);
        SocialCitizen victim = new SocialCitizen(state.nextId(), faction.id(), town.id(), 0, "Otto", 0,
                CitizenRole.TRADER, 0, new CitizenPersonality(.5, .5, .5, .5, .5, .5));
        state.addSocialCitizen(victim);
        double foodBefore = town.stockpile().get(ResourceType.FOOD);
        double orderBefore = town.publicOrder();
        var result = state.reportCrime("player:thief", faction.id(), CrimeType.THEFT, 40, town.position(),
                true, 2, "citizen:" + victim.id(), "market_stall");
        check(result.registered(), "crime registered");
        check(town.stockpile().get(ResourceType.FOOD) < foodBefore, "property loss from stock");
        check(town.publicOrder() <= orderBefore, "public order reacts");
        check(victim.memories().stream().anyMatch(m -> m.type() == MemoryType.STOLEN_FROM), "victim memory");
        check(state.crimeLedger().findProfile("player:thief").isPresent(), "heat/bounty profile");
    }

    private static void underworldDashboardAcceptAndCodec() {
        SimulationState state = new SimulationState(0x55DD13L);
        Faction realm = new Faction(state.nextId(), "Shadowvale", "Mayor");
        realm.government().adjustCorruption(.5);
        Settlement town = new Settlement(state.nextId(), "Shadowport", new SimPosition(100, 100), 600, 700);
        realm.addSettlement(town);
        state.addFaction(realm);
        check(UnderworldActions.offerContract(state, UnderworldContractType.THEFT, realm.id(),
                "merchant", 20, 50, 14).success(), "offer");
        long id = state.underworldContracts().getFirst().id();
        var accept = DashboardActionService.apply(state, "player:thief", town.position(),
                new DashboardActionCommand(DashboardActionCommand.Action.UNDERWORLD_ACCEPT, id));
        check(accept.success(), "dashboard accept: " + accept.reason());
        var bribe = DashboardActionService.apply(state, "player:thief", town.position(),
                new DashboardActionCommand(DashboardActionCommand.Action.UNDERWORLD_BRIBE, realm.id(), "80"));
        check(bribe.success(), "dashboard bribe: " + bribe.reason());
        RealmDashboardSnapshot snap = RealmDashboardBuilder.build(state, "player:thief", town.position());
        check(!snap.underworld().contracts().isEmpty(), "underworld contracts in snapshot");
        check(snap.underworld().contracts().stream().anyMatch(c -> c.acceptedByYou()), "accepted visible");
        check(snap.underworld().briberySkill() > 0 || snap.underworld().streetCred() >= 0, "profile visible");
        String json = RealmDashboardCodec.encode(snap);
        RealmDashboardSnapshot round = RealmDashboardCodec.decode(json);
        check(round.underworld().contracts().size() == snap.underworld().contracts().size(), "underworld codec");
        check(round.warRoom() != null, "warRoom section present");
    }

    private static void warRoomSnapshotEscortNeverEnemy() {
        SimulationState state = new SimulationState(0x57415214L);
        var founded = PlayerSettlementFounder.found(
                state, "player:warlord", "Warlord", "Warford", new SimPosition(12_000, 12_000));
        check(founded.success(), "found: " + founded.reason());
        Faction self = state.findFaction(founded.factionId()).orElseThrow();
        Faction enemy = new Faction(state.nextId(), "Hostile March", "Rival");
        Settlement enemyTown = new Settlement(state.nextId(), "Rivalmarch", new SimPosition(14_200, 12_000), 800, 900);
        enemy.addSettlement(enemyTown);
        self.addArmy(new Army(state.nextId(), self.id(), new SimPosition(12_080, 12_000), 200));
        state.addFaction(enemy);
        self.relationWith(enemy.id()).adjust(-40);
        enemy.relationWith(self.id()).adjust(-40);
        RealmDashboardSnapshot snap = RealmDashboardBuilder.build(state, "player:warlord", new SimPosition(12_000, 12_000));
        check(!snap.warRoom().armies().isEmpty(), "army details");
        check(snap.warRoom().armies().getFirst().personnel() > 0, "army strength");
        for (var escort : snap.warRoom().escortTargets()) {
            check(escort.id() != enemyTown.id(), "escort must never be enemy settlement");
        }
        if (!snap.warRoom().enemies().isEmpty()) {
            var e = snap.warRoom().enemies().getFirst();
            String goal = e.validGoals().isEmpty() ? "CONQUEST" : e.validGoals().getFirst();
            var war = DashboardActionService.apply(state, "player:warlord", new SimPosition(12_000, 12_000),
                    new DashboardActionCommand(DashboardActionCommand.Action.DECLARE_WAR, e.factionId(),
                            e.suggestedTargetSettlementId(), goal));
            check(war.success() || "petition_only".equals(war.reason()) || war.reason().contains("already"),
                    "declare/petition path: " + war.reason());
        }
    }

    private static void check(boolean v, String m) {
        if (!v) throw new AssertionError(m);
    }
}
