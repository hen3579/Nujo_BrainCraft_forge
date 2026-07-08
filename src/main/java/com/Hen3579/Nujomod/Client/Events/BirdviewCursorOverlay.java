package com.Hen3579.Nujomod.Client.Events;

import com.Hen3579.Nujomod.NujoBraincraft;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * 鸟瞰模式 2D 叠层光标。
 * 参考 Reign of Nether 的 RTS 光标方案：
 * - GLFW_CURSOR_NORMAL：鼠标自由可见，直接在屏幕上移动
 * - 光标位置 = glfwGetCursorPos 直接读取（不再用虚拟光标追踪 delta）
 * - 光标纹理画在鼠标位置，热点与地面标记中心精确重合
 */
public class BirdviewCursorOverlay implements IGuiOverlay {
    public static final ResourceLocation ID = new ResourceLocation(NujoBraincraft.MODID, "birdview_cursor");

    private static final int CURSOR_SIZE = 24;
    private static final int LINE_WIDTH = 2;

    // 光标纹理（32x32 自定义箭头 / 16x16 自定义攻击图标）
    private static final ResourceLocation CURSOR_NORMAL      = new ResourceLocation(NujoBraincraft.MODID, "textures/gui/mouse/normal.png");
    private static final ResourceLocation CURSOR_CLICK       = new ResourceLocation(NujoBraincraft.MODID, "textures/gui/mouse/click.png");
    private static final ResourceLocation CURSOR_ATTACK      = new ResourceLocation(NujoBraincraft.MODID, "textures/gui/mouse/attack_hanging.png");
    private static final ResourceLocation CURSOR_ATTACK_CLICK = new ResourceLocation(NujoBraincraft.MODID, "textures/gui/mouse/attack_click.png");

    // 光标显示参数：光标热点 = 地面标记中心 = 同一个屏幕坐标
    // normal/click 纹理是箭头风格（热点在左上角），因此零偏移即可让热点和地面标记中心重合
    private static final int CURSOR_DISPLAY_SIZE = 16;  // 显示尺寸（GUI像素）
    private static final int CURSOR_OFFSET_X = 0;         // 水平无偏移
    private static final int CURSOR_OFFSET_Y = 0;         // 垂直无偏移

    // 跟踪上一帧的鸟瞰状态，用于在退出时还原光标
    private static boolean wasActive = false;

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

