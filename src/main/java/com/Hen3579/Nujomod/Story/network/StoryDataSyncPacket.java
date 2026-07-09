package com.Hen3579.Nujomod.Story.network;

import com.Hen3579.Nujomod.Story.StoryManager;
import com.Hen3579.Nujomod.Story.model.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Supplier;

/**
 * S→C: 同步 StoryManager 注册表到客户端。
 * <p>
 * 在玩家登录时发送，让客户端也持有完整的剧情注册表。
 * 客户端可以用它来查询对话内容、NPC 信息等。
 */
public class StoryDataSyncPacket {

    private static final Gson GSON = new GsonBuilder().create();

    private final String stagesJson;
    private final String dialoguesJson;
    private final String questsJson;
    private final String npcsJson;

    public StoryDataSyncPacket(String stages, String dialogues, String quests, String npcs) {
        this.stagesJson = stages;
        this.dialoguesJson = dialogues;
        this.questsJson = quests;
        this.npcsJson = npcs;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(stagesJson);
        buf.writeUtf(dialoguesJson);
        buf.writeUtf(questsJson);
        buf.writeUtf(npcsJson);
    }

    public static StoryDataSyncPacket decode(FriendlyByteBuf buf) {
        return new StoryDataSyncPacket(
                buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf()
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            StoryManager.INSTANCE.clear();

            Type stageListType = new TypeToken<List<StageDef>>(){}.getType();
            List<StageDef> stages = GSON.fromJson(stagesJson, stageListType);
            if (stages != null) stages.forEach(StoryManager.INSTANCE::registerStage);

            Type dialogueListType = new TypeToken<List<DialogueDef>>(){}.getType();
            List<DialogueDef> dialogues = GSON.fromJson(dialoguesJson, dialogueListType);
            if (dialogues != null) dialogues.forEach(StoryManager.INSTANCE::registerDialogue);

            Type questListType = new TypeToken<List<QuestDef>>(){}.getType();
            List<QuestDef> quests = GSON.fromJson(questsJson, questListType);
            if (quests != null) quests.forEach(StoryManager.INSTANCE::registerQuest);

            Type npcListType = new TypeToken<List<NpcDef>>(){}.getType();
            List<NpcDef> npcs = GSON.fromJson(npcsJson, npcListType);
            if (npcs != null) npcs.forEach(StoryManager.INSTANCE::registerNpc);
        });
        ctx.get().setPacketHandled(true);
    }
}
