package dev.livingrealms.minecraft.network;

import dev.livingrealms.LivingRealms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Creative-only request to obtain one registered item from the fixed target modpack. */
public record CreativeSpawnItemPayload(String itemId, int count) implements CustomPacketPayload {
    public static final Type<CreativeSpawnItemPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"creative_spawn_item"));
    public static final StreamCodec<ByteBuf,CreativeSpawnItemPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), CreativeSpawnItemPayload::itemId,
            ByteBufCodecs.VAR_INT, CreativeSpawnItemPayload::count,
            CreativeSpawnItemPayload::new
    );

    public CreativeSpawnItemPayload {
        if (itemId == null || itemId.isBlank() || itemId.length() > 256) throw new IllegalArgumentException("itemId");
        ResourceLocation.parse(itemId);
        if (count < 1 || count > 64) throw new IllegalArgumentException("count");
    }

    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
