package dev.livingrealms.minecraft;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.sim.data.SpeciesJsonCodec;
import dev.livingrealms.sim.ecology.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Loads data/<namespace>/livingrealms/species/*.json. Datapack priority is handled by ResourceManager. */
public final class SpeciesReloadListener extends SimplePreparableReloadListener<Map<String, SpeciesDefinition>> {
    private static final String DIRECTORY = LivingRealms.MOD_ID + "/species";

    @Override
    protected Map<String, SpeciesDefinition> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, net.minecraft.server.packs.resources.Resource> resources =
                manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json"));
        List<ResourceLocation> ids = new ArrayList<>(resources.keySet());
        ids.sort(ResourceLocation::compareTo);

        Map<String, SpeciesDefinition> catalog = new LinkedHashMap<>();
        for (ResourceLocation resourceId : ids) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    resources.get(resourceId).open(), StandardCharsets.UTF_8))) {
                StringBuilder json = new StringBuilder();
                for (String line; (line = reader.readLine()) != null;) json.append(line).append('\n');
                SpeciesDefinition species = SpeciesJsonCodec.decode(json.toString());
                String path = resourceId.getPath();
                String expectedId = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
                if (!species.id().equals(expectedId)) {
                    throw new IllegalArgumentException("Species id '" + species.id() + "' must match file name '"
                            + expectedId + "' in " + resourceId);
                }
                SpeciesDefinition previous = catalog.put(species.id(), species);
                if (previous != null) {
                    throw new IllegalArgumentException("Duplicate Living Realms species id '" + species.id()
                            + "' while loading " + resourceId);
                }
            } catch (IOException | RuntimeException ex) {
                throw new IllegalStateException("Failed to load Living Realms species resource " + resourceId, ex);
            }
        }

        if (catalog.isEmpty()) throw new IllegalStateException("No Living Realms species definitions were loaded");
        SpeciesCatalogValidator.Report report = SpeciesCatalogValidator.validate(catalog);
        report.throwIfInvalid();
        for (String warning : report.warnings()) LivingRealms.LOGGER.warn("Species data: {}", warning);
        return Map.copyOf(catalog);
    }

    @Override
    protected void apply(Map<String, SpeciesDefinition> catalog, ResourceManager manager, ProfilerFiller profiler) {
        SpeciesDataRegistry.install(catalog);
        LivingRealms.LOGGER.info("Loaded {} Living Realms species definitions (revision {})",
                catalog.size(), SpeciesDataRegistry.revision());
    }
}
