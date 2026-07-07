package com.Hen3579.Nujomod.Client.Events;

import com.Hen3579.Nujomod.NujoBraincraft;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.lwjgl.glfw.GLFW;

/**
 * 鸟瞰模式 2D 叠层光标。
 * 在渲染帧（60 FPS）中更新虚拟光标位置，使用自己的 lastMouseX/lastMouseY
 * 跟踪帧间真实鼠标 delta（不受 turnPlayer 重置影响），实现平滑大范围移动。
 * 参考 Reign of Nether 的 RTS 光标方案。
 */
public class BirdviewCursorOverlay implements IGuiOverlay {
    public static final ResourceLocation ID = new ResourceLocation(NujoBraincraft.MODID, "birdview_cursor");

    private static final int CURSOR_SIZE = 24;
    private static final int LINE_WIDTH = 2;

    // 跟踪上一帧的鸟瞰状态，用于在退出时还原光标
    private static boolean wasActive = false;

    // 自跟踪鼠标位置，用于计算帧间真实 delta（不受 turnPlayer 重置影响）
    private static double lastMouseX = 0;
    private static double lastMouseY = 0;

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;

        Window window = mc.getWindow();
        boolean active = BirdviewClientEvent.isBirdseyeActive();

        // 退出鸟瞰 → 恢复系统光标为 DISABLED（第一人称状态）+ 清除虚拟光标
        if (!active) {
            if (wasActive) {
                GLFW.glfwSetInputMode(window.getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                BirdviewClientEvent.clearVirtCursor();
                wasActive = false;
            }
            return;
        }

        // 进入鸟瞰 → GLFW_CURSOR_DISABLED + 重置虚拟光标 + 初始化 lastMouse
        if (!wasActive) {
            wasActive = true;
            GLFW.glfwSetInputMode(window.getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
            double physCenterX = window.getWidth() / 2.0;
            double physCenterY = window.getHeight() / 2.0;
            BirdviewClientEvent.resetVirtCursorToCenter(physCenterX, physCenterY);
            // 初始化跟踪位置为当前鼠标位置（首次 delta = 0）
            lastMouseX = mc.mouseHandler.xpos();
            lastMouseY = mc.mouseHandler.ypos();
        }

        // ===== 每帧用自跟踪 delta 更新虚拟光标（60 FPS 平滑） =====
        double currentX = mc.mouseHandler.xpos();
        double currentY = mc.mouseHandler.ypos();
        double dx = currentX - lastMouseX;
        double dy = currentY - lastMouseY;
        lastMouseX = currentX;
        lastMouseY = currentY;

        double[] virt = BirdviewClientEvent.getVirtCursorPos();
        if (virt != null) {
            double newX = Math.max(0, Math.min(window.getWidth(), virt[0] + dx));
            double newY = Math.max(0, Math.min(window.getHeight(), virt[1] + dy));
            BirdviewClientEvent.setVirtCursorPos(newX, newY);
        }

        if (mc.player == null) return;

        // ===== 玩家头顶高亮ID（固定在屏幕居中偏上位置） =====
        double screenX = screenWidth / 2.0;
        double screenY = screenHeight / 2.0 - 50;

        String idName = "✦ " + mc.player.getDisplayName().getString() + " ✦";
        int textWidth = mc.font.width("§b" + idName);
        int textX = (int) screenX - textWidth / 2;
        int textY = (int) screenY;
        // 背景框（半透明黑）
        guiGraphics.fill(textX - 6, textY - 3, textX + textWidth + 6, textY + 12, 0x80000000);
        // 青色文字（缩放 0.85x 略小）
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate((float) screenX, textY, 0);
        guiGraphics.pose().scale(0.85f, 0.85f, 1.0f);
        guiGraphics.drawString(mc.font, "§b" + idName, -textWidth / 2, 0, 0xFFFFFF, false);
        guiGraphics.pose().popPose();
    }
}