package dev.livingrealms.minecraft.compat.npc;

import dev.livingrealms.api.NpcAdapter;
import dev.livingrealms.minecraft.compat.CivilianNpcAdoption;
import dev.livingrealms.sim.civilian.CitizenRole;
import java.util.Locale;
import net.minecraft.world.entity.Entity;

/**
 * Wave 22 — civilian NPC adoption adapter boundary.
 * Keeps foreign villager namespaces out of canonical sim packages.
 */
public final class CivilianNpcAdapter implements NpcAdapter {
    public static final CivilianNpcAdapter INSTANCE = new CivilianNpcAdapter();

    private CivilianNpcAdapter() {}

    @Override
    public boolean isAdoptable(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isBlank()) return false;
        String id = entityTypeId.toLowerCase(Locale.ROOT);
        // Registry-path heuristic without requiring a live Entity instance.
        if (id.equals("minecraft:villager") || id.equals("minecraft:wandering_trader")) return true;
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String ns = id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft";
        return CivilianNpcAdoption.isAdoptableCivilianType(ns, path);
    }

    @Override
    public String inferRoleKey(String entityTypeId, String displayName) {
        String path = entityTypeId == null ? "" : entityTypeId;
        if (path.contains(":")) path = path.substring(path.indexOf(':') + 1);
        CitizenRole role = CivilianNpcAdoption.inferRoleFromSignals(path, displayName == null ? "" : displayName);
        return role.name();
    }

    public boolean isAdoptable(Entity entity) {
        return CivilianNpcAdoption.isAdoptableCivilian(entity);
    }

    public CitizenRole inferRole(Entity entity) {
        return CivilianNpcAdoption.inferRole(entity);
    }
}
