package com.Hen3579.Nujomod.Client.GameMode.Hud;

import com.Hen3579.Nujomod.Client.GameMode.GameModeManager;
import com.Hen3579.Nujomod.Client.GameMode.GameModeState;
import com.Hen3579.Nujomod.NujoBraincraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

/**
 * 战斗 HUD 渲染器（MOBA 风格）
 *
 * 布局参考 1920×1080 分辨率，所有位置使用屏幕百分比计算以适配不同分辨率。
 * 贴图按屏幕宽度比例缩放，保持相对大小一致。
 *
 * 层级（从底到顶）：
 *   1. 玩家血条/蓝条（屏幕底部居中，距底 ~11%）
 *   2. 技能槽 Z/X/C/V + 召唤师技能（屏幕底部居中，距底 ~4%）
 *   3. 模式指示器（右上角）
 */
public class CombatHudOverlay implements IGuiOverlay {
    public static final ResourceLocation ID = new ResourceLocation(NujoBraincraft.MODID, "combat_hud");

    // ===== 参考分辨率 =====
    private static final int REF_WIDTH = 1920;
    private static final int REF_HEIGHT = 1080;

    // ===== 贴图资源 =====
    private static final ResourceLocation HP_BAR_BG = rl("hp_bar_bg.png");
    private static final ResourceLocation HP_BAR_FILL = rl("hp_bar_fill.png");
    private static final ResourceLocation HP_BAR_BORDER = rl("hp_bar_border.png");
    private static final ResourceLocation HP_BAR_OVERLAY = rl("hp_bar_overlay.png");

    private static final ResourceLocation MP_BAR_BG = rl("mp_bar_bg.png");
    private static final ResourceLocation MP_BAR_FILL = rl("mp_bar_fill.png");
    private static final ResourceLocation MP_BAR_BORDER = rl("mp_bar_border.png");

    private static final ResourceLocation SKILL_SLOT_BG = rl("skill_slot_bg.png");
    private static final ResourceLocation SKILL_SLOT_BORDER = rl("skill_slot_border.png");
    private static final ResourceLocation SKILL_SLOT_BORDER_V = rl("skill_slot_border_v.png");

    private static final ResourceLocation SKILL_Z = rl("skill_z.png");
    private static final ResourceLocation SKILL_X = rl("skill_x.png");
    private static final ResourceLocation SKILL_C = rl("skill_c.png");
    private static final ResourceLocation SKILL_V = rl("skill_v.png");

    private static final ResourceLocation SUMMONER_SLOT_BG = rl("summoner_slot_bg.png");
    private static final ResourceLocation SUMMONER_SLOT_BORDER = rl("summoner_slot_border.png");
    private static final ResourceLocation SUMMONER_SKILL_FLASH = rl("summoner_skill_flash.png");
    private static final ResourceLocation SUMMONER_SKILL_HEAL = rl("summoner_skill_heal.png");

    // ===== 参考尺寸（1920×1080 下的像素尺寸） =====
    private static final int HP_BAR_W = 320, HP_BAR_H = 32;
    private static final int MP_BAR_W = 320, MP_BAR_H = 16;
    private static final int SLOT_SIZE = 72;
    private static final int SLOT_SPACING = 16;
    private static final int SKILL_ICON_SIZE = 64;
    private static final int SUMMONER_SIZE = 56;
    private static final int SUMMONER_SPACING = 16;
    private static final int SKILL_SUMMONER_GAP = 32;

    // ===== 布局参数 =====
    private static final float PLAYER_BAR_BOTTOM_RATIO = 0.111f;
    private static final int BAR_TO_SKILL_GAP = 12;

    /** MP 占比（占位值） */
    private static float mpPercent = 0.7f;

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        float alpha = GameModeManager.getFadeAlpha(GameModeState.COMBAT);
        if (alpha <= 0f) return;

        // 计算缩放因子（基于屏幕宽度与参考宽度的比例）
        float scale = screenWidth / (float) REF_WIDTH;

        // 应用全局透明度
        if (alpha < 1f) {
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        }

        // 绘制玩家状态栏（血条+蓝条），返回状态栏底部 Y 坐标
        int barBottomY = drawPlayerStatus(guiGraphics, mc, screenWidth, screenHeight, scale);

        // 绘制技能槽（在状态栏下方）
        drawSkillBar(guiGraphics, screenWidth, screenHeight, barBottomY, scale);

        // 绘制模式指示器
        drawModeIndicator(guiGraphics, screenWidth, screenHeight, scale);

