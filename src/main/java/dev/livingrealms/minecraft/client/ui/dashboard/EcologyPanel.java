package dev.livingrealms.minecraft.client.ui.dashboard;

import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.ArrayList;
import java.util.List;

public final class EcologyPanel implements DashboardPanel {
    @Override
    public void rebuildWidgets(PanelHost host, RealmDashboardSnapshot snapshot, DashboardLayout layout, int page) {}

    @Override
    public List<DashboardLine> lines(RealmDashboardSnapshot snapshot, int page) {
        List<DashboardLine> lines = new ArrayList<>();
        var eco = snapshot.ecology();
        lines.add(DashboardLine.header("Living ecology"));
        lines.add(DashboardLine.text("Catalog " + eco.catalogSpecies() + " species • " + eco.regionCount()
                + " regions • " + eco.populationGroups() + " populations"));
        lines.add(DashboardLine.text("Estimated wildlife: " + DashboardPanel.whole(eco.totalAnimals())));
        for (var region : eco.regions()) {
            lines.add(DashboardLine.header(DashboardPanel.titleCase(region.biome()) + " • " + DashboardPanel.whole(region.distanceBlocks()) + "m"));
            lines.add(DashboardLine.dim("Region #" + region.id() + " • " + DashboardPanel.one(region.areaKm2())
                    + " km² • plants " + DashboardPanel.whole(region.plantBiomass())
                    + " • animals " + DashboardPanel.whole(region.animals())
                    + " • groups " + region.groups(), 1));
            for (var species : region.dominantSpecies()) {
                lines.add(DashboardLine.text("  " + species.commonName() + " × " + DashboardPanel.whole(species.population())
                        + " • health " + DashboardPanel.pct(species.health())));
                lines.add(DashboardLine.dim("hunger " + DashboardPanel.pct(species.hunger())
                        + " • thirst " + DashboardPanel.pct(species.thirst())
                        + " • " + DashboardPanel.titleCase(species.locomotion())
                        + " / " + DashboardPanel.titleCase(species.morphology()), 2));
            }
            if (region.dominantSpecies().isEmpty()) lines.add(DashboardLine.dim("No established animal populations.", 1));
        }
        if (eco.regions().isEmpty()) lines.add(DashboardLine.dim("No ecosystem regions discovered around this world yet.", 0));
        return lines;
    }
}
