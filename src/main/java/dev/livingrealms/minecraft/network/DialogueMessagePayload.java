package dev.livingrealms.minecraft.network;

import dev.livingrealms.LivingRealms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client free-text message. Server resolves intent/context/knowledge; client never decides consequences. */
public record DialogueMessagePayload(long citizenId,String message) implements CustomPacketPayload {
    public static final Type<DialogueMessagePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"dialogue_message"));
    public static final StreamCodec<ByteBuf,DialogueMessagePayload> STREAM_CODEC=StreamCodec.composite(ByteBufCodecs.VAR_LONG,DialogueMessagePayload::citizenId,ByteBufCodecs.stringUtf8(512),DialogueMessagePayload::message,DialogueMessagePayload::new);
    public DialogueMessagePayload {if(citizenId<=0||message==null||message.isBlank()||message.length()>512)throw new IllegalArgumentException("dialogue message");}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
