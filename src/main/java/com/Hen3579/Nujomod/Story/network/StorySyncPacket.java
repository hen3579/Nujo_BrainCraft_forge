package com.Hen3579.Nujomod.Story.network;

import com.Hen3579.Nujomod.Story.StoryCapability;
import com.Hen3579.Nujomod.Story.StoryCapabilityProvider;
import com.Hen3579.Nujomod.Story.client.ClientStoryData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S→C: 同步玩家剧情进度到客户端。
 * <p>
 * 服务端发送 NBT，客户端读取后更新本地 Capability 副本。
 */
public class StorySyncPacket {

    private final CompoundTag data;

    public StorySyncPacket(CompoundTag data) {
        this.data = data;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(data);
    }

    public static StorySyncPacket decode(FriendlyByteBuf buf) {
        CompoundTag nbt = buf.readNbt();
        return new StorySyncPacket(nbt != null ? nbt : new CompoundTag());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 客户端处理：更新本地 Capability 副本
            ClientStoryData.updateCapability(data);
        });
        ctx.get().setPacketHandled(true);
    }
}
