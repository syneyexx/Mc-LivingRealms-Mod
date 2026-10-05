package dev.livingrealms.sim.dialogue;

import dev.livingrealms.sim.content.SettlementIdentityProfile;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/**
 * Deterministic no-LLM conversation engine. Free text is normalized into intents/topics/entities,
 * short context resolves follow-ups, and every factual answer is grounded in canonical state or
 * the individual citizen's bounded knowledge.
 *
 * <p>Answer bodies live in {@link DialogueAnswerCatalog} (Wave residual extraction).
 */
public final class NaturalLanguageDialogueEngine {
    private final DialogueInterpreter interpreter = new DialogueInterpreter();
    private final DialogueKnowledgeService knowledgeService = new DialogueKnowledgeService();
    private final DialoguePlanner planner = new DialoguePlanner();
    private final DialogueStyleProfile styleProfile = new DialogueStyleProfile();
    private final DialogueRealizer realizer = new DialogueRealizer();

    public DialogueResult respond(SimulationState state, SocialCitizen citizen, String playerKey, String input, DialogueContext context) {
        Objects.requireNonNull(state);
        Objects.requireNonNull(citizen);
        if (playerKey == null || playerKey.isBlank()) throw new IllegalArgumentException("playerKey");
        if (input == null) input = "";
        if (context == null) context = new DialogueContext();
        DialogueInterpreter.Interpreted interpreted = interpreter.interpret(input, context);
        Parsed parsed = new Parsed(interpreted.intent(), interpreted.topic(), interpreted.subject());
        long day = state.clock().day();
        Faction faction = state.findFaction(citizen.factionId()).orElse(null);
        Settlement settlement = state.findSettlement(citizen.settlementId()).orElse(null);
        if (faction == null || settlement == null) {
            return new DialogueResult(parsed.intent, parsed.topic, "I don't know where I belong anymore.", List.of());
        }
        List<DialogueAction> actions = new ArrayList<>();
        DialogueKnowledge knowledge = switch (parsed.intent) {
            case ASK_ARMY -> knowledgeService.army(state, citizen, faction, day);
            case ASK_POLITICS -> knowledgeService.politics(state, citizen, faction, day);
            case ASK_TECHNOLOGY -> knowledgeService.technology(state, citizen, faction, day);
            case ASK_FOOD -> knowledgeService.food(state, citizen, settlement, faction, day, actions);
            default -> null;
        };
        String legacyBody = knowledge != null ? null : switch (parsed.intent) {
            case GREET -> DialogueAnswerCatalog.compose(citizen, day, "greet",
                    List.of("Hello", "Good to see another face", "Greetings")) + ", traveler.";
            case ASK_SELF -> DialogueAnswerCatalog.answerSelf(state, citizen, settlement, faction, day);
            case ASK_AGE -> DialogueAnswerCatalog.answerAge(citizen, day);
            case ASK_JOB -> DialogueAnswerCatalog.answerJob(citizen, settlement, day);
            case ASK_FAMILY -> DialogueAnswerCatalog.answerFamily(state, citizen, day);
            case ASK_HEALTH -> DialogueAnswerCatalog.answerHealth(state, citizen, settlement, day);
            case ASK_RULER -> DialogueAnswerCatalog.answerRuler(state, citizen, faction, day);
            case ASK_DYNASTY -> DialogueAnswerCatalog.answerDynasty(state, citizen, faction, day);
            case ASK_SETTLEMENT -> DialogueAnswerCatalog.answerSettlement(state, citizen, settlement, faction, day);
            case ASK_FACTION -> DialogueAnswerCatalog.answerFaction(state, citizen, faction, day);
            case ASK_LAW -> DialogueAnswerCatalog.answerLaw(citizen, settlement, faction, day);
            case ASK_CRIME -> DialogueAnswerCatalog.answerCrime(state, citizen, settlement, faction, playerKey, parsed.subject, day);
            case ASK_TAX -> DialogueAnswerCatalog.answerTax(citizen, faction, day);
            case ASK_WATER -> DialogueAnswerCatalog.answerWater(state, citizen, settlement, faction, day);
            case ASK_RESOURCES -> DialogueAnswerCatalog.answerResources(state, citizen, settlement, faction, day);
            case ASK_TRADE -> DialogueAnswerCatalog.answerTradeState(citizen, settlement, faction, day);
            case ASK_PRICE -> DialogueAnswerCatalog.answerPrice(citizen, faction, settlement, parsed.subject, day);
            case ASK_SCHOOL -> DialogueAnswerCatalog.answerSchool(state, citizen, settlement, faction, day);
            case ASK_WAR -> DialogueAnswerCatalog.answerWar(state, citizen, faction, day, actions);
            case ASK_GUARDS -> DialogueAnswerCatalog.answerGuards(state, citizen, settlement, faction, day);
            case ASK_ROUTE -> DialogueAnswerCatalog.answerRoute(state, citizen, settlement, day, actions);
            case ASK_MIGRATION -> DialogueAnswerCatalog.answerMigration(state, citizen, settlement, faction, day);
            case ASK_CULTURE -> DialogueAnswerCatalog.answerCulture(state, citizen, faction, settlement, day);
            case ASK_RELIGION -> DialogueAnswerCatalog.answerReligion(state, citizen, faction, day);
            case ASK_HISTORY -> DialogueAnswerCatalog.answerHistory(state, citizen, faction, settlement, day);
            case ASK_RUMOR -> DialogueAnswerCatalog.answerRumor(citizen, day);
            case ASK_CALENDAR -> DialogueAnswerCatalog.answerCalendar(citizen, day);
            case ASK_DANGER -> DialogueAnswerCatalog.answerDanger(state, citizen, settlement, parsed.topic, day, actions);
            case ASK_DIRECTION -> DialogueAnswerCatalog.answerDirection(state, citizen, context, parsed.subject, settlement, day, actions);
            case ASK_SOURCE -> DialogueAnswerCatalog.answerSource(citizen, context, day);
            case ASK_RECENT_EVENT -> DialogueAnswerCatalog.answerRecent(state, citizen, settlement, day);
            case ASK_OPINION -> DialogueAnswerCatalog.answerOpinion(citizen, playerKey, day);
            case ASK_HELP -> DialogueAnswerCatalog.answerHelp(state, citizen, settlement, faction, day, actions);
            case TRADE -> {
                actions.add(new DialogueAction(DialogueActionType.OPEN_TRADE, "settlement:" + settlement.id(), settlement.position(), 0));
                yield DialogueAnswerCatalog.compose(citizen, day, "trade",
                        List.of("We can talk prices", "If you have coin or goods, we can trade", "Let's see what the market will bear")) + ".";
            }
            case GIVE_GIFT -> {
                actions.add(new DialogueAction(DialogueActionType.ACCEPT_GIFT, playerKey, null, 1));
                yield "If that is for me, hand it over and I will remember it.";
            }
            case COMPLIMENT -> DialogueAnswerCatalog.interaction(state, citizen, playerKey, MemoryType.CONVERSATION, .08, -.03, 1.5, "That's kind of you to say.", actions);
            case APOLOGIZE -> DialogueAnswerCatalog.interaction(state, citizen, playerKey, MemoryType.CONVERSATION, .04, -.08, .8, "I hear your apology. What happens next will matter more than the words.", actions);
            case REPORT_CRIME -> DialogueAnswerCatalog.reportInformation(state, citizen, playerKey, MemoryType.RUMOR, "reported-crime", "The player reported a crime nearby.", .72, actions, true);
            case REPORT_DANGER -> DialogueAnswerCatalog.reportInformation(state, citizen, playerKey, MemoryType.RUMOR, "reported-danger", "The player reported a nearby danger.", .72, actions, true);
            case PROVIDE_INFORMATION -> DialogueAnswerCatalog.reportInformation(state, citizen, playerKey, MemoryType.RUMOR, "player-information", DialogueAnswerCatalog.summarizeInput(input), .58, actions, false);
            case THREATEN -> DialogueAnswerCatalog.interaction(state, citizen, playerKey, MemoryType.THREAT, -.15, .22, -5.0, "Threats travel quickly here.", actions);
            case INSULT -> DialogueAnswerCatalog.interaction(state, citizen, playerKey, MemoryType.INSULT, -.09, .12, -2.0, "I won't forget the way you speak to people.", actions);
            case GOODBYE -> DialogueAnswerCatalog.compose(citizen, day, "bye", List.of("Safe travels", "Until next time", "Go carefully")) + ".";
            case UNKNOWN -> DialogueAnswerCatalog.unknown(citizen, day);
            case ASK_ARMY, ASK_POLITICS, ASK_TECHNOLOGY, ASK_FOOD -> "";
        };
        if (knowledge == null) knowledge = knowledgeService.wrapGrounded(legacyBody);
        DialogueResponsePlan plan = planner.plan(parsed.intent, knowledge, citizen, day);
        var culturePack = SettlementIdentityProfile.cultureOf(faction).orElse(null);
        plan = styleProfile.apply(plan, citizen, day, faction, culturePack);
        String response = realizer.realize(plan);
        citizen.remember(new CitizenMemory(day, MemoryType.CONVERSATION, playerKey, "self",
                DialogueAnswerCatalog.summarizeInput(input), settlement.position(), .12, 1));
        DialogueTopic topic = parsed.topic == DialogueTopic.NONE
                ? DialogueAnswerCatalog.inferTopic(parsed.intent) : parsed.topic;
        String subject = parsed.subject.isBlank()
                ? DialogueAnswerCatalog.subjectFor(topic) : parsed.subject;
        context.update(parsed.intent, topic, subject, day);
        return new DialogueResult(parsed.intent, topic, response, actions);
    }

    public Parsed parse(String raw, DialogueContext context) {
        DialogueInterpreter.Interpreted interpreted = interpreter.interpret(raw, context);
        return new Parsed(interpreted.intent(), interpreted.topic(), interpreted.subject());
    }

    public record Parsed(DialogueIntent intent, DialogueTopic topic, String subject) {}
}
