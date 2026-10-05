package dev.livingrealms;

import dev.livingrealms.sim.construction.StructureAccessValidator;
import dev.livingrealms.sim.construction.StructureAccessValidator.AccessKind;
import dev.livingrealms.sim.construction.StructureAccessValidator.AccessSample;

/** Door-to-road access acceptance cases for road-first urbanism. */
public final class StructureAccessValidatorTest {
    private StructureAccessValidatorTest() {}

    public static void main(String[] args) {
        flatEntrancePasses();
        stairsAndSwitchbackPass();
        shortBridgePasses();
        cliffAndSealedFail();
        deepWaterWithoutDockFails();
        blockedStructureFails();
        gradeWithoutRoadBlocksReceipt();
        System.out.println("PASS structure access validator: flat/stairs/switchback/bridge + fail cases");
    }

    private static void flatEntrancePasses() {
        var v = StructureAccessValidator.validate(new AccessSample(AccessKind.FLAT, 64, 64, true, false, false));
        check(v.pass(), "flat entrance must pass");
        check(StructureAccessValidator.validateGrade(64, 64, true).pass(), "grade 0 must pass");
        check(StructureAccessValidator.validateGrade(64, 65, true).pass(), "one-block grade must pass");
    }

    private static void stairsAndSwitchbackPass() {
        check(StructureAccessValidator.validate(new AccessSample(AccessKind.STAIRS, 67, 64, true, false, false)).pass(),
                "stairs within 3 must pass");
        check(StructureAccessValidator.validate(new AccessSample(AccessKind.SWITCHBACK, 72, 64, true, false, false)).pass(),
                "switchback within 8 must pass");
        check(!StructureAccessValidator.validateGrade(80, 64, true).pass(), "extreme grade must fail");
    }

    private static void shortBridgePasses() {
        check(StructureAccessValidator.validate(new AccessSample(AccessKind.SHORT_BRIDGE, 64, 64, true, false, false)).pass(),
                "short bridge to road must pass");
        check(!StructureAccessValidator.validate(new AccessSample(AccessKind.SHORT_BRIDGE, 64, 64, false, false, false)).pass(),
                "bridge without road connection must fail");
    }

    private static void cliffAndSealedFail() {
        check(!StructureAccessValidator.validate(new AccessSample(AccessKind.CLIFF, 80, 64, true, false, false)).pass(),
                "door into cliff must fail");
        check(!StructureAccessValidator.validate(new AccessSample(AccessKind.SEALED_WALL, 64, 64, true, false, false)).pass(),
                "sealed wall must fail");
        check(!StructureAccessValidator.validate(new AccessSample(AccessKind.VERTICAL_JUMP, 66, 64, true, false, false)).pass(),
                "two-block jump must fail");
        check(!StructureAccessValidator.validate(new AccessSample(AccessKind.FLOATING_FLOOR, 65, 64, true, false, false)).pass(),
                "floating floor must fail");
    }

    private static void deepWaterWithoutDockFails() {
        check(!StructureAccessValidator.validate(new AccessSample(AccessKind.DEEP_WATER, 62, 64, true, false, false)).pass(),
                "deep water without dock must fail");
        check(StructureAccessValidator.validate(new AccessSample(AccessKind.DEEP_WATER, 62, 64, true, true, false)).pass(),
                "dock-intended water access must pass");
    }

    private static void blockedStructureFails() {
        check(!StructureAccessValidator.validate(new AccessSample(AccessKind.BLOCKED_STRUCTURE, 64, 64, true, false, false)).pass(),
                "blocked player structure must fail");
    }

    private static void gradeWithoutRoadBlocksReceipt() {
        check(!StructureAccessValidator.validateGrade(64, 65, false).pass(),
                "walkable grade without road must fail completedConstructionReceipt");
        check(StructureAccessValidator.validateGrade(67, 64, true).pass(),
                "stairs with road connection may pass");
    }

    private static void check(boolean cond, String message) {
        if (!cond) throw new AssertionError(message);
    }
}
