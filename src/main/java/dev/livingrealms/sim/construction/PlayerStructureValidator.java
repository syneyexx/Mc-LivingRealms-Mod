package dev.livingrealms.sim.construction;

import java.util.Objects;

/**
 * Headless validation rules for player-built structure surveys. Minecraft-side scanning produces
 * {@link SurveyMetrics}; this class decides pass/fail without world access.
 */
public final class PlayerStructureValidator {
    public static final int MAX_HORIZONTAL = 48;
    public static final int MAX_HEIGHT = 32;
    public static final int MAX_INTERIOR_CELLS = 8192;
    public static final int MAX_INSPECTED_BLOCKS = 24_000;
    public static final double MIN_ROOF_COVERAGE = 0.62;
    public static final double MIN_FLOOR_COVERAGE = 0.55;
    public static final int MIN_INTERIOR_VOLUME = 27;
    public static final int MIN_BEDS_FOR_HOUSE = 1;

    private PlayerStructureValidator() {}

    public record SurveyMetrics(
            int width,
            int depth,
            int height,
            int interiorCells,
            int inspectedBlocks,
            double roofCoverage,
            double floorCoverage,
            int bedCount,
            boolean enclosed,
            boolean validEntrance,
            boolean overlapsProtected,
            boolean forceLoaded
    ) {
        public SurveyMetrics {
            width = Math.max(0, width);
            depth = Math.max(0, depth);
            height = Math.max(0, height);
            interiorCells = Math.max(0, interiorCells);
            inspectedBlocks = Math.max(0, inspectedBlocks);
            roofCoverage = clamp01(roofCoverage);
            floorCoverage = clamp01(floorCoverage);
            bedCount = Math.max(0, bedCount);
        }

        private static double clamp01(double v) {
            if (!Double.isFinite(v)) return 0;
            return Math.max(0, Math.min(1, v));
        }
    }

    public record ValidationResult(boolean ok, String reason, int capacity) {
        public static ValidationResult fail(String reason) { return new ValidationResult(false, reason, 0); }
        public static ValidationResult pass(int capacity) { return new ValidationResult(true, "ok", capacity); }
    }

    public static ValidationResult validate(RegisteredPlayerStructure.Role role, SurveyMetrics m) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(m, "metrics");
        if (m.forceLoaded()) return ValidationResult.fail("Cannot register: survey must not force-load chunks.");
        if (m.overlapsProtected()) {
            return ValidationResult.fail("Cannot register: structure overlaps a protected Living Realms or foreign footprint.");
        }
        if (m.width() <= 0 || m.depth() <= 0 || m.height() <= 0) {
            return ValidationResult.fail("Cannot register: no enclosed structure found near the entrance.");
        }
        if (m.width() > MAX_HORIZONTAL || m.depth() > MAX_HORIZONTAL) {
            return ValidationResult.fail("Cannot register: footprint exceeds " + MAX_HORIZONTAL + "×" + MAX_HORIZONTAL + " blocks.");
        }
        if (m.height() > MAX_HEIGHT) {
            return ValidationResult.fail("Cannot register: height exceeds " + MAX_HEIGHT + " blocks.");
        }
        if (m.inspectedBlocks() > MAX_INSPECTED_BLOCKS || m.interiorCells() > MAX_INTERIOR_CELLS) {
            return ValidationResult.fail("Cannot register: structure is too large for the bounded survey.");
        }
        if (!m.enclosed()) {
            return ValidationResult.fail("Cannot register: open shell — walls do not enclose an interior.");
        }
        if (!m.validEntrance()) {
            return ValidationResult.fail("Cannot register: no valid walkable entrance at the survey point.");
        }
        if (m.floorCoverage() < MIN_FLOOR_COVERAGE) {
            return ValidationResult.fail("Cannot register: floor covers only "
                    + Math.round(m.floorCoverage() * 100) + "% of the interior (need ≥"
                    + Math.round(MIN_FLOOR_COVERAGE * 100) + "%).");
        }
        if (m.roofCoverage() < MIN_ROOF_COVERAGE) {
            return ValidationResult.fail("Cannot register: no valid roof was found over "
                    + Math.round((1.0 - m.roofCoverage()) * 100) + "% of the interior.");
        }
        if (m.interiorCells() < MIN_INTERIOR_VOLUME) {
            return ValidationResult.fail("Cannot register: interior volume is too small ("
                    + m.interiorCells() + " cells; need ≥" + MIN_INTERIOR_VOLUME + ").");
        }
        if (role == RegisteredPlayerStructure.Role.HOUSE) {
            if (m.bedCount() < MIN_BEDS_FOR_HOUSE) {
                return ValidationResult.fail("Cannot register this house: no bed was found inside.");
            }
            int capacity = Math.max(2, Math.min(48, m.bedCount() * 2 + m.interiorCells() / 18));
            return ValidationResult.pass(capacity);
        }
        // Civic / workplace roles: capacity is functional credit, not housing beds.
        int capacity = switch (role) {
            case TOWN_HALL -> 0;
            case WAREHOUSE, WORKSHOP, MARKET, TAVERN, TEMPLE, BARRACKS, CLINIC, SCHOOL ->
                    Math.max(1, Math.min(24, m.interiorCells() / 40));
            case FARM, PASTURE -> Math.max(1, Math.min(12, m.width() * m.depth() / 64));
            default -> 0;
        };
        return ValidationResult.pass(capacity);
    }
}
