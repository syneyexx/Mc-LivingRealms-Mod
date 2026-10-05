package dev.livingrealms.minecraft.client.ui.dashboard;

import dev.livingrealms.sim.ui.DashboardActionCommand;
import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Wave 14: war goals, army identity, escort from WarRoomOptionsBuilder projection. */
public final class WarPanel implements DashboardPanel {
    private String selectedWarGoal = "CONQUEST";
    private long selectedEscortTargetId;
    private final Runnable rebuild;

    public WarPanel(Runnable rebuild) {
        this.rebuild = rebuild == null ? () -> {} : rebuild;
    }

    public WarPanel() {
        this(() -> {});
    }

    @Override
    public void rebuildWidgets(PanelHost host, RealmDashboardSnapshot snapshot, DashboardLayout layout, int page) {
        long selfId = snapshot.player().memberFactionId();
        if (selfId <= 0) return;
        var room = snapshot.warRoom();
        int y = layout.contentY + 4;
        List<String> goalPool = new ArrayList<>();
        for (var e : room.enemies()) {
            for (String g : e.validGoals()) if (!goalPool.contains(g)) goalPool.add(g);
        }
        if (goalPool.isEmpty()) goalPool.add("CONQUEST");
        if (!goalPool.contains(selectedWarGoal)) selectedWarGoal = goalPool.getFirst();
        int goalIndex = goalPool.indexOf(selectedWarGoal);
        host.addWidget(Button.builder(Component.literal("Goal: " + selectedWarGoal), b -> {
            selectedWarGoal = goalPool.get((goalIndex + 1) % goalPool.size());
            rebuild.run();
        }).bounds(layout.left + 10, y, Math.min(160, layout.panelWidth - 20), 16).build());
        y += 18;
        List<Long> escortIds = new ArrayList<>();
        for (var t : room.escortTargets()) escortIds.add(t.id());
        if (escortIds.isEmpty() && room.defaultEscortTargetId() > 0) {
            escortIds.add(room.defaultEscortTargetId());
        }
        if (!escortIds.contains(selectedEscortTargetId)) {
            selectedEscortTargetId = escortIds.isEmpty() ? 0 : escortIds.getFirst();
        }
        if (!escortIds.isEmpty()) {
            String escortLabel = escortLabel(room, selectedEscortTargetId);
            host.addWidget(Button.builder(Component.literal("Escort tgt: " + escortLabel), b -> {
                int idx = escortIds.indexOf(selectedEscortTargetId);
                selectedEscortTargetId = escortIds.get((Math.max(0, idx) + 1) % escortIds.size());
                rebuild.run();
            }).bounds(layout.left + 10, y, Math.min(240, layout.panelWidth - 20), 16).build());
            y += 18;
        }
        int shown = 0;
        for (var enemy : room.enemies()) {
            if (shown >= 3) break;
            if (!enemy.canDeclare() && !enemy.petitionOnly()) continue;
            long enemyId = enemy.factionId();
            long targetId = enemy.suggestedTargetSettlementId();
            String goal = enemy.validGoals().contains(selectedWarGoal) ? selectedWarGoal
                    : (enemy.validGoals().isEmpty() ? "CONQUEST" : enemy.validGoals().getFirst());
            String label = (enemy.canDeclare() ? "Declare " : "Petition ") + goal + " vs " + enemy.name();
            host.addWidget(Button.builder(Component.literal(label),
                            b -> DashboardActionDispatcher.dispatch(
                                    DashboardActionCommand.Action.DECLARE_WAR, enemyId, targetId, goal))
                    .bounds(layout.left + 10, y, Math.min(260, layout.panelWidth - 20), 16).build());
            y += 18;
            shown++;
        }
        long hostile = room.defaultHostileSettlementId();
        long escortTarget = selectedEscortTargetId > 0 ? selectedEscortTargetId : room.defaultEscortTargetId();
        long patrolTarget = room.defaultPatrolSettlementId();
        int bw = Math.max(62, (layout.panelWidth - 28) / 5);
        for (var army : room.armies()) {
            if (y > layout.contentY + 120) break;
            long armyId = army.id();
            if (hostile > 0) {
                long captureTarget = hostile;
                host.addWidget(Button.builder(Component.literal("Capture #" + armyId),
                                b -> DashboardActionDispatcher.dispatch(DashboardActionCommand.Action.ARMY_CAPTURE, armyId, captureTarget))
                        .bounds(layout.left + 10, y, bw - 2, 16).build());
                host.addWidget(Button.builder(Component.literal("Siege"),
                                b -> DashboardActionDispatcher.dispatch(DashboardActionCommand.Action.ARMY_SIEGE, armyId, captureTarget))
                        .bounds(layout.left + 10 + bw, y, bw - 2, 16).build());
                host.addWidget(Button.builder(Component.literal("Raid"),
                                b -> DashboardActionDispatcher.dispatch(DashboardActionCommand.Action.ARMY_RAID, armyId, captureTarget))
                        .bounds(layout.left + 10 + bw * 2, y, bw - 2, 16).build());
            }
            if (escortTarget > 0) {
                long escortId = escortTarget;
                host.addWidget(Button.builder(Component.literal("Escort"),
                                b -> DashboardActionDispatcher.dispatch(DashboardActionCommand.Action.ARMY_ESCORT, armyId, escortId))
                        .bounds(layout.left + 10 + bw * 3, y, bw - 2, 16).build());
            }
            if (patrolTarget > 0) {
                long patrolId = patrolTarget;
                host.addWidget(Button.builder(Component.literal("Patrol"),
                                b -> DashboardActionDispatcher.dispatch(DashboardActionCommand.Action.ARMY_PATROL, armyId, patrolId))
                        .bounds(layout.left + 10 + bw * 4, y, bw - 2, 16).build());
            }
            y += 18;
        }
    }

