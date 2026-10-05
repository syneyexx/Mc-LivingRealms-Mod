package dev.livingrealms.sim.runtime;

/**
 * Publishes the latest runtime budget pressure for physical projection adapters.
 * Updated at the start of each Minecraft runtime tick from the prior tick's measurement.
 */
public final class RuntimePressureBridge {
    private static volatile RuntimeBudgetController.Pressure published = RuntimeBudgetController.Pressure.HEALTHY;

    private RuntimePressureBridge() {}

    public static RuntimeBudgetController.Pressure current() {
        return published;
    }

    public static void publish(RuntimeBudgetController.Pressure pressure) {
        published = pressure == null ? RuntimeBudgetController.Pressure.HEALTHY : pressure;
    }

    public static void reset() {
        published = RuntimeBudgetController.Pressure.HEALTHY;
    }
}
