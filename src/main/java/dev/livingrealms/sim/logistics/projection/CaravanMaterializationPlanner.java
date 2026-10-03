package dev.livingrealms.sim.logistics.projection;

import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.world.SimPosition;
import java.util.*;

/** Selects the bounded subset of strategic shipments that should exist as Minecraft caravan entities. */
public final class CaravanMaterializationPlanner {
    private final CaravanProjectionConfig config;

    public CaravanMaterializationPlanner(CaravanProjectionConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public List<CaravanProjectionRequest> plan(Collection<TradeShipment> shipments, Collection<SimPosition> players) {
        Objects.requireNonNull(shipments, "shipments");
        Objects.requireNonNull(players, "players");
        if (players.isEmpty() || config.maxPhysicalCaravans() == 0) return List.of();

        List<CaravanProjectionRequest> candidates = new ArrayList<>();
        for (TradeShipment shipment : shipments) {
            if (shipment.arrived()) continue;
            SimPosition position = shipment.position();
            double nearest = players.stream().mapToDouble(position::distanceTo).min().orElse(Double.POSITIVE_INFINITY);
            if (nearest <= config.physicalRadiusBlocks()) candidates.add(new CaravanProjectionRequest(shipment.id(), position, nearest));
        }
        candidates.sort(Comparator.comparingDouble(CaravanProjectionRequest::nearestPlayerDistance)
                .thenComparingLong(CaravanProjectionRequest::shipmentId));
        if (candidates.size() > config.maxPhysicalCaravans()) {
            return List.copyOf(candidates.subList(0, config.maxPhysicalCaravans()));
        }
        return List.copyOf(candidates);
    }

    public CaravanProjectionPlan reconcile(Collection<CaravanProjectionRequest> desired,
                                             Collection<CaravanProjectionSnapshot> actual,
                                             Set<Long> activeShipmentIds) {
        Objects.requireNonNull(desired, "desired");
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(activeShipmentIds, "activeShipmentIds");

        Map<Long, CaravanProjectionRequest> desiredById = new TreeMap<>();
        for (CaravanProjectionRequest request : desired) {
            if (desiredById.put(request.shipmentId(), request) != null) throw new IllegalArgumentException("duplicate desired shipment " + request.shipmentId());
        }

        Map<Long, List<CaravanProjectionSnapshot>> actualById = new TreeMap<>();
        for (CaravanProjectionSnapshot snapshot : actual) actualById.computeIfAbsent(snapshot.shipmentId(), ignored -> new ArrayList<>()).add(snapshot);
        actualById.values().forEach(list -> list.sort(Comparator.comparing(CaravanProjectionSnapshot::entityKey)));

        List<CaravanProjectionSpawn> spawns = new ArrayList<>();
        List<CaravanProjectionDespawn> despawns = new ArrayList<>();
        Set<Long> all = new TreeSet<>(); all.addAll(desiredById.keySet()); all.addAll(actualById.keySet());
        for (long shipmentId : all) {
            CaravanProjectionRequest request = desiredById.get(shipmentId);
            List<CaravanProjectionSnapshot> loaded = actualById.getOrDefault(shipmentId, List.of());
            if (request == null) {
                CaravanProjectionRemovalReason reason = activeShipmentIds.contains(shipmentId)
                        ? CaravanProjectionRemovalReason.OUTSIDE_PHYSICAL_RADIUS
                        : CaravanProjectionRemovalReason.SHIPMENT_FINISHED;
                for (CaravanProjectionSnapshot snapshot : loaded) despawns.add(new CaravanProjectionDespawn(snapshot.entityKey(), shipmentId, reason));
                continue;
            }
            if (loaded.isEmpty()) {
                spawns.add(new CaravanProjectionSpawn(shipmentId, request.position()));
            } else {
                for (int i = 1; i < loaded.size(); i++) {
                    CaravanProjectionSnapshot duplicate = loaded.get(i);
                    despawns.add(new CaravanProjectionDespawn(duplicate.entityKey(), shipmentId, CaravanProjectionRemovalReason.DUPLICATE));
                }
            }
        }
        spawns.sort(Comparator.comparingLong(CaravanProjectionSpawn::shipmentId));
        despawns.sort(Comparator.comparingLong(CaravanProjectionDespawn::shipmentId).thenComparing(CaravanProjectionDespawn::entityKey));
        return new CaravanProjectionPlan(spawns, despawns);
    }
}
