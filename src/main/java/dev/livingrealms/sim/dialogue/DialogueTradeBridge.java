package dev.livingrealms.sim.dialogue;

import dev.livingrealms.sim.economy.LocalMarketEngine;
import dev.livingrealms.sim.economy.MarketQuote;
import dev.livingrealms.sim.economy.MarketTransactionEngine;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.world.SimulationState;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Pure server-side trade quote text for dialogue OPEN_TRADE actions. */
public final class DialogueTradeBridge {
    private DialogueTradeBridge() {}

    public static String quoteSummary(SimulationState state, SocialCitizen citizen) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(citizen, "citizen");
        Faction faction = state.findFaction(citizen.factionId()).orElse(null);
        Settlement settlement = state.findSettlement(citizen.settlementId()).orElse(null);
        if (faction == null || settlement == null) return "Trade is available through this settlement's market.";
        long day = state.clock().day();
        StringBuilder sb = new StringBuilder("Local market in ").append(settlement.name()).append(':');
        for (ResourceType resource : List.of(ResourceType.FOOD, ResourceType.WOOD, ResourceType.IRON, ResourceType.TEXTILES)) {
            MarketQuote quote = LocalMarketEngine.quote(faction, settlement, resource, day);
            sb.append(' ').append(resource.name()).append('=').append(String.format(Locale.ROOT, "%.2f", quote.unitPrice()));
        }
        var foodBuy = MarketTransactionEngine.quote(faction, ResourceType.FOOD, MarketTransactionEngine.Side.BUY_FROM_REALM);
        var foodSell = MarketTransactionEngine.quote(faction, ResourceType.FOOD, MarketTransactionEngine.Side.SELL_TO_REALM);
        sb.append(". Realm package FOOD buy ").append(foodBuy.available() ? foodBuy.emeralds() + " emeralds" : "unavailable")
                .append(", sell ").append(foodSell.available() ? foodSell.emeralds() + " emeralds" : "unavailable")
                .append(". Open F12 Economy to complete a transaction.");
        return sb.toString();
    }
}
