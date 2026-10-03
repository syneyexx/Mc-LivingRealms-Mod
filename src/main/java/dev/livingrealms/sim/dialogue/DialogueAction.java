package dev.livingrealms.sim.dialogue;
import dev.livingrealms.sim.world.SimPosition;
import java.util.Objects;
public record DialogueAction(DialogueActionType type,String subject,SimPosition location,double magnitude) {
    public DialogueAction { if(type==null)throw new IllegalArgumentException("type");subject=Objects.requireNonNullElse(subject,"");if(!Double.isFinite(magnitude))throw new IllegalArgumentException("magnitude"); }
    public static DialogueAction none(){return new DialogueAction(DialogueActionType.NONE,"",null,0);}
}
