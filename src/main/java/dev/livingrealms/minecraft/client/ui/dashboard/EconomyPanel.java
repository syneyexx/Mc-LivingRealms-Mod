package dev.livingrealms.minecraft.client.ui.dashboard;

import dev.livingrealms.sim.economy.MarketTransactionEngine;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.ui.DashboardActionCommand;
import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class EconomyPanel implements DashboardPanel {
    @Override
    public void rebuildWidgets(PanelHost host, RealmDashboardSnapshot snapshot, DashboardLayout layout, int page) {
        var player = snapshot.player();
        var realm = snapshot.realm();
        if (realm.factionId() <= 0) return;
        if (player.memberFactionId() == realm.factionId()) {
            host.addWidget(Button.builder(Component.literal("Tax -1%"),
                            b -> DashboardActionDispatcher.dispatch(DashboardActionCommand.Action.TAX_LOWER, realm.factionId()))
                    .bounds(layout.left + layout.panelWidth - 150, layout.contentY + 3, 64, 18).build());
            host.addWidget(Button.builder(Component.literal("Tax +1%"),
                            b -> DashboardActionDispatcher.dispatch(DashboardActionCommand.Action.TAX_RAISE, realm.factionId()))
                    .bounds(layout.left + layout.panelWidth - 82, layout.contentY + 3, 64, 18).build());
        }
        String key = selectedMarketResource(snapshot, page);
        if (key == null) return;
        ResourceType resource = ResourceType.valueOf(key);
        long encoded = resource.ordinal() + 1L;
        host.addWidget(Button.builder(Component.literal("Buy " + MarketTransactionEngine.PACKAGE_UNITS),
                        b -> DashboardActionDispatcher.dispatch(DashboardActionCommand.Action.MARKET_BUY, encoded))
                .bounds(layout.left + 10, layout.contentY + 126, 82, 18).build());
        host.addWidget(Button.builder(Component.literal("Sell " + MarketTransactionEngine.PACKAGE_UNITS),
                        b -> DashboardActionDispatcher.dispatch(DashboardActionCommand.Action.MARKET_SELL, encoded))
                .bounds(layout.left + 96, layout.contentY + 126, 82, 18).build());
    }

    @Override
    public List<DashboardLine> lines(RealmDashboardSnapshot snapshot, int page) {
        List<DashboardLine> lines = new ArrayList<>();
        var r = snapshot.realm();
        if (r.factionId() <= 0) {
            lines.add(DashboardLine.dim("No realm selected. Stand in claimed territory or join a faction.", 0));
            return lines;
        }
        lines.add(DashboardLine.header(r.name() + " economy"));
        lines.add(DashboardLine.text("Treasury: " + DashboardPanel.whole(r.treasury()) + "   Technology: " + DashboardPanel.one(r.technology())));
        lines.add(DashboardLine.text("Stability " + DashboardPanel.pct(r.stability()) + " • legitimacy " + DashboardPanel.pct(r.legitimacy()) + " • corruption " + DashboardPanel.pct(r.corruption())));
        lines.add(DashboardLine.text("Tax rate: " + DashboardPanel.pct(r.taxRate())));
        lines.add(DashboardLine.header("Local market"));
        String key = selectedMarketResource(snapshot, page);
        if (key == null) {
            lines.add(DashboardLine.dim("No player-tradable commodities at this realm.", 0));
            return lines;
        }
        double stock = r.resources().getOrDefault(key, 0d), unit = r.marketPrices().getOrDefault(key, 0d);
        int buy = r.marketBuyCosts().getOrDefault(key, 0), sell = r.marketSellPayouts().getOrDefault(key, 0);
        lines.add(DashboardLine.text(DashboardPanel.titleCase(key) + " • realm stock " + DashboardPanel.whole(stock)));
        lines.add(DashboardLine.text("Market value " + DashboardPanel.one(unit) + " / unit"));
        lines.add(DashboardLine.text("Buy " + MarketTransactionEngine.PACKAGE_UNITS + " → " + buy + " emeralds"));
        lines.add(DashboardLine.text("Sell " + MarketTransactionEngine.PACKAGE_UNITS + " → " + sell + " emeralds"));
        lines.add(DashboardLine.dim("Trade requires an operational Living Realms market nearby.", 0));
        lines.add(DashboardLine.dim("Prices are server-authoritative and change with scarcity.", 0));
        return lines;
    }

    @Override
    public int maxPage(RealmDashboardSnapshot snapshot, int pageLines) {
        return Math.max(0, snapshot.realm().marketPrices().size() - 1);
    }

    static String selectedMarketResource(RealmDashboardSnapshot snapshot, int page) {
        if (snapshot.realm().marketPrices().isEmpty()) return null;
        var keys = new ArrayList<>(snapshot.realm().marketPrices().keySet());
        Collections.sort(keys);
        return keys.get(Math.min(page, keys.size() - 1));
    }
}
