package com.Hen3579.Nujomod.Story.model;

import java.util.ArrayList;
import java.util.List;

/**
 * NPC 定义 — 对应 data/nujobraincraft/story/npcs/*.json
 * <p>
 * 描述剧情 NPC 的元数据：名称、实体类型、交互时触发的对话/任务等。
 * 实际的实体生成由 ActionExecutor.spawn_npc 执行。
 */
public class NpcDef {

    /** 唯一标识，如 "nujo", "brian", "darcy" */
    public String id;

    /** 实体类型注册 id，如 "nujobraincraft:nujo_thinker" */
    public String entity_type;

    /** 显示名称 lang key */
    public String display_name;

    /** 交互时默认打开的对话 id（可选） */
    public String default_dialogue;

    /** 交互时触发的任务 id 列表（可选，会检查条件） */
    public List<String> interact_quests = new ArrayList<>();

    /** NPC 头像贴图路径（客户端 GUI 用，可选） */
    public String portrait;

    public NpcDef() {}

    public String getId() { return id; }
    public String getEntityType() { return entity_type; }
    public String getDisplayName() { return display_name; }
    public String getDefaultDialogue() { return default_dialogue; }
    public List<String> getInteractQuests() { return interact_quests; }
    public String getPortrait() { return portrait; }
}
