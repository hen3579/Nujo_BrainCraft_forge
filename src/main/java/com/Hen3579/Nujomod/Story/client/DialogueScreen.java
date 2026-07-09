package com.Hen3579.Nujomod.Story.client;

import com.Hen3579.Nujomod.Story.model.DialogueDef;
import com.Hen3579.Nujomod.Story.model.DialogueNode;
import com.Hen3579.Nujomod.Story.model.DialogueOption;
import com.Hen3579.Nujomod.Story.network.StoryNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话 GUI — 客户端显示对话文本和选项。
 * <p>
 * 当玩家选择选项时：
 * 1. 发送 DialogueActionPacket 到服务端（执行选项关联的动作）
 * 2. 如果有 next 节点，切换到该节点
 * 3. 如果 next 为 null，关闭对话
 */
@OnlyIn(Dist.CLIENT)
public class DialogueScreen extends Screen {

    private final DialogueDef dialogue;
    private DialogueNode currentNode;

    // 布局常量
    private static final int DIALOGUE_WIDTH = 400;
    private static final int DIALOGUE_HEIGHT = 160;
    private static final int OPTION_BUTTON_HEIGHT = 20;
    private static final int OPTION_SPACING = 4;
    private static final int PADDING = 16;

    // 动画
    private int tickCount = 0;
    private int textRevealChars = 0;

    public DialogueScreen(DialogueDef dialogue) {
        super(Component.literal("Dialogue"));
        this.dialogue = dialogue;
        // 设置起始节点
        String startId = dialogue.getStartNode();
        if (startId != null) {
            this.currentNode = dialogue.getNode(startId);
        } else if (!dialogue.getNodes().isEmpty()) {
            this.currentNode = dialogue.getNodes().get(0);
        }
    }

    @Override
    protected void init() {
        super.init();
        tickCount = 0;
        textRevealChars = 0;
        rebuildButtons();
    }

    private void rebuildButtons() {
        // 清除旧按钮
        clearWidgets();

        if (currentNode == null) return;

        List<DialogueOption> options = currentNode.getOptions();
        if (options == null || options.isEmpty()) {
            // 没有选项，显示"继续"按钮
            String next = currentNode.getNext();
            if (next != null) {
                addRenderableWidget(Button.builder(
                        Component.translatable("story.nujobraincraft.dialogue.continue"),
                        btn -> goToNode(next)
                ).bounds(
                        width / 2 - 60,
                        height / 2 + DIALOGUE_HEIGHT / 2 - OPTION_BUTTON_HEIGHT - PADDING,
                        120, OPTION_BUTTON_HEIGHT
                ).build());
            } else {
                // 终点节点，显示"关闭"按钮
                addRenderableWidget(Button.builder(
                        Component.translatable("story.nujobraincraft.dialogue.close"),
                        btn -> onClose()
                ).bounds(
                        width / 2 - 60,
                        height / 2 + DIALOGUE_HEIGHT / 2 - OPTION_BUTTON_HEIGHT - PADDING,
                        120, OPTION_BUTTON_HEIGHT
                ).build());
            }
            return;
        }

        // 有选项，创建选项按钮
        int startY = height / 2 + DIALOGUE_HEIGHT / 2 - options.size() * (OPTION_BUTTON_HEIGHT + OPTION_SPACING) - PADDING;
        for (int i = 0; i < options.size(); i++) {
            final int optionIndex = i;
            final DialogueOption option = options.get(i);

            addRenderableWidget(Button.builder(
                    Component.translatable(option.getText()),
                    btn -> selectOption(optionIndex)
            ).bounds(
                    width / 2 - DIALOGUE_WIDTH / 2 + PADDING,
                    startY + i * (OPTION_BUTTON_HEIGHT + OPTION_SPACING),
                    DIALOGUE_WIDTH - PADDING * 2,
                    OPTION_BUTTON_HEIGHT
            ).build());
        }
    }

