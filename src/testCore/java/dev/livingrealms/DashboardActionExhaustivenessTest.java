package dev.livingrealms;

import dev.livingrealms.sim.ui.DashboardActionCommand;
import dev.livingrealms.sim.ui.DashboardActionService;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** A6: every DashboardActionCommand.Action is reachable via the exhaustive service switch. */
public final class DashboardActionExhaustivenessTest {
    private DashboardActionExhaustivenessTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0xA6A6L);
        SimPosition pos = new SimPosition(0, 0);
        for (DashboardActionCommand.Action action : DashboardActionCommand.Action.values()) {
            // Must not throw — unreachable arms return Result, never fall out of switch.
            DashboardActionService.Result result = DashboardActionService.apply(
                    state, "player:exhaust", pos, new DashboardActionCommand(action, 1));
            check(result != null && result.reason() != null, "action " + action + " must return a result");
        }
        System.out.println("PASS dashboard action exhaustiveness: " + DashboardActionCommand.Action.values().length + " actions covered");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
