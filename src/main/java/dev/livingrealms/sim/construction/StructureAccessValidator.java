package dev.livingrealms.sim.construction;

import java.util.Locale;
import java.util.Objects;

/**
 * Validates that a structure entrance has a walkable connection from door → landing → path/sidewalk
 * → road graph. Used as a completion gate for houses and ordinary civic buildings near players.
 */
public final class StructureAccessValidator {
    public enum AccessKind {
        FLAT,
        STAIRS,
        SWITCHBACK,
        SHORT_BRIDGE,
        GATE_PASSAGE,
        CLIFF,
        SEALED_WALL,
        DEEP_WATER,
        BLOCKED_STRUCTURE,
        FLOATING_FLOOR,
        VERTICAL_JUMP
    }

    public record AccessSample(
            AccessKind kind,
            int doorFloorY,
            int approachY,
            boolean roadConnected,
            boolean dockIntended,
            boolean throughGate
    ) {
        public AccessSample {
            kind = Objects.requireNonNull(kind, "kind");
        }
    }

    public record Verdict(boolean pass, String reason, AccessKind kind) {
        public Verdict {
            reason = reason == null ? "" : reason;
            kind = Objects.requireNonNull(kind, "kind");
        }

        public static Verdict pass(AccessKind kind, String reason) {
            return new Verdict(true, reason, kind);
        }

        public static Verdict fail(AccessKind kind, String reason) {
            return new Verdict(false, reason, kind);
        }
    }

    private StructureAccessValidator() {}

    public static Verdict validate(AccessSample sample) {
        Objects.requireNonNull(sample, "sample");
        return switch (sample.kind()) {
            case CLIFF -> Verdict.fail(AccessKind.CLIFF, "door opens into a cliff");
            case SEALED_WALL -> Verdict.fail(AccessKind.SEALED_WALL, "entrance sealed by curtain wall");
            case DEEP_WATER -> sample.dockIntended()
                    ? Verdict.pass(AccessKind.DEEP_WATER, "dock access intended")
                    : Verdict.fail(AccessKind.DEEP_WATER, "deep water without dock");
            case BLOCKED_STRUCTURE -> Verdict.fail(AccessKind.BLOCKED_STRUCTURE, "blocked by player/foreign structure");
            case FLOATING_FLOOR -> Verdict.fail(AccessKind.FLOATING_FLOOR, "one-block floating floor");
            case VERTICAL_JUMP -> Verdict.fail(AccessKind.VERTICAL_JUMP, "two-block vertical jump required");
            case FLAT -> gradeOk(sample, AccessKind.FLAT, 1);
            case STAIRS -> gradeOk(sample, AccessKind.STAIRS, 3);
            case SWITCHBACK -> gradeOk(sample, AccessKind.SWITCHBACK, 8);
            case SHORT_BRIDGE -> sample.roadConnected()
                    ? Verdict.pass(AccessKind.SHORT_BRIDGE, "short bridge to road")
                    : Verdict.fail(AccessKind.SHORT_BRIDGE, "bridge does not reach road graph");
            case GATE_PASSAGE -> sample.throughGate() && sample.roadConnected()
                    ? Verdict.pass(AccessKind.GATE_PASSAGE, "gate passage to road")
                    : Verdict.fail(AccessKind.GATE_PASSAGE, "gate passage blocked");
        };
    }

    /** Convenience for EntranceAccessPlanner-backed grade checks. */
    public static Verdict validateGrade(int doorFloorY, int approachY, boolean roadConnected) {
        int delta = Math.abs(doorFloorY - approachY);
        if (delta <= 1) {
            return validate(new AccessSample(AccessKind.FLAT, doorFloorY, approachY, roadConnected, false, false));
        }
        if (delta <= 3) {
            return validate(new AccessSample(AccessKind.STAIRS, doorFloorY, approachY, roadConnected, false, false));
        }
        if (delta <= 8) {
            return validate(new AccessSample(AccessKind.SWITCHBACK, doorFloorY, approachY, roadConnected, false, false));
        }
        return Verdict.fail(AccessKind.CLIFF, "grade beyond switchback repair");
    }

    public static boolean accepts(AccessSample sample) {
        return validate(sample).pass();
    }

    private static Verdict gradeOk(AccessSample sample, AccessKind kind, int maxDelta) {
        int delta = Math.abs(sample.doorFloorY() - sample.approachY());
        if (delta > maxDelta) {
            return Verdict.fail(kind, "grade delta " + delta + " exceeds " + kind.name().toLowerCase(Locale.ROOT) + " limit");
        }
        if (!sample.roadConnected()) {
            return Verdict.fail(kind, "no walkable connection to road graph");
        }
        // Extreme sites that EntranceAccessPlanner cannot repair.
        if (EntranceAccessPlanner.isExtremeSite(sample.doorFloorY(), sample.approachY())) {
            return Verdict.fail(AccessKind.CLIFF, "extreme site");
        }
        return Verdict.pass(kind, kind.name().toLowerCase(Locale.ROOT) + " entrance reachable");
    }
}
