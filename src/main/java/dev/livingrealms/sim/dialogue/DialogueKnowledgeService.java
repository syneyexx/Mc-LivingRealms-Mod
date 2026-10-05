package dev.livingrealms.sim.dialogue;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.civilization.FactionCivilizationState;
import dev.livingrealms.sim.faction.Army;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.society.WorldCauseExplainer;
import dev.livingrealms.sim.world.SimulationState;
import java.util.List;
import java.util.Objects;

/** Layer 2 — query canonical knowledge. Profession changes depth only; never fabricates facts. */
public final class DialogueKnowledgeService {
    public DialogueKnowledge wrapGrounded(String fact) {
        return DialogueKnowledge.grounded(Objects.requireNonNullElse(fact, ""));
    }

    public DialogueKnowledge army(SimulationState state, SocialCitizen c, Faction f, long day) {
        int personnel = f.armies().stream().mapToInt(Army::totalPersonnel).sum();
        double morale = f.armies().stream().mapToDouble(Army::morale).average().orElse(.5);
        if (!authority(c) && c.role() != CitizenRole.SCHOLAR) {
            if (personnel <= 0) return DialogueKnowledge.unknown("I don't know of a standing field army here");
            return DialogueKnowledge.limited(
                    "We have soldiers in the realm, but I don't know the exact strength",
                    "exact troop counts are official knowledge");
        }
        return DialogueKnowledge.grounded(
                "The realm currently has about " + personnel + " represented personnel across "
                        + f.armies().size() + " field formations. Their average morale is " + level(morale) + ".")
                .withConsequence("Poor morale will show on the roads first.");
    }

    public DialogueKnowledge politics(SimulationState state, SocialCitizen c, Faction f, long day) {
        if (!authority(c) && c.role() != CitizenRole.SCHOLAR) {
            return DialogueKnowledge.limited(
                    "I leave court politics to people with cleaner clothes than mine. " + f.rulerName() + " rules " + f.name() + ".",
                    "court detail is limited for common roles");
        }
        FactionCivilizationState civ = state.ensureFactionCivilization(f.id());
        return DialogueKnowledge.grounded(
                "The government is " + pretty(f.government().type().name()) + " under " + f.rulerName()
                        + ". Stability is " + level(f.government().stability())
                        + ", legitimacy " + level(f.government().legitimacy())
                        + ", and public propaganda pressure " + level(civ.propaganda()) + ".")
                .withOpinion("Court bargains matter as much as proclamations.");
    }

    public DialogueKnowledge technology(SimulationState state, SocialCitizen c, Faction f, long day) {
        FactionCivilizationState civ = state.ensureFactionCivilization(f.id());
        if (c.role() != CitizenRole.SCHOLAR && c.role() != CitizenRole.ARTISAN && c.role() != CitizenRole.OFFICIAL) {
            return DialogueKnowledge.limited(
                    "Scholars and craftspeople know more about that than I do",
                    "technical depth is limited outside scholar/artisan/official roles");
        }
        return DialogueKnowledge.grounded(
                "Our technical development is " + level(Math.min(1, f.technology()))
                        + " and education is " + level(civ.education())
                        + ". Schools, scholars, craftspeople and trade help knowledge spread.");
    }

    public DialogueKnowledge food(SimulationState state, SocialCitizen c, Settlement s, Faction f, long day, List<DialogueAction> actions) {
        if (actions != null) actions.add(new DialogueAction(DialogueActionType.MARK_LOCATION, "market:" + s.id(), s.position(), 0));
        double localFood = s.edibleStock();
        String why = WorldCauseExplainer.settlementPressureCause(state, f, s);
        if (s.foodSecurity() < .35 || localFood < s.population() * .8) {
            if (c.role() == CitizenRole.FARMER) {
                return DialogueKnowledge.grounded("The fields are not keeping us fed. " + why + " The granary is " + stockLevel(localFood) + ".")
                        .withOpinion("The fields decide more than speeches.");
            }
            return DialogueKnowledge.limited(
                    "Food is scarce. " + why + " Try the market in " + s.name() + ", but expect high prices.",
                    "granary ledgers are thinner for non-farmers");
        }
        DialogueKnowledge k = DialogueKnowledge.grounded(
                "Try the market here in " + s.name() + ". The granary looks " + stockLevel(localFood) + ". " + why);
        if (c.role() == CitizenRole.TRADER || c.role() == CitizenRole.OFFICIAL) {
            return k.withOpinion("Prices follow the granary.").withConsequence("Short stores push market prices up.");
        }
        if (c.role() == CitizenRole.FARMER) return k.withOpinion("The fields decide more than speeches.");
        return k;
    }

    private static boolean authority(SocialCitizen c) {
        return c.role() == CitizenRole.GUARD || c.role() == CitizenRole.OFFICIAL;
    }
    private static String level(double v) {
        return v >= .8 ? "very high" : v >= .62 ? "high" : v >= .42 ? "moderate" : v >= .22 ? "low" : "very low";
    }
    private static String stockLevel(double v) {
        return v >= 1000 ? "abundant" : v >= 250 ? "healthy" : v >= 60 ? "limited" : "scarce";
    }
    private static String pretty(String value) {
        String x = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return x.isBlank() ? x : Character.toUpperCase(x.charAt(0)) + x.substring(1);
    }
}
