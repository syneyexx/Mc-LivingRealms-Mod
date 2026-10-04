package dev.livingrealms.sim.world;

/**
 * Cumulative activity counters for long-run liveness proofs. These are runtime metrics on the
 * live SimulationState (not part of schema-16 payload bytes). Snapshot tests and soak scenarios
 * use them so a single "shipments==0 right now" observation cannot hide past activity.
 */
public final class LivenessCounters {
    private long shipmentsDispatched;
    private long shipmentsDelivered;
    private long shipmentsIntercepted;
    private long routesBuilt;
    private long births;
    private long deaths;
    private long householdsFormed;
    private long migrationsStarted;
    private long crimesRecorded;
    private long constructionCompleted;

    public void onShipmentDispatched() { shipmentsDispatched++; }
    public void onShipmentDelivered() { shipmentsDelivered++; }
    public void onShipmentIntercepted() { shipmentsIntercepted++; }
    public void onRouteBuilt() { routesBuilt++; }
    public void onBirth() { births++; }
    public void onDeath() { deaths++; }
    public void onHouseholdFormed() { householdsFormed++; }
    public void onMigrationStarted() { migrationsStarted++; }
    public void onCrimeRecorded() { crimesRecorded++; }
    public void onConstructionCompleted() { constructionCompleted++; }

    public long shipmentsDispatched() { return shipmentsDispatched; }
    public long shipmentsDelivered() { return shipmentsDelivered; }
    public long shipmentsIntercepted() { return shipmentsIntercepted; }
    public long routesBuilt() { return routesBuilt; }
    public long births() { return births; }
    public long deaths() { return deaths; }
    public long householdsFormed() { return householdsFormed; }
    public long migrationsStarted() { return migrationsStarted; }
    public long crimesRecorded() { return crimesRecorded; }
    public long constructionCompleted() { return constructionCompleted; }

    public long tradeResolutions() { return shipmentsDelivered + shipmentsIntercepted; }

    @Override public String toString() {
        return "LivenessCounters{dispatch="+shipmentsDispatched+", delivered="+shipmentsDelivered
                +", intercepted="+shipmentsIntercepted+", routes="+routesBuilt
                +", births="+births+", deaths="+deaths+", households="+householdsFormed
                +", migrations="+migrationsStarted+", crimes="+crimesRecorded
                +", construction="+constructionCompleted+"}";
    }
}
