package com.Hen3579.Nujomod.Story.model;

/**
 * 条件定义 — 用于判断玩家当前状态是否满足触发要求。
 * <p>
 * 支持的 type：
 * <ul>
 *   <li>stage_equals    — 玩家当前阶段 == 指定 stage</li>
 *   <li>stage_not       — 玩家当前阶段 != 指定 stage</li>
 *   <li>has_mark        — 玩家拥有指定标记且星级 >= min_stars</li>
 *   <li>not_has_mark    — 玩家不拥有指定标记</li>
 *   <li>character_is    — 玩家选择的角色 == 指定角色</li>
 *   <li>has_item        — 玩家背包中有指定物品</li>
 *   <li>has_flag        — 玩家标志位为 true</li>
 *   <li>not_has_flag    — 玩家标志位为 false 或不存在</li>
 *   <li>and             — 所有子条件都满足</li>
 *   <li>or              — 任一子条件满足</li>
 *   <li>not             — 子条件不满足</li>
 * </ul>
 */
public class ConditionDef {

    /** 条件类型 */
    public String type;

    // --- stage_equals / stage_not ---
    public String stage;

    // --- has_mark / not_has_mark ---
    public String mark;
    public int min_stars = 1;

    // --- character_is ---
    public String character;

    // --- has_item ---
    public String item;
    public int count = 1;

    // --- has_flag / not_has_flag ---
    public String flag;

    // --- and / or ---
    public java.util.List<ConditionDef> conditions;

    // --- not ---
    public ConditionDef condition;

    public ConditionDef() {}

    public String getType() { return type; }
}
