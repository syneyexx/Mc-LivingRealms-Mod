package dev.livingrealms.minecraft.client.ui.dashboard;

import dev.livingrealms.minecraft.client.ui.DashboardAccessibility;
import dev.livingrealms.sim.ui.DashboardActionCommand;
import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Runnable;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public final class SettingsPanel implements DashboardPanel {
    private final Runnable onAccessibilityChanged;

    public SettingsPanel(Runnable onAccessibilityChanged) {
        this.onAccessibilityChanged = onAccessibilityChanged == null ? () -> {} : onAccessibilityChanged;
    }

    @Override
    public void rebuildWidgets(PanelHost host, RealmDashboardSnapshot snapshot, DashboardLayout layout, int page) {
        DashboardActionCommand.Action[] actions = {
                DashboardActionCommand.Action.CONFIG_PERFORMANCE,
                DashboardActionCommand.Action.CONFIG_BALANCED,
                DashboardActionCommand.Action.CONFIG_IMMERSIVE,
                DashboardActionCommand.Action.CONFIG_CINEMATIC
        };
        String[] labels = {"Performance", "Balanced", "Immersive", "Cinematic"};
        int gap = 6, buttonWidth = Math.max(90, (layout.panelWidth - 26 - gap) / 2);
        int stackTop = Math.min(layout.contentY + 72, layout.footerY - 22 * 4 - 8);
        for (int i = 0; i < actions.length; i++) {
            final DashboardActionCommand.Action action = actions[i];
            int col = i % 2;
            int row = i / 2;
            int by = Math.min(stackTop + row * 22, layout.footerY - 44);
            host.addWidget(Button.builder(Component.literal(labels[i]),
                            b -> DashboardActionDispatcher.dispatch(action, 1))
                    .bounds(layout.left + 10 + col * (buttonWidth + gap), by, buttonWidth, 18).build());
        }
        int a11yY = Math.min(stackTop + 48, layout.footerY - 22);
        var scaleBtn = Button.builder(Component.translatable("button.livingrealms.ui_scale", DashboardAccessibility.scale().name()),
                        b -> {
                            DashboardAccessibility.cycleScale();
                            onAccessibilityChanged.run();
                        })
                .bounds(layout.left + 10, a11yY, buttonWidth, 18).build();
        scaleBtn.setTooltip(Tooltip.create(Component.translatable("tooltip.livingrealms.ui_scale")));
        host.addWidget(scaleBtn);
        String contrastLabel = DashboardAccessibility.highContrast() ? "High contrast: ON" : "High contrast: OFF";
        var contrastBtn = Button.builder(Component.literal(contrastLabel),
                        b -> {
                            DashboardAccessibility.toggleHighContrast();
                            onAccessibilityChanged.run();
                        })
                .bounds(layout.left + 10 + buttonWidth + gap, a11yY, buttonWidth, 18).build();
        contrastBtn.setTooltip(Tooltip.create(Component.translatable("tooltip.livingrealms.high_contrast")));
        host.addWidget(contrastBtn);
    }

    @Override
    public List<DashboardLine> lines(RealmDashboardSnapshot snapshot, int page) {
        List<DashboardLine> lines = new ArrayList<>();
        var c = snapshot.settings();
        lines.add(DashboardLine.header("Singleplayer simulation profile"));
        lines.add(DashboardLine.text("Current: " + DashboardPanel.titleCase(c.profile())));
        lines.add(DashboardLine.dim("Physical radius " + DashboardPanel.whole(c.physicalRadius())
                + "m • regional radius " + DashboardPanel.whole(c.regionalRadius()) + "m", 1));
        lines.add(DashboardLine.dim("Wildlife " + c.wildlifeBudget() + " • caravans " + c.caravanBudget()
                + " • military " + c.militaryBudget() + " • naval " + c.navalBudget(), 1));
        lines.add(DashboardLine.dim("Construction budget " + c.constructionOpsPerTick() + " block ops/tick", 1));
        lines.add(DashboardLine.header("Profiles"));
        lines.add(DashboardLine.dim("Performance — fewer nearby physical projections.", 0));
        lines.add(DashboardLine.dim("Balanced — default recommendation.", 0));
        lines.add(DashboardLine.dim("Immersive — denser living world around you.", 0));
        lines.add(DashboardLine.warn("Cinematic — highest entity/build budgets; use only if performance remains stable."));
        lines.add(DashboardLine.header("Accessibility"));
        lines.add(DashboardLine.text("UI scale: " + DashboardAccessibility.scale().name()));
        lines.add(DashboardLine.text("High contrast: " + (DashboardAccessibility.highContrast() ? "ON" : "OFF")));
        lines.add(DashboardLine.dim("Client-only; does not change simulation budgets.", 1));
        return lines;
    }
}
