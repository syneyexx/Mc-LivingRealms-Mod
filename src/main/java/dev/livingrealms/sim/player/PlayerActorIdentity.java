package dev.livingrealms.sim.player;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Single canonical player actor-key authority.
 * Format: {@code player:<UUID>} — stable across rename, logout, and save/reload.
 * Display names must never be used for identity.
 */
public final class PlayerActorIdentity {
    public static final String PREFIX = "player:";

    private PlayerActorIdentity() {}

    /** Canonical actor key from a UUID. */
    public static String of(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return PREFIX + uuid;
    }

    /** Canonical actor key from a UUID string (must be a valid UUID). */
    public static String ofUuidString(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            throw new IllegalArgumentException("uuid required");
        }
        return of(UUID.fromString(uuid.strip()));
    }

    /**
     * Normalize a stored actor key. Accepts only {@code player:<uuid>} form.
     * Rejects display-name based keys such as {@code player:Steve}.
     */
    public static String requireCanonical(String actorKey) {
        if (!isCanonical(actorKey)) {
            throw new IllegalArgumentException("non-canonical player actor key: " + actorKey);
        }
        return actorKey.strip();
    }

    public static boolean isPlayerKey(String actorKey) {
        return actorKey != null && actorKey.regionMatches(true, 0, PREFIX, 0, PREFIX.length());
    }

    /** True only when the key is {@code player:} followed by a parseable UUID. */
    public static boolean isCanonical(String actorKey) {
        if (!isPlayerKey(actorKey)) return false;
        String rest = actorKey.substring(PREFIX.length()).strip();
        if (rest.isEmpty()) return false;
        try {
            UUID.fromString(rest);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public static UUID uuidOrNull(String actorKey) {
        if (!isCanonical(actorKey)) return null;
        return UUID.fromString(actorKey.substring(PREFIX.length()).strip());
    }

    /** Lower-case form for deterministic comparisons (UUIDs are already canonical). */
    public static String normalize(String actorKey) {
        if (actorKey == null) return "";
        return actorKey.strip().toLowerCase(Locale.ROOT);
    }
}
