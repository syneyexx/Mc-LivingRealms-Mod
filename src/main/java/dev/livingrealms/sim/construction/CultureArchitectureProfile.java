package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.civilization.FactionCivilizationState;
import dev.livingrealms.sim.civilization.SettlementCivilizationState;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementGeographyProfile;
import dev.livingrealms.sim.faction.SettlementSpecialization;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic culture → architecture derivation from civilization traits and geography.
 * Faction id modulo is never the primary selector — only a final tie salt.
 */
public final class CultureArchitectureProfile {
    private static final ConcurrentHashMap<Long, CultureArchitecture> SETTLEMENT_BINDINGS = new ConcurrentHashMap<>();

    private final CultureArchitecture architecture;
    private final String rationale;

    public CultureArchitectureProfile(CultureArchitecture architecture, String rationale) {
        this.architecture = Objects.requireNonNull(architecture, "architecture");
        this.rationale = rationale == null ? "" : rationale;
    }

    public CultureArchitecture architecture() { return architecture; }
    public String rationale() { return rationale; }

    public static void bind(long settlementId, CultureArchitecture architecture) {
        if (settlementId <= 0 || architecture == null) return;
        SETTLEMENT_BINDINGS.put(settlementId, architecture);
    }

    public static CultureArchitecture peek(long settlementId) {
        return SETTLEMENT_BINDINGS.get(settlementId);
    }

    public static void clearBindings() {
        SETTLEMENT_BINDINGS.clear();
    }

    public static CultureArchitectureProfile derive(Faction faction, Settlement settlement) {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        return derive(null, null, faction, settlement);
    }

    public static CultureArchitectureProfile derive(
            FactionCivilizationState factionCiv,
            SettlementCivilizationState settlementCiv,
            Faction faction,
            Settlement settlement
    ) {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        SettlementGeographyProfile geo = settlement.geography();
        double mercantile = factionCiv == null ? softMercantile(faction, settlement) : factionCiv.mercantileTradition();
        double martial = factionCiv == null ? softMartial(faction, settlement) : factionCiv.martialTradition();
        double agrarian = factionCiv == null ? softAgrarian(faction, settlement) : factionCiv.agrarianTradition();
        double artistic = factionCiv == null ? softArtistic(faction, settlement) : factionCiv.artisticTradition();
        double education = factionCiv == null ? Math.min(1, faction.technology() * 0.8 + settlement.prosperity() * 0.2)
                : factionCiv.education();
        if (settlementCiv != null) {
            education = Math.max(education, settlementCiv.education());
        }
        return derive(mercantile, martial, agrarian, artistic, education, faction.technology(),
                settlement.prosperity(), geo, settlement.specialization(
                        faction.settlements().stream().max((a, b) -> Integer.compare(a.population(), b.population()))
                                .map(s -> s.id() == settlement.id()).orElse(false),
                        faction.technology()),
                settlement.id() ^ faction.id());
    }

    public static CultureArchitectureProfile derive(
            double mercantile,
            double martial,
            double agrarian,
            double artistic,
            double education,
            double technology,
            double prosperity,
            SettlementGeographyProfile geo,
            SettlementSpecialization specialization,
            long tieSalt
    ) {
        geo = geo == null ? SettlementGeographyProfile.unknown() : geo;
        specialization = specialization == null ? SettlementSpecialization.MIXED : specialization;

        double[] scores = new double[8];
        // MEDIEVAL_FACHWERK
        scores[0] = 0.35 + agrarian * 0.25 + (1 - technology) * 0.2;
        // NORDIC_FORTRESS
        scores[1] = martial * 0.55 + coldBias(geo) * 0.45 + mountainBias(geo) * 0.25;
        // DESERT_COURTYARD
        scores[2] = aridBias(geo) * 0.7 + agrarian * 0.15;
        // FANTASY_MANOR
        scores[3] = artistic * 0.45 + prosperity * 0.25 + martial * 0.1;
        // MERCANTILE_TOWNHOUSE
        scores[4] = mercantile * 0.55 + (specialization == SettlementSpecialization.TRADE ? 0.35 : 0)
                + (specialization == SettlementSpecialization.INDUSTRIAL ? 0.15 : 0);
        // COASTAL_VILLA
        scores[5] = coastalBias(geo) * 0.65 + mercantile * 0.2
                + (specialization == SettlementSpecialization.NAVAL || specialization == SettlementSpecialization.FISHING ? 0.3 : 0);
        // TIMBER_PAVILION
        scores[6] = forestBias(geo) * 0.45 + agrarian * 0.35 + (1 - technology) * 0.15;
        // SCHOLAR_VILLA
        scores[7] = artistic * 0.35 + education * 0.45 + prosperity * 0.15
                + (specialization == SettlementSpecialization.ACADEMIC || specialization == SettlementSpecialization.RELIGIOUS ? 0.3 : 0);

        int best = 0;
        double bestScore = scores[0];
        for (int i = 1; i < scores.length; i++) {
            if (scores[i] > bestScore + 1e-9) {
                best = i;
                bestScore = scores[i];
            } else if (Math.abs(scores[i] - bestScore) <= 1e-9) {
                // Tie salt only — never factionId % 8 as the primary driver.
                int pick = Math.floorMod((int) (tieSalt ^ (i * 0x9E3779B9L)), 2);
                if (pick == 0) {
                    best = i;
                    bestScore = scores[i];
                }
            }
        }
        CultureArchitecture architecture = CultureArchitecture.fromStyleIndex(best);
        String rationale = "geo=" + biomeLabel(geo)
                + " merc=" + round(mercantile)
                + " martial=" + round(martial)
                + " agrarian=" + round(agrarian)
                + " art=" + round(artistic)
                + " edu=" + round(education)
                + " spec=" + specialization.wireName()
                + " -> " + architecture.name().toLowerCase(Locale.ROOT);
        return new CultureArchitectureProfile(architecture, rationale);
    }

