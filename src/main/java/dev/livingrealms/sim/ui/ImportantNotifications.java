package dev.livingrealms.sim.ui;

import java.util.Locale;
import java.util.Set;

/** Classifies world history into bounded important player notifications (Wave 151). */
public final class ImportantNotifications {
    private static final Set<String> IMPORTANT = Set.of(
            "war_declared", "war_ended", "peace_treaty",
            "siege_started", "siege_breached", "siege_lifted",
            "ruler_died", "succession", "succession_crisis",
            "epidemic", "rebellion", "rebellion_started",
            "grand_project_complete", "grand_project_started", "player_proposed_project",
            "trade_partial_loss", "trade_intercepted", "trade_caravan_lost",
            "hero_monumented", "hero_recognized",
            "player_audience", "player_military_support", "player_trade_petition", "player_clergy_petition",
            "promotion", "faction_joined", "faction_left"
    );

    private ImportantNotifications() {}

    public static boolean isImportant(String type) {
        if (type == null || type.isBlank()) return false;
        String t = type.toLowerCase(Locale.ROOT);
        if (IMPORTANT.contains(t)) return true;
        return t.contains("war_") || t.contains("siege") || t.contains("epidemic")
                || t.contains("rebellion") || t.contains("succession") || t.contains("ruler_")
                || t.contains("grand_project") || t.contains("hero_") || t.startsWith("player_");
    }

    public static String label(String type) {
        if (type == null || type.isBlank()) return "Notice";
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "war_declared" -> "War";
            case "war_ended", "peace_treaty" -> "Peace";
            case "epidemic" -> "Epidemic";
            case "rebellion", "rebellion_started" -> "Rebellion";
            case "grand_project_complete" -> "Project complete";
            case "grand_project_started", "player_proposed_project" -> "Project";
            case "trade_partial_loss", "trade_intercepted", "trade_caravan_lost" -> "Trade";
            case "hero_monumented", "hero_recognized" -> "Hero";
            case "ruler_died", "succession", "succession_crisis" -> "Dynasty";
            default -> "Notice";
        };
    }
}
