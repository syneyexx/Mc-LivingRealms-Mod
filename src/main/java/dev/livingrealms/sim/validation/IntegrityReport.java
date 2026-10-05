package dev.livingrealms.sim.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Result of a canonical integrity inspection (and optional safe repair).
 * Fatal issues require human/admin intervention; rebuildable projection indexes may be repaired safely.
 */
public record IntegrityReport(
        List<String> fatalCanonical,
        List<String> rebuildableProjections,
        List<String> repaired,
        boolean repairApplied
) {
    public IntegrityReport {
        fatalCanonical = List.copyOf(Objects.requireNonNull(fatalCanonical, "fatalCanonical"));
        rebuildableProjections = List.copyOf(Objects.requireNonNull(rebuildableProjections, "rebuildableProjections"));
        repaired = List.copyOf(Objects.requireNonNull(repaired, "repaired"));
    }

    public static IntegrityReport empty() {
        return new IntegrityReport(List.of(), List.of(), List.of(), false);
    }

    public boolean healthy() {
        return fatalCanonical.isEmpty() && rebuildableProjections.isEmpty();
    }

    public boolean hasFatal() {
        return !fatalCanonical.isEmpty();
    }

    public boolean hasRebuildable() {
        return !rebuildableProjections.isEmpty();
    }

    public int issueCount() {
        return fatalCanonical.size() + rebuildableProjections.size();
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("integrity fatal=").append(fatalCanonical.size())
                .append(" rebuildable=").append(rebuildableProjections.size())
                .append(" repaired=").append(repaired.size());
        if (repairApplied) sb.append(" (repair applied)");
        return sb.toString();
    }

    public List<String> allMessages() {
        List<String> out = new ArrayList<>(issueCount() + repaired.size());
        for (String s : fatalCanonical) out.add("FATAL " + s);
        for (String s : rebuildableProjections) out.add("REBUILDABLE " + s);
        for (String s : repaired) out.add("REPAIRED " + s);
        return List.copyOf(out);
    }
}
