package dev.livingrealms;

import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.EntranceAccessPlanner;
import dev.livingrealms.sim.construction.ResolvedBuildSite;
import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.StructureAccessValidator;
import dev.livingrealms.sim.construction.StructureAccessValidator.AccessKind;
import dev.livingrealms.sim.construction.StructureAccessValidator.AccessSample;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import java.util.List;

public final class ResolvedBuildSiteTest {
    private ResolvedBuildSiteTest() {}
    public static void main(String[] args) {
        check(ResolvedBuildSite.footprintInsideParcel(new SimPosition(100, 200), 9, 9, 0, new SimPosition(100, 200), 13, 11), "fit");
        check(!ResolvedBuildSite.footprintInsideParcel(new SimPosition(108, 200), 9, 9, 0, new SimPosition(100, 200), 13, 11), "shift");
        ConstructionIntent house = new ConstructionIntent("house:0", 1, 1, StructureRole.HOUSE, new SimPosition(50, 50), 9, 9, 0, 88, "parcel:house:0", 11, 11);
        check(house.hasParcel(), "bound");
        check(!ResolvedBuildSite.footprintInsideParcel(new SimPosition(58, 50), 9, 9, 0, house.center(), 11, 11), "8-block");
        check(!ResolvedBuildSite.footprintInsideParcel(new SimPosition(70, 50), 9, 9, 0, house.center(), 11, 11), "20-block");
        for (int[] off : ResolvedBuildSite.parcelAlignmentOffsets()) check(Math.abs(off[0]) <= 2 && Math.abs(off[1]) <= 2, "±2");
        for (int[] off : ResolvedBuildSite.openSiteAlignmentOffsets()) check(Math.abs(off[0]) <= 4 && Math.abs(off[1]) <= 4, "±4");
        check(ResolvedBuildSite.adaptationForGrade(64, 64, 1) == ResolvedBuildSite.Adaptation.NONE, "flat");
        check(ResolvedBuildSite.adaptationForGrade(70, 64, 2) == ResolvedBuildSite.Adaptation.STAIRS_RETAINING, "stairs");
        check(ResolvedBuildSite.adaptationForGrade(80, 64, 5) == ResolvedBuildSite.Adaptation.REJECTED, "reject");
        check(EntranceAccessPlanner.isExtremeSite(80, 64), "extreme");
        check(!StructureAccessValidator.validateGrade(80, 64, true).pass(), "cliff");
        Faction faction = new Faction(91, "Parcel Bound", "Mayor");
        Settlement town = new Settlement(910, "Lotlaw", new SimPosition(0, 0), 900, 850);
        faction.addSettlement(town);
        List<ConstructionIntent> houses = SettlementPlanner.plan(faction, town).stream().filter(i -> i.role() == StructureRole.HOUSE).toList();
        check(!houses.isEmpty(), "houses");
        int withParcel = 0;
        for (ConstructionIntent h : houses) {
            if (!h.hasParcel()) continue;
            withParcel++;
            check(h.parcelWidth() >= h.width() && h.parcelDepth() >= h.depth(), "parcel fits");
            check(ResolvedBuildSite.footprintInsideParcel(h.center(), h.width(), h.depth(), h.rotationQuarterTurns(), h.center(), h.parcelWidth(), h.parcelDepth()), "inside");
            check(!ResolvedBuildSite.footprintInsideParcel(new SimPosition(h.center().x() + 8, h.center().z()), h.width(), h.depth(), h.rotationQuarterTurns(), h.center(), h.parcelWidth(), h.parcelDepth()), "no 8 move");
        }
        check(withParcel >= 3, "ids=" + withParcel);
        check(StructureAccessValidator.validate(new AccessSample(AccessKind.FLAT, 64, 64, true, false, false)).pass(), "flat ok");
        check(!StructureAccessValidator.validate(new AccessSample(AccessKind.CLIFF, 80, 64, true, false, false)).pass(), "cliff blocks");
        check(!StructureAccessValidator.validateGrade(64, 64, false).pass(), "no road");
        System.out.println("PASS resolved build site: parcel containment, bounded alignment, access cascade");
    }
    private static void check(boolean cond, String message) { if (!cond) throw new AssertionError(message); }
}