        if (alpha < 1f) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    /**
     * 绘制玩家状态栏：名称+等级、HP血条、MP蓝条。
     * 使用 PoseStack 缩放，保持贴图比例。
     *
     * @return 状态栏底部 Y 坐标（用于技能槽定位）
     */
    private int drawPlayerStatus(GuiGraphics guiGraphics, Minecraft mc, int screenWidth, int screenHeight, float scale) {
        Player player = mc.player;

        int hpDrawW = (int) (HP_BAR_W * scale);
        int hpDrawH = (int) (HP_BAR_H * scale);
        int mpDrawW = (int) (MP_BAR_W * scale);
        int mpDrawH = (int) (MP_BAR_H * scale);

        int barX = (screenWidth - hpDrawW) / 2;
        int totalBarHeight = hpDrawH + mpDrawH;
        int barBottom = (int) (screenHeight * PLAYER_BAR_BOTTOM_RATIO);
        int hpY = screenHeight - barBottom - totalBarHeight;
        int mpY = hpY + hpDrawH;

        // 玩家名称 + 等级（血条上方，不缩放文字）
        String playerName = player.getDisplayName().getString();
        String levelText = "Lv." + player.experienceLevel;
        int nameY = hpY - mc.font.lineHeight - 2;
        guiGraphics.drawString(mc.font, playerName, barX, nameY, 0xFFFFFF);
        int levelX = barX + mc.font.width(playerName) + 8;
        guiGraphics.drawString(mc.font, levelText, levelX, nameY, 0xFFAA00);

        // HP 血条
        float hpPercent = player.getHealth() / player.getMaxHealth();
        drawBarScaled(guiGraphics, HP_BAR_BG, HP_BAR_FILL, HP_BAR_BORDER, HP_BAR_OVERLAY,
                barX, hpY, hpDrawW, hpDrawH, HP_BAR_W, HP_BAR_H, hpPercent);

        // HP 数值文字（居中，不缩放）
        String hpText = (int) player.getHealth() + " / " + (int) player.getMaxHealth();
        int hpTextX = barX + (hpDrawW - mc.font.width(hpText)) / 2;
        int hpTextY = hpY + (hpDrawH - mc.font.lineHeight) / 2;
        guiGraphics.drawString(mc.font, hpText, hpTextX, hpTextY, 0xFFFFFF);

        // MP 蓝条
        drawBarScaled(guiGraphics, MP_BAR_BG, MP_BAR_FILL, MP_BAR_BORDER, null,
                barX, mpY, mpDrawW, mpDrawH, MP_BAR_W, MP_BAR_H, mpPercent);

        // MP 数值文字
        String mpText = (int) (mpPercent * 100) + " / 100";
        int mpTextX = barX + (mpDrawW - mc.font.width(mpText)) / 2;
        int mpTextY = mpY + (mpDrawH - mc.font.lineHeight) / 2;
        guiGraphics.drawString(mc.font, mpText, mpTextX, mpTextY, 0xFFFFFF);

        return mpY + mpDrawH;
    }

