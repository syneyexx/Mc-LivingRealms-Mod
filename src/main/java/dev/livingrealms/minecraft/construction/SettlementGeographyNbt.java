package dev.livingrealms.minecraft.construction;

import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementGeographyProfile;
import dev.livingrealms.sim.world.SimulationState;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Persists world-discovered settlement geography outside schema-16 payload bytes. */
public final class SettlementGeographyNbt {
    public static final String KEY = "SettlementGeographyV1";

    private SettlementGeographyNbt() {}

    public static void write(CompoundTag tag, SimulationState state) {
        ListTag list = new ListTag();
        for (var faction : state.factions()) {
            for (Settlement settlement : faction.settlements()) {
                SettlementGeographyProfile g = settlement.geography();
                if (!g.worldDiscovered()) continue;
                CompoundTag row = new CompoundTag();
                row.putLong("Id", settlement.id());
                row.putBoolean("Coastal", g.coastal());
                row.putBoolean("River", g.riverAdjacent());
                row.putBoolean("Navigable", g.navigableWater());
                row.putBoolean("Fresh", g.freshwater());
                row.putDouble("Harbor", g.harborSuitability());
                row.putDouble("Elevation", g.elevation());
                row.putDouble("Slope", g.slope());
                row.putDouble("Fertility", g.fertility());
                row.putDouble("Forest", g.forest());
                row.putDouble("Mining", g.miningPotential());
                row.putString("Biome", g.biomeId());
                list.add(row);
            }
        }
        tag.put(KEY, list);
    }

    public static void read(CompoundTag tag, SimulationState state) {
        if (!tag.contains(KEY, Tag.TAG_LIST)) return;
        ListTag list = tag.getList(KEY, Tag.TAG_COMPOUND);
        Map<Long, Settlement> byId = new LinkedHashMap<>();
        for (var faction : state.factions()) for (Settlement s : faction.settlements()) byId.put(s.id(), s);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            Settlement settlement = byId.get(row.getLong("Id"));
            if (settlement == null) continue;
            settlement.setGeography(new SettlementGeographyProfile(
                    row.getBoolean("Coastal"),
                    row.getBoolean("River"),
                    row.getBoolean("Navigable"),
                    row.getBoolean("Fresh"),
                    row.getDouble("Harbor"),
                    row.getDouble("Elevation"),
                    row.getDouble("Slope"),
                    row.getDouble("Fertility"),
                    row.getDouble("Forest"),
                    row.getDouble("Mining"),
                    row.getString("Biome"),
                    true
            ));
        }
    }
}