        // 进入鸟瞰 → GLFW_CURSOR_HIDDEN（隐藏系统光标但鼠标自由移动）
        // Reign of Nether 风格：鼠标自由移动到屏幕任何位置，自定义纹理替代系统光标
        if (!wasActive) {
            wasActive = true;
            GLFW.glfwSetInputMode(window.getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
        }

        // ===== 直接读取鼠标位置（不再用虚拟光标 delta 追踪） =====
        double mouseX = mc.mouseHandler.xpos();
        double mouseY = mc.mouseHandler.ypos();

        // 把物理像素坐标存为虚拟光标（供 screenPosToWorldPos / updateHoveredBlock 使用）
        BirdviewClientEvent.setVirtCursorPos(mouseX, mouseY);

        // ===== 光标纹理渲染 =====
        // 正交投影下，光标位置和地面标记天然对齐（线性映射，无透视畸变）
        // 光标热点 = 地面标记中心 = 同一个屏幕坐标
        double guiScale = window.getGuiScale();
        int cursorX = (int) (mouseX / guiScale) + CURSOR_OFFSET_X;
        int cursorY = (int) (mouseY / guiScale) + CURSOR_OFFSET_Y;

        HitResult hoverHit = BirdviewClientEvent.getHoveredHitResult();
        boolean hoveringAttackable = hoverHit != null && hoverHit.getType() == HitResult.Type.ENTITY;

        if (hoveringAttackable && mc.options.keyAttack.isDown()) {
            // 悬停可攻击实体 + 按住攻击键 → attack_click
            guiGraphics.blit(CURSOR_ATTACK_CLICK, cursorX, cursorY,
                    CURSOR_DISPLAY_SIZE, CURSOR_DISPLAY_SIZE,
                    0, 0, 16, 16, 16, 16);
        } else if (hoveringAttackable) {
            // 悬停可攻击实体（未按攻击键） → attack_hanging
            guiGraphics.blit(CURSOR_ATTACK, cursorX, cursorY,
                    CURSOR_DISPLAY_SIZE, CURSOR_DISPLAY_SIZE,
                    0, 0, 16, 16, 16, 16);
        } else if (mc.options.keyAttack.isDown()) {
            // 左键按下但未悬停实体 → 原点击态光标
            guiGraphics.blit(CURSOR_CLICK, cursorX, cursorY,
                    CURSOR_DISPLAY_SIZE, CURSOR_DISPLAY_SIZE,
                    0, 0, 32, 32, 32, 32);
        } else {
            // 默认态光标
            guiGraphics.blit(CURSOR_NORMAL, cursorX, cursorY,
                    CURSOR_DISPLAY_SIZE, CURSOR_DISPLAY_SIZE,
                    0, 0, 32, 32, 32, 32);
        }

        if (mc.player == null) return;

        // ===== 玩家头顶高亮ID（位置随滚轮缩放动态变化） =====
        double screenX = screenWidth / 2.0;
        // 镜头越近（zoom越小），ID越远离中心；镜头越远（zoom越大），ID越靠近中心
        double zoom = OrthoviewClientEvent.getZoom();
        double offsetRatio = (OrthoviewClientEvent.ZOOM_MAX - zoom) / (OrthoviewClientEvent.ZOOM_MAX - OrthoviewClientEvent.ZOOM_MIN);
        double maxOffset = -40.0; // 最近时偏上40px
        double screenY = screenHeight / 2.0 + maxOffset * offsetRatio;

        String idName = "✦ " + mc.player.getDisplayName().getString() + " ✦";
        int textWidth = mc.font.width("§b" + idName);
        int textX = (int) screenX - textWidth / 2;
        int textY = (int) screenY;
        // 背景框（半透明黑），上下边距相等
        int textH = (int) (mc.font.lineHeight * 0.85f); // 缩放后文字高度 ≈ 8
        int pad = 3;
        guiGraphics.fill(textX - 6, textY - pad, textX + textWidth + 6, textY + textH + pad, 0x80000000);
        // 青色文字（缩放 0.85x 略小）
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate((float) screenX, textY, 0);
        guiGraphics.pose().scale(0.85f, 0.85f, 1.0f);
        guiGraphics.drawString(mc.font, "§b" + idName, -textWidth / 2, 0, 0xFFFFFF, false);
        guiGraphics.pose().popPose();

        // ===== 弓蓄力进度条（在屏幕底部中央） =====
        if (LockTargetSystem.getRangedState() == LockTargetSystem.RangedState.BOW_CHARGING) {
            float progress = LockTargetSystem.getBowChargeProgress();
            int barWidth = 100;
            int barHeight = 6;
            int barX = (screenWidth - barWidth) / 2;
            int barY = screenHeight - 40;
            int fillWidth = (int) (barWidth * progress);

            // 背景
            guiGraphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF333333);
            // 进度填充（橙色渐变为金色）
            int fillColor;
            if (progress < 0.5f) {
                // 橙色 → 黄橙色
                fillColor = 0xFFFF8800;
            } else {
                // 黄橙色 → 金色
                fillColor = 0xFFFFCC00;
            }
            if (fillWidth > 0) {
                guiGraphics.fill(barX, barY, barX + fillWidth, barY + barHeight, fillColor);
            }
            // 文字："弓 蓄力 XX%"
            String chargeText = "§e弓蓄力 " + (int)(progress * 100) + "%";
            int chargeTextWidth = mc.font.width(chargeText);
            guiGraphics.drawString(mc.font, chargeText,
                    barX + (barWidth - chargeTextWidth) / 2, barY - 12, 0xFFFFFF, false);
        }
    }
}
