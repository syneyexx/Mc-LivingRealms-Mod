package dev.livingrealms.minecraft.network;

import dev.livingrealms.LivingRealms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client waypoint from dialogue MARK_LOCATION (or other server cues). */
public record WaypointPayload(String label, double x, double z, int ttlSeconds) implements CustomPacketPayload {
    public static final Type<WaypointPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID, "waypoint"));
    public static final StreamCodec<ByteBuf, WaypointPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(128), WaypointPayload::label,
            ByteBufCodecs.DOUBLE, WaypointPayload::x,
            ByteBufCodecs.DOUBLE, WaypointPayload::z,
            ByteBufCodecs.VAR_INT, WaypointPayload::ttlSeconds,
            WaypointPayload::new);

    public WaypointPayload {
        if (label == null || label.isBlank() || label.length() > 128 || !Double.isFinite(x) || !Double.isFinite(z) || ttlSeconds <= 0 || ttlSeconds > 86_400) {
            throw new IllegalArgumentException("waypoint");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
