package com.Hen3579.Nujomod.Story;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家剧情进度存储 — 挂载在玩家身上的 Capability。
 * <p>
 * 存储内容：
 * <ul>
 *   <li>currentStage — 玩家当前所处的剧情阶段 id</li>
 *   <li>completedQuests — 已完成的一次性任务 id 集合</li>
 *   <li>marks — 标记系统（markId → 星级），如 菲尔斯(1), 明聪科技(1), 与王相识(5)</li>
 *   <li>character — 玩家选择的角色（actor / actress）</li>
 *   <li>flags — 通用布尔标志位，供 JSON 自定义使用</li>
 * </ul>
 */
public class StoryCapability {

    public static final String DEFAULT_STAGE = "none";

    private String currentStage = DEFAULT_STAGE;
    private final Set<String> completedQuests = new HashSet<>();
    private final Map<String, Integer> marks = new HashMap<>();
    private String character = "none";
    private final Map<String, Boolean> flags = new HashMap<>();

    // --- 阶段 ---

    public String getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(String stage) {
        this.currentStage = stage;
    }

    // --- 已完成任务 ---

    public boolean isQuestCompleted(String questId) {
        return completedQuests.contains(questId);
    }

    public void markQuestCompleted(String questId) {
        completedQuests.add(questId);
    }

    public Set<String> getCompletedQuests() {
        return completedQuests;
    }

    // --- 标记 ---

    public boolean hasMark(String markId) {
        return marks.containsKey(markId);
    }

    public int getMarkStars(String markId) {
        return marks.getOrDefault(markId, 0);
    }

    public void addMark(String markId, int stars) {
        marks.put(markId, stars);
    }

    public void removeMark(String markId) {
        marks.remove(markId);
    }

    public Map<String, Integer> getMarks() {
        return marks;
    }

    // --- 角色 ---

    public String getCharacter() {
        return character;
    }

    public void setCharacter(String character) {
        this.character = character;
    }

    // --- 标志位 ---

    public boolean hasFlag(String flagId) {
        return flags.getOrDefault(flagId, false);
    }

    public void setFlag(String flagId, boolean value) {
        flags.put(flagId, value);
    }

    public void clearFlag(String flagId) {
        flags.put(flagId, false);
    }

    public Map<String, Boolean> getFlags() {
        return flags;
    }

    // --- NBT 序列化 ---

    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("CurrentStage", currentStage);
        nbt.putString("Character", character);

        // 已完成任务
        ListTag questList = new ListTag();
        for (String questId : completedQuests) {
            questList.add(StringTag.valueOf(questId));
        }
        nbt.put("CompletedQuests", questList);

        // 标记
        CompoundTag marksNbt = new CompoundTag();
        for (Map.Entry<String, Integer> entry : marks.entrySet()) {
            marksNbt.putInt(entry.getKey(), entry.getValue());
        }
        nbt.put("Marks", marksNbt);

        // 标志位
        CompoundTag flagsNbt = new CompoundTag();
        for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
            flagsNbt.putBoolean(entry.getKey(), entry.getValue());
        }
        nbt.put("Flags", flagsNbt);

        return nbt;
    }

    public void deserializeNBT(CompoundTag nbt) {
        currentStage = nbt.getString("CurrentStage");
        character = nbt.getString("Character");

        completedQuests.clear();
        ListTag questList = nbt.getList("CompletedQuests", Tag.TAG_STRING);
        for (int i = 0; i < questList.size(); i++) {
            completedQuests.add(questList.getString(i));
        }

        marks.clear();
        CompoundTag marksNbt = nbt.getCompound("Marks");
        for (String key : marksNbt.getAllKeys()) {
            marks.put(key, marksNbt.getInt(key));
        }

        flags.clear();
        CompoundTag flagsNbt = nbt.getCompound("Flags");
        for (String key : flagsNbt.getAllKeys()) {
            flags.put(key, flagsNbt.getBoolean(key));
        }
    }

    /**
     * 从另一个实例拷贝数据（用于客户端同步）
     */
    public void copyFrom(StoryCapability other) {
        this.currentStage = other.currentStage;
        this.character = other.character;
        this.completedQuests.clear();
        this.completedQuests.addAll(other.completedQuests);
        this.marks.clear();
        this.marks.putAll(other.marks);
        this.flags.clear();
        this.flags.putAll(other.flags);
    }

    /**
     * 重置所有进度（调试用）
     */
    public void reset() {
        currentStage = DEFAULT_STAGE;
        completedQuests.clear();
        marks.clear();
        character = "none";
        flags.clear();
    }
}
