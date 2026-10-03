package dev.livingrealms.minecraft.network;

import dev.livingrealms.LivingRealms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Opens a bounded free-text dialogue with one server-authoritative social citizen. */
public record DialogueOpenPayload(long citizenId,String citizenName,String role,String greeting) implements CustomPacketPayload {
    public static final Type<DialogueOpenPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"dialogue_open"));
    public static final StreamCodec<ByteBuf,DialogueOpenPayload> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,DialogueOpenPayload::citizenId,
            ByteBufCodecs.stringUtf8(128),DialogueOpenPayload::citizenName,
            ByteBufCodecs.stringUtf8(32),DialogueOpenPayload::role,
            ByteBufCodecs.stringUtf8(2048),DialogueOpenPayload::greeting,
            DialogueOpenPayload::new);
    public DialogueOpenPayload {if(citizenId<=0||citizenName==null||citizenName.isBlank()||citizenName.length()>128||role==null||role.length()>32||greeting==null||greeting.length()>2048)throw new IllegalArgumentException("dialogue open");}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
