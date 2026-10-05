package dev.livingrealms.sim.content;

import dev.livingrealms.sim.construction.CultureArchitecture;
import dev.livingrealms.sim.data.MiniJson;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Loads and indexes culture definitions from {@code data/livingrealms/cultures/*.json}. */
public final class CultureDefinitionLoader {
    private static final Object LOCK = new Object();
    private static volatile Map<String, CultureDefinition> CACHED;

    private CultureDefinitionLoader() {}

    public static Map<String, CultureDefinition> loadAll() {
        Map<String, CultureDefinition> local = CACHED;
        if (local != null) return local;
        synchronized (LOCK) {
            if (CACHED != null) return CACHED;
            CACHED = Map.copyOf(loadFromFilesystem());
            return CACHED;
        }
    }

    public static void clearCache() {
        synchronized (LOCK) {
            CACHED = null;
        }
    }

    public static Optional<CultureDefinition> find(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.ofNullable(loadAll().get(id));
    }

    public static CultureDefinition require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown culture: " + id));
    }

    public static CultureDefinition decode(String json) {
        Map<String, Object> m = ContentJson.object(MiniJson.parse(json), "culture");
        CultureArchitecture family = ContentJson.requireEnum(CultureArchitecture.class, m, "architectureFamily");
        CultureArchitecture related = ContentJson.optionalEnum(
                CultureArchitecture.class, m, "relatedFamily", family);
        return new CultureDefinition(
                ContentJson.requireString(m, "id"),
                ContentJson.requireString(m, "displayName"),
                family,
                ContentJson.requireString(m, "namingStyle"),
                ContentJson.requireString(m, "dialectStyle"),
                ContentJson.optionalStringList(m, "materialPalette"),
                ContentJson.optionalNumber(m, "economicTendency", 0.5),
                ContentJson.optionalNumber(m, "martialTendency", 0.45),
                ContentJson.optionalNumber(m, "artisticTendency", 0.35),
                ContentJson.optionalStringList(m, "districtTendencies"),
                related);
    }

    private static Map<String, CultureDefinition> loadFromFilesystem() {
        List<Path> files = ContentDataPaths.listJsonFiles(ContentDataPaths.CULTURES);
        if (files.isEmpty()) throw new IllegalStateException("No culture definitions under data/livingrealms/cultures");
        Map<String, CultureDefinition> byId = new LinkedHashMap<>();
        for (Path path : files) {
            CultureDefinition def = decode(ContentDataPaths.readUtf8(path));
            if (byId.put(def.id(), def) != null) {
                throw new IllegalStateException("Duplicate culture id: " + def.id());
            }
        }
        if (!byId.containsKey("wizard_trees")) {
            throw new IllegalStateException("Missing wizard_trees culture");
        }
        if (byId.size() < 13) {
            throw new IllegalStateException("Expected ≥13 cultures (12 surface + wizard_trees), got " + byId.size());
        }
        return byId;
    }
}