    private static String escortLabel(RealmDashboardSnapshot.WarRoomView room, long id) {
        for (var t : room.escortTargets()) {
            if (t.id() == id) {
                String kind = DashboardPanel.titleCase(t.kind());
                String name = t.label().isBlank() ? ("#" + id) : t.label();
                return kind + " " + name;
            }
        }
        return "#" + id;
    }

    @Override
    public List<DashboardLine> lines(RealmDashboardSnapshot snapshot, int page) {
        List<DashboardLine> lines = new ArrayList<>();
        var room = snapshot.warRoom();
        lines.add(DashboardLine.header("War room"));
        lines.add(DashboardLine.dim("Contextual war goals. Escort uses friendly shipments/settlements only.", 0));
        if (!room.enemies().isEmpty()) {
            lines.add(DashboardLine.header("Potential enemies"));
            for (var e : room.enemies()) {
                String mode = e.canDeclare() ? "DECLARE" : e.petitionOnly() ? "PETITION" : "LOCKED";
                lines.add(DashboardLine.text(e.name() + " — " + mode));
                lines.add(DashboardLine.dim("Goals: " + String.join(", ", e.validGoals())
                        + (e.suggestedTargetName().isBlank() ? "" : " → " + e.suggestedTargetName()), 1));
            }
        }
        lines.add(DashboardLine.header("Armies"));
        for (var a : room.armies()) {
            lines.add(DashboardLine.text("Army #" + a.id() + " — " + a.personnel() + " troops • power "
                    + DashboardPanel.one(a.combatPower())));
            lines.add(DashboardLine.dim("Morale " + DashboardPanel.pct(a.morale()) + " • supply "
                    + DashboardPanel.pct(a.supply()) + " • home "
                    + (a.homeSettlement().isBlank() ? "?" : a.homeSettlement()), 1));
            lines.add(DashboardLine.dim("Objective: " + a.objective() + " @ "
                    + DashboardPanel.whole(a.x()) + "," + DashboardPanel.whole(a.z()), 1));
        }
        if (room.armies().isEmpty()) lines.add(DashboardLine.dim("No armies under your command.", 0));
        if (!room.escortTargets().isEmpty()) {
            lines.add(DashboardLine.header("Valid escort targets"));
            for (var t : room.escortTargets()) {
                lines.add(DashboardLine.dim(DashboardPanel.titleCase(t.kind()) + " #" + t.id() + " — " + t.label(), 0));
            }
        }
        lines.add(DashboardLine.header("Active wars"));
        for (var w : snapshot.wars()) {
            lines.add(DashboardLine.warn(w.attackerName() + " vs " + w.defenderName()));
            lines.add(DashboardLine.dim(w.goal() + (w.targetSettlementName().isBlank() ? "" : " → " + w.targetSettlementName())
                    + " • since day " + w.startDay() + " • score " + DashboardPanel.one(w.attackerScore()), 1));
            lines.add(DashboardLine.dim("Exhaustion A " + DashboardPanel.pct(w.attackerExhaustion())
                    + " / D " + DashboardPanel.pct(w.defenderExhaustion()), 1));
        }
        if (snapshot.wars().isEmpty()) lines.add(DashboardLine.dim("No active wars relevant to this realm.", 0));
        lines.add(DashboardLine.header("Army objectives"));
        for (var objective : snapshot.warfare().objectives()) {
            lines.add(DashboardLine.text("Army #" + objective.armyId() + " — "
                    + DashboardPanel.titleCase(objective.type()) + " → " + objective.target()));
            lines.add(DashboardLine.dim("Priority " + objective.priority() + " • "
                    + DashboardPanel.whole(objective.distanceBlocks()) + "m from you", 1));
        }
        if (snapshot.warfare().objectives().isEmpty()) {
            lines.add(DashboardLine.dim("No active army objectives for this realm.", 0));
        }
        lines.add(DashboardLine.header("Active sieges"));
        for (var siege : snapshot.warfare().sieges()) {
            lines.add(DashboardLine.warn(siege.attacker() + " besieges " + siege.settlement()));
            lines.add(DashboardLine.dim("Defender " + siege.defender() + " • since day " + siege.startDay(), 1));
            lines.add(DashboardLine.dim("Progress " + DashboardPanel.pct(siege.progress())
                    + " • blockade " + DashboardPanel.pct(siege.blockade()), 1));
        }
        if (snapshot.warfare().sieges().isEmpty()) {
            lines.add(DashboardLine.dim("No active sieges involving this realm.", 0));
        }
        return lines;
    }
}
