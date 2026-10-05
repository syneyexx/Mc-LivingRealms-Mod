package dev.livingrealms.api;
import dev.livingrealms.api.event.LifecycleEvent;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Public extension surface: read-only providers + immutable lifecycle events. */
public final class LivingRealmsApi {
    private static final CopyOnWriteArrayList<Consumer<LifecycleEvent>> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile SpeciesProvider speciesProvider = Map::of;
    private static volatile CultureProvider cultureProvider = emptyCulture();
    private static volatile StructureProvider structureProvider = q -> Optional.empty();
    private static volatile NpcAdapter npcAdapter;
    private static volatile IndustryAdapter industryAdapter;
    private static volatile TradeGoodsProvider tradeGoodsProvider = List::of;
    private static volatile MagicAdapter magicAdapter = id -> false;
    private LivingRealmsApi() {}

    public static void subscribe(Consumer<LifecycleEvent> listener) {
        LISTENERS.add(Objects.requireNonNull(listener, "listener"));
    }
    public static void unsubscribe(Consumer<LifecycleEvent> listener) {
        if (listener != null) LISTENERS.remove(listener);
    }
    public static void clearListeners() { LISTENERS.clear(); }
    public static void publish(LifecycleEvent event) {
        Objects.requireNonNull(event, "event");
        for (Consumer<LifecycleEvent> listener : LISTENERS) {
            try { listener.accept(event); } catch (RuntimeException ignored) {}
        }
    }
    public static SpeciesProvider species() { return speciesProvider; }
    public static CultureProvider cultures() { return cultureProvider; }
    public static StructureProvider structures() { return structureProvider; }
    public static Optional<NpcAdapter> npc() { return Optional.ofNullable(npcAdapter); }
    public static Optional<IndustryAdapter> industry() { return Optional.ofNullable(industryAdapter); }
    public static TradeGoodsProvider tradeGoods() { return tradeGoodsProvider; }
    public static MagicAdapter magic() { return magicAdapter; }
    public static void installDefaults(SpeciesProvider species, CultureProvider cultures, StructureProvider structures, TradeGoodsProvider goods) {
        speciesProvider = Objects.requireNonNull(species, "species");
        cultureProvider = Objects.requireNonNull(cultures, "cultures");
        structureProvider = Objects.requireNonNull(structures, "structures");
        tradeGoodsProvider = Objects.requireNonNull(goods, "goods");
    }
    public static void installNpcAdapter(NpcAdapter adapter) { npcAdapter = adapter; }
    public static void installIndustryAdapter(IndustryAdapter adapter) { industryAdapter = adapter; }
    public static void installMagicAdapter(MagicAdapter adapter) { magicAdapter = Objects.requireNonNullElse(adapter, id -> false); }
    public static int listenerCount() { return LISTENERS.size(); }
    private static CultureProvider emptyCulture() {
        return new CultureProvider() {
            @Override public Map<String, dev.livingrealms.sim.content.CultureDefinition> all() { return Map.of(); }
            @Override public Optional<dev.livingrealms.sim.content.CultureDefinition> find(String id) { return Optional.empty(); }
        };
    }
}
