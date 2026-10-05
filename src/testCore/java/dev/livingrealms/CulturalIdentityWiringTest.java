package dev.livingrealms;

import dev.livingrealms.sim.content.CulturalNaming;
import dev.livingrealms.sim.content.CultureDefinition;
import dev.livingrealms.sim.content.CultureDefinitionRegistry;
import dev.livingrealms.sim.content.SettlementIdentityProfile;
import dev.livingrealms.sim.construction.CultureArchitecture;
import dev.livingrealms.sim.dialogue.DialogueContext;
import dev.livingrealms.sim.dialogue.DialogueIntent;
import dev.livingrealms.sim.dialogue.NaturalLanguageDialogueEngine;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SimulationState;
import java.util.HashSet;
import java.util.Set;

/** Wave 30 — culture packs wire into naming, architecture, dialogue dialect, and festival flavour. */
public final class CulturalIdentityWiringTest {
    private CulturalIdentityWiringTest() {}

    public static void main(String[] args) {
        namingStylesDiffer();
        architecturePinnedByCulture();
        dialogueMentionsCultureDialect();
        System.out.println("PASS CulturalIdentityWiring: naming + architecture + dialogue dialect");
    }

    private static void namingStylesDiffer() {
        CultureDefinition river = CultureDefinitionRegistry.require("aster_riverborn");
        CultureDefinition dwarven = CultureDefinitionRegistry.require("dravik_stoneward");
        CultureDefinition mercantile = CultureDefinitionRegistry.require("aurenthal_mercantile");
        Set<String> riverNames = new HashSet<>();
        Set<String> dwarvenNames = new HashSet<>();
        for (int i = 0; i < 24; i++) {
            riverNames.add(CulturalNaming.name(river, 0x111L + i).full());
            dwarvenNames.add(CulturalNaming.name(dwarven, 0x111L + i).full());
        }
        check(!riverNames.equals(dwarvenNames), "riverine vs forge_dwarven name pools differ");
        String gold = CulturalNaming.name(mercantile, 42L).family();
        check(gold.toLowerCase().contains("gold") || gold.toLowerCase().contains("coin")
                        || gold.toLowerCase().contains("gilt") || gold.toLowerCase().contains("vault")
                        || gold.toLowerCase().contains("market") || gold.toLowerCase().contains("silk")
                        || gold.toLowerCase().contains("prosper") || gold.toLowerCase().contains("aure"),
                "mercantile family flavour: " + gold);
    }

    private static void architecturePinnedByCulture() {
        SimulationState state = new SimulationState(0xC013001L);
        DemoSeeder.seed(state);
        Faction aster = state.factions().stream().filter(f -> f.name().equals("Kingdom of Aster")).findFirst().orElseThrow();
        var identity = SettlementIdentityProfile.derive(state, aster, aster.settlements().getFirst());
        check(identity.architecturalEra() == CultureArchitecture.MEDIEVAL_FACHWERK, "aster architecture");
        Faction dravik = state.factions().stream().filter(f -> f.name().contains("Dravik") || f.name().contains("Stone")).findFirst().orElse(null);
        if (dravik != null) {
            var dIdentity = SettlementIdentityProfile.derive(state, dravik, dravik.settlements().getFirst());
            check(dIdentity.architecturalEra() == CultureArchitecture.NORDIC_FORTRESS
                            || SettlementIdentityProfile.cultureOf(dravik).isPresent(),
                    "dravik culture present");
        }
    }

    private static void dialogueMentionsCultureDialect() {
        SimulationState state = new SimulationState(0xC013002L);
        DemoSeeder.seed(state);
        Faction aster = state.factions().stream().filter(f -> f.name().equals("Kingdom of Aster")).findFirst().orElseThrow();
        var settlement = aster.settlements().getFirst();
        SocialCitizen citizen = state.ensureSocialCitizen(aster.id(), settlement.id(), 0,
                dev.livingrealms.sim.civilian.CitizenRole.SCHOLAR);
        var engine = new NaturalLanguageDialogueEngine();
        var result = engine.respond(state, citizen, "player:culture", "tell me about our culture", new DialogueContext());
        check(result.intent() == DialogueIntent.ASK_CULTURE, "culture intent");
        String lower = result.response().toLowerCase();
        check(lower.contains("aster") || lower.contains("river") || lower.contains("riverborn"),
                "culture display in dialogue: " + result.response());
        check(lower.contains("dialect") || lower.contains("tongue") || lower.contains("speech")
                        || lower.contains("naming") || lower.contains("material") || lower.contains("fachwerk")
                        || lower.contains("medieval"),
                "dialect/naming/materials/architecture in dialogue: " + result.response());
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
