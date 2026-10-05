package dev.livingrealms.minecraft.client.ui;

import dev.livingrealms.sim.ui.DashboardActionCommand;
import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.economy.MarketTransactionEngine;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only strategic dashboard backed solely by a server-generated immutable snapshot. */
public final class RealmDashboardScreen extends Screen {
    private static final int BOUNTIES_PER_PAGE = 4;

    private RealmDashboardSnapshot snapshot;
    private Tab tab = Tab.OVERVIEW;
    private int page;
    /** Pixel scroll offset inside the content pane (mouse wheel). */
    private int contentScroll;

    private static int panelWidthPref() { return DashboardAccessibility.scale().panelWidth; }
    private static int panelHeightPref() { return DashboardAccessibility.scale().panelHeight; }
    private static int lineHeight() { return DashboardAccessibility.scale().lineHeight; }
    private static int pageLines() { return DashboardAccessibility.scale().pageLines; }

    public RealmDashboardScreen(RealmDashboardSnapshot snapshot) {
        super(Component.translatable("screen.livingrealms.dashboard"));
        this.snapshot = snapshot;
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
        int panelWidth = Math.min(panelWidthPref(), width - 20);
        int panelHeight = Math.min(panelHeightPref(), height - 20);
        int left = (width - panelWidth) / 2;
        int top = Math.max(10, (height - panelHeight) / 2);
        int buttonY = top + 28;
        int columns = tabColumns(panelWidth);
        int tabWidth = Math.max(42, (panelWidth - 16) / columns);
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab value=tabs[i];int row=i/columns,col=i%columns;
            int x=left+8+col*tabWidth;int y=buttonY+row*20;
            int buttonWidth=Math.max(40,tabWidth-2);
            var tabButton = Button.builder(Component.literal(value.label), b -> {
                tab = value;
                page = 0;
                contentScroll = 0;
                rebuildDashboardWidgets();
            }).bounds(x, y, buttonWidth, 18).build();
            tabButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("tooltip.livingrealms.dashboard_tab", value.label)));
            addRenderableWidget(tabButton);
        }
        int footerY = top + panelHeight - 26;
        addRenderableWidget(Button.builder(Component.translatable("button.livingrealms.refresh"), b -> DashboardClientState.requestRefresh())
                .bounds(left + 8, footerY, 70, 18).build());
        var prev = Button.builder(Component.literal("<"), b -> { page = Math.max(0, page - 1); rebuildDashboardWidgets(); })
                .bounds(left + panelWidth - 82, footerY, 32, 18).build();
        prev.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("tooltip.livingrealms.prev_page")));
        addRenderableWidget(prev);
        var next = Button.builder(Component.literal(">"), b -> { page = Math.min(maxPage(), page + 1); rebuildDashboardWidgets(); })
                .bounds(left + panelWidth - 44, footerY, 32, 18).build();
        next.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("tooltip.livingrealms.next_page")));
        addRenderableWidget(next);
        int contentY=contentTop(top,panelWidth);
        if (tab == Tab.LAW) rebuildLawButtons(left, contentY, panelWidth);
        if (tab == Tab.SETTINGS) rebuildSettingsButtons(left,contentY,panelWidth);
        if (tab == Tab.MAP) {
            addRenderableWidget(Button.builder(Component.literal("Open world map (M)"),
                    b -> DashboardClientState.requestOpenMap())
                    .bounds(left + panelWidth - 158, Math.max(contentY, footerY - 22), 148, 18).build());
        }
        if (tab == Tab.OVERVIEW) {
            rebuildFactionButton(left,contentY,panelWidth);
            rebuildInfluenceButtons(left,contentY,panelWidth);
        }
        if (tab == Tab.ECONOMY) rebuildEconomyButtons(left,contentY,panelWidth);
        if (tab == Tab.SETTLEMENTS) rebuildSettlementButtons(left,contentY,panelWidth);
    }

    private void rebuildLawButtons(int left,int top,int panelWidth){
        int start=Math.min(snapshot.bounties().size(),page*BOUNTIES_PER_PAGE);
        int end=Math.min(snapshot.bounties().size(),start+BOUNTIES_PER_PAGE);
        for(int i=start;i<end;i++){
            var bounty=snapshot.bounties().get(i);int row=i-start;String label=bounty.assignedToYou()?"Abandon":"Accept";
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                var action=bounty.assignedToYou()?DashboardActionCommand.Action.BOUNTY_ABANDON:DashboardActionCommand.Action.BOUNTY_ACCEPT;
                DashboardClientState.sendAction(new DashboardActionCommand(action,bounty.id()));
            }).bounds(left+panelWidth-84,top+7+row*33,66,18).build());
        }
    }

    private void rebuildFactionButton(int left,int contentY,int panelWidth){
        var player=snapshot.player();var jurisdiction=snapshot.jurisdiction();
        int panelHeight = Math.min(panelHeightPref(), height - 20);
        int top = Math.max(10, (height - panelHeight) / 2);
        int footerY = top + panelHeight - 26;
        int joinY = Math.min(contentY + 146, footerY - 22);
        if(player.memberFactionId()>0){
            addRenderableWidget(Button.builder(Component.literal("Leave "+player.memberFactionName()),b->DashboardClientState.sendAction(new DashboardActionCommand(DashboardActionCommand.Action.FACTION_LEAVE,1))).bounds(left+panelWidth-150,joinY,136,18).build());
        }else if(jurisdiction.claimed()&&!jurisdiction.contested()&&jurisdiction.primaryFactionId()>0){
            addRenderableWidget(Button.builder(Component.literal("Join "+jurisdiction.primaryName()),b->DashboardClientState.sendAction(new DashboardActionCommand(DashboardActionCommand.Action.FACTION_JOIN_LOCAL,jurisdiction.primaryFactionId()))).bounds(left+panelWidth-150,joinY,136,18).build());
        }else if(player.memberFactionId()<=0){
            addRenderableWidget(Button.builder(Component.literal("Found settlement here"),b->DashboardClientState.sendAction(new DashboardActionCommand(DashboardActionCommand.Action.FOUND_SETTLEMENT,1))).bounds(left+panelWidth-150,joinY,136,18).build());
        }
    }

    private void rebuildInfluenceButtons(int left,int contentY,int panelWidth){
        long factionId=snapshot.player().memberFactionId()>0?snapshot.player().memberFactionId():snapshot.jurisdiction().primaryFactionId();
        if(factionId<=0)return;
        DashboardActionCommand.Action[] actions={
                DashboardActionCommand.Action.REQUEST_AUDIENCE,
                DashboardActionCommand.Action.PROPOSE_PROJECT,
                DashboardActionCommand.Action.REQUEST_MILITARY_SUPPORT,
                DashboardActionCommand.Action.PETITION_TRADE,
                DashboardActionCommand.Action.PETITION_CLERGY
        };
        String[] labels={"Audience","Propose","Military","Trade","Clergy"};
        String[] tips={
                "tooltip.livingrealms.influence",
                "tooltip.livingrealms.grand_project",
                "tooltip.livingrealms.influence",
                "tooltip.livingrealms.route_security",
                "tooltip.livingrealms.influence"
        };
        int panelHeight = Math.min(panelHeightPref(), height - 20);
        int top = Math.max(10, (height - panelHeight) / 2);
        int footerY = top + panelHeight - 26;
        int rowY = Math.min(contentY + 166, footerY - 40);
        int bw=Math.max(58,(panelWidth-28)/5);
        for(int i=0;i<actions.length;i++){
            final DashboardActionCommand.Action action=actions[i];
            var button=Button.builder(Component.literal(labels[i]),
                    b->DashboardClientState.sendAction(new DashboardActionCommand(action,factionId)))
                    .bounds(left+10+i*bw,rowY,bw-3,16).build();
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(tips[i])));
            addRenderableWidget(button);
        }
    }


    private void rebuildEconomyButtons(int left,int contentY,int panelWidth){
        var player=snapshot.player();var realm=snapshot.realm();if(realm.factionId()<=0)return;
        if(player.memberFactionId()==realm.factionId()){
            addRenderableWidget(Button.builder(Component.literal("Tax -1%"),b->DashboardClientState.sendAction(new DashboardActionCommand(DashboardActionCommand.Action.TAX_LOWER,realm.factionId()))).bounds(left+panelWidth-150,contentY+3,64,18).build());
            addRenderableWidget(Button.builder(Component.literal("Tax +1%"),b->DashboardClientState.sendAction(new DashboardActionCommand(DashboardActionCommand.Action.TAX_RAISE,realm.factionId()))).bounds(left+panelWidth-82,contentY+3,64,18).build());
        }
        String key=selectedMarketResource();if(key==null)return;ResourceType resource=ResourceType.valueOf(key);long encoded=resource.ordinal()+1L;
        addRenderableWidget(Button.builder(Component.literal("Buy "+MarketTransactionEngine.PACKAGE_UNITS),b->DashboardClientState.sendAction(new DashboardActionCommand(DashboardActionCommand.Action.MARKET_BUY,encoded))).bounds(left+10,contentY+126,82,18).build());
        addRenderableWidget(Button.builder(Component.literal("Sell "+MarketTransactionEngine.PACKAGE_UNITS),b->DashboardClientState.sendAction(new DashboardActionCommand(DashboardActionCommand.Action.MARKET_SELL,encoded))).bounds(left+96,contentY+126,82,18).build());
    }
    private String selectedMarketResource(){if(snapshot.realm().marketPrices().isEmpty())return null;var keys=new java.util.ArrayList<>(snapshot.realm().marketPrices().keySet());java.util.Collections.sort(keys);return keys.get(Math.min(page,keys.size()-1));}

    private void rebuildSettlementButtons(int left,int contentY,int panelWidth){
        var player=snapshot.player();var realm=snapshot.realm();if(player.memberFactionId()<=0||player.memberFactionId()!=realm.factionId()||snapshot.settlements().isEmpty())return;
        int index=Math.min(snapshot.settlements().size()-1,page);var settlement=snapshot.settlements().get(index);
        DashboardActionCommand.Action[] actions={DashboardActionCommand.Action.SETTLEMENT_BALANCED,DashboardActionCommand.Action.SETTLEMENT_FOOD,DashboardActionCommand.Action.SETTLEMENT_HOUSING,DashboardActionCommand.Action.SETTLEMENT_INDUSTRY,DashboardActionCommand.Action.SETTLEMENT_DEFENSE};
        String[] labels={"Balanced","Food","Housing","Industry","Defense"};int bw=Math.max(66,(panelWidth-28)/5);
        for (int i = 0; i < actions.length; i++) {
            final DashboardActionCommand.Action action = actions[i];
            final String label = labels[i];
            int x = left + 10 + i * bw;
            addRenderableWidget(Button.builder(Component.literal(label),
                    b -> DashboardClientState.sendAction(new DashboardActionCommand(action, settlement.id())))
                    .bounds(x, contentY + 118, bw - 3, 18).build());
        }
    }

    private void rebuildSettingsButtons(int left,int contentY,int panelWidth){
        DashboardActionCommand.Action[] actions={DashboardActionCommand.Action.CONFIG_PERFORMANCE,DashboardActionCommand.Action.CONFIG_BALANCED,DashboardActionCommand.Action.CONFIG_IMMERSIVE,DashboardActionCommand.Action.CONFIG_CINEMATIC};
        String[] labels={"Performance","Balanced","Immersive","Cinematic"};
        int gap=6,buttonWidth=Math.max(90,(panelWidth-26-gap)/2);
        int panelHeight = Math.min(panelHeightPref(), height - 20);
        int top = Math.max(10, (height - panelHeight) / 2);
        int footerY = top + panelHeight - 26;
        // Keep all settings controls above the footer so LARGE UI never clips off-screen.
        int stackTop = Math.min(contentY + 72, footerY - 22 * 4 - 8);
        for (int i = 0; i < actions.length; i++) {
            final DashboardActionCommand.Action action = actions[i];
            final String label = labels[i];
            int col = i % 2;
            int row = i / 2;
            int by = Math.min(stackTop + row * 22, footerY - 44);
            addRenderableWidget(Button.builder(Component.literal(label),
                    b -> DashboardClientState.sendAction(new DashboardActionCommand(action, 1)))
                    .bounds(left + 10 + col * (buttonWidth + gap), by, buttonWidth, 18).build());
        }
        int a11yY = Math.min(stackTop + 48, footerY - 22);
        var scaleBtn = Button.builder(Component.translatable("button.livingrealms.ui_scale", DashboardAccessibility.scale().name()),
                b -> { DashboardAccessibility.cycleScale(); rebuildDashboardWidgets(); })
                .bounds(left + 10, a11yY, buttonWidth, 18).build();
        scaleBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("tooltip.livingrealms.ui_scale")));
        addRenderableWidget(scaleBtn);
        String contrastLabel = DashboardAccessibility.highContrast() ? "High contrast: ON" : "High contrast: OFF";
        var contrastBtn = Button.builder(Component.literal(contrastLabel),
                b -> { DashboardAccessibility.toggleHighContrast(); rebuildDashboardWidgets(); })
                .bounds(left + 10 + buttonWidth + gap, a11yY, buttonWidth, 18).build();
        contrastBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("tooltip.livingrealms.high_contrast")));
        addRenderableWidget(contrastBtn);
    }

    private static int tabColumns(int panelWidth){return Math.max(3,Math.min(7,Math.max(1,(panelWidth-16)/70)));}
    private static int tabRows(int panelWidth){return (Tab.values().length+tabColumns(panelWidth)-1)/tabColumns(panelWidth);}
    private static int contentTop(int top,int panelWidth){return top+28+tabRows(panelWidth)*20+7;}

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        LivingRealmsScreens.clearBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        LivingRealmsScreens.paintBackdrop(graphics, width, height, DashboardAccessibility.backdrop());
        int panelWidth = Math.min(panelWidthPref(), width - 20);
        int panelHeight = Math.min(panelHeightPref(), height - 20);
        // Prefer fitting the whole panel on screen; shrink further on short displays.
        panelHeight = Math.min(panelHeight, Math.max(260, height - 24));
        panelWidth = Math.min(panelWidth, Math.max(360, width - 24));
        int left = (width - panelWidth) / 2;
        int top = Math.max(8, (height - panelHeight) / 2);
        var panelTex = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("livingrealms", "textures/gui/dashboard_panel.png");
        graphics.blit(panelTex, left, top, 0, 0, panelWidth, panelHeight, panelWidth, panelHeight);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, DashboardAccessibility.panelScrim());
        graphics.fill(left, top, left + panelWidth, top + 3, DashboardAccessibility.textColor(0xFFB59A5A));
        graphics.drawCenteredString(font, title, width / 2, top + 9, DashboardAccessibility.textColor(0xFFF2E8C9));
        super.render(graphics, mouseX, mouseY, partialTick);

        int contentY=contentTop(top,panelWidth);
        int contentBottom = top + panelHeight - 30;
        int clipH = Math.max(40, contentBottom - contentY);
        if (tab == Tab.MAP) {
            renderStrategicMap(graphics, left + 10, contentY, panelWidth - 20, clipH);
        } else if (tab == Tab.LAW) {
            renderLaw(graphics,left+11,contentY+2,panelWidth-22);
        } else {
            List<Line> lines = linesForCurrentTab();
            int start = Math.min(lines.size(), page * pageLines());
            int end = Math.min(lines.size(), start + pageLines());
            int y = contentY - contentScroll;
            graphics.enableScissor(left + 8, contentY, left + panelWidth - 8, contentBottom);
            for (int i = start; i < end; i++) {
                Line line = lines.get(i);
                if (y + lineHeight() >= contentY && y < contentBottom) {
                    graphics.drawString(font, line.text, left + 11 + line.indent * 8, y, DashboardAccessibility.textColor(line.color), false);
                }
                y += lineHeight();
            }
            graphics.disableScissor();
            String pageText = (maxPage() + 1) <= 1 ? "1/1" : (page + 1) + "/" + (maxPage() + 1);
            graphics.drawCenteredString(font, pageText + "  • scroll", width / 2, top + panelHeight - 21, DashboardAccessibility.textColor(0xFFAAAAAA));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            if (tab != Tab.MAP && tab != Tab.LAW) {
                contentScroll = Math.max(0, contentScroll - (int) Math.round(scrollY * lineHeight() * 2));
                int maxScroll = Math.max(0, pageLines() * lineHeight() - 40);
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
        // Arrow keys page the current tab; number row jumps to the first ten tabs.
        if (keyCode == 263 || keyCode == 265) { // left / up
            page = Math.max(0, page - 1);
            rebuildDashboardWidgets();
            return true;
        }
        if (keyCode == 262 || keyCode == 264) { // right / down
            page = Math.min(maxPage(), page + 1);
            rebuildDashboardWidgets();
            return true;
        }
        if (keyCode >= 49 && keyCode <= 57) { // 1-9
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
        if (tab == Tab.MAP) return 0;
        if (tab == Tab.LAW) return Math.max(0,(snapshot.bounties().size()-1)/BOUNTIES_PER_PAGE);
        if (tab == Tab.SETTLEMENTS) return Math.max(0,snapshot.settlements().size()-1);
        if (tab == Tab.ECONOMY) return Math.max(0,snapshot.realm().marketPrices().size()-1);
        int size = linesForCurrentTab().size();
        return Math.max(0, (size - 1) / pageLines());
    }

    private List<Line> linesForCurrentTab() {
        return switch (tab) {
            case OVERVIEW -> overviewLines();
            case KINGDOMS -> kingdomLines();
            case POLITICS -> politicsLines();
            case SETTLEMENTS -> settlementLines();
            case SOCIETY -> societyLines();
            case SETTINGS -> settingsLines();
            case WARS -> warLines();
            case ECONOMY -> economyLines();
            case OPERATIONS -> operationsLines();
            case ECOLOGY -> ecologyLines();
            case FORCES -> forcesLines();
            case MAP -> List.of();
            case LAW -> List.of();
            case HISTORY -> historyLines();
        };
    }

    private List<Line> overviewLines() {
        List<Line> lines = new ArrayList<>();
        var j = snapshot.jurisdiction();
        var p = snapshot.player();
        var r = snapshot.realm();
        lines.add(header("Day " + snapshot.day()));
        lines.add(text(j.claimed() ? "Location: " + j.primaryName() + (j.contested() ? " (CONTESTED)" : "") : "Location: Wilderness"));
        lines.add(text("Membership: " + (p.memberFactionId() > 0 ? p.memberFactionName() + " / " + p.rank() : "None")));
        lines.add(text("Reputation: " + whole(p.localReputation()) + "   Infamy: " + whole(p.globalInfamy())));
        if (p.careerTrack() != null && !p.careerTrack().isBlank()) {
            lines.add(text("Career: " + p.careerTrack() + " / " + p.careerRank()));
        }
        if (p.influence() != null && !p.influence().isEmpty()) {
            StringBuilder inf = new StringBuilder("Influence:");
            p.influence().entrySet().stream().sorted((a, b) -> Double.compare(b.getValue(), a.getValue())).limit(4)
                    .forEach(e -> inf.append(' ').append(e.getKey()).append('=').append(whole(e.getValue())));
            lines.add(text(inf.toString()));
        }
        lines.add(text("Wanted: " + p.wantedLevel() + "   Bounty: " + whole(p.bounty())));
        if (p.inCustody()) lines.add(warn("IN CUSTODY until day " + p.custodyReleaseDay()));
        lines.add(header("Player actions"));
        lines.add(dim("J dashboard: settlements, economy, politics, wars. M opens the world map. Chat NPCs freely.", 0));
        lines.add(dim("Found a growing realm with Found settlement (Overview) or /livingrealms found <name>.", 0));
        lines.add(dim("Policy buttons on Settlements change what your towns build next.", 0));
        if (r.factionId() > 0) {
            lines.add(header("Realm"));
            lines.add(text(r.name() + " — ruler " + r.ruler()));
            lines.add(text(r.governmentType() + " / " + r.successionLaw()));
            lines.add(text("Population " + r.population() + "   settlements " + r.settlementCount()));
            lines.add(text("Army " + r.armyPersonnel() + "   airframes " + r.airframes() + "   ships " + r.ships()));
            lines.add(text("Industry " + r.industrialSites() + "   ports " + r.ports() + "   shipments " + r.activeShipments()));
            lines.add(text("Wars " + r.activeWars() + "   treaties " + r.activeTreaties()));
            if (r.activeDebts() > 0 || r.grandProjects() > 0 || r.campaignPlans() > 0) {
                lines.add(text("Debts " + r.activeDebts() + "   projects " + r.grandProjects() + "   campaigns " + r.campaignPlans()));
            }
        }
        List<Line> notices = importantNotices();
        if (!notices.isEmpty()) {
            lines.add(header("Important notices"));
            lines.addAll(notices);
        }
        return lines;
    }

    private List<Line> importantNotices() {
        List<Line> out = new ArrayList<>();
        var history = snapshot.history();
        for (int i = history.size() - 1; i >= 0; i--) {
            var h = history.get(i);
            if (!dev.livingrealms.sim.ui.ImportantNotifications.isImportant(h.type())) continue;
            String label = dev.livingrealms.sim.ui.ImportantNotifications.label(h.type());
            out.add(text("Day " + h.day() + " [" + label + "] " + h.message()));
            if (out.size() >= 5) break;
        }
        return out;
    }

    private List<Line> politicsLines() {
        List<Line> lines = new ArrayList<>();
        var realm = snapshot.realm();
        var politics = snapshot.politics();
        if (realm.factionId() <= 0) {
            lines.add(dim("No realm selected. Stand in claimed territory or join a faction.", 0));
            return lines;
        }
        lines.add(header("Government"));
        lines.add(text(realm.name() + " — " + realm.governmentType() + " / " + realm.successionLaw()));
        lines.add(text("Ruler: " + realm.ruler()));
        lines.add(dim("Stability " + pct(realm.stability()) + " • legitimacy " + pct(realm.legitimacy()) + " • corruption " + pct(realm.corruption()), 1));
        lines.add(dim("Tax rate " + pct(realm.taxRate()) + " • active treaties " + realm.activeTreaties(), 1));

        lines.add(header("Foreign relations"));
        for (var relation : politics.relations()) {
            int color = switch (relation.status()) {
                case "WAR", "HOSTILE" -> 0xFFFF7B72;
                case "RIVAL" -> 0xFFFFB56B;
                case "FRIENDLY", "ALLIED" -> 0xFF7EE787;
                default -> 0xFFE0E0E0;
            };
            String trade = relation.tradeAgreement() ? " • trade" : "";
            lines.add(new Line(relation.factionName() + " — " + relation.status() + " • opinion " + whole(relation.opinion()) + trade, color, 0));
        }
        if (politics.relations().isEmpty()) lines.add(dim("No foreign relations recorded.", 0));

        lines.add(header("Active treaties"));
        for (var treaty : politics.treaties()) {
            lines.add(text("#" + treaty.id() + " " + titleCase(treaty.type()) + " — " + treaty.otherFactionName()));
            lines.add(dim("Day " + treaty.startDay() + " → " + treaty.endDay(), 1));
        }
        if (politics.treaties().isEmpty()) lines.add(dim("No active treaties.", 0));
        return lines;
    }

    private List<Line> kingdomLines() {
        List<Line> lines = new ArrayList<>();
        lines.add(header("Known kingdoms"));
        for (var f : snapshot.factions()) {
            String marker = f.memberRealm() ? "[YOU] " : f.localRealm() ? "[HERE] " : "";
            lines.add(text(marker + f.name() + " — pop " + f.population() + ", settlements " + f.settlements()));
            lines.add(dim("Ruler " + f.ruler() + " • treasury " + whole(f.treasury()) + " • tech " + one(f.technology()), 1));
        }
        if (snapshot.factions().isEmpty()) lines.add(dim("No kingdoms known.", 0));
        return lines;
    }

    private List<Line> settlementLines() {
        List<Line> lines = new ArrayList<>();
        lines.add(header("How settlements work"));
        lines.add(dim("Settlements grow when food, housing and order stay healthy. Policy buttons change the construction queue.", 0));
        lines.add(dim("Found your own realm with the Found button (or /livingrealms found <name>) when not in a faction.", 0));
        lines.add(dim("Visited frontier lands beyond the starter belt seed sparse outposts so the world never empties.", 0));
        if (snapshot.settlements().isEmpty()) {
            lines.add(dim("No settlement data for the current/member realm yet — join a kingdom or found one.", 0));
            return lines;
        }
        int index=Math.min(snapshot.settlements().size()-1,page);var s=snapshot.settlements().get(index);
        lines.add(header("Settlement " + (index+1) + "/" + snapshot.settlements().size()));
        lines.add(text(s.name() + " [" + s.tier() + "] — pop " + s.population() + " • " + whole(s.distanceBlocks()) + "m"));
        lines.add(text("Development policy: " + titleCase(s.developmentPriority())));
        lines.add(dim("Housing " + s.housing() + " • prosperity " + pct(s.prosperity()) + " • unrest " + pct(s.unrest()), 1));
        lines.add(dim("Food " + pct(s.foodSecurity()) + " • order " + pct(s.publicOrder()) + " • employment " + pct(s.employment()), 1));
        lines.add(dim("Housing satisfaction " + pct(s.housingSatisfaction()) + " • goods " + pct(s.goodsAccess()), 1));
        lines.add(dim("Society satisfaction " + pct(s.societySatisfaction()) + " • pressure " + titleCase(s.primaryPressure()) + " " + pct(s.pressureSeverity()), 1));
        if(s.causeSummary()!=null&&!s.causeSummary().isBlank()) lines.add(dim("Why: "+s.causeSummary(),1));
        if(snapshot.player().memberFactionId()==snapshot.realm().factionId()) lines.add(dim("Use the policy buttons below; changes affect real construction order.",0));
        return lines;
    }

    private List<Line> societyLines() {
        List<Line> lines = new ArrayList<>();
        if (snapshot.settlements().isEmpty()) {
            lines.add(dim("No society data for the current/member realm.", 0));
            return lines;
        }
        double satisfaction = snapshot.settlements().stream().mapToDouble(RealmDashboardSnapshot.SettlementView::societySatisfaction).average().orElse(0);
        double unrest = snapshot.settlements().stream().mapToDouble(RealmDashboardSnapshot.SettlementView::unrest).average().orElse(0);
        lines.add(header("Civilian conditions"));
        lines.add(text("Realm satisfaction " + pct(satisfaction) + " • average unrest " + pct(unrest)));
        for (var s : snapshot.settlements()) {
            String state = s.societySatisfaction() >= .82 && s.unrest() < .25 ? "STABLE" : s.societySatisfaction() >= .60 && s.unrest() < .55 ? "STRAINED" : "CRITICAL";
            lines.add((state.equals("CRITICAL") ? warn(s.name() + " — " + state) : text(s.name() + " — " + state)));
            lines.add(dim("Satisfaction " + pct(s.societySatisfaction()) + " • prosperity " + pct(s.prosperity()) + " • unrest " + pct(s.unrest()), 1));
            lines.add(dim("Food " + pct(s.foodSecurity()) + " • housing " + pct(s.housingSatisfaction()) + " • safety " + pct(s.publicOrder()), 1));
            lines.add(dim("Employment " + pct(s.employment()) + " • goods " + pct(s.goodsAccess()), 1));
            if (!s.primaryPressure().equals("BALANCED")) {
                lines.add(warn("  Main pressure: " + titleCase(s.primaryPressure()) + " (" + pct(s.pressureSeverity()) + " deficit)"));
                if (s.causeSummary() != null && !s.causeSummary().isBlank()) {
                    lines.add(dim("  Why: " + s.causeSummary(), 1));
                }
            } else {
                lines.add(dim("Needs are broadly balanced.", 1));
                if (s.causeSummary() != null && !s.causeSummary().isBlank()) {
                    lines.add(dim("  " + s.causeSummary(), 1));
                }
            }
        }
        return lines;
    }

    private List<Line> settingsLines(){
        List<Line> lines=new ArrayList<>();var c=snapshot.settings();
        lines.add(header("Singleplayer simulation profile"));
        lines.add(text("Current: "+titleCase(c.profile())));
        lines.add(dim("Physical radius "+whole(c.physicalRadius())+"m • regional radius "+whole(c.regionalRadius())+"m",1));
        lines.add(dim("Wildlife "+c.wildlifeBudget()+" • caravans "+c.caravanBudget()+" • military "+c.militaryBudget()+" • naval "+c.navalBudget(),1));
        lines.add(dim("Construction budget "+c.constructionOpsPerTick()+" block ops/tick",1));
        lines.add(header("Profiles"));
        lines.add(dim("Performance — fewer nearby physical projections.",0));
        lines.add(dim("Balanced — default recommendation.",0));
        lines.add(dim("Immersive — denser living world around you.",0));
        lines.add(warn("Cinematic — highest entity/build budgets; use only if performance remains stable."));
        lines.add(header("Accessibility"));
        lines.add(text("UI scale: "+DashboardAccessibility.scale().name()));
        lines.add(text("High contrast: "+(DashboardAccessibility.highContrast()?"ON":"OFF")));
        lines.add(dim("Client-only; does not change simulation budgets.",1));
        return lines;
    }

    private List<Line> warLines() {
        List<Line> lines = new ArrayList<>();
        lines.add(header("Active wars"));
        for (var w : snapshot.wars()) {
            lines.add(warn(w.attackerName() + " vs " + w.defenderName()));
            lines.add(dim(w.goal() + " • since day " + w.startDay() + " • score " + one(w.attackerScore()), 1));
            lines.add(dim("Exhaustion A " + pct(w.attackerExhaustion()) + " / D " + pct(w.defenderExhaustion()), 1));
        }
        if (snapshot.wars().isEmpty()) lines.add(dim("No active wars relevant to this realm.", 0));
        lines.add(header("Army objectives"));
        for (var objective : snapshot.warfare().objectives()) {
            lines.add(text("Army #" + objective.armyId() + " — " + titleCase(objective.type()) + " → " + objective.target()));
            lines.add(dim("Priority " + objective.priority() + " • " + whole(objective.distanceBlocks()) + "m from you", 1));
        }
        if (snapshot.warfare().objectives().isEmpty()) lines.add(dim("No active army objectives for this realm.", 0));
        lines.add(header("Active sieges"));
        for (var siege : snapshot.warfare().sieges()) {
            lines.add(warn(siege.attacker() + " besieges " + siege.settlement()));
            lines.add(dim("Defender " + siege.defender() + " • since day " + siege.startDay(), 1));
            lines.add(dim("Progress " + pct(siege.progress()) + " • blockade " + pct(siege.blockade()), 1));
        }
        if (snapshot.warfare().sieges().isEmpty()) lines.add(dim("No active sieges involving this realm.", 0));
        return lines;
    }

    private List<Line> economyLines() {
        List<Line> lines = new ArrayList<>();
        var r = snapshot.realm();
        if (r.factionId() <= 0) {
            lines.add(dim("No realm selected. Stand in claimed territory or join a faction.", 0));
            return lines;
        }
        lines.add(header(r.name() + " economy"));
        lines.add(text("Treasury: " + whole(r.treasury()) + "   Technology: " + one(r.technology())));
        lines.add(text("Stability " + pct(r.stability()) + " • legitimacy " + pct(r.legitimacy()) + " • corruption " + pct(r.corruption())));
        lines.add(text("Tax rate: " + pct(r.taxRate())));
        lines.add(header("Local market"));
        String key=selectedMarketResource();
        if(key==null){lines.add(dim("No player-tradable commodities at this realm.",0));return lines;}
        double stock=r.resources().getOrDefault(key,0d),unit=r.marketPrices().getOrDefault(key,0d);int buy=r.marketBuyCosts().getOrDefault(key,0),sell=r.marketSellPayouts().getOrDefault(key,0);
        lines.add(text(titleCase(key)+" • realm stock "+whole(stock)));
        lines.add(text("Market value "+one(unit)+" / unit"));
        lines.add(text("Buy "+MarketTransactionEngine.PACKAGE_UNITS+" → "+buy+" emeralds"));
        lines.add(text("Sell "+MarketTransactionEngine.PACKAGE_UNITS+" → "+sell+" emeralds"));
        lines.add(dim("Trade requires an operational Living Realms market nearby.",0));
        lines.add(dim("Prices are server-authoritative and change with scarcity.",0));
        return lines;
    }



    private void renderLaw(GuiGraphics graphics,int x,int y,int w){
        var p=snapshot.player();var j=snapshot.jurisdiction();
        graphics.drawString(font,j.claimed()?"Law office — "+j.primaryName():"No local jurisdiction",x,y,0xFFF2E8C9,false);y+=14;
        graphics.drawString(font,"Wanted: "+p.wantedLevel()+"  bounty "+whole(p.bounty())+"  infamy "+whole(p.globalInfamy()),x,y,0xFFE0E0E0,false);y+=17;
        if(j.contested()){graphics.drawString(font,"Board unavailable in contested territory.",x,y,0xFFFFB56B,false);return;}
        if(snapshot.bounties().isEmpty()){graphics.drawString(font,"No open or assigned bounties at this board.",x,y,0xFFA5A5A5,false);return;}
        int start=Math.min(snapshot.bounties().size(),page*BOUNTIES_PER_PAGE),end=Math.min(snapshot.bounties().size(),start+BOUNTIES_PER_PAGE);
        for(int i=start;i<end;i++){
            var b=snapshot.bounties().get(i);int rowY=y+(i-start)*33;
            graphics.drawString(font,"#"+b.id()+"  "+compactTarget(b.targetKey()),x,rowY,b.assignedToYou()?0xFFFFD47A:0xFFE0E0E0,false);
            graphics.drawString(font,"Reward "+whole(b.reward())+" • "+b.status(),x+8,rowY+11,0xFFA5A5A5,false);
        }
        String pageText=(page+1)+"/"+(maxPage()+1);graphics.drawString(font,pageText,x+w-24,y+BOUNTIES_PER_PAGE*33,0xFF888888,false);
    }

    private static String compactTarget(String key){
        if(key==null||key.isBlank())return "Unknown target";int colon=key.indexOf(':');String value=colon>=0?key.substring(colon+1):key;return value.length()>18?value.substring(0,18)+"…":value;
    }

    private void renderStrategicMap(GuiGraphics graphics,int x,int y,int w,int h) {
        var map=snapshot.map();
        graphics.fill(x,y,x+w,y+h,0xFF0D1117);
        graphics.fill(x,y,x+w,y+1,0xFF3A4654);graphics.fill(x,y+h-1,x+w,y+h,0xFF3A4654);
        graphics.fill(x,y,x+1,y+h,0xFF3A4654);graphics.fill(x+w-1,y,x+w,y+h,0xFF3A4654);
        // Soft geographic wash so the panel reads as a map, not an abstract icon soup.
        ClientTerrainMapCache.paintIfAvailable(graphics,snapshot,x+1,y+1,w-2,h-2);
        double spanX=Math.max(1,map.maxX()-map.minX()),spanZ=Math.max(1,map.maxZ()-map.minZ());
        for(var c:map.claims())drawClaim(graphics,map,c,x,y,w,h,spanX,spanZ,factionColor(c.factionId(),0x88));
        for(var r:map.routes()){
            int color=r.operational()?("RAIL".equals(r.mode())?0xFFD1B56A:"CARAVAN".equals(r.mode())?0xFF8B7355:0xFF88919C):0xFF4A4F56;
            drawWorldLine(graphics,map,r.fromX(),r.fromZ(),r.toX(),r.toZ(),x,y,w,h,color);
        }
        for(var f:map.fronts())drawWorldLine(graphics,map,f.fromX(),f.fromZ(),f.toX(),f.toZ(),x,y,w,h,0xFFFF625E);
        int labeled=0;
        for(var s:map.settlements()){
            int sx=mapX(map,s.x(),x,w),sy=mapY(map,s.z(),y,h),color=factionColor(s.factionId(),0xFF);
            int size=s.population()>=10000?4:s.population()>=2000?3:2;
            graphics.fill(sx-size,sy-size,sx+size+1,sy+size+1,color);
            if(labeled<18 && s.name()!=null && !s.name().isBlank()){
                graphics.drawString(font,s.name(),sx+size+2,sy-3,0xFFE8EEF5,false);
                labeled++;
            }
        }
        for(var a:map.armies()){
            int sx=mapX(map,a.x(),x,w),sy=mapY(map,a.z(),y,h),color=factionColor(a.factionId(),0xFF);
            graphics.fill(sx-2,sy-4,sx+3,sy-2,color);graphics.fill(sx-1,sy-2,sx+2,sy+3,color);
        }
        int px=mapX(map,map.playerX(),x,w),py=mapY(map,map.playerZ(),y,h);
        graphics.fill(px-4,py,px+5,py+1,0xFFFFFFFF);graphics.fill(px,py-4,px+1,py+5,0xFFFFFFFF);
        graphics.drawString(font,"Geographic map · settlements labeled · press M for full terrain",x+4,y+4,0xFFB9C2CC,false);
        graphics.drawString(font,"You @ X "+whole(map.playerX())+"  Z "+whole(map.playerZ())+"  ·  founding needs "
                +(int)Math.round(dev.livingrealms.sim.player.PlayerSettlementFounder.MIN_SETTLEMENT_SPACING)+"m clearance",x+4,y+h-11,0xFFB9C2CC,false);
    }

    private static int mapX(RealmDashboardSnapshot.StrategicMapView map,double worldX,int x,int w){return x+(int)Math.round((worldX-map.minX())/Math.max(1,map.maxX()-map.minX())*(w-1));}
    private static int mapY(RealmDashboardSnapshot.StrategicMapView map,double worldZ,int y,int h){return y+(int)Math.round((worldZ-map.minZ())/Math.max(1,map.maxZ()-map.minZ())*(h-1));}

    private static void drawWorldLine(GuiGraphics g,RealmDashboardSnapshot.StrategicMapView map,double ax,double az,double bx,double bz,int x,int y,int w,int h,int color){
        drawLine(g,mapX(map,ax,x,w),mapY(map,az,y,h),mapX(map,bx,x,w),mapY(map,bz,y,h),color);
    }

    private static void drawLine(GuiGraphics g,int x0,int y0,int x1,int y1,int color){
        int dx=Math.abs(x1-x0),sx=x0<x1?1:-1,dy=-Math.abs(y1-y0),sy=y0<y1?1:-1,err=dx+dy;
        for(;;){g.fill(x0,y0,x0+1,y0+1,color);if(x0==x1&&y0==y1)break;int e2=2*err;if(e2>=dy){err+=dy;x0+=sx;}if(e2<=dx){err+=dx;y0+=sy;}}
    }

    private static void drawClaim(GuiGraphics g,RealmDashboardSnapshot.StrategicMapView map,RealmDashboardSnapshot.MapClaim c,int x,int y,int w,int h,double spanX,double spanZ,int color){
        int cx=mapX(map,c.x(),x,w),cy=mapY(map,c.z(),y,h);double rx=c.radius()/spanX*(w-1),ry=c.radius()/spanZ*(h-1);int px=0,py=0;
        for(int i=0;i<=24;i++){double a=Math.PI*2*i/24.0;int nx=cx+(int)Math.round(Math.cos(a)*rx),ny=cy+(int)Math.round(Math.sin(a)*ry);if(i>0)drawLine(g,px,py,nx,ny,color);px=nx;py=ny;}
    }

    private static int factionColor(long id,int alpha){
        int[] colors={0xD95C5C,0x5C8DD9,0x65B96E,0xC69A4B,0x9A6DD1,0x4CB6B0,0xD47AA5,0xA0A85A,0xD9784A,0x6C7FD1,0x6FB09B,0xB27A52};
        int rgb=colors[Math.floorMod(Long.hashCode(id),colors.length)];return (alpha<<24)|rgb;
    }


    private List<Line> operationsLines() {
        List<Line> lines=new ArrayList<>();var ops=snapshot.operations();
        lines.add(header("Trade shipments"));
        for(var s:ops.shipments()){
            lines.add(text("#"+s.id()+" "+titleCase(s.resource())+" "+whole(s.amount())+" • "+pct(s.progress())));
            lines.add(dim(s.seller()+" → "+s.buyer()+" • value "+whole(s.value())+" • "+whole(s.distanceBlocks())+"m",1));
        }
        if(ops.shipments().isEmpty())lines.add(dim("No active shipments for this realm.",0));
        lines.add(header("Transport network"));
        for(var r:ops.routes()){
            lines.add(text("#"+r.id()+" "+r.mode()+" • "+r.from()+" → "+r.to()));
            lines.add(dim((r.operational()?"ONLINE":"OFFLINE")+" • quality "+pct(r.quality())+" • security "+pct(r.security())+" • cap "+whole(r.capacityPerDay())+"/day",1));
        }
        if(ops.routes().isEmpty())lines.add(dim("No canonical routes for this realm.",0));
        lines.add(header("Industry"));
        for(var site:ops.industry()){
            int color=("ACTIVE".equals(site.status())?0xFFE0E0E0:0xFFFFB56B);
            lines.add(new Line("#"+site.id()+" "+titleCase(site.kind())+" L"+site.level()+" @ "+site.settlement(),color,0));
            lines.add(dim(site.status()+" • condition "+pct(site.condition())+" • utilization "+pct(site.utilization())+" • cycles "+site.cycles()+" • downtime "+site.downtimeDays()+"d",1));
        }
        if(ops.industry().isEmpty())lines.add(dim("No industrial sites for this realm.",0));
        lines.add(header("Assistance contracts"));
        for(var t:ops.assistanceTasks()){
            lines.add(text("#"+t.id()+" "+titleCase(t.type())+" @ "+t.settlement()+" • "+pct(t.progress())+" done"));
            lines.add(dim("Pressure left "+pct(t.remainingPressure())+" • expires day "+t.expiresDay()+" • "+t.cause(),1));
            lines.add(dim("Deliver verified goods with /livingrealms assist deliver",2));
        }
        if(ops.assistanceTasks().isEmpty())lines.add(dim("No open assistance contracts for this realm.",0));
        return lines;
    }


    private List<Line> forcesLines() {
        List<Line> lines = new ArrayList<>();
        var forces = snapshot.forces();
        lines.add(header("Air forces"));
        for (var wing : forces.airWings()) {
            lines.add(text("#" + wing.id() + " " + titleCase(wing.model()) + " × " + wing.aircraft() + " • " + titleCase(wing.mission())));
            lines.add(dim(titleCase(wing.role()) + " • fuel " + pct(wing.fuel()) + " • readiness " + pct(wing.readiness()) + " • exp " + pct(wing.experience()) + " • " + whole(wing.distanceBlocks()) + "m", 1));
        }
        if (forces.airWings().isEmpty()) lines.add(dim("No active air wings for this realm.", 0));

        lines.add(header("Naval forces"));
        for (var fleet : forces.fleets()) {
            lines.add(text("#" + fleet.id() + " " + fleet.ships() + " ships • " + titleCase(fleet.mission()) + " • power " + whole(fleet.combatPower())));
            lines.add(dim(fleet.composition() + " • fuel " + pct(fleet.fuel()) + " • supply " + pct(fleet.supply()) + " • readiness " + pct(fleet.readiness()), 1));
            if (fleet.embarkedPersonnel() > 0) lines.add(dim("Embarked personnel " + fleet.embarkedPersonnel() + " • " + whole(fleet.distanceBlocks()) + "m", 2));
        }
        if (forces.fleets().isEmpty()) lines.add(dim("No active fleets for this realm.", 0));

        lines.add(header("Ports"));
        for (var port : forces.ports()) {
            String status = port.operational() ? "ONLINE" : "OFFLINE";
            lines.add(text("#" + port.id() + " " + port.settlement() + " • level " + port.level() + " • " + status));
            lines.add(dim("Condition " + pct(port.condition()) + " • security " + pct(port.security()) + " • " + whole(port.distanceBlocks()) + "m", 1));
        }
        if (forces.ports().isEmpty()) lines.add(dim("No ports for this realm.", 0));
        return lines;
    }

    private List<Line> ecologyLines() {
        List<Line> lines=new ArrayList<>();var eco=snapshot.ecology();
        lines.add(header("Living ecology"));
        lines.add(text("Catalog "+eco.catalogSpecies()+" species • "+eco.regionCount()+" regions • "+eco.populationGroups()+" populations"));
        lines.add(text("Estimated wildlife: "+whole(eco.totalAnimals())));
        for(var region:eco.regions()){
            lines.add(header(titleCase(region.biome())+" • "+whole(region.distanceBlocks())+"m"));
            lines.add(dim("Region #"+region.id()+" • "+one(region.areaKm2())+" km² • plants "+whole(region.plantBiomass())+" • animals "+whole(region.animals())+" • groups "+region.groups(),1));
            for(var species:region.dominantSpecies()){
                lines.add(text("  "+species.commonName()+" × "+whole(species.population())+" • health "+pct(species.health())));
                lines.add(dim("hunger "+pct(species.hunger())+" • thirst "+pct(species.thirst())+" • "+titleCase(species.locomotion())+" / "+titleCase(species.morphology()),2));
            }
            if(region.dominantSpecies().isEmpty())lines.add(dim("No established animal populations.",1));
        }
        if(eco.regions().isEmpty())lines.add(dim("No ecosystem regions discovered around this world yet.",0));
        return lines;
    }

    private List<Line> historyLines() {
        List<Line> lines = new ArrayList<>();
        lines.add(header("Recent world history"));
        for (var h : snapshot.history()) lines.add(text("D" + h.day() + " • " + h.message()));
        if (snapshot.history().isEmpty()) lines.add(dim("No recorded history yet.", 0));
        return lines;
    }

    private static Line header(String value) { return new Line(value, 0xFFF2E8C9, 0); }
    private static Line text(String value) { return new Line(value, 0xFFE0E0E0, 0); }
    private static Line warn(String value) { return new Line(value, 0xFFFFB56B, 0); }
    private static Line dim(String value, int indent) { return new Line(value, 0xFFA5A5A5, indent); }
    private static String whole(double value) { return String.format(Locale.ROOT, "%.0f", value); }
    private static String one(double value) { return String.format(Locale.ROOT, "%.1f", value); }
    private static String pct(double value) { return String.format(Locale.ROOT, "%.0f%%", value * 100.0); }
    private static String titleCase(String value) {
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private record Line(String text, int color, int indent) {}

    private enum Tab {
        OVERVIEW("Overview"), MAP("Map"), LAW("Law"), KINGDOMS("Realms"), POLITICS("Politics"), SETTLEMENTS("Cities"), SOCIETY("Society"), SETTINGS("Settings"), WARS("Wars"), ECONOMY("Economy"), OPERATIONS("Ops"), FORCES("Forces"), ECOLOGY("Ecology"), HISTORY("History");
        private final String label;
        Tab(String label) { this.label = label; }
    }
}
