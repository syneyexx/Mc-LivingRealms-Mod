package dev.livingrealms.minecraft.client.ui;

import dev.livingrealms.minecraft.client.ui.dashboard.DashboardActionDispatcher;
import dev.livingrealms.minecraft.client.ui.dashboard.DashboardLayout;
import dev.livingrealms.minecraft.client.ui.dashboard.DashboardPanel;
import dev.livingrealms.minecraft.client.ui.dashboard.DashboardTheme;
import dev.livingrealms.minecraft.client.ui.dashboard.EcologyPanel;
import dev.livingrealms.minecraft.client.ui.dashboard.EconomyPanel;
import dev.livingrealms.minecraft.client.ui.dashboard.ForcesPanel;
import dev.livingrealms.minecraft.client.ui.dashboard.HistoryPanel;
import dev.livingrealms.minecraft.client.ui.dashboard.KingdomsPanel;
import dev.livingrealms.minecraft.client.ui.dashboard.LawPanel;
import dev.livingrealms.minecraft.client.ui.dashboard.MapPanel;
import dev.livingrealms.minecraft.client.ui.dashboard.OperationsPanel;
import dev.livingrealms.minecraft.client.ui.dashboard.OverviewPanel;
import dev.livingrealms.minecraft.client.ui.dashboard.PoliticsPanel;
import dev.livingrealms.minecraft.client.ui.dashboard.SettlementPanel;
import dev.livingrealms.minecraft.client.ui.dashboard.SettingsPanel;
import dev.livingrealms.minecraft.client.ui.dashboard.SocietyPanel;
import dev.livingrealms.minecraft.client.ui.dashboard.UnderworldPanel;
import dev.livingrealms.minecraft.client.ui.dashboard.WarPanel;
import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Dashboard compositor: tab chrome + panel delegation. Panels dispatch intents only;
 * canonical authorization stays on the server.
 */
public final class RealmDashboardScreen extends Screen implements DashboardPanel.PanelHost {
    private RealmDashboardSnapshot snapshot;
    private Tab tab = Tab.OVERVIEW;
    private int page;
    private int contentScroll;
    private final Map<Tab, DashboardPanel> panels = new EnumMap<>(Tab.class);

    public RealmDashboardScreen(RealmDashboardSnapshot snapshot) {
        super(Component.translatable("screen.livingrealms.dashboard"));
        this.snapshot = snapshot;
        panels.put(Tab.OVERVIEW, new OverviewPanel());
        panels.put(Tab.MAP, new MapPanel());
        panels.put(Tab.LAW, new LawPanel());
        panels.put(Tab.KINGDOMS, new KingdomsPanel());
        panels.put(Tab.POLITICS, new PoliticsPanel());
        panels.put(Tab.SETTLEMENTS, new SettlementPanel());
        panels.put(Tab.SOCIETY, new SocietyPanel());
        panels.put(Tab.SETTINGS, new SettingsPanel(this::rebuildDashboardWidgets));
        panels.put(Tab.WARS, new WarPanel(this::rebuildDashboardWidgets));
        panels.put(Tab.UNDERWORLD, new UnderworldPanel());
        panels.put(Tab.ECONOMY, new EconomyPanel());
        panels.put(Tab.OPERATIONS, new OperationsPanel());
        panels.put(Tab.FORCES, new ForcesPanel());
        panels.put(Tab.ECOLOGY, new EcologyPanel());
        panels.put(Tab.HISTORY, new HistoryPanel());
    }

    public void replaceSnapshot(RealmDashboardSnapshot newSnapshot) {
        this.snapshot = newSnapshot;
        this.page = 0;
        this.contentScroll = 0;
        rebuildDashboardWidgets();
    }

    @Override
    protected void init() {
        rebuildDashboardWidgets();
    }

