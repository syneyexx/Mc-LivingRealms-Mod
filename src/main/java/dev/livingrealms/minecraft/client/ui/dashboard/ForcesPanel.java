package dev.livingrealms.minecraft.client.ui.dashboard;

import dev.livingrealms.sim.ui.DashboardActionCommand;
import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class ForcesPanel implements DashboardPanel {
    @Override
    public void rebuildWidgets(PanelHost host, RealmDashboardSnapshot snapshot, DashboardLayout layout, int page) {
        if (snapshot.player().memberFactionId() <= 0 || snapshot.map().armies().isEmpty()) return;
        var army = snapshot.map().armies().stream()
                .filter(a -> a.factionId() == snapshot.player().memberFactionId())
                .findFirst().orElse(null);
        if (army == null) return;
        int bw = Math.max(70, (layout.panelWidth - 28) / 3);
        host.addWidget(Button.builder(Component.literal("Defend home"),
                        b -> DashboardActionDispatcher.dispatch(DashboardActionCommand.Action.ARMY_DEFEND_HOME, army.id()))
                .bounds(layout.left + 10, layout.contentY + 4, bw - 3, 16).build());
        host.addWidget(Button.builder(Component.literal("Rally"),
                        b -> DashboardActionDispatcher.dispatch(DashboardActionCommand.Action.ARMY_RALLY, army.id()))
                .bounds(layout.left + 10 + bw, layout.contentY + 4, bw - 3, 16).build());
        host.addWidget(Button.builder(Component.literal("Stand down"),
                        b -> DashboardActionDispatcher.dispatch(DashboardActionCommand.Action.ARMY_STAND_DOWN, army.id()))
                .bounds(layout.left + 10 + bw * 2, layout.contentY + 4, bw - 3, 16).build());
    }

    @Override
    public List<DashboardLine> lines(RealmDashboardSnapshot snapshot, int page) {
        List<DashboardLine> lines = new ArrayList<>();
        var forces = snapshot.forces();
        lines.add(DashboardLine.header("Air forces"));
        for (var wing : forces.airWings()) {
            lines.add(DashboardLine.text("#" + wing.id() + " " + DashboardPanel.titleCase(wing.model()) + " × " + wing.aircraft()
                    + " • " + DashboardPanel.titleCase(wing.mission())));
            lines.add(DashboardLine.dim(DashboardPanel.titleCase(wing.role()) + " • fuel " + DashboardPanel.pct(wing.fuel())
                    + " • readiness " + DashboardPanel.pct(wing.readiness()) + " • exp " + DashboardPanel.pct(wing.experience())
                    + " • " + DashboardPanel.whole(wing.distanceBlocks()) + "m", 1));
        }
        if (forces.airWings().isEmpty()) lines.add(DashboardLine.dim("No active air wings for this realm.", 0));
        lines.add(DashboardLine.header("Naval forces"));
        for (var fleet : forces.fleets()) {
            lines.add(DashboardLine.text("#" + fleet.id() + " " + fleet.ships() + " ships • " + DashboardPanel.titleCase(fleet.mission())
                    + " • power " + DashboardPanel.whole(fleet.combatPower())));
            lines.add(DashboardLine.dim(fleet.composition() + " • fuel " + DashboardPanel.pct(fleet.fuel())
                    + " • supply " + DashboardPanel.pct(fleet.supply()) + " • readiness " + DashboardPanel.pct(fleet.readiness()), 1));
            if (fleet.embarkedPersonnel() > 0) {
                lines.add(DashboardLine.dim("Embarked personnel " + fleet.embarkedPersonnel()
                        + " • " + DashboardPanel.whole(fleet.distanceBlocks()) + "m", 2));
            }
        }
        if (forces.fleets().isEmpty()) lines.add(DashboardLine.dim("No active fleets for this realm.", 0));
        lines.add(DashboardLine.header("Ports"));
        for (var port : forces.ports()) {
            String status = port.operational() ? "ONLINE" : "OFFLINE";
            lines.add(DashboardLine.text("#" + port.id() + " " + port.settlement() + " • level " + port.level() + " • " + status));
            lines.add(DashboardLine.dim("Condition " + DashboardPanel.pct(port.condition()) + " • security "
                    + DashboardPanel.pct(port.security()) + " • " + DashboardPanel.whole(port.distanceBlocks()) + "m", 1));
        }
        if (forces.ports().isEmpty()) lines.add(DashboardLine.dim("No ports for this realm.", 0));
        return lines;
    }
}
