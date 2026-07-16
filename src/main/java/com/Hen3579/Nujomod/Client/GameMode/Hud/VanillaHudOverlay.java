package com.Hen3579.Nujomod.Client.GameMode.Hud;

import com.Hen3579.Nujomod.Client.GameMode.GameModeManager;
import com.Hen3579.Nujomod.Client.GameMode.GameModeState;
import com.Hen3579.Nujomod.NujoBraincraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class VanillaHudOverlay implements IGuiOverlay {
    public static final ResourceLocation ID = new ResourceLocation(NujoBraincraft.MODID, "vanilla_hud");

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // 获取淡入淡出透明度
        float alpha = GameModeManager.getFadeAlpha(GameModeState.VANILLA);
        if (alpha <= 0f) return;

        // 应用透明度（淡入淡出动画）
        if (alpha < 1f) {
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        }

        drawModeIndicator(guiGraphics, screenWidth, screenHeight);

        // 重置透明度
        if (alpha < 1f) {
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    private void drawModeIndicator(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        String text = "原版模式 (B键切换)";
        int textWidth = mc.font.width(text);
        int x = screenWidth - textWidth - 10;
        int y = 10;
        guiGraphics.drawString(mc.font, text, x, y, 0xAAAAAA);
    }
}