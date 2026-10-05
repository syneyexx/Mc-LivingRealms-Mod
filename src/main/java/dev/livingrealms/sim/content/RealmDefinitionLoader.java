package dev.livingrealms.sim.content;

import dev.livingrealms.sim.data.MiniJson;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Loads authored realm definitions from {@code data/livingrealms/realms/*.json}. */
public final class RealmDefinitionLoader {
    private static final Object LOCK = new Object();
    private static volatile List<RealmDefinition> CACHED;

    private RealmDefinitionLoader() {}

    public static List<RealmDefinition> loadAll() {
        List<RealmDefinition> local = CACHED;
        if (local != null) return local;
        synchronized (LOCK) {
            if (CACHED != null) return CACHED;
            CACHED = List.copyOf(loadFromFilesystem());
            return CACHED;
        }
    }

    /** Test/hook reset — next {@link #loadAll()} reloads from disk. */
    public static void clearCache() {
        synchronized (LOCK) {
            CACHED = null;
        }
    }

    public static RealmDefinition byDisplayName(String displayName) {
        Objects.requireNonNull(displayName, "displayName");
        for (RealmDefinition def : loadAll()) {
            if (def.displayName().equals(displayName)) return def;
        }
        return null;
    }

    public static RealmDefinition byId(String id) {
        Objects.requireNonNull(id, "id");
        for (RealmDefinition def : loadAll()) {
            if (def.id().equals(id)) return def;
        }
        return null;
    }

    public static RealmDefinition decode(String json) {
        Map<String, Object> m = ContentJson.object(MiniJson.parse(json), "realm");
        List<RealmDefinition.SatelliteDefinition> satellites = new ArrayList<>();
        for (Map<String, Object> sat : ContentJson.optionalObjectList(m, "satellites")) {
            satellites.add(new RealmDefinition.SatelliteDefinition(
                    ContentJson.requireString(sat, "name"),
                    ContentJson.requireNumber(sat, "dx"),
                    ContentJson.requireNumber(sat, "dz"),
                    ContentJson.requireInt(sat, "population"),
                    ContentJson.requireInt(sat, "housing")));
        }
        return new RealmDefinition(
                ContentJson.requireString(m, "id"),
                ContentJson.requireString(m, "displayName"),
                ContentJson.requireString(m, "rulerSeedName"),
                ContentJson.requireString(m, "capitalName"),
                ContentJson.requireNumber(m, "x"),
                ContentJson.requireNumber(m, "z"),
                ContentJson.requireInt(m, "capitalPopulation"),
                ContentJson.requireInt(m, "capitalHousing"),
                ContentJson.requireNumber(m, "technology"),
                ContentJson.requireNumber(m, "treasury"),
                ContentJson.requireInt(m, "armyInfantry"),
                satellites,
                ContentJson.optionalStringList(m, "namingHints"),
                ContentJson.optionalString(m, "cultureId", ""));
    }

    /** Canonical surface order matching the historical authored seed sequence (placement is order-sensitive). */
    private static final List<String> CANONICAL_ORDER = List.of(
            "aster", "veyran", "eldermere", "solenne", "dravik", "norwyn",
            "sablemere", "verdance", "aurenthal", "redmarch", "stormcoast", "glassmere");

    private static List<RealmDefinition> loadFromFilesystem() {
        List<Path> files = ContentDataPaths.listJsonFiles(ContentDataPaths.REALMS);
        if (files.isEmpty()) throw new IllegalStateException("No realm definitions under data/livingrealms/realms");
        Map<String, RealmDefinition> byId = new LinkedHashMap<>();
        for (Path path : files) {
            RealmDefinition def = decode(ContentDataPaths.readUtf8(path));
            if (byId.put(def.id(), def) != null) {
                throw new IllegalStateException("Duplicate realm id: " + def.id());
            }
        }
        if (byId.size() != 12) {
            throw new IllegalStateException("Expected 12 surface realms, got " + byId.size());
        }
        List<RealmDefinition> ordered = new ArrayList<>();
        for (String id : CANONICAL_ORDER) {
            RealmDefinition def = byId.get(id);
            if (def == null) throw new IllegalStateException("Missing canonical realm id: " + id);
            ordered.add(def);
        }
        if (ordered.size() != byId.size()) {
            throw new IllegalStateException("Realm pack contains ids outside canonical order");
        }
        return List.copyOf(ordered);
    }
}
