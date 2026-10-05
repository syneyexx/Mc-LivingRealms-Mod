package dev.livingrealms.sim.dialogue;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reusable synonym / intent pattern groups for the no-LLM language path. */
public final class DialogueLexicalGroups {
    private DialogueLexicalGroups() {}
    public static final Set<String> GREETINGS = Set.of("hi", "hello", "hey", "halo", "hoi", "goedendag", "greetings");
    public static final Set<String> GOODBYES = Set.of("bye", "goodbye", "farewell", "dag", "tot ziens", "vaarwel");
    public static final List<String> SOURCE_ASK = List.of("wie vertelde", "van wie hoorde", "wat is je bron", "who told", "where did you hear", "source");
    public static final List<String> DIRECTION_FOLLOWUP = List.of("waar gingen ze heen", "where did they go", "waar gingen ze", "waar zijn ze", "waarheen gingen", "where did they", "where are they", "which way did they");
    public static final List<String> WHERE_PREFIXES = List.of("waar is ", "waar ligt ", "waar vind ik ", "where is ", "where can i find ");

    public static String resourceSubject(String normalized) {
        if (containsAny(normalized, "brood", "bread")) return "bread";
        if (containsAny(normalized, "graan", "grain", "wheat", "meel", "flour")) return "grain";
        if (containsAny(normalized, "vlees", "meat", "beef", "pork")) return "meat";
        if (containsAny(normalized, "bier", "ale", "beer")) return "ale";
        if (containsAny(normalized, "wol", "wool")) return "wool";
        if (containsAny(normalized, "eten", "voedsel", "food")) return "food";
        if (containsAny(normalized, "hout", "wood", "timber")) return "wood";
        if (containsAny(normalized, "steen", "stone")) return "stone";
        if (containsAny(normalized, "ijzer", "iron")) return "iron";
        if (containsAny(normalized, "kolen", "coal")) return "coal";
        if (containsAny(normalized, "koper", "copper")) return "copper";
        if (containsAny(normalized, "goud", "gold")) return "gold";
        if (containsAny(normalized, "brandstof", "fuel")) return "fuel";
        if (containsAny(normalized, "munitie", "ammunition", "ammo")) return "ammunition";
        if (containsAny(normalized, "gereedschap", "tools")) return "tools";
        if (containsAny(normalized, "machines", "machinery")) return "machinery";
        if (containsAny(normalized, "textiel", "textiles", "cloth")) return "textiles";
        return "";
    }
    public static boolean containsAny(String haystack, String... needles) {
        if (haystack == null || haystack.isBlank()) return false;
        String s = haystack.toLowerCase(Locale.ROOT);
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }
    public static boolean containsAny(String haystack, List<String> needles) {
        if (haystack == null || haystack.isBlank() || needles == null) return false;
        String s = haystack.toLowerCase(Locale.ROOT);
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }
    public static boolean tokenMatch(Set<String> tokens, Set<String> group) {
        if (tokens == null || group == null) return false;
        for (String t : tokens) if (group.contains(t)) return true;
        return false;
    }
}
