package dev.livingrealms.sim.content;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Resolves Living Realms content JSON directories from the filesystem (headless/core tests)
 * or the classpath (bundled runtime). Spacing and density policy stay in Java — never here.
 */
public final class ContentDataPaths {
    public static final String REALMS = "realms";
    public static final String CULTURES = "cultures";
    public static final String BUILDINGS = "buildings";
    public static final String ARCHITECTURE = "architecture";

    private static final String FS_ROOT = "src/main/resources/data/livingrealms";
    private static final String CP_ROOT = "/data/livingrealms";

    private ContentDataPaths() {}

    /** Prefer on-disk resources when present (core suite); else list classpath entries via known names. */
    public static Path filesystemDir(String category) {
        Objects.requireNonNull(category, "category");
        return Path.of(FS_ROOT, category);
    }

    public static String classpathPrefix(String category) {
        Objects.requireNonNull(category, "category");
        return CP_ROOT + "/" + category + "/";
    }

    public static List<Path> listJsonFiles(String category) {
        Path dir = filesystemDir(category);
        if (!Files.isDirectory(dir)) return List.of();
        try (var stream = Files.list(dir)) {
            List<Path> out = new ArrayList<>();
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(out::add);
            return List.copyOf(out);
        } catch (Exception e) {
            throw new IllegalStateException("Failed listing content dir " + dir, e);
        }
    }

    public static String readUtf8(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new IllegalStateException("Failed reading " + path, e);
        }
    }

    public static String readClasspathOrFs(String category, String fileName) {
        Objects.requireNonNull(fileName, "fileName");
        String cp = classpathPrefix(category) + fileName;
        try (var in = ContentDataPaths.class.getResourceAsStream(cp)) {
            if (in != null) return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed reading classpath " + cp, e);
        }
        Path fs = filesystemDir(category).resolve(fileName);
        if (Files.isRegularFile(fs)) return readUtf8(fs);
        throw new IllegalStateException("Missing content resource " + category + "/" + fileName);
    }
}
