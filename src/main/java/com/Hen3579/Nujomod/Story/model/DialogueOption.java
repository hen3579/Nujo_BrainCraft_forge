package com.Hen3579.Nujomod.Story.model;

/**
 * 对话选项 — 玩家在对话 GUI 中可以点击的选项。
 * <p>
 * 点击后跳转到 next 指定的节点，并可选执行一个动作。
 */
public class DialogueOption {

    /** 选项文本 lang key */
    public String text;

    /** 跳转目标节点 id，null 表示关闭对话 */
    public String next;

    /** 选择此选项时执行的动作（可选） */
    public ActionDef action;

    public DialogueOption() {}

    public String getText() { return text; }
    public String getNext() { return next; }
    public ActionDef getAction() { return action; }
}
