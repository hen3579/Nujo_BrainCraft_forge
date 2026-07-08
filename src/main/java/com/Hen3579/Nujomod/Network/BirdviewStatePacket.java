package com.Hen3579.Nujomod.Network;

import com.Hen3579.Nujomod.Server.BirdviewServerState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S 网络包：客户端通知服务器鸟瞰模式开关状态
 *
 * 客户端按 F5 切换到/退出鸟瞰模式时发送此包，
 * 服务器据此更新 BirdviewServerState 中对应玩家的状态。
 */
public class BirdviewStatePacket {

    private final boolean active;

    public BirdviewStatePacket(boolean active) {
        this.active = active;
    }

    public static void encode(BirdviewStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
    }

    public static BirdviewStatePacket decode(FriendlyByteBuf buf) {
        return new BirdviewStatePacket(buf.readBoolean());
    }

    public static void handle(BirdviewStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                BirdviewServerState.setBirdviewActive(player.getUUID(), msg.active);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public boolean isActive() {
        return active;
    }
}
