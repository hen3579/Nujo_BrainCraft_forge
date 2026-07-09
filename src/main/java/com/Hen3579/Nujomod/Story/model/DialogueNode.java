package com.Hen3579.Nujomod.Story.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话节点 — 对话树中的一个文本节点。
 * <p>
 * 节点可以有一条线性后继（next），也可以有多条选项（options）。
 * 如果 next 和 options 都为空/null，则该节点是对话的终点。
 */
public class DialogueNode {

    /** 节点 id，在同一个对话内唯一 */
    public String id;

    /** 文本内容 lang key，如 "story.nujobraincraft.nujo_intro.start" */
    public String text;

    /** 线性后继节点 id（与 options 互斥） */
    public String next;

    /** 选项列表（与 next 互斥） */
    public List<DialogueOption> options = new ArrayList<>();

    /** 进入此节点时执行的动作（在文本显示之前执行） */
    public List<ActionDef> on_enter = new ArrayList<>();

    public DialogueNode() {}

    public String getId() { return id; }
    public String getText() { return text; }
    public String getNext() { return next; }
    public List<DialogueOption> getOptions() { return options; }
    public List<ActionDef> getOnEnter() { return on_enter; }
}
