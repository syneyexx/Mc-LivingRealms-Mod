package dev.livingrealms;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.content.ArchitecturePaletteLoader;
import dev.livingrealms.sim.content.SettlementIdentityProfile;
import dev.livingrealms.sim.construction.CultureArchitecture;
import dev.livingrealms.sim.dialogue.DialogueContext;
import dev.livingrealms.sim.dialogue.DialogueIntent;
import dev.livingrealms.sim.dialogue.NaturalLanguageDialogueEngine;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SimulationState;

/** Wave 9 identity profile + Wave 15.1 architecture palette smoke; wired into dialogue. */
public final class SettlementIdentityProfileTest {
    private SettlementIdentityProfileTest() {}

    public static void main(String[] args) {
        ArchitecturePaletteLoader.clearCache();
        var palettes = ArchitecturePaletteLoader.loadAll();
        check(palettes.size() >= 8, "architecture palettes for culture families");
        check(ArchitecturePaletteLoader.forFamily(CultureArchitecture.NORDIC_FORTRESS).isPresent(),
                "nordic palette");
        check(ArchitecturePaletteLoader.forFamily(CultureArchitecture.SCHOLAR_VILLA).isPresent(),
                "scholar palette");

        SimulationState state = new SimulationState(0x1D3A71L);
        DemoSeeder.seed(state);
        var faction = state.factions().stream()
                .filter(f -> f.name().equals("Kingdom of Aster"))
                .findFirst().orElseThrow();
        var settlement = faction.settlements().getFirst();
        SettlementIdentityProfile identity = SettlementIdentityProfile.derive(state, faction, settlement);
        check("aster_riverborn".equals(identity.cultureId()), "aster culture id: " + identity.cultureId());
        check(identity.origin() == SettlementOrigin.AUTHORED_SEED, "authored origin");
        check(identity.foundationDay() == 0L, "authored foundation day 0");
        check(identity.architecturalEra() == CultureArchitecture.MEDIEVAL_FACHWERK,
                "aster architectural era: " + identity.architecturalEra());
        check(identity.morphology() != null, "morphology");
        check(identity.specialization() != null, "specialization");
        check(!identity.landmarkFocus().isBlank(), "landmark focus");

        SocialCitizen citizen = state.ensureSocialCitizen(faction.id(), settlement.id(), 0, CitizenRole.SCHOLAR);
        var engine = new NaturalLanguageDialogueEngine();
        var result = engine.respond(state, citizen, "player:test", "tell me about our culture", new DialogueContext());
        check(result.intent() == DialogueIntent.ASK_CULTURE, "culture intent: " + result.intent());
        String lower = result.response().toLowerCase();
        check(lower.contains("aster") || lower.contains("riverborn") || lower.contains("morph"),
                "identity should influence culture dialogue: " + result.response());
        check(lower.contains("landmark") || lower.contains("focus") || lower.contains("keep")
                        || lower.contains("square") || lower.contains("settlement"),
                "morphology/landmark wire in dialogue: " + result.response());

        System.out.println("PASS settlement identity profile: derived traits + architecture palettes + dialogue wire");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
