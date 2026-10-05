package dev.livingrealms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A4: current-status docs must pin exactly the live constants — not historical schema/protocol/revision
 * or an obsolete settlement density target.
 */
public final class DocumentationPinTest {
    private static final List<String> STATUS_DOCS = List.of(
            "PROJECT_STATE.md",
            "COMPLETION_MATRIX.md",
            "IMPLEMENTATION_LEDGER.md",
            "docs/DETAIL_MATRIX.md",
            "docs/ROADMAP.md",
            "docs/RELEASE_GATES.md",
            "docs/PRODUCTION_READINESS.md",
            "docs/AUTONOMOUS_COMPLETION_CHECKPOINT.md",
            "docs/WAVE0_SYSTEM_INVENTORY.md",
            "README.md"
    );

    private DocumentationPinTest() {}

    public static void main(String[] args) throws Exception {
        int schema = readIntConst("src/main/java/dev/livingrealms/sim/persistence/SimulationStateCodec.java", "SCHEMA_VERSION");
        int minSchema = readIntConst("src/main/java/dev/livingrealms/sim/persistence/SimulationStateCodec.java", "MIN_SUPPORTED_SCHEMA");
        int protocol = readIntConst("src/main/java/dev/livingrealms/sim/ui/RealmDashboardSnapshot.java", "PROTOCOL_VERSION");
        String network = readStringConst("src/main/java/dev/livingrealms/minecraft/network/LivingRealmsNetwork.java", "NETWORK_VERSION");
        int contentRevision = readIntConst("src/main/java/dev/livingrealms/minecraft/LivingRealmsSavedData.java", "CONTENT_REVISION");
        int surface = readIntConst("src/main/java/dev/livingrealms/sim/world/SettlementDensitySeeder.java", "SURFACE_STARTER_SETTLEMENTS");
        int targetPerRealm = readIntConst("src/main/java/dev/livingrealms/sim/world/SettlementDensitySeeder.java", "TARGET_SETTLEMENTS_PER_REALM");
        int spacing = (int) Double.parseDouble(readNumberConst(
                "src/main/java/dev/livingrealms/sim/world/SettlementDensitySeeder.java", "MIN_SETTLEMENT_SPACING"));

        check(schema == 18, "expected SCHEMA_VERSION 18, got " + schema);
        check(minSchema == 1, "expected MIN_SUPPORTED_SCHEMA 1, got " + minSchema);
        check(protocol == 18, "expected PROTOCOL_VERSION 18, got " + protocol);
        check("14".equals(network), "expected NETWORK_VERSION 14, got " + network);
        check(contentRevision == 14, "expected CONTENT_REVISION 14, got " + contentRevision);
        check(targetPerRealm == 13, "expected TARGET_SETTLEMENTS_PER_REALM 13, got " + targetPerRealm);
        check(surface == 156, "expected SURFACE_STARTER_SETTLEMENTS 156, got " + surface);
        check(spacing == 800, "expected MIN_SETTLEMENT_SPACING 800, got " + spacing);

        String pinNeedle = "CURRENT PINS: schema " + schema
                + " / minSchema " + minSchema
                + " / protocol " + protocol
                + " / network " + network
                + " / contentRevision " + contentRevision
                + " / surfaceSettlements " + surface
                + " / perRealm " + targetPerRealm
                + " / spacing " + spacing;

        for (String doc : STATUS_DOCS) {
            Path path = Path.of(doc);
            check(Files.exists(path), "missing status doc " + doc);
            String text = Files.readString(path);
            // Only the current-status section above any "## Geschiedenis" / "## History" heading is pinned.
            String current = text;
            int hist = indexOfIgnoreCase(text, "\n## Geschiedenis");
            if (hist < 0) hist = indexOfIgnoreCase(text, "\n## History");
            if (hist >= 0) current = text.substring(0, hist);
            check(current.contains(pinNeedle),
                    doc + " current status must contain exact pin line: " + pinNeedle);
            // Forbid obsolete current pins outside history.
            forbid(current, doc, "schema 16", Pattern.compile("(?i)\\bschema\\s*16\\b"));
            forbid(current, doc, "schema 17", Pattern.compile("(?i)\\bschema\\s*17\\b"));
            forbid(current, doc, "protocol 17", Pattern.compile("(?i)\\bprotocol\\s*17\\b"));
            forbid(current, doc, "ContentRevision 11", Pattern.compile("(?i)content\\s*revision\\s*=?\\s*11\\b"));
            forbid(current, doc, "380+", Pattern.compile("380\\+"));
            forbid(current, doc, "32 per realm", Pattern.compile("(?i)32\\s+(settlements\\s+)?per\\s+(surface\\s+)?realm"));
            forbid(current, doc, "~35% countryside", Pattern.compile("~35%\\s+countryside"));
            forbid(current, doc, "2000-block spacing as current", Pattern.compile("(?i)2000[- ]block"));
            forbid(current, doc, "EXTERNAL GATE as current status", Pattern.compile("(?i)EXTERNAL\\s+GATE"));
            forbid(current, doc, "PARTIAL as current matrix claim without truth", Pattern.compile("(?i)\\bPARTIAL\\b"));
            forbid(current, doc, "COMPLETE (core)", Pattern.compile("COMPLETE \\(core\\)"));
        }

        System.out.println("PASS documentation pins: " + pinNeedle);
    }

    private static void forbid(String text, String doc, String label, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            throw new AssertionError(doc + " current status still mentions obsolete " + label + " near: "
                    + text.substring(Math.max(0, m.start() - 40), Math.min(text.length(), m.end() + 40)).replace('\n', ' '));
        }
    }

    private static int indexOfIgnoreCase(String hay, String needle) {
        return hay.toLowerCase().indexOf(needle.toLowerCase());
    }

    private static int readIntConst(String file, String name) throws Exception {
        return Integer.parseInt(readNumberConst(file, name));
    }

    private static String readNumberConst(String file, String name) throws Exception {
        String text = Files.readString(Path.of(file));
        Matcher m = Pattern.compile("\\b" + Pattern.quote(name) + "\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)").matcher(text);
        check(m.find(), "missing " + name + " in " + file);
        return m.group(1);
    }

    private static String readStringConst(String file, String name) throws Exception {
        String text = Files.readString(Path.of(file));
        Matcher m = Pattern.compile("\\b" + Pattern.quote(name) + "\\s*=\\s*\"([^\"]+)\"").matcher(text);
        check(m.find(), "missing string " + name + " in " + file);
        return m.group(1);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
