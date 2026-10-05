package dev.livingrealms.sim.content;

import dev.livingrealms.sim.civilization.FactionCivilizationState;
import dev.livingrealms.sim.civilization.SettlementCivilizationState;
import dev.livingrealms.sim.construction.CultureArchitecture;
import dev.livingrealms.sim.construction.CultureArchitectureProfile;
import dev.livingrealms.sim.construction.SettlementMorphology;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.faction.SettlementSpecialization;
import dev.livingrealms.sim.world.SimulationState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Wave 9 — settlement identity profile derived from existing schema-20 fields
 * (provenance/origin, civilization culture, morphology, specialization, prosperity/traditions).
 * No schema bump: traits already persist via Settlement provenance + FactionCivilizationState.
 * Wired into architecture selection and culture dialogue.
 */
public record SettlementIdentityProfile(
        String cultureId,
        String cultureDisplayName,
        long foundationDay,
        SettlementOrigin origin,
        SettlementMorphology morphology,
        SettlementSpecialization specialization,
        double wealthCharacter,
        double militaryCharacter,
        double religiousCharacter,
        double tradeCharacter,
        List<String> historicalScars,
        String landmarkFocus,
        CultureArchitecture architecturalEra
) {
    public SettlementIdentityProfile {
        Objects.requireNonNull(cultureId, "cultureId");
        Objects.requireNonNull(cultureDisplayName, "cultureDisplayName");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(morphology, "morphology");
        Objects.requireNonNull(specialization, "specialization");
        Objects.requireNonNull(historicalScars, "historicalScars");
        Objects.requireNonNull(landmarkFocus, "landmarkFocus");
        Objects.requireNonNull(architecturalEra, "architecturalEra");
        if (!Double.isFinite(wealthCharacter) || !Double.isFinite(militaryCharacter)
                || !Double.isFinite(religiousCharacter) || !Double.isFinite(tradeCharacter)) {
            throw new IllegalArgumentException("identity characters");
        }
        historicalScars = List.copyOf(historicalScars);
    }

    public static SettlementIdentityProfile derive(SimulationState state, Faction faction, Settlement settlement) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");

        String cultureId = resolveCultureId(faction);
        CultureDefinition culture = CultureDefinitionRegistry.find(cultureId).orElse(null);
        String cultureDisplay = culture != null
                ? culture.displayName()
                : state.ensureFactionCivilization(faction.id()).cultureName();

        FactionCivilizationState factionCiv = state.ensureFactionCivilization(faction.id());
        SettlementCivilizationState settlementCiv = state.ensureSettlementCivilization(settlement.id(), faction.id());

        boolean capital = faction.settlements().stream()
                .max((a, b) -> {
                    int c = Integer.compare(a.population(), b.population());
                    return c != 0 ? c : Long.compare(a.id(), b.id());
                })
                .map(s -> s.id() == settlement.id())
                .orElse(false);

        SettlementSpecialization specialization = settlement.specialization(capital, faction.technology());
        SettlementMorphology morphology = SettlementMorphology.derive(faction, settlement);

        CultureArchitectureProfile archProfile = CultureArchitectureProfile.derive(
                factionCiv, settlementCiv, faction, settlement);
        CultureArchitecture era = culture != null ? culture.architectureFamily() : archProfile.architecture();
        // Prefer bound settlement architecture when present (existing side channel).
        CultureArchitecture bound = CultureArchitectureProfile.peek(settlement.id());
        if (bound != null) era = bound;

        double wealth = settlement.prosperity();
        double military = factionCiv.martialTradition() * 0.6 + settlement.publicOrder() * 0.4;
        boolean hasTemple = settlement.completedConstruction().stream().anyMatch(k -> k.startsWith("temple:"));
        double religious = factionCiv.religiousInfluence() * 0.5
                + (hasTemple ? 0.35 : 0)
                + settlementCiv.culturalCohesion() * 0.15;
        double trade = factionCiv.mercantileTradition() * 0.55 + wealth * 0.25
                + (specialization == SettlementSpecialization.TRADE
                || specialization == SettlementSpecialization.NAVAL ? 0.2 : 0);

        List<String> scars = scarsOf(settlement, settlementCiv);
        String landmark = landmarkFocusOf(settlement, specialization, capital);

        // Stable without schema bump: genesis origins → day 0; others → deterministic id stamp.
        long foundationDay = settlement.origin() == SettlementOrigin.AUTHORED_SEED
                || settlement.origin() == SettlementOrigin.WIZARD_TREES
                || settlement.origin() == SettlementOrigin.LEGACY
                ? 0L
                : Math.floorMod(settlement.id(), 10_000L);

        return new SettlementIdentityProfile(
                cultureId,
                cultureDisplay,
                foundationDay,
                settlement.origin(),
                morphology,
                specialization,
                clamp01(wealth),
                clamp01(military),
                clamp01(religious),
                clamp01(trade),
                scars,
                landmark,
                era);
    }

    /** Resolve authored culture id from realm pack, else wizard_trees, else empty (caller falls back). */
    public static String resolveCultureId(Faction faction) {
        Objects.requireNonNull(faction, "faction");
        if ("Wizard Trees".equals(faction.name())) return "wizard_trees";
        RealmDefinition realm = RealmDefinitionLoader.byDisplayName(faction.name());
        if (realm != null && realm.cultureId() != null && !realm.cultureId().isBlank()) {
            return realm.cultureId();
        }
        return "";
    }

    public static Optional<CultureDefinition> cultureOf(Faction faction) {
        String id = resolveCultureId(faction);
        return CultureDefinitionRegistry.find(id);
    }

    private static List<String> scarsOf(Settlement settlement, SettlementCivilizationState civ) {
        List<String> scars = new ArrayList<>();
        if (settlement.unrest() >= 0.45) scars.add("unrest");
        if (settlement.foodSecurity() < 0.35) scars.add("famine_memory");
        if (civ.diseasePressure() >= 0.35) scars.add("epidemic_scar");
        if (civ.banditPressure() >= 0.35) scars.add("bandit_scar");
        if (civ.refugeePressure() >= 0.35) scars.add("refugee_influx");
        if (settlement.infrastructure() < 0.2) scars.add("ruinous_walls");
        return scars;
    }

    private static String landmarkFocusOf(Settlement settlement, SettlementSpecialization spec, boolean capital) {
        if (!settlement.priorityLandmarks().isEmpty()) {
            return settlement.priorityLandmarks().iterator().next();
        }
        if (capital) return "keep";
        return switch (spec) {
            case RELIGIOUS -> "temple";
            case MILITARY -> "barracks";
            case TRADE, NAVAL -> "market";
            case ACADEMIC -> "school";
            case INDUSTRIAL -> "factory";
            case FISHING -> "dock";
            default -> settlement.tier().name().toLowerCase(Locale.ROOT) + "_square";
        };
    }

    private static double clamp01(double v) {
        if (!Double.isFinite(v)) return 0;
        return Math.max(0, Math.min(1, v));
    }
}
