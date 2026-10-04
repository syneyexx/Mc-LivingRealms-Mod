package dev.livingrealms.sim.construction;

/**
 * Result of attempting one physical block operation.
 *
 * <p>Cursor advancement and required-geometry satisfaction are intentionally separate:
 * an obstructed player wall must not stall the queue forever, but also must not count as a
 * successfully realized required block.
 */
public enum BuildApplyResult {
    /** Block was written by Living Realms. */
    APPLIED,
    /** Target already present; required geometry is satisfied. */
    ALREADY_CORRECT,
    /** Optional decoration/clearing skipped without harming structure integrity. */
    SAFELY_IGNORED,
    /** Chunk unloaded; do not advance — retry later. */
    DEFERRED_UNLOADED,
    /** Player/foreign/protected/block-entity obstruction; advance but required ops stay unsatisfied. */
    OBSTRUCTED_PROTECTED,
    /** setBlock failed or equivalent hard failure on this attempt; advance, unsatisfied. */
    FAILED,
    /** Temporary world condition; do not advance. */
    RETRYABLE,
    /** Permanently impossible placement; advance, unsatisfied. */
    TERMINALLY_IMPOSSIBLE,
    /**
     * Legacy alias retained for older call sites/tests. Treated as {@link #ALREADY_CORRECT}.
     * New code should return the precise result instead.
     */
    SKIPPED,
    /**
     * Legacy alias retained for older call sites/tests. Treated as {@link #DEFERRED_UNLOADED}.
     * New code should return {@link #DEFERRED_UNLOADED} or {@link #RETRYABLE}.
     */
    BLOCKED;

    public boolean advancesCursor() {
        return switch (this) {
            case APPLIED, ALREADY_CORRECT, SAFELY_IGNORED, OBSTRUCTED_PROTECTED, FAILED, TERMINALLY_IMPOSSIBLE, SKIPPED -> true;
            case DEFERRED_UNLOADED, RETRYABLE, BLOCKED -> false;
        };
    }

    /** Whether a required geometry op counts as physically satisfied. */
    public boolean satisfiesRequired() {
        return switch (this) {
            case APPLIED, ALREADY_CORRECT, SKIPPED -> true;
            default -> false;
        };
    }

    public boolean countsAsAppliedWrite() {
        return this == APPLIED;
    }

    public boolean isDeferral() {
        return this == DEFERRED_UNLOADED || this == RETRYABLE || this == BLOCKED;
    }
}
