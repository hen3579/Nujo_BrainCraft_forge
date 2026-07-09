package com.Hen3579.Nujomod.Story.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务定义 — 对应 data/nujobraincraft/story/quests/*.json
 * <p>
 * 一个 Quest 是"触发条件 + 前置条件 + 执行动作"的完整单元。
 * 当 trigger 匹配且 conditions 全部满足时，执行 actions。
 * <p>
 * 如果 once=true，则该 Quest 只会触发一次（完成后记录到 Capability）。
 */
public class QuestDef {

    /** 唯一标识，如 "enter_brain_world" */
    public String id;

    /** 触发器 */
    public TriggerDef trigger;

    /** 前置条件列表（全部满足才可触发） */
    public List<ConditionDef> conditions = new ArrayList<>();

    /** 触发后执行的动作列表 */
    public List<ActionDef> actions = new ArrayList<>();

    /** 是否只触发一次 */
    public boolean once = true;

    public QuestDef() {}

    public String getId() { return id; }
    public TriggerDef getTrigger() { return trigger; }
    public List<ConditionDef> getConditions() { return conditions; }
    public List<ActionDef> getActions() { return actions; }
    public boolean isOnce() { return once; }
}
