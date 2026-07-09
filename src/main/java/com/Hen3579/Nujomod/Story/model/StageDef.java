package com.Hen3579.Nujomod.Story.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 剧情阶段定义 — 对应 data/nujobraincraft/story/stages/*.json
 * <p>
 * 一个 stage 代表玩家在主线剧情中的一个进度节点。
 * 玩家同一时间只处于一个 stage，通过 quest 触发 change_stage 动作来推进。
 */
public class StageDef {

    /** 唯一标识，如 "ch1_anomaly" */
    public String id;

    /** 所属篇章，如 "main_world", "brain_world", "finale" */
    public String chapter;

    /** 篇章内序号，用于排序 */
    public int chapter_index;

    /** 显示名称 lang key，如 "story.nujobraincraft.ch1.title" */
    public String display_name;

    /** 描述文本 lang key */
    public String description;

    /** 进入此阶段时自动赋予的初始标记 { "mark_id": stars } */
    public java.util.Map<String, Integer> initial_marks;

    /** 进入此阶段时自动执行的动作列表 */
    public List<ActionDef> on_enter = new ArrayList<>();

    /** 离开此阶段时执行的动作列表 */
    public List<ActionDef> on_exit = new ArrayList<>();

    public StageDef() {}

    public String getId() { return id; }
    public String getChapter() { return chapter; }
    public int getChapterIndex() { return chapter_index; }
    public String getDisplayName() { return display_name; }
    public String getDescription() { return description; }
    public java.util.Map<String, Integer> getInitialMarks() { return initial_marks; }
    public List<ActionDef> getOnEnter() { return on_enter; }
    public List<ActionDef> getOnExit() { return on_exit; }
}
