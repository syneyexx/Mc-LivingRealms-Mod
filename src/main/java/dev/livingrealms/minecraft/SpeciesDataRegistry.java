package dev.livingrealms.minecraft;

import dev.livingrealms.sim.ecology.SpeciesCatalog;
import dev.livingrealms.sim.ecology.SpeciesDefinition;
import dev.livingrealms.sim.ecology.SpeciesCatalogValidator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Server-side snapshot installed atomically after a successful datapack reload. */
public final class SpeciesDataRegistry {
    private static volatile Map<String, SpeciesDefinition> current = SpeciesCatalog.starter();
    private static final AtomicLong revision = new AtomicLong();
    private static volatile boolean ready;
    private SpeciesDataRegistry() {}

    public static Map<String, SpeciesDefinition> current() { return current; }
    public static long revision() { return revision.get(); }
    public static boolean ready() { return ready; }

    public static void install(Map<String, SpeciesDefinition> catalog) {
        Objects.requireNonNull(catalog, "catalog");
        SpeciesCatalogValidator.Report report = SpeciesCatalogValidator.validate(catalog);
        report.throwIfInvalid();
        current = Map.copyOf(catalog);
        ready = true;
        revision.incrementAndGet();
    }
}
