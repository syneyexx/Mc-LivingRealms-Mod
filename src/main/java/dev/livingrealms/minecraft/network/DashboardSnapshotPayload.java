package dev.livingrealms.minecraft.network;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.sim.ui.RealmDashboardCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client immutable dashboard JSON. The content is produced only by RealmDashboardBuilder. */
public record DashboardSnapshotPayload(String json) implements CustomPacketPayload {
    public static final Type<DashboardSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID, "dashboard_snapshot")
    );
    public static final StreamCodec<ByteBuf, DashboardSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(RealmDashboardCodec.MAX_JSON_CHARS),
            DashboardSnapshotPayload::json,
            DashboardSnapshotPayload::new
    );

    public DashboardSnapshotPayload {
        if (json == null || json.isBlank() || json.length() > RealmDashboardCodec.MAX_JSON_CHARS) {
            throw new IllegalArgumentException("json");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
