package dev.livingrealms.sim.content;

import dev.livingrealms.sim.construction.CultureArchitecture;
import dev.livingrealms.sim.construction.SettlementDistrict;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.data.MiniJson;
import dev.livingrealms.sim.faction.Settlement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads building archetype JSON from {@code data/livingrealms/buildings/*.json}. */
public final class BuildingDefinitionLoader {
    private static final Object LOCK = new Object();
    private static volatile List<BuildingDefinition> CACHED;

    private BuildingDefinitionLoader() {}

    public static List<BuildingDefinition> loadAll() {
        List<BuildingDefinition> local = CACHED;
        if (local != null) return local;
        synchronized (LOCK) {
            if (CACHED != null) return CACHED;
            CACHED = List.copyOf(loadFromFilesystem());
            return CACHED;
        }
    }

    public static void clearCache() {
        synchronized (LOCK) {
            CACHED = null;
        }
    }

    public static BuildingDefinition decode(String json) {
        Map<String, Object> m = ContentJson.object(MiniJson.parse(json), "building");
        return new BuildingDefinition(
                ContentJson.requireString(m, "id"),
                ContentJson.requireString(m, "displayName"),
                ContentJson.requireString(m, "cultureId"),
                ContentJson.requireEnum(CultureArchitecture.class, m, "architectureFamily"),
                ContentJson.requireEnum(StructureRole.class, m, "role"),
                ContentJson.requireEnum(Settlement.Tier.class, m, "tier"),
                ContentJson.requireEnum(SettlementDistrict.class, m, "district"),
                ContentJson.optionalNumber(m, "wealthMin", 0),
                ContentJson.optionalNumber(m, "wealthMax", 1),
                ContentJson.requireInt(m, "footprintWidth"),
                ContentJson.requireInt(m, "footprintDepth"),
                ContentJson.optionalBool(m, "genericFallback", false));
    }

    private static List<BuildingDefinition> loadFromFilesystem() {
        List<String> files = ContentDataPaths.listJsonFileNames(ContentDataPaths.BUILDINGS);
        if (files.isEmpty()) throw new IllegalStateException("No building definitions under data/livingrealms/buildings");
        Map<String, BuildingDefinition> byId = new LinkedHashMap<>();
        for (String fileName : files) {
            BuildingDefinition def = decode(ContentDataPaths.readClasspathOrFs(ContentDataPaths.BUILDINGS, fileName));
            if (byId.put(def.id(), def) != null) {
                throw new IllegalStateException("Duplicate building id: " + def.id());
            }
        }
        return new ArrayList<>(byId.values());
    }
}
