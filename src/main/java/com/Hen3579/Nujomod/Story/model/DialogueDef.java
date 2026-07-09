package com.Hen3579.Nujomod.Story.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话定义 — 对应 data/nujobraincraft/story/dialogues/*.json
 * <p>
 * 一个对话由多个 DialogueNode 组成，构成一棵对话树。
 * 玩家从 start 节点开始，通过选项或线性 next 推进。
 */
public class DialogueDef {

    /** 唯一标识，如 "nujo_blank_communion" */
    public String id;

    /** 说话者 NPC id（用于显示头像/名称） */
    public String speaker;

    /** 说话者显示名称 lang key */
    public String speaker_name;

    /** 对话节点列表 */
    public List<DialogueNode> nodes = new ArrayList<>();

    /** 起始节点 id（默认为 nodes 列表第一个的 id） */
    public String start_node;

    public DialogueDef() {}

    public String getId() { return id; }
    public String getSpeaker() { return speaker; }
    public String getSpeakerName() { return speaker_name; }
    public List<DialogueNode> getNodes() { return nodes; }
    public String getStartNode() { return start_node; }

    /**
     * 根据 id 查找节点
     */
    public DialogueNode getNode(String nodeId) {
        if (nodes == null) return null;
        for (DialogueNode node : nodes) {
            if (node.id.equals(nodeId)) return node;
        }
        return null;
    }
}
