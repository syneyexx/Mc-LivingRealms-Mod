package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.world.SimPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Physical resolution of a planned construction intent against terrain and parcel law. */
public record ResolvedBuildSite(
        SimPosition plannedCenter,
        SimPosition actualCenter,
        int footprintWidth,
        int footprintDepth,
        int rotationQuarterTurns,
        SettlementParcelPlanner.ParcelFrontage frontage,
        EntranceSpec entrance,
        int foundationY,
        StructureAccessValidator.AccessSample accessConnection,
        String parcelId,
        int parcelWidth,
        int parcelDepth,
        Adaptation adaptation
) {
    public enum Adaptation { NONE, FOUNDATION_PIERS, STAIRS_RETAINING, REJECTED }
    public record EntranceSpec(int doorLocalX, int doorLocalZ, int doorFloorY, int approachY) {}

    public ResolvedBuildSite {
        plannedCenter = Objects.requireNonNull(plannedCenter, "plannedCenter");
        actualCenter = Objects.requireNonNull(actualCenter, "actualCenter");
        if (footprintWidth <= 0 || footprintDepth <= 0) throw new IllegalArgumentException("footprint");
        rotationQuarterTurns = Math.floorMod(rotationQuarterTurns, 4);
        parcelId = parcelId == null ? "" : parcelId;
        adaptation = Objects.requireNonNullElse(adaptation, Adaptation.NONE);
    }

    public boolean hasParcel() { return !parcelId.isBlank(); }

    public ConstructionIntent toIntent(ConstructionIntent original) {
        return Objects.requireNonNull(original, "original").withCenter(actualCenter);
    }

    public static boolean footprintInsideParcel(
            SimPosition buildingCenter, int buildingWidth, int buildingDepth, int rotationQuarterTurns,
            SimPosition parcelCenter, int parcelWidth, int parcelDepth) {
        Objects.requireNonNull(buildingCenter, "buildingCenter");
        Objects.requireNonNull(parcelCenter, "parcelCenter");
        if (parcelWidth <= 0 || parcelDepth <= 0) return false;
        int turns = Math.floorMod(rotationQuarterTurns, 4);
        int w = (turns & 1) == 0 ? buildingWidth : buildingDepth;
        int d = (turns & 1) == 0 ? buildingDepth : buildingWidth;
        double halfW = w / 2.0, halfD = d / 2.0;
        double bx0 = buildingCenter.x() - halfW, bx1 = buildingCenter.x() + halfW;
        double bz0 = buildingCenter.z() - halfD, bz1 = buildingCenter.z() + halfD;
        double px0 = parcelCenter.x() - parcelWidth / 2.0, px1 = parcelCenter.x() + parcelWidth / 2.0;
        double pz0 = parcelCenter.z() - parcelDepth / 2.0, pz1 = parcelCenter.z() + parcelDepth / 2.0;
        return bx0 >= px0 - 1e-9 && bx1 <= px1 + 1e-9 && bz0 >= pz0 - 1e-9 && bz1 <= pz1 + 1e-9;
    }

    public static List<int[]> parcelAlignmentOffsets() {
        List<int[]> out = new ArrayList<>();
        out.add(new int[]{0, 0});
        for (int r = 1; r <= 2; r++) {
            out.add(new int[]{r, 0}); out.add(new int[]{-r, 0});
            out.add(new int[]{0, r}); out.add(new int[]{0, -r});
            out.add(new int[]{r, r}); out.add(new int[]{r, -r});
            out.add(new int[]{-r, r}); out.add(new int[]{-r, -r});
        }
        return List.copyOf(out);
    }

    public static List<int[]> openSiteAlignmentOffsets() {
        List<int[]> out = new ArrayList<>();
        out.add(new int[]{0, 0});
        for (int r = 2; r <= 4; r += 2) {
            out.add(new int[]{r, 0}); out.add(new int[]{-r, 0});
            out.add(new int[]{0, r}); out.add(new int[]{0, -r});
        }
        out.add(new int[]{3, 3}); out.add(new int[]{3, -3});
        out.add(new int[]{-3, 3}); out.add(new int[]{-3, -3});
        return List.copyOf(out);
    }

    public static Adaptation adaptationForGrade(int doorFloorY, int approachY, int padSlope) {
        if (padSlope <= 2 && Math.abs(doorFloorY - approachY) <= 1) return Adaptation.NONE;
        if (padSlope <= 4 || EntranceAccessPlanner.canRepair(doorFloorY, approachY)) {
            if (Math.abs(doorFloorY - approachY) > 1) return Adaptation.STAIRS_RETAINING;
            return Adaptation.FOUNDATION_PIERS;
        }
        return Adaptation.REJECTED;
    }
}
