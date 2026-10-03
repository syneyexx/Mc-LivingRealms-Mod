package dev.livingrealms.minecraft.network;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Common-side indirection that keeps all net.minecraft.client classes out of networking registration.
 * Dedicated servers load this class safely; the physical client installs its receiver during client setup.
 */
public final class DashboardClientBridge {
    private static volatile Consumer<String> receiver = ignored -> {};

    private DashboardClientBridge() {}

    public static void install(Consumer<String> newReceiver) {
        receiver = Objects.requireNonNull(newReceiver, "newReceiver");
    }

    public static void receive(String json) {
        receiver.accept(json);
    }

    public static void reset() {
        receiver = ignored -> {};
    }
}
