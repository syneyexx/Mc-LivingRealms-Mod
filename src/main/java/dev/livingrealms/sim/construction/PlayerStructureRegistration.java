package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.player.FactionRank;
import dev.livingrealms.sim.player.PlayerStanding;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.WorldEvent;
import java.util.Objects;

/** Canonical registration of a validated player-built structure. Recognition only — no block mutation. */
public final class PlayerStructureRegistration {
    private PlayerStructureRegistration() {}

    public record Result(boolean success, String reason, long structureId, int capacity) {
        public static Result fail(String reason) { return new Result(false, reason, 0, 0); }
        public static Result ok(long id, int capacity) { return new Result(true, "registered", id, capacity); }
    }

    public static Result register(SimulationState state, String actorKey, long settlementId,
                                  RegisteredPlayerStructure.Role role,
                                  int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                  int doorX, int doorY, int doorZ,
                                  PlayerStructureValidator.SurveyMetrics metrics,
                                  long fingerprint) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(metrics, "metrics");
        if (actorKey == null || actorKey.isBlank()) return Result.fail("invalid_actor");
        Settlement settlement = state.findSettlement(settlementId).orElse(null);
        if (settlement == null) return Result.fail("settlement_missing");
        Faction owner = state.factions().stream()
                .filter(f -> f.settlements().stream().anyMatch(s -> s.id() == settlementId))
                .findFirst().orElse(null);
        if (owner == null) return Result.fail("settlement_unowned");
        PlayerStanding standing = state.findPlayerStanding(actorKey).orElse(null);
        if (standing == null || !standing.isMember() || standing.memberFactionId() != owner.id()) {
            return Result.fail("not_settlement_member");
        }
        if (standing.rank() != FactionRank.RULER && standing.rank() != FactionRank.NOBLE
                && standing.rank() != FactionRank.CITIZEN) {
            return Result.fail("rank_too_low");
        }
        // Nearness: door must be within a generous settlement radius (no chunk load required).
        double dx = doorX - settlement.position().x();
        double dz = doorZ - settlement.position().z();
        if (Math.hypot(dx, dz) > 420) {
            return Result.fail("Cannot register: entrance is too far from " + settlement.name() + ".");
        }
        for (RegisteredPlayerStructure existing : state.registeredPlayerStructures()) {
            if (!existing.valid() || existing.settlementId() != settlementId) continue;
            if (boxesOverlap(existing, minX, minY, minZ, maxX, maxY, maxZ)) {
                return Result.fail("Cannot register: overlaps another registered building.");
            }
        }
        var validation = PlayerStructureValidator.validate(role, metrics);
        if (!validation.ok()) return Result.fail(validation.reason());

        RegisteredPlayerStructure structure = new RegisteredPlayerStructure(
                state.nextId(), settlementId, actorKey, role,
                minX, minY, minZ, maxX, maxY, maxZ,
                doorX, doorY, doorZ, validation.capacity(), state.clock().day(), fingerprint);
        state.addRegisteredPlayerStructure(structure);
        if (!settlement.physicallyAnchored()) settlement.markPhysicallyAnchored();
        if (settlement.origin() == SettlementOrigin.AUTHORED_SEED) {
            // Player registration anchors the town permanently.
        }
        if (role == RegisteredPlayerStructure.Role.HOUSE) {
            int verified = HousingCapacity.calculate(settlement, state);
            if (verified > settlement.housing()) settlement.setHousing(verified);
        }
        state.history().add(new WorldEvent(state.clock().day(), "player_structure_registered",
                "actor=" + actorKey + ", settlement=" + settlementId + ", role=" + role
                        + ", capacity=" + validation.capacity()));
        return Result.ok(structure.id(), validation.capacity());
    }

    public static Result setDevelopmentMode(SimulationState state, String actorKey, long settlementId,
                                            String modeName) {
        Objects.requireNonNull(state, "state");
        if (actorKey == null || actorKey.isBlank()) return Result.fail("invalid_actor");
        Settlement settlement = state.findSettlement(settlementId).orElse(null);
        if (settlement == null) return Result.fail("settlement_missing");
        Faction owner = state.factions().stream()
                .filter(f -> f.settlements().stream().anyMatch(s -> s.id() == settlementId))
                .findFirst().orElse(null);
        if (owner == null) return Result.fail("settlement_unowned");
        PlayerStanding standing = state.findPlayerStanding(actorKey).orElse(null);
        if (standing == null || !standing.isMember() || standing.memberFactionId() != owner.id()) {
            return Result.fail("not_settlement_member");
        }
        if (standing.rank() != FactionRank.RULER && standing.rank() != FactionRank.NOBLE) {
            return Result.fail("rank_too_low");
        }
        dev.livingrealms.sim.faction.DevelopmentMode mode;
        try {
            mode = dev.livingrealms.sim.faction.DevelopmentMode.valueOf(
                    modeName == null ? "" : modeName.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Result.fail("Unknown development mode. Use AUTO, HYBRID, or PLAYER_LED.");
        }
        if (settlement.developmentMode() == mode) return Result.fail("mode_unchanged");
        settlement.setDevelopmentMode(mode);
        state.history().add(new WorldEvent(state.clock().day(), "development_mode_changed",
                "settlement=" + settlementId + ", mode=" + mode + ", actor=" + actorKey));
        return new Result(true, "mode_" + mode.name().toLowerCase(java.util.Locale.ROOT), settlementId, 0);
    }

    private static boolean boxesOverlap(RegisteredPlayerStructure a,
                                        int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return a.minX() <= maxX && a.maxX() >= minX
                && a.minY() <= maxY && a.maxY() >= minY
                && a.minZ() <= maxZ && a.maxZ() >= minZ;
    }
}