    /** Resolve architecture for a construction intent (settlement binding first). */
    public static CultureArchitecture forIntent(ConstructionIntent intent) {
        Objects.requireNonNull(intent, "intent");
        CultureArchitecture bound = peek(intent.settlementId());
        if (bound != null) return bound;
        // Geography-unknown neutral traits — settlement salt breaks ties, not factionId%8 alone.
        return derive(0.5, 0.45, 0.5, 0.35, 0.28, 0.1, 0.5,
                SettlementGeographyProfile.unknown(), SettlementSpecialization.MIXED,
                intent.settlementId()).architecture();
    }

    private static double softMercantile(Faction faction, Settlement settlement) {
        return Math.min(1, 0.35 + faction.technology() * 0.25 + settlement.prosperity() * 0.35
                + (settlement.geography().shipSuitable() ? 0.2 : 0));
    }

    private static double softMartial(Faction faction, Settlement settlement) {
        return Math.min(1, 0.3 + settlement.publicOrder() * 0.25
                + (settlement.tier().ordinal() >= Settlement.Tier.TOWN.ordinal() ? 0.15 : 0)
                + mountainBias(settlement.geography()) * 0.2);
    }

    private static double softAgrarian(Faction faction, Settlement settlement) {
        return Math.min(1, 0.35 + settlement.geography().fertility() * 0.45 + (1 - faction.technology()) * 0.15);
    }

    private static double softArtistic(Faction faction, Settlement settlement) {
        return Math.min(1, 0.25 + settlement.prosperity() * 0.35 + faction.technology() * 0.2);
    }

    private static double coldBias(SettlementGeographyProfile geo) {
        String b = geo.biomeId() == null ? "" : geo.biomeId().toLowerCase(Locale.ROOT);
        if (b.contains("snow") || b.contains("ice") || b.contains("frozen") || b.contains("taiga") || b.contains("grove")) return 1;
        if (geo.elevation() >= 110) return 0.55;
        return 0;
    }

    private static double aridBias(SettlementGeographyProfile geo) {
        String b = geo.biomeId() == null ? "" : geo.biomeId().toLowerCase(Locale.ROOT);
        if (b.contains("desert") || b.contains("badland") || b.contains("savanna")) return 1;
        return geo.fertility() < 0.25 ? 0.55 : 0;
    }

    private static double coastalBias(SettlementGeographyProfile geo) {
        if (geo.shipSuitable()) return 1;
        if (geo.coastal()) return 0.75;
        String b = geo.biomeId() == null ? "" : geo.biomeId().toLowerCase(Locale.ROOT);
        if (b.contains("beach") || b.contains("ocean") || b.contains("stony_shore")) return 0.8;
        return 0;
    }

    private static double mountainBias(SettlementGeographyProfile geo) {
        String b = geo.biomeId() == null ? "" : geo.biomeId().toLowerCase(Locale.ROOT);
        if (b.contains("mountain") || b.contains("peak") || b.contains("windswept")) return 1;
        return geo.slope() >= 8 || geo.elevation() >= 110 ? 0.7 : 0;
    }

    private static double forestBias(SettlementGeographyProfile geo) {
        String b = geo.biomeId() == null ? "" : geo.biomeId().toLowerCase(Locale.ROOT);
        if (b.contains("forest") || b.contains("jungle") || b.contains("taiga") || b.contains("grove")) return 1;
        return geo.forest();
    }

    private static String biomeLabel(SettlementGeographyProfile geo) {
        if (geo.biomeId() != null && !geo.biomeId().isBlank()) return geo.biomeId();
        if (geo.shipSuitable()) return "coast";
        if (geo.riverSuitable()) return "river";
        if (geo.slope() >= 8) return "highland";
        return "temperate";
    }

    private static String round(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
