package com.Hen3579.Nujomod.Story.network;

import com.Hen3579.Nujomod.NujoBraincraft;
import com.Hen3579.Nujomod.Story.StoryCapability;
import com.Hen3579.Nujomod.Story.StoryProgress;
import com.Hen3579.Nujomod.Story.model.DialogueDef;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 剧情系统网络通道
 * <p>
 * 注册的数据包：
 * <ul>
 *   <li>StorySyncPacket (S→C) — 同步玩家剧情进度到客户端</li>
 *   <li>OpenDialoguePacket (S→C) — 通知客户端打开对话 GUI（携带对话 JSON）</li>
 *   <li>DialogueActionPacket (C→S) — 玩家选择对话选项</li>
 *   <li>StoryDataSyncPacket (S→C) — 同步 StoryManager 注册表到客户端</li>
 * </ul>
 */
public class StoryNetwork {

    private static final String PROTOCOL_VERSION = "1";
    private static final Gson GSON = new GsonBuilder().create();

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(NujoBraincraft.MODID, "story"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    /** 注册所有数据包（在 FMLCommonSetupEvent 中调用） */
    public static void register() {
        INSTANCE.registerMessage(
                packetId++,
                StorySyncPacket.class,
                StorySyncPacket::encode,
                StorySyncPacket::decode,
                StorySyncPacket::handle
        );

        INSTANCE.registerMessage(
                packetId++,
                OpenDialoguePacket.class,
                OpenDialoguePacket::encode,
                OpenDialoguePacket::decode,
                OpenDialoguePacket::handle
        );

        INSTANCE.registerMessage(
                packetId++,
                DialogueActionPacket.class,
                DialogueActionPacket::encode,
                DialogueActionPacket::decode,
                DialogueActionPacket::handle
        );

        INSTANCE.registerMessage(
                packetId++,
                StoryDataSyncPacket.class,
                StoryDataSyncPacket::encode,
                StoryDataSyncPacket::decode,
                StoryDataSyncPacket::handle
        );
    }

    // ===== 发送辅助方法 =====

    /** S→C: 同步玩家剧情进度到客户端 */
    public static void syncCapabilityToClient(ServerPlayer player) {
        StoryCapability cap = StoryProgress.getOrNull(player);
        if (cap != null) {
            INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new StorySyncPacket(cap.serializeNBT()));
        }
    }

    /** S→C: 通知客户端打开对话 GUI */
    public static void sendDialogueToClient(ServerPlayer player, DialogueDef dialogue) {
        String json = GSON.toJson(dialogue);
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenDialoguePacket(json));
    }

    /** C→S: 玩家选择对话选项（由客户端调用） */
    public static void sendDialogueAction(String dialogueId, String nodeId, int optionIndex) {
        INSTANCE.sendToServer(new DialogueActionPacket(dialogueId, nodeId, optionIndex));
    }
}
