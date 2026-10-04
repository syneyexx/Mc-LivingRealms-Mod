package dev.livingrealms.sim.construction;

import java.util.Objects;

/**
 * Bounded physical realization receipt for one construction job. Tracks whether required geometry
 * was actually satisfied so SKIPPED/obstructed/omitted ops cannot silently complete a structure.
 */
public final class StructureMaterializationReceipt {
    private final String intentKey;
    private final StructureRole role;
    private final int expectedRequired;
    private final int expectedOptional;
    private final int expectedDoors;
    private final int omittedRequired;
    private int satisfiedRequired;
    private int satisfiedOptional;
    private int satisfiedDoors;
    private int appliedWrites;
    private int obstructedProtected;
    private int deferred;
    private int failed;
    private int safelyIgnored;
    private String obstructionReason = "";
    private boolean finished;

    public StructureMaterializationReceipt(String intentKey, StructureRole role, int expectedRequired, int expectedOptional, int expectedDoors) {
        this(intentKey, role, expectedRequired, expectedOptional, expectedDoors, 0);
    }

    public StructureMaterializationReceipt(String intentKey, StructureRole role, int expectedRequired, int expectedOptional, int expectedDoors, int omittedRequired) {
        this.intentKey = Objects.requireNonNull(intentKey, "intentKey");
        this.role = Objects.requireNonNull(role, "role");
        if (expectedRequired < 0 || expectedOptional < 0 || expectedDoors < 0 || omittedRequired < 0) throw new IllegalArgumentException("counts");
        this.expectedRequired = expectedRequired;
        this.expectedOptional = expectedOptional;
        this.expectedDoors = expectedDoors;
        this.omittedRequired = omittedRequired;
    }

    public static StructureMaterializationReceipt forJob(ConstructionJob job) {
        return forJob(job, job.omittedRequired());
    }

    public static StructureMaterializationReceipt forJob(ConstructionJob job, int omittedRequired) {
        Objects.requireNonNull(job, "job");
        int required = 0, optional = 0, doors = 0;
        for (BuildOperation op : job.operations()) {
            if (op.slot() == PaletteSlot.DOOR) doors++;
            if (StructureGeometryRules.isRequiredGeometry(op.slot(), op.phase())) required++;
            else optional++;
        }
        return new StructureMaterializationReceipt(job.intent().key(), job.intent().role(), required, optional, doors, Math.max(0, omittedRequired));
    }

    public void record(BuildOperation operation, BuildApplyResult result) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(result, "result");
        if (result.countsAsAppliedWrite()) appliedWrites++;
        if (result.isDeferral()) deferred++;
        if (result == BuildApplyResult.FAILED || result == BuildApplyResult.TERMINALLY_IMPOSSIBLE) failed++;
        if (result == BuildApplyResult.SAFELY_IGNORED) safelyIgnored++;
        if (result == BuildApplyResult.OBSTRUCTED_PROTECTED) {
            obstructedProtected++;
            if (obstructionReason.isEmpty()) obstructionReason = "protected_or_foreign";
        }
        boolean required = StructureGeometryRules.isRequiredGeometry(operation.slot(), operation.phase());
        if (result.satisfiesRequired()) {
            if (required) satisfiedRequired++;
            else satisfiedOptional++;
            if (operation.slot() == PaletteSlot.DOOR) satisfiedDoors++;
        } else if (result == BuildApplyResult.SAFELY_IGNORED && !required) {
            satisfiedOptional++;
        }
    }

    public void markFinished() { finished = true; }

    public boolean physicallyAcceptable() {
        if (!finished) return false;
        int effectiveRequired = expectedRequired + omittedRequired;
        if (effectiveRequired == 0) return appliedWrites > 0 || satisfiedOptional > 0 || safelyIgnored > 0;
        double ratio = satisfiedRequired / (double) effectiveRequired;
        if (ratio + 1e-9 < StructureGeometryRules.requiredCompletionThreshold(role)) return false;
        if (StructureGeometryRules.requiresAllDoors(role) && expectedDoors > 0 && satisfiedDoors < expectedDoors) return false;
        return true;
    }

    public double completionRatio() {
        int effectiveRequired = expectedRequired + omittedRequired;
        if (effectiveRequired == 0) return 1.0D;
        return satisfiedRequired / (double) effectiveRequired;
    }

    public String intentKey() { return intentKey; }
    public StructureRole role() { return role; }
    public int expectedRequired() { return expectedRequired; }
    public int expectedOptional() { return expectedOptional; }
    public int expectedDoors() { return expectedDoors; }
    public int omittedRequired() { return omittedRequired; }
    public int satisfiedRequired() { return satisfiedRequired; }
    public int satisfiedOptional() { return satisfiedOptional; }
    public int satisfiedDoors() { return satisfiedDoors; }
    public int appliedWrites() { return appliedWrites; }
    public int obstructedProtected() { return obstructedProtected; }
    public int deferred() { return deferred; }
    public int failed() { return failed; }
    public int safelyIgnored() { return safelyIgnored; }
    public String obstructionReason() { return obstructionReason; }
    public boolean finished() { return finished; }

    @Override public String toString() {
        return "StructureMaterializationReceipt{key="+intentKey+", role="+role+", required="+satisfiedRequired+"/"+(expectedRequired+omittedRequired)
                +", omitted="+omittedRequired+", doors="+satisfiedDoors+"/"+expectedDoors+", applied="+appliedWrites+", obstructed="+obstructedProtected
                +", acceptable="+physicallyAcceptable()+"}";
    }
}
