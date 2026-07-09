package com.Hen3579.Nujomod.Story.client;

import com.Hen3579.Nujomod.Story.StoryCapability;
import com.Hen3579.Nujomod.Story.model.DialogueDef;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 客户端剧情数据缓存
 * <p>
 * 接收服务端同步的数据并在客户端使用：
 * <ul>
 *   <li>capabilityData — 玩家进度的本地副本（用于 GUI 显示）</li>
 *   <li>当前打开的对话</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class ClientStoryData {

    /** 客户端缓存的玩家剧情进度 */
    private static StoryCapability localCapability = new StoryCapability();

    /** 当前正在显示的对话 */
    private static DialogueDef currentDialogue = null;

    /**
     * 接收服务端同步的进度数据
     */
    public static void updateCapability(CompoundTag nbt) {
        localCapability = new StoryCapability();
        localCapability.deserializeNBT(nbt);
    }

    /**
     * 获取客户端缓存的进度
     */
    public static StoryCapability getLocalCapability() {
        return localCapability;
    }

    /**
     * 接收服务端发来的对话，打开对话 GUI
     */
    public static void openDialogue(DialogueDef dialogue) {
        currentDialogue = dialogue;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            DialogueScreen screen = new DialogueScreen(dialogue);
            mc.setScreen(screen);
        });
    }

    /**
     * 获取当前对话
     */
    public static DialogueDef getCurrentDialogue() {
        return currentDialogue;
    }

    /**
     * 清除当前对话（关闭 GUI 时调用）
     */
    public static void clearCurrentDialogue() {
        currentDialogue = null;
    }
}
