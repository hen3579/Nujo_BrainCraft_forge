package com.Hen3579.Nujomod.Story;

import com.Hen3579.Nujomod.Story.model.*;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 剧情注册表 — 持有所有从 JSON 加载的剧情数据，提供查询 API。
 * <p>
 * 全局单例，服务端和客户端各持有一份。
 * 服务端的数据来自 StoryLoader，客户端的数据通过 StorySyncPacket 同步。
 */
public class StoryManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final StoryManager INSTANCE = new StoryManager();

    // 注册表
    private final Map<String, StageDef> stages = new LinkedHashMap<>();
    private final Map<String, DialogueDef> dialogues = new LinkedHashMap<>();
    private final Map<String, QuestDef> quests = new LinkedHashMap<>();
    private final Map<String, NpcDef> npcs = new LinkedHashMap<>();

    // 按 trigger type 索引的 quest 列表（加速事件匹配）
    private final Map<String, List<QuestDef>> questsByTriggerType = new HashMap<>();

    private StoryManager() {}

    // ===== 注册 =====

    public void registerStage(StageDef stage) {
        stages.put(stage.id, stage);
    }

    public void registerDialogue(DialogueDef dialogue) {
        dialogues.put(dialogue.id, dialogue);
    }

    public void registerQuest(QuestDef quest) {
        quests.put(quest.id, quest);
        if (quest.trigger != null && quest.trigger.type != null) {
            questsByTriggerType
                    .computeIfAbsent(quest.trigger.type, k -> new ArrayList<>())
                    .add(quest);
        }
    }

    public void registerNpc(NpcDef npc) {
        npcs.put(npc.id, npc);
    }

    // ===== 查询 =====

    public StageDef getStage(String id) {
        return stages.get(id);
    }

    public DialogueDef getDialogue(String id) {
        return dialogues.get(id);
    }

    public QuestDef getQuest(String id) {
        return quests.get(id);
    }

    public NpcDef getNpc(String id) {
        return npcs.get(id);
    }

    /**
     * 获取所有指定触发类型的 Quest
     */
    public List<QuestDef> getQuestsByTriggerType(String triggerType) {
        return questsByTriggerType.getOrDefault(triggerType, Collections.emptyList());
    }

    /**
     * 获取所有阶段，按 chapter_index 排序
     */
    public List<StageDef> getAllStagesSorted() {
        return stages.values().stream()
                .sorted(Comparator.comparingInt(s -> s.chapter_index))
                .collect(Collectors.toList());
    }

    /**
     * 获取所有 NPC 定义
     */
    public Collection<NpcDef> getAllNpcs() {
        return npcs.values();
    }

    // ===== 统计 =====

    public int getStageCount() { return stages.size(); }
    public int getDialogueCount() { return dialogues.size(); }
    public int getQuestCount() { return quests.size(); }
    public int getNpcCount() { return npcs.size(); }

    // ===== 管理 =====

    public void clear() {
        stages.clear();
        dialogues.clear();
        quests.clear();
        npcs.clear();
        questsByTriggerType.clear();
    }

    public void sortStages() {
        // LinkedHashMap 保持插入顺序，需要重建以按 chapter_index 排序
        Map<String, StageDef> sorted = stages.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getValue().chapter_index))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        stages.clear();
        stages.putAll(sorted);
    }

    // ===== JSON 序列化辅助（用于网络同步） =====

    /** 返回所有对话的 List 形式（供 Gson 序列化） */
    public List<DialogueDef> dialoguesToJsonList() {
        return new ArrayList<>(dialogues.values());
    }

    /** 返回所有任务的 List 形式 */
    public List<QuestDef> questsToJsonList() {
        return new ArrayList<>(quests.values());
    }

    /** 返回所有 NPC 的 List 形式 */
    public List<NpcDef> npcsToJsonList() {
        return new ArrayList<>(npcs.values());
    }

    /**
     * 从另一个 StoryManager 拷贝全部数据（用于客户端同步）
     */
    public void copyFrom(StoryManager other) {
        clear();
        stages.putAll(other.stages);
        dialogues.putAll(other.dialogues);
        quests.putAll(other.quests);
        npcs.putAll(other.npcs);
        // 重建 trigger 索引
        for (QuestDef quest : quests.values()) {
            if (quest.trigger != null && quest.trigger.type != null) {
                questsByTriggerType
                        .computeIfAbsent(quest.trigger.type, k -> new ArrayList<>())
                        .add(quest);
            }
        }
    }
}
