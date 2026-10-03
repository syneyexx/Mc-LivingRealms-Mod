package dev.livingrealms.minecraft.network;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.sim.ui.DashboardActionCommand;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client intent only. Every action is revalidated against canonical server state. */
public record DashboardActionPayload(String command) implements CustomPacketPayload {
    public static final Type<DashboardActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"dashboard_action"));
    public static final StreamCodec<ByteBuf,DashboardActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), DashboardActionPayload::command, DashboardActionPayload::new
    );

    public DashboardActionPayload {
        DashboardActionCommand.parse(command);
    }

    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
