package com.Hen3579.Nujomod.Story.network;

import com.Hen3579.Nujomod.Story.client.ClientStoryData;
import com.Hen3579.Nujomod.Story.model.DialogueDef;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S→C: 通知客户端打开对话 GUI。
 * <p>
 * 服务端将 DialogueDef 序列化为 JSON 字符串发送，
 * 客户端反序列化后打开 DialogueScreen。
 */
public class OpenDialoguePacket {

    private static final Gson GSON = new GsonBuilder().create();

    private final String dialogueJson;

    public OpenDialoguePacket(String dialogueJson) {
        this.dialogueJson = dialogueJson;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(dialogueJson);
    }

    public static OpenDialoguePacket decode(FriendlyByteBuf buf) {
        return new OpenDialoguePacket(buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 客户端处理：反序列化并打开对话 GUI
            DialogueDef dialogue = GSON.fromJson(dialogueJson, DialogueDef.class);
            ClientStoryData.openDialogue(dialogue);
        });
        ctx.get().setPacketHandled(true);
    }
}
