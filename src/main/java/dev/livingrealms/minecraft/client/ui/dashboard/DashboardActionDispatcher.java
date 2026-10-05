package dev.livingrealms.minecraft.client.ui.dashboard;

import dev.livingrealms.minecraft.client.ui.DashboardClientState;
import dev.livingrealms.sim.ui.DashboardActionCommand;

/**
 * Client-side intent dispatcher. Sends dashboard actions to the server without
 * re-checking canonical authorization — the server remains authoritative.
 */
public final class DashboardActionDispatcher {
    private DashboardActionDispatcher() {}

    public static void dispatch(DashboardActionCommand command) {
        if (command == null) return;
        DashboardClientState.sendAction(command);
    }

    public static void dispatch(DashboardActionCommand.Action action, long targetId) {
        dispatch(new DashboardActionCommand(action, targetId));
    }

    public static void dispatch(DashboardActionCommand.Action action, long targetId, String payload) {
        dispatch(new DashboardActionCommand(action, targetId, payload));
    }

    public static void dispatch(DashboardActionCommand.Action action, long targetId, long secondaryId) {
        dispatch(new DashboardActionCommand(action, targetId, secondaryId));
    }

    public static void dispatch(DashboardActionCommand.Action action, long targetId, long secondaryId, String payload) {
        dispatch(new DashboardActionCommand(action, targetId, secondaryId, payload));
    }

    public static void requestRefresh() {
        DashboardClientState.requestRefresh();
    }

    public static void requestOpenMap() {
        DashboardClientState.requestOpenMap();
    }
}
