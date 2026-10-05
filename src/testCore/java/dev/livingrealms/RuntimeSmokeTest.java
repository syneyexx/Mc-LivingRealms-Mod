package dev.livingrealms;

import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.military.MilitaryUnitClass;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.player.FactionRank;
import dev.livingrealms.sim.player.PlayerSettlementFounder;
import dev.livingrealms.sim.ui.DashboardActionCommand;
import dev.livingrealms.sim.ui.DashboardActionService;
import dev.livingrealms.sim.ui.RealmDashboardBuilder;
import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.faction.ResourceType;

/**
 * Headless release-smoke covering founding, dashboard found name, save/load, day advance,
 * and escort identity namespace rules (the Minecraft entity path is audited separately).
 */
public final class RuntimeSmokeTest {
    public static void main(String[] args) {
        SimulationState state = new SimulationState(42L);
        DemoSeeder.seed(state);
        long day0 = state.clock().day();

        // Found via command-equivalent API.
        var founded = PlayerSettlementFounder.found(state, "player:smoke", "Smoke", "Smokehaven", new SimPosition(40_000, 40_000));
        check(founded.success(), "found must succeed: " + founded.reason());
        check(state.playerStanding("player:smoke").rank() == FactionRank.RULER, "founder is ruler");

        // Dashboard Found with custom name (second player far away).
        var dashFound = DashboardActionService.apply(
                state, "player:dash", new SimPosition(50_000, 50_000),
                new DashboardActionCommand(DashboardActionCommand.Action.FOUND_SETTLEMENT, 1, "Dashford"));
        check(dashFound.success(), "dashboard found custom name: " + dashFound.reason());
        check(state.factions().stream().flatMap(f -> f.settlements().stream()).anyMatch(s -> s.name().equals("Dashford")),
                "Dashford settlement must exist");

        // Ruler leave must fail honestly.
        var leave = DashboardActionService.apply(
                state, "player:smoke", new SimPosition(40_000, 40_000),
                new DashboardActionCommand(DashboardActionCommand.Action.FACTION_LEAVE, 1));
        check(!leave.success() && leave.reason().equals("ruler_cannot_leave"), "ruler leave reason");

        // Join thresholds: insufficient reputation.
        var outsider = state.playerStanding("player:outsider");
        long localFaction = state.factions().getFirst().id();
        outsider.adjustReputation(localFaction, 3);
        var joinLow = state.joinFaction("player:outsider", localFaction);
        check(!joinLow.success() && joinLow.reason().equals("insufficient_reputation"), "join rep gate");

        // Escort identity namespace: negative army id encodes shipment id.
        TradeShipment shipment = new TradeShipment(
                state.nextId(), founded.factionId(), state.factions().getFirst().id(),
                ResourceType.FOOD, 40, 80, new SimPosition(1, 1), new SimPosition(100, 100));
        shipment.restoreLogistics(founded.settlementId(), state.factions().getFirst().settlements().getFirst().id(),
                0, 0, state.clock().day(), state.clock().day() + 5, .2, .8, TradeShipment.LossState.NONE, 0);
        state.addShipment(shipment);
        long escortArmyId = -shipment.id();
        check(escortArmyId < 0, "escort namespace is negative");
        check(state.findArmy(escortArmyId).isEmpty(), "escort id must not resolve as a real army");
        check(state.findShipment(-escortArmyId).isPresent(), "negated escort id resolves shipment");
        check(MilitaryUnitClass.INFANTRY != null, "escort class available");

        // Dashboard command round-trip with name argument.
        DashboardActionCommand named = new DashboardActionCommand(DashboardActionCommand.Action.FOUND_SETTLEMENT, 1, "Riverstead");
        check(DashboardActionCommand.parse(named.encode()).equals(named), "named found command round-trip");

        // Save / load / advance one day.
        byte[] payload = SimulationStateCodec.encode(state);
        SimulationState restored = SimulationStateCodec.decode(payload, state.species());
        check(restored.playerStanding("player:smoke").rank() == FactionRank.RULER, "rulership survives save");
        check(restored.findShipment(shipment.id()).isPresent(), "shipment survives save");
        long before = restored.clock().day();
        restored.advanceDays(1);
        check(restored.clock().day() == before + 1, "advance one sim day");
        check(restored.clock().day() > day0, "clock moved past seed day");

        // Dashboard snapshot builds for founder (F12 path contract).
        var snap = RealmDashboardBuilder.build(restored, "player:smoke", new SimPosition(40_000, 40_000));
        check(snap.protocolVersion() == RealmDashboardSnapshot.PROTOCOL_VERSION, "dashboard protocol");
        check(snap.day() >= 0, "dashboard snapshot builds");
        check("RULER".equalsIgnoreCase(snap.player().rank()), "snapshot exposes ruler rank");

        System.out.println("PASS runtime smoke: found + dashboard name + ruler leave + join gate + escort namespace + save/load + day advance + dashboard build");
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }
}
