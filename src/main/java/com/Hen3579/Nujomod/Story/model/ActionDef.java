package com.Hen3579.Nujomod.Story.model;

/**
 * 动作定义 — 剧情引擎中最核心的数据单元。
 * <p>
 * 一个 ActionDef 描述"做什么"，由 ActionExecutor 在服务端执行。
 * type 字段决定如何解析其余字段。
 * <p>
 * 支持的 type：
 * <ul>
 *   <li>teleport        — 传送玩家到指定坐标/维度</li>
 *   <li>spawn_npc       — 在指定位置生成 NPC</li>
 *   <li>dialogue        — 打开对话 GUI</li>
 *   <li>give_item       — 给予玩家物品</li>
 *   <li>change_stage    — 切换玩家剧情阶段</li>
 *   <li>add_mark        — 添加标记</li>
 *   <li>remove_mark     — 移除标记</li>
 *   <li>message         — 发送聊天消息</li>
 *   <li>play_sound      — 播放音效</li>
 *   <li>delay           — 延迟执行子动作</li>
 *   <li>set_flag        — 设置自定义标志位</li>
 *   <li>clear_flag      — 清除自定义标志位</li>
 *   <li>run_function    — 运行 mcfunction</li>
 * </ul>
 */
public class ActionDef {

    /** 动作类型 */
    public String type;

    // --- teleport ---
    public String dimension;    // 如 "minecraft:overworld"
    public double x, y, z;

    // --- spawn_npc ---
    public String npc;          // NPC 定义 id

    // --- dialogue ---
    public String id;           // 对话定义 id（也用于 change_stage 的 stage id）

    // --- give_item ---
    public String item;         // 物品注册 id
    public int count = 1;

    // --- add_mark ---
    public String mark;         // 标记 id
    public int stars = 1;

    // --- message ---
    public String text;         // lang key 或纯文本
    public String color;        // 颜色名称，如 "gold", "red"

    // --- play_sound ---
    public String sound;        // 音效注册 id

    // --- delay ---
    public int ticks;           // 延迟 tick 数
    public java.util.List<ActionDef> then; // 延迟后执行的动作

    // --- set_flag / clear_flag ---
    public String flag;         // 标志位 key

    // --- run_function ---
    public String function;     // 函数路径，如 "nujobraincraft:story/ch1_intro"

    public ActionDef() {}

    public String getType() { return type; }
}
