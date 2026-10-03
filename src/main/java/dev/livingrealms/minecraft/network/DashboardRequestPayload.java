package dev.livingrealms.minecraft.network;

import dev.livingrealms.LivingRealms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Zero-data client request for a fresh, server-authoritative strategic dashboard snapshot. */
public record DashboardRequestPayload() implements CustomPacketPayload {
    public static final DashboardRequestPayload INSTANCE = new DashboardRequestPayload();
    public static final Type<DashboardRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID, "dashboard_request")
    );
    public static final StreamCodec<ByteBuf, DashboardRequestPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
