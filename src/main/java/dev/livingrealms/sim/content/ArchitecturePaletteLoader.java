package dev.livingrealms.sim.content;

import dev.livingrealms.sim.construction.CultureArchitecture;
import dev.livingrealms.sim.data.MiniJson;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Loads architecture palettes from {@code data/livingrealms/architecture/*.json}. */
public final class ArchitecturePaletteLoader {
    private static final Object LOCK = new Object();
    private static volatile Map<String, ArchitecturePaletteDefinition> CACHED;

    private ArchitecturePaletteLoader() {}

    public static Map<String, ArchitecturePaletteDefinition> loadAll() {
        Map<String, ArchitecturePaletteDefinition> local = CACHED;
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

    public static Optional<ArchitecturePaletteDefinition> forFamily(CultureArchitecture family) {
        Objects.requireNonNull(family, "family");
        ArchitecturePaletteDefinition generic = null;
        for (ArchitecturePaletteDefinition p : loadAll().values()) {
            if (p.architectureFamily() == family && !p.genericFallback()) return Optional.of(p);
            if (p.genericFallback()) generic = p;
        }
        return Optional.ofNullable(generic);
    }

    public static ArchitecturePaletteDefinition decode(String json) {
        Map<String, Object> m = ContentJson.object(MiniJson.parse(json), "architecture");
        return new ArchitecturePaletteDefinition(
                ContentJson.requireString(m, "id"),
                ContentJson.requireEnum(CultureArchitecture.class, m, "architectureFamily"),
                ContentJson.optionalStringMap(m, "slots"),
                ContentJson.optionalBool(m, "genericFallback", false));
    }

    private static Map<String, ArchitecturePaletteDefinition> loadFromFilesystem() {
        List<Path> files = ContentDataPaths.listJsonFiles(ContentDataPaths.ARCHITECTURE);
        if (files.isEmpty()) {
            throw new IllegalStateException("No architecture palettes under data/livingrealms/architecture");
        }
        Map<String, ArchitecturePaletteDefinition> byId = new LinkedHashMap<>();
        for (Path path : files) {
            ArchitecturePaletteDefinition def = decode(ContentDataPaths.readUtf8(path));
            if (byId.put(def.id(), def) != null) {
                throw new IllegalStateException("Duplicate architecture palette id: " + def.id());
            }
        }
        return byId;
    }
}
