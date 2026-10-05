package dev.livingrealms.minecraft.network;

import java.util.Objects;
import java.util.function.Consumer;

/** Common-side client indirection for waypoint packets (dedicated-server safe). */
public final class WaypointClientBridge {
    private static volatile Consumer<WaypointPayload> receiver = ignored -> {};

    private WaypointClientBridge() {}

    public static void install(Consumer<WaypointPayload> value) {
        receiver = Objects.requireNonNull(value);
    }

    public static void receive(WaypointPayload value) {
        receiver.accept(value);
    }

    public static void reset() {
        receiver = ignored -> {};
    }
}