    /**
     * 玩家选择了一个选项
     */
    private void selectOption(int optionIndex) {
        if (currentNode == null || currentNode.getOptions() == null) return;
        if (optionIndex < 0 || optionIndex >= currentNode.getOptions().size()) return;

        DialogueOption option = currentNode.getOptions().get(optionIndex);

        // 发送到服务端执行动作
        StoryNetwork.sendDialogueAction(dialogue.getId(), currentNode.getId(), optionIndex);

        // 跳转到下一个节点
        if (option.getNext() != null) {
            goToNode(option.getNext());
        } else {
            // null = 关闭对话
            onClose();
        }
    }

    /**
     * 跳转到指定节点
     */
    private void goToNode(String nodeId) {
        DialogueNode nextNode = dialogue.getNode(nodeId);
        if (nextNode == null) {
            onClose();
            return;
        }
        currentNode = nextNode;
        textRevealChars = 0;
        rebuildButtons();
    }

    @Override
    public void tick() {
        super.tick();
        tickCount++;

        // 文本逐字显示效果
        if (currentNode != null && currentNode.getText() != null) {
            String fullText = currentNode.getText();
            // 使用翻译后的文本长度
            String translated = Component.translatable(fullText).getString();
            if (textRevealChars < translated.length()) {
                textRevealChars += 2; // 每 tick 显示 2 个字符
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 半透明背景遮罩
        graphics.fill(0, 0, width, height, 0x80000000);

        // 对话框背景
        int dialogueX = width / 2 - DIALOGUE_WIDTH / 2;
        int dialogueY = height / 2 - DIALOGUE_HEIGHT / 2;
        graphics.fill(dialogueX, dialogueY,
                dialogueX + DIALOGUE_WIDTH, dialogueY + DIALOGUE_HEIGHT,
                0xE0222222);
        // 边框
        graphics.renderOutline(dialogueX, dialogueY, DIALOGUE_WIDTH, DIALOGUE_HEIGHT, 0xFF888888);

        // 说话者名称
        if (dialogue.getSpeakerName() != null) {
            Component speakerName = Component.translatable(dialogue.getSpeakerName());
            graphics.drawString(font, speakerName,
                    dialogueX + PADDING, dialogueY + PADDING,
                    0xFFAAFFAA, false); // 绿色说话者名
        }

        // 对话文本（逐字显示）
        if (currentNode != null && currentNode.getText() != null) {
            String fullText = Component.translatable(currentNode.getText()).getString();
            String displayText = fullText.substring(0, Math.min(textRevealChars, fullText.length()));

            // 多行文本渲染
            List<Component> lines = new ArrayList<>();
            for (String line : wrapText(displayText, DIALOGUE_WIDTH - PADDING * 2)) {
                lines.add(Component.literal(line));
            }

            int textY = dialogueY + PADDING + 16;
            for (Component line : lines) {
                graphics.drawString(font, line,
                        dialogueX + PADDING, textY,
                        0xFFFFFFFF, false);
                textY += font.lineHeight + 2;
            }
        }

        // 渲染按钮
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * 文本自动换行 — 逐字符测量像素宽度，兼容中文/英文/混合文本。
     * 中文等 CJK 字符天然无需空格分词，英文会按字符断行（游戏中可接受）。
     */
    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }

        StringBuilder currentLine = new StringBuilder();
        int currentWidth = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // 显式换行符
            if (c == '\n') {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder();
                currentWidth = 0;
                continue;
            }

            String charStr = String.valueOf(c);
            int charWidth = font.width(charStr);

            // 当前行放不下 → 换行
            if (currentWidth + charWidth > maxWidth && currentLine.length() > 0) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder();
                currentWidth = 0;
            }

            currentLine.append(c);
            currentWidth += charWidth;
        }

        // 最后一行
        if (currentLine.length() > 0 || lines.isEmpty()) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ClientStoryData.clearCurrentDialogue();
        super.onClose();
    }
}
