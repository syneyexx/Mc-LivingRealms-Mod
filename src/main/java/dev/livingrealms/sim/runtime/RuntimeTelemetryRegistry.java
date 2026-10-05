package dev.livingrealms.sim.runtime;

import java.util.EnumMap;
import java.util.Map;

/** Aggregated telemetry keyed by {@link RuntimeDomain}. */
public final class RuntimeTelemetryRegistry {
    private final EnumMap<RuntimeDomain, RuntimeTelemetry> byDomain = new EnumMap<>(RuntimeDomain.class);

    public RuntimeTelemetryRegistry() {
        for (RuntimeDomain domain : RuntimeDomain.values()) {
            byDomain.put(domain, new RuntimeTelemetry());
        }
    }

    public RuntimeTelemetry domain(RuntimeDomain domain) {
        return byDomain.get(domain);
    }

    public Map<RuntimeDomain, RuntimeTelemetry> snapshot() {
        return Map.copyOf(byDomain);
    }

    public void resetPeaksAndDeferred() {
        for (RuntimeTelemetry telemetry : byDomain.values()) {
            telemetry.resetPeaksAndDeferred();
        }
    }

    public void resetAll() {
        for (RuntimeTelemetry telemetry : byDomain.values()) {
            telemetry.resetAll();
        }
    }
}
