package com.Hen3579.Nujomod.Story.model;

/**
 * 触发器定义 — 描述什么游戏事件会触发这个 Quest。
 * <p>
 * 支持的 type：
 * <ul>
 *   <li>dimension_change — 玩家进入指定维度（to 字段为目标维度 id）</li>
 *   <li>interact_npc     — 玩家右键交互指定 NPC（npc 字段为 NPC id）</li>
 *   <li>deliver_item     — 玩家向指定 NPC 交付物品（npc + item）</li>
 *   <li>kill_entity      — 玩家击杀指定实体（entity_type 字段）</li>
 *   <li>kill_boss        — 玩家击杀指定 BOSS（boss 字段为 boss id）</li>
 *   <li>enter_area       — 玩家进入指定区域（需 Mixin 或 tick 检测）</li>
 *   <li>custom           — 自定义触发（通过代码调用 triggerCustom）</li>
 * </ul>
 */
public class TriggerDef {

    /** 触发类型 */
    public String type;

    // --- dimension_change ---
    /** 目标维度 id（from 为空表示从任意维度进入） */
    public String from;
    public String to;

    // --- interact_npc / deliver_item ---
    public String npc;

    // --- deliver_item ---
    public String item;
    public int count = 1;

    // --- kill_entity / kill_boss ---
    public String entity_type;
    public String boss;

    // --- enter_area ---
    public String area_id;
    public double x, y, z;
    public double radius;

    // --- custom ---
    public String custom_id;

    public TriggerDef() {}

    public String getType() { return type; }
}
