package com.Hen3579.Nujomod.Story;

import com.Hen3579.Nujomod.Story.model.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.Map;

/**
 * 剧情 JSON 加载器 — 在服务器资源重载时读取所有剧情数据文件。
 * <p>
 * 扫描 data/&lt;modid&gt;/story/ 下的所有子目录：
 * <ul>
 *   <li>story/stages/*.json   → StageDef</li>
 *   <li>story/dialogues/*.json → DialogueDef</li>
 *   <li>story/quests/*.json   → QuestDef</li>
 *   <li>story/npcs/*.json     → NpcDef</li>
 * </ul>
 * 解析后注册到 StoryManager。
 * <p>
 * 使用 /reload 命令或 StoryLoader.reload() 可热重载所有剧情数据。
 */
public class StoryLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final StoryLoader INSTANCE = new StoryLoader();

    private StoryLoader() {
        super(GSON, "story");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager rm, ProfilerFiller profiler) {
        // 清空旧数据
        StoryManager.INSTANCE.clear();

        int count = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation rl = entry.getKey();
            String path = rl.getPath(); // 如 "stages/ch1_anomaly"
            String[] parts = path.split("/", 2);
            if (parts.length < 2) {
                LOGGER.warn("[StoryLoader] 跳过不在子目录中的文件: {}", rl);
                continue;
            }

            String category = parts[0]; // "stages", "dialogues", "quests", "npcs"
            try {
                switch (category) {
                    case "stages" -> {
                        StageDef stage = GSON.fromJson(entry.getValue(), StageDef.class);
                        if (stage != null && stage.id != null) {
                            StoryManager.INSTANCE.registerStage(stage);
                            count++;
                        }
                    }
                    case "dialogues" -> {
                        DialogueDef dialogue = GSON.fromJson(entry.getValue(), DialogueDef.class);
                        if (dialogue != null && dialogue.id != null) {
                            StoryManager.INSTANCE.registerDialogue(dialogue);
                            count++;
                        }
                    }
                    case "quests" -> {
                        QuestDef quest = GSON.fromJson(entry.getValue(), QuestDef.class);
                        if (quest != null && quest.id != null) {
                            StoryManager.INSTANCE.registerQuest(quest);
                            count++;
                        }
                    }
                    case "npcs" -> {
                        NpcDef npc = GSON.fromJson(entry.getValue(), NpcDef.class);
                        if (npc != null && npc.id != null) {
                            StoryManager.INSTANCE.registerNpc(npc);
                            count++;
                        }
                    }
                    default ->
                        LOGGER.warn("[StoryLoader] 未知的剧情数据类别: {} (文件: {})", category, rl);
                }
            } catch (Exception e) {
                LOGGER.error("[StoryLoader] 解析剧情文件失败: {}", rl, e);
            }
        }

        // 按 chapter_index 排序阶段
        StoryManager.INSTANCE.sortStages();

        LOGGER.info("[StoryLoader] 剧情数据加载完成: {} 个条目 (stages={}, dialogues={}, quests={}, npcs={})",
                count,
                StoryManager.INSTANCE.getStageCount(),
                StoryManager.INSTANCE.getDialogueCount(),
                StoryManager.INSTANCE.getQuestCount(),
                StoryManager.INSTANCE.getNpcCount());
    }
}
