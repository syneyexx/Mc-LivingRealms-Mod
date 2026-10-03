package dev.livingrealms.sim.dialogue;
import java.util.Objects;
/** Short-lived conversation context; persistent facts belong in NPC memory, not here. */
public final class DialogueContext {
    private DialogueIntent lastIntent=DialogueIntent.UNKNOWN; private DialogueTopic lastTopic=DialogueTopic.NONE; private String lastSubject=""; private long lastDay=-1;
    public DialogueIntent lastIntent(){return lastIntent;} public DialogueTopic lastTopic(){return lastTopic;} public String lastSubject(){return lastSubject;} public long lastDay(){return lastDay;}
    public void update(DialogueIntent intent,DialogueTopic topic,String subject,long day){lastIntent=Objects.requireNonNull(intent);lastTopic=Objects.requireNonNull(topic);lastSubject=Objects.requireNonNullElse(subject,"");lastDay=day;}
}
