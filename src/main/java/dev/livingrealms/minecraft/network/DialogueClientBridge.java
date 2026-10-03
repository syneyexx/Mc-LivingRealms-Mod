package dev.livingrealms.minecraft.network;

import java.util.Objects;
import java.util.function.Consumer;

/** Common-side client indirection so networking registration remains dedicated-server safe. */
public final class DialogueClientBridge {
    public sealed interface Event permits Open,Response {}
    public record Open(DialogueOpenPayload payload) implements Event {}
    public record Response(DialogueResponsePayload payload) implements Event {}
    private static volatile Consumer<Event> receiver=ignored->{};
    private DialogueClientBridge(){}
    public static void install(Consumer<Event> value){receiver=Objects.requireNonNull(value);} public static void open(DialogueOpenPayload value){receiver.accept(new Open(value));} public static void response(DialogueResponsePayload value){receiver.accept(new Response(value));} public static void reset(){receiver=ignored->{};}
}
