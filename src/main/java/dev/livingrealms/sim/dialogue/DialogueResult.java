package dev.livingrealms.sim.dialogue;
import java.util.*;
public record DialogueResult(DialogueIntent intent,DialogueTopic topic,String response,List<DialogueAction> actions) {
    public DialogueResult { if(intent==null||topic==null||response==null)throw new IllegalArgumentException("dialogue result");actions=List.copyOf(actions==null?List.of():actions); }
}