    private void rebuildDashboardWidgets() {
        clearWidgets();
        if (width <= 0 || height <= 0) return;
        DashboardLayout layout = new DashboardLayout(width, height, Tab.values().length);
        int columns = DashboardLayout.tabColumns(layout.panelWidth);
        int tabWidth = Math.max(42, (layout.panelWidth - 16) / columns);
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab value = tabs[i];
            int row = i / columns, col = i % columns;
            int x = layout.left + 8 + col * tabWidth;
            int y = layout.top + 28 + row * 20;
            int buttonWidth = Math.max(40, tabWidth - 2);
            var tabButton = Button.builder(Component.literal(value.label), b -> {
                tab = value;
                page = 0;
                contentScroll = 0;
                rebuildDashboardWidgets();
            }).bounds(x, y, buttonWidth, 18).build();
            tabButton.setTooltip(Tooltip.create(DashboardTheme.tabTooltip(value.label)));
            addRenderableWidget(tabButton);
        }
        addRenderableWidget(Button.builder(Component.translatable("button.livingrealms.refresh"),
                        b -> DashboardActionDispatcher.requestRefresh())
                .bounds(layout.left + 8, layout.footerY, 70, 18).build());
        var prev = Button.builder(Component.literal("<"), b -> {
            page = Math.max(0, page - 1);
            rebuildDashboardWidgets();
        }).bounds(layout.left + layout.panelWidth - 82, layout.footerY, 32, 18).build();
        prev.setTooltip(Tooltip.create(Component.translatable("tooltip.livingrealms.prev_page")));
        addRenderableWidget(prev);
        var next = Button.builder(Component.literal(">"), b -> {
            page = Math.min(maxPage(), page + 1);
            rebuildDashboardWidgets();
        }).bounds(layout.left + layout.panelWidth - 44, layout.footerY, 32, 18).build();
        next.setTooltip(Tooltip.create(Component.translatable("tooltip.livingrealms.next_page")));
        addRenderableWidget(next);
        panels.get(tab).rebuildWidgets(this, snapshot, layout, page);
    }

    @Override
    public void addWidget(AbstractWidget widget) {
        addRenderableWidget(widget);
    }

    @Override
    public Font font() {
        return font;
    }

    @Override
    public int screenHeight() {
        return height;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        LivingRealmsScreens.clearBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        LivingRealmsScreens.paintBackdrop(graphics, width, height, DashboardTheme.backdrop());
        DashboardLayout layout = new DashboardLayout(width, height, Tab.values().length);
        var panelTex = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("livingrealms", "textures/gui/dashboard_panel.png");
        graphics.blit(panelTex, layout.left, layout.top, 0, 0, layout.panelWidth, layout.panelHeight, layout.panelWidth, layout.panelHeight);
        graphics.fill(layout.left, layout.top, layout.left + layout.panelWidth, layout.top + layout.panelHeight, DashboardTheme.panelScrim());
        graphics.fill(layout.left, layout.top, layout.left + layout.panelWidth, layout.top + 3, DashboardTheme.accent());
        graphics.drawCenteredString(font, title, width / 2, layout.top + 9, DashboardTheme.header());
        super.render(graphics, mouseX, mouseY, partialTick);

        DashboardPanel panel = panels.get(tab);
        if (tab == Tab.MAP || tab == Tab.LAW) {
            panel.renderCustom(graphics, font, snapshot, layout, page);
        } else {
            List<DashboardPanel.DashboardLine> lines = panel.lines(snapshot, page);
            int start = Math.min(lines.size(), page * layout.pageLines);
            int end = Math.min(lines.size(), start + layout.pageLines);
            int y = layout.contentY - contentScroll;
            graphics.enableScissor(layout.left + 8, layout.contentY, layout.left + layout.panelWidth - 8, layout.contentBottom);
            for (int i = start; i < end; i++) {
                DashboardPanel.DashboardLine line = lines.get(i);
                if (y + layout.lineHeight >= layout.contentY && y < layout.contentBottom) {
                    graphics.drawString(font, line.text(), layout.left + 11 + line.indent() * 8, y, line.color(), false);
                }
                y += layout.lineHeight;
            }
            graphics.disableScissor();
            String pageText = (maxPage() + 1) <= 1 ? "1/1" : (page + 1) + "/" + (maxPage() + 1);
            graphics.drawCenteredString(font, pageText + "  • scroll", width / 2, layout.top + layout.panelHeight - 21, DashboardTheme.dim());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            DashboardLayout layout = new DashboardLayout(width, height, Tab.values().length);
            if (tab != Tab.MAP && tab != Tab.LAW) {
                contentScroll = Math.max(0, contentScroll - (int) Math.round(scrollY * layout.lineHeight * 2));
                int maxScroll = Math.max(0, layout.pageLines * layout.lineHeight - 40);
                contentScroll = Math.min(contentScroll, maxScroll);
                return true;
            }
            if (scrollY < 0) {
                page = Math.min(maxPage(), page + 1);
                contentScroll = 0;
                rebuildDashboardWidgets();
                return true;
            }
            if (scrollY > 0) {
                page = Math.max(0, page - 1);
                contentScroll = 0;
                rebuildDashboardWidgets();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 263 || keyCode == 265) {
            page = Math.max(0, page - 1);
            rebuildDashboardWidgets();
            return true;
        }
        if (keyCode == 262 || keyCode == 264) {
            page = Math.min(maxPage(), page + 1);
            rebuildDashboardWidgets();
            return true;
        }
        if (keyCode >= 49 && keyCode <= 57) {
            Tab[] tabs = Tab.values();
            int index = keyCode - 49;
            if (index < tabs.length) {
                tab = tabs[index];
                page = 0;
                rebuildDashboardWidgets();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int maxPage() {
        DashboardLayout layout = new DashboardLayout(Math.max(1, width), Math.max(1, height), Tab.values().length);
        return panels.get(tab).maxPage(snapshot, layout.pageLines);
    }

    private enum Tab {
        OVERVIEW("Overview"), MAP("Map"), LAW("Law"), KINGDOMS("Realms"), POLITICS("Politics"),
        SETTLEMENTS("Cities"), SOCIETY("Society"), SETTINGS("Settings"), WARS("Wars"),
        UNDERWORLD("Underworld"), ECONOMY("Economy"), OPERATIONS("Ops"), FORCES("Forces"),
        ECOLOGY("Ecology"), HISTORY("History");
        private final String label;
        Tab(String label) { this.label = label; }
    }
}
