package com.Hen3579.Nujomod.Story.network;

import com.Hen3579.Nujomod.Story.ActionExecutor;
import com.Hen3579.Nujomod.Story.ConditionEvaluator;
import com.Hen3579.Nujomod.Story.StoryManager;
import com.Hen3579.Nujomod.Story.StoryProgress;
import com.Hen3579.Nujomod.Story.model.ActionDef;
import com.Hen3579.Nujomod.Story.model.DialogueDef;
import com.Hen3579.Nujomod.Story.model.DialogueNode;
import com.Hen3579.Nujomod.Story.model.DialogueOption;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S: 玩家在对话 GUI 中选择了一个选项。
 * <p>
 * 客户端发送 dialogueId + 当前 nodeId + 选项索引，
 * 服务端执行选项关联的动作，并决定下一步。
 */
public class DialogueActionPacket {

    private final String dialogueId;
    private final String nodeId;
    private final int optionIndex;

    public DialogueActionPacket(String dialogueId, String nodeId, int optionIndex) {
        this.dialogueId = dialogueId;
        this.nodeId = nodeId;
        this.optionIndex = optionIndex;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(dialogueId);
        buf.writeUtf(nodeId);
        buf.writeInt(optionIndex);
    }

    public static DialogueActionPacket decode(FriendlyByteBuf buf) {
        return new DialogueActionPacket(buf.readUtf(), buf.readUtf(), buf.readInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            DialogueDef dialogue = StoryManager.INSTANCE.getDialogue(dialogueId);
            if (dialogue == null) return;

            DialogueNode node = dialogue.getNode(nodeId);
            if (node == null || node.options == null || optionIndex < 0 || optionIndex >= node.options.size()) {
                return;
            }

            DialogueOption option = node.options.get(optionIndex);

            // 执行选项关联的动作
            if (option.action != null) {
                ActionExecutor.execute(option.action, player);
            }

            // 如果有 next，服务端发送下一个节点给客户端
            // 如果 next 为 null，客户端自行关闭对话
            // （客户端在选选项时已经处理了 UI 切换，这里只处理服务端动作）

            // 同步进度（以防动作修改了阶段/标记）
            StoryNetwork.syncCapabilityToClient(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
