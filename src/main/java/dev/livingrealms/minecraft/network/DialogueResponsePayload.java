package dev.livingrealms.minecraft.network;

import dev.livingrealms.LivingRealms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-composed NPC response. */
public record DialogueResponsePayload(long citizenId,String response) implements CustomPacketPayload {
    public static final Type<DialogueResponsePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"dialogue_response"));
    public static final StreamCodec<ByteBuf,DialogueResponsePayload> STREAM_CODEC=StreamCodec.composite(ByteBufCodecs.VAR_LONG,DialogueResponsePayload::citizenId,ByteBufCodecs.stringUtf8(4096),DialogueResponsePayload::response,DialogueResponsePayload::new);
    public DialogueResponsePayload {if(citizenId<=0||response==null||response.isBlank()||response.length()>4096)throw new IllegalArgumentException("dialogue response");}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
