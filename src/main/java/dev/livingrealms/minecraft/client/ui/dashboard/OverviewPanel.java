package dev.livingrealms.minecraft.client.ui.dashboard;

import dev.livingrealms.sim.ui.DashboardActionCommand;
import dev.livingrealms.sim.ui.ImportantNotifications;
import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public final class OverviewPanel implements DashboardPanel {
    private String foundNameDraft = "";
    private EditBox foundNameBox;

    @Override
    public void rebuildWidgets(PanelHost host, RealmDashboardSnapshot snapshot, DashboardLayout layout, int page) {
        rebuildFactionButton(host, snapshot, layout);
        rebuildInfluenceButtons(host, snapshot, layout);
    }

    @Override
    public List<DashboardLine> lines(RealmDashboardSnapshot snapshot, int page) {
        List<DashboardLine> lines = new ArrayList<>();
        var j = snapshot.jurisdiction();
        var p = snapshot.player();
        var r = snapshot.realm();
        lines.add(DashboardLine.header("Day " + snapshot.day()));
        lines.add(DashboardLine.text(j.claimed() ? "Location: " + j.primaryName() + (j.contested() ? " (CONTESTED)" : "") : "Location: Wilderness"));
        lines.add(DashboardLine.text("Membership: " + (p.memberFactionId() > 0 ? p.memberFactionName() + " / " + p.rank() : "None")));
        lines.add(DashboardLine.text("Reputation: " + DashboardPanel.whole(p.localReputation()) + "   Infamy: " + DashboardPanel.whole(p.globalInfamy())));
        if (p.careerTrack() != null && !p.careerTrack().isBlank()) {
            lines.add(DashboardLine.text("Career: " + p.careerTrack() + " / " + p.careerRank()));
        }
        if (p.influence() != null && !p.influence().isEmpty()) {
            StringBuilder inf = new StringBuilder("Influence:");
            p.influence().entrySet().stream().sorted((a, b) -> Double.compare(b.getValue(), a.getValue())).limit(4)
                    .forEach(e -> inf.append(' ').append(e.getKey()).append('=').append(DashboardPanel.whole(e.getValue())));
            lines.add(DashboardLine.text(inf.toString()));
        }
        lines.add(DashboardLine.text("Wanted: " + p.wantedLevel() + "   Bounty: " + DashboardPanel.whole(p.bounty())));
        if (p.inCustody()) lines.add(DashboardLine.warn("IN CUSTODY until day " + p.custodyReleaseDay()));
        lines.add(DashboardLine.header("Player actions"));
        lines.add(DashboardLine.dim("F12 dashboard: settlements, economy, politics, wars. M opens the world map. Chat NPCs freely.", 0));
        lines.add(DashboardLine.dim("Found a growing realm with Found settlement (Overview) or /livingrealms found <name>.", 0));
        if ("RULER".equalsIgnoreCase(p.rank())) {
            lines.add(DashboardLine.dim("As ruler you cannot Leave — succession/abdication is required to step down.", 0));
        }
        lines.add(DashboardLine.dim("Policy buttons on Settlements change what your towns build next.", 0));
        if (r.factionId() > 0) {
            lines.add(DashboardLine.header("Realm"));
            lines.add(DashboardLine.text(r.name() + " — ruler " + r.ruler()));
            lines.add(DashboardLine.text(r.governmentType() + " / " + r.successionLaw()));
            lines.add(DashboardLine.text("Population " + r.population() + "   settlements " + r.settlementCount()));
            lines.add(DashboardLine.text("Army " + r.armyPersonnel() + "   airframes " + r.airframes() + "   ships " + r.ships()));
            lines.add(DashboardLine.text("Industry " + r.industrialSites() + "   ports " + r.ports() + "   shipments " + r.activeShipments()));
            lines.add(DashboardLine.text("Wars " + r.activeWars() + "   treaties " + r.activeTreaties()));
            if (r.activeDebts() > 0 || r.grandProjects() > 0 || r.campaignPlans() > 0) {
                lines.add(DashboardLine.text("Debts " + r.activeDebts() + "   projects " + r.grandProjects() + "   campaigns " + r.campaignPlans()));
            }
        }
        List<DashboardLine> notices = importantNotices(snapshot);
        if (!notices.isEmpty()) {
            lines.add(DashboardLine.header("Important notices"));
            lines.addAll(notices);
        }
        return lines;
    }

    private static List<DashboardLine> importantNotices(RealmDashboardSnapshot snapshot) {
        List<DashboardLine> out = new ArrayList<>();
        var history = snapshot.history();
        for (int i = history.size() - 1; i >= 0; i--) {
            var h = history.get(i);
            if (!ImportantNotifications.isImportant(h.type())) continue;
            String label = ImportantNotifications.label(h.type());
            out.add(DashboardLine.text("Day " + h.day() + " [" + label + "] " + h.message()));
            if (out.size() >= 5) break;
        }
        return out;
    }

    private void rebuildFactionButton(PanelHost host, RealmDashboardSnapshot snapshot, DashboardLayout layout) {
        var player = snapshot.player();
        var jurisdiction = snapshot.jurisdiction();
        int joinY = Math.min(layout.contentY + 146, layout.footerY - 22);
        foundNameBox = null;
        if (player.memberFactionId() > 0) {
            if ("RULER".equalsIgnoreCase(player.rank())) {
                var abdicate = Button.builder(Component.literal("Abdicate"), b -> DashboardActionDispatcher.dispatch(
                                DashboardActionCommand.Action.ABDICATE, player.memberFactionId()))
                        .bounds(layout.left + layout.panelWidth - 150, joinY, 136, 18).build();
                abdicate.setTooltip(Tooltip.create(Component.literal("Step down as ruler. A court successor takes the name; you become an outsider.")));
                host.addWidget(abdicate);
            } else {
                host.addWidget(Button.builder(Component.literal("Leave " + player.memberFactionName()),
                                b -> DashboardActionDispatcher.dispatch(DashboardActionCommand.Action.FACTION_LEAVE, 1))
                        .bounds(layout.left + layout.panelWidth - 150, joinY, 136, 18).build());
            }
        } else if (jurisdiction.claimed() && !jurisdiction.contested() && jurisdiction.primaryFactionId() > 0) {
            boolean canJoin = player.localReputation() >= 10 && player.bounty() <= 25 && !player.inCustody();
            var join = Button.builder(Component.literal("Join " + jurisdiction.primaryName()), b -> {
                if (!canJoin) return;
                DashboardActionDispatcher.dispatch(DashboardActionCommand.Action.FACTION_JOIN_LOCAL, jurisdiction.primaryFactionId());
            }).bounds(layout.left + layout.panelWidth - 150, joinY, 136, 18).build();
            join.active = canJoin;
            String tip = canJoin
                    ? "Join this realm (reputation " + Math.round(player.localReputation()) + ")."
                    : "Requires reputation ≥ 10 and bounty ≤ 25"
                    + (player.inCustody() ? "; you are in custody." : ".")
                    + " Current: rep " + Math.round(player.localReputation())
                    + ", bounty " + Math.round(player.bounty()) + ".";
            join.setTooltip(Tooltip.create(Component.literal(tip)));
            host.addWidget(join);
        } else if (player.memberFactionId() <= 0) {
            int nameWidth = Math.max(90, layout.panelWidth - 170);
            foundNameBox = new EditBox(host.font(), layout.left + 10, joinY, nameWidth, 18, Component.literal("Settlement name"));
            foundNameBox.setMaxLength(40);
            foundNameBox.setHint(Component.literal("Settlement name"));
            if (foundNameDraft == null || foundNameDraft.isBlank()) {
                String actor = player.actorKey();
                String base = actor.contains(":") ? actor.substring(actor.indexOf(':') + 1) : actor;
                if (base.length() > 24) base = base.substring(0, 24);
                foundNameDraft = base.length() >= 2 ? base + "stead" : "Newstead";
            }
            foundNameBox.setValue(foundNameDraft);
            foundNameBox.setResponder(value -> foundNameDraft = value == null ? "" : value);
            host.addWidget(foundNameBox);
            host.addWidget(Button.builder(Component.literal("Found settlement"), b -> {
                String name = foundNameBox != null ? foundNameBox.getValue().trim() : foundNameDraft;
                if (name.length() < 2) name = "Newstead";
                DashboardActionDispatcher.dispatch(DashboardActionCommand.Action.FOUND_SETTLEMENT, 1, name);
            }).bounds(layout.left + layout.panelWidth - 150, joinY, 136, 18).build());
        }
    }

    private void rebuildInfluenceButtons(PanelHost host, RealmDashboardSnapshot snapshot, DashboardLayout layout) {
        long factionId = snapshot.player().memberFactionId() > 0
                ? snapshot.player().memberFactionId() : snapshot.jurisdiction().primaryFactionId();
        if (factionId <= 0) return;
        DashboardActionCommand.Action[] actions = {
                DashboardActionCommand.Action.REQUEST_AUDIENCE,
                DashboardActionCommand.Action.PROPOSE_PROJECT,
                DashboardActionCommand.Action.REQUEST_MILITARY_SUPPORT,
                DashboardActionCommand.Action.PETITION_TRADE,
                DashboardActionCommand.Action.PETITION_CLERGY
        };
        String[] labels = {"Audience", "Propose", "Military", "Trade", "Clergy"};
        String[] tips = {
                "tooltip.livingrealms.influence",
                "tooltip.livingrealms.grand_project",
                "tooltip.livingrealms.influence",
                "tooltip.livingrealms.route_security",
                "tooltip.livingrealms.influence"
        };
        int rowY = Math.min(layout.contentY + 166, layout.footerY - 40);
        int bw = Math.max(58, (layout.panelWidth - 28) / 5);
        for (int i = 0; i < actions.length; i++) {
            final DashboardActionCommand.Action action = actions[i];
            var button = Button.builder(Component.literal(labels[i]),
                            b -> DashboardActionDispatcher.dispatch(action, factionId))
                    .bounds(layout.left + 10 + i * bw, rowY, bw - 3, 16).build();
            button.setTooltip(Tooltip.create(Component.translatable(tips[i])));
            host.addWidget(button);
        }
    }
}