    /**
     * 绘制技能槽：Z X C V + 召唤师技能 G H。
     * 使用 PoseStack 缩放。
     */
    private void drawSkillBar(GuiGraphics guiGraphics, int screenWidth, int screenHeight, int barBottomY, float scale) {
        Minecraft mc = Minecraft.getInstance();

        int slotDrawSize = (int) (SLOT_SIZE * scale);
        int slotSpacing = (int) (SLOT_SPACING * scale);
        int iconDrawSize = (int) (SKILL_ICON_SIZE * scale);
        int summonerDrawSize = (int) (SUMMONER_SIZE * scale);
        int summonerSpacing = (int) (SUMMONER_SPACING * scale);
        int skillSummonerGap = (int) (SKILL_SUMMONER_GAP * scale);
        int barToSkillGap = (int) (BAR_TO_SKILL_GAP * scale);

        int skillTotalWidth = 4 * slotDrawSize + 3 * slotSpacing;
        int summonerTotalWidth = 2 * summonerDrawSize + 1 * summonerSpacing;
        int totalWidth = skillTotalWidth + skillSummonerGap + summonerTotalWidth;
        int startX = (screenWidth - totalWidth) / 2;

        int skillY = barBottomY + barToSkillGap;
        int summonerY = skillY + (slotDrawSize - summonerDrawSize) / 2;

        PoseStack poseStack = guiGraphics.pose();

        // Z X C V 技能槽
        ResourceLocation[] skillIcons = {SKILL_Z, SKILL_X, SKILL_C, SKILL_V};
        String[] skillKeys = {"Z", "X", "C", "V"};

        for (int i = 0; i < 4; i++) {
            int slotX = startX + i * (slotDrawSize + slotSpacing);

            // 背景
            poseStack.pushPose();
            poseStack.translate(slotX, skillY, 0);
            poseStack.scale(scale, scale, 1);
            guiGraphics.blit(SKILL_SLOT_BG, 0, 0, 0, 0, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
            poseStack.popPose();

            // 技能图标
            int iconOffset = (slotDrawSize - iconDrawSize) / 2;
            poseStack.pushPose();
            poseStack.translate(slotX + iconOffset, skillY + iconOffset, 0);
            poseStack.scale(scale, scale, 1);
            guiGraphics.blit(skillIcons[i], 0, 0, 0, 0, SKILL_ICON_SIZE, SKILL_ICON_SIZE, SKILL_ICON_SIZE, SKILL_ICON_SIZE);
            poseStack.popPose();

            // 边框
            ResourceLocation border = (i == 3) ? SKILL_SLOT_BORDER_V : SKILL_SLOT_BORDER;
            poseStack.pushPose();
            poseStack.translate(slotX, skillY, 0);
            poseStack.scale(scale, scale, 1);
            guiGraphics.blit(border, 0, 0, 0, 0, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
            poseStack.popPose();

            // 快捷键标签（不缩放文字）
            guiGraphics.drawString(mc.font, skillKeys[i],
                    slotX + 3, skillY + slotDrawSize - mc.font.lineHeight - 1, 0xFFFFFF);
        }

        // 召唤师技能槽 G H
        int summonerStartX = startX + skillTotalWidth + skillSummonerGap;
        ResourceLocation[] summonerIcons = {SUMMONER_SKILL_FLASH, SUMMONER_SKILL_HEAL};
        String[] summonerKeys = {"G", "H"};

        for (int i = 0; i < 2; i++) {
            int slotX = summonerStartX + i * (summonerDrawSize + summonerSpacing);

            // 背景
            poseStack.pushPose();
            poseStack.translate(slotX, summonerY, 0);
            poseStack.scale(scale, scale, 1);
            guiGraphics.blit(SUMMONER_SLOT_BG, 0, 0, 0, 0, SUMMONER_SIZE, SUMMONER_SIZE, SUMMONER_SIZE, SUMMONER_SIZE);
            poseStack.popPose();

            // 图标
            poseStack.pushPose();
            poseStack.translate(slotX, summonerY, 0);
            poseStack.scale(scale, scale, 1);
            guiGraphics.blit(summonerIcons[i], 0, 0, 0, 0, SUMMONER_SIZE, SUMMONER_SIZE, SUMMONER_SIZE, SUMMONER_SIZE);
            poseStack.popPose();

            // 边框
            poseStack.pushPose();
            poseStack.translate(slotX, summonerY, 0);
            poseStack.scale(scale, scale, 1);
            guiGraphics.blit(SUMMONER_SLOT_BORDER, 0, 0, 0, 0, SUMMONER_SIZE, SUMMONER_SIZE, SUMMONER_SIZE, SUMMONER_SIZE);
            poseStack.popPose();

            // 快捷键标签
            guiGraphics.drawString(mc.font, summonerKeys[i],
                    slotX + 3, summonerY + summonerDrawSize - mc.font.lineHeight - 1, 0xFFFFFF);
        }
    }

    /**
     * 绘制缩放后的状态条：背景 → 填充 → 光泽 → 边框。
     * 使用 PoseStack 将纹理缩放到目标绘制尺寸。
     */
    private void drawBarScaled(GuiGraphics guiGraphics,
                               ResourceLocation bg, ResourceLocation fill,
                               ResourceLocation border, ResourceLocation overlay,
                               int x, int y, int drawW, int drawH,
                               int texW, int texH, float percent) {
        PoseStack poseStack = guiGraphics.pose();
        float scaleX = drawW / (float) texW;
        float scaleY = drawH / (float) texH;

        // 背景
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        poseStack.scale(scaleX, scaleY, 1);
        guiGraphics.blit(bg, 0, 0, 0, 0, texW, texH, texW, texH);
        poseStack.popPose();

        // 填充（按百分比从左侧裁剪）
        float clamped = Math.max(0f, Math.min(1f, percent));
        int fillTexWidth = (int) (texW * clamped);
        if (fillTexWidth > 0) {
            poseStack.pushPose();
            poseStack.translate(x, y, 0);
            poseStack.scale(scaleX, scaleY, 1);
            guiGraphics.blit(fill, 0, 0, 0, 0, fillTexWidth, texH, texW, texH);
            poseStack.popPose();
        }

        // 光泽覆盖层
        if (overlay != null) {
            poseStack.pushPose();
            poseStack.translate(x, y, 0);
            poseStack.scale(scaleX, scaleY, 1);
            guiGraphics.blit(overlay, 0, 0, 0, 0, texW, texH, texW, texH);
            poseStack.popPose();
        }

        // 边框
        if (border != null) {
            poseStack.pushPose();
            poseStack.translate(x, y, 0);
            poseStack.scale(scaleX, scaleY, 1);
            guiGraphics.blit(border, 0, 0, 0, 0, texW, texH, texW, texH);
            poseStack.popPose();
        }
    }

    private void drawModeIndicator(GuiGraphics guiGraphics, int screenWidth, int screenHeight, float scale) {
        Minecraft mc = Minecraft.getInstance();
        String text = "\u2694 \u6218\u6597\u6a21\u5f0f (B\u952e\u5207\u6362)";
        int textWidth = mc.font.width(text);
        int x = screenWidth - textWidth - 10;
        int y = 10;
        guiGraphics.drawString(mc.font, text, x, y, 0xFF5555);
    }

    // ===== 外部接口 =====

    public static void setMpPercent(float percent) {
        mpPercent = Math.max(0f, Math.min(1f, percent));
    }

    private static ResourceLocation rl(String name) {
        return new ResourceLocation(NujoBraincraft.MODID, "textures/gui/combat/" + name);
    }
}
