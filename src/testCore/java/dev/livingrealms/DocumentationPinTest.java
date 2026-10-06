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
        int authoredSatellites = readIntConst(
                "src/main/java/dev/livingrealms/sim/world/SettlementDensitySeeder.java", "AUTHORED_SATELLITES_PER_REALM");
        int minRuralHamlets = readIntConst(
                "src/main/java/dev/livingrealms/sim/world/SettlementDensitySeeder.java", "MIN_RURAL_HAMLETS_PER_REALM");
        int maxRuralHamlets = readIntConst(
                "src/main/java/dev/livingrealms/sim/world/SettlementDensitySeeder.java", "MAX_RURAL_HAMLETS_PER_REALM");
        int minPerRealm = 1 + authoredSatellites + minRuralHamlets;
        int maxPerRealm = 1 + authoredSatellites + maxRuralHamlets;
        int minSurface = 12 * minPerRealm;
        int maxSurface = 12 * maxPerRealm;
        double[] capitalSpacing = readRangeConst(
                "src/main/java/dev/livingrealms/sim/world/SettlementSpacingPolicy.java", "CAPITAL_TO_CAPITAL");

        check(schema == 21, "expected SCHEMA_VERSION 21, got " + schema);
        check(minSchema == 1, "expected MIN_SUPPORTED_SCHEMA 1, got " + minSchema);
        check(protocol == 20, "expected PROTOCOL_VERSION 20, got " + protocol);
        check("16".equals(network), "expected NETWORK_VERSION 16, got " + network);
        check(contentRevision == 16, "expected CONTENT_REVISION 16, got " + contentRevision);
        check(authoredSatellites == 10, "expected 10 authored satellites/realm, got " + authoredSatellites);
        check(minRuralHamlets == 6 && maxRuralHamlets == 14,
                "expected 6-14 rural hamlets/realm, got " + minRuralHamlets + "-" + maxRuralHamlets);
        check(minPerRealm == 17 && maxPerRealm == 25,
                "expected 17-25 starter settlements/realm, got " + minPerRealm + "-" + maxPerRealm);
        check(minSurface == 204 && maxSurface == 300,
                "expected 204-300 surface starter settlements, got " + minSurface + "-" + maxSurface);
        check(capitalSpacing[0] == 3000 && capitalSpacing[1] == 4500,
                "expected capital spacing 3000-4500, got " + capitalSpacing[0] + "-" + capitalSpacing[1]);

        String pinNeedle = "CURRENT PINS: schema " + schema
                + " / minSchema " + minSchema
                + " / protocol " + protocol
                + " / network " + network
                + " / contentRevision " + contentRevision
                + " / starterSettlements " + minSurface + "-" + maxSurface
                + " / perRealm " + minPerRealm + "-" + maxPerRealm
                + " / capitalSpacing " + (int) capitalSpacing[0] + "-" + (int) capitalSpacing[1]
                + " / roleAwareSpacing";

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
            // Forbid obsolete *current* pins. Historical mentions belong under ## History / ## Geschiedenis.
            forbid(current, doc, "schema 16 as current", Pattern.compile("(?i)(CURRENT PINS:.*\\bschema\\s*16\\b|\\bsave schema\\s*\\*?\\*?16\\*?\\*?\\b(?!\\s+adds))"));
            forbid(current, doc, "schema 17 as current", Pattern.compile("(?i)(CURRENT PINS:.*\\bschema\\s*17\\b|\\bsave schema\\s*\\*?\\*?17\\*?\\*?\\b(?!\\s+adds))"));
            forbid(current, doc, "schema 18 as current", Pattern.compile("(?i)(CURRENT PINS:.*\\bschema\\s*18\\b|\\bsave schema\\s*\\*?\\*?18\\*?\\*?\\b(?!\\s+adds)|\\bschema\\s*18\\s*/\\s*minSchema)"));
            forbid(current, doc, "schema 19 as current", Pattern.compile("(?i)(CURRENT PINS:.*\\bschema\\s*19\\b|\\bsave schema\\s*\\*?\\*?19\\*?\\*?\\b(?!\\s+adds)|\\bschema\\s*19\\s*/\\s*minSchema)"));
            forbid(current, doc, "protocol 17 as current", Pattern.compile("(?i)(CURRENT PINS:.*\\bprotocol\\s*17\\b|\\bdashboard protocol\\s*\\*?\\*?17\\*?\\*?)"));
            forbid(current, doc, "protocol 18 as current", Pattern.compile("(?i)(CURRENT PINS:.*\\bprotocol\\s*18\\b|\\bdashboard protocol\\s*\\*?\\*?18\\*?\\*?)"));
            forbid(current, doc, "protocol 19 as current", Pattern.compile("(?i)(CURRENT PINS:.*\\bprotocol\\s*19\\b|\\bdashboard protocol\\s*\\*?\\*?19\\*?\\*?)"));
            forbid(current, doc, "ContentRevision 11", Pattern.compile("(?i)content\\s*revision\\s*=?\\s*11\\b"));
            forbid(current, doc, "380+", Pattern.compile("380\\+"));
            forbid(current, doc, "32 per realm", Pattern.compile("(?i)32\\s+(settlements\\s+)?per\\s+(surface\\s+)?realm"));
            forbid(current, doc, "~35% countryside", Pattern.compile("~35%\\s+countryside"));
            forbid(current, doc, "800-block spacing as current", Pattern.compile("(?i)800[- ]block"));
            forbid(current, doc, "156 surface as current", Pattern.compile("(?i)\\b156\\b.*settlement|surfaceSettlements 156|surface starter \\*\\*156\\*\\*"));
            forbid(current, doc, "36 surface as current", Pattern.compile("(?i)(surfaceSettlements\\s+36|surface starter \\*\\*36\\*\\*|\\b36\\b.*surface.*settlement)"));
            forbid(current, doc, "3 per realm as current", Pattern.compile("(?i)(perRealm\\s+3\\b|3\\s+(settlements\\s+)?per\\s+realm|12\\s*[×x]\\s*3)"));
            forbid(current, doc, "global 2000 spacing as current", Pattern.compile("(?i)(/ spacing 2000|minimum settlement clearance is \\*\\*2000|spacing \\*\\*2000\\*\\*)"));
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

    private static double[] readRangeConst(String file, String name) throws Exception {
        String text = Files.readString(Path.of(file));
        Matcher m = Pattern.compile("\\b" + Pattern.quote(name)
                + "\\s*=\\s*new\\s+Range\\(\\s*([0-9]+(?:\\.[0-9]+)?)\\s*,\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\)")
                .matcher(text);
        check(m.find(), "missing range " + name + " in " + file);
        return new double[]{Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2))};
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
