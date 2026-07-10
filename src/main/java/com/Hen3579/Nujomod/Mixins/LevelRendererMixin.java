package com.Hen3579.Nujomod.Mixins;

import com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LevelRenderer Mixin — 鸟瞰模式下关闭云、天气和天空渲染。
 *
 * <p>问题：鸟瞰视角相机高度通常在 y=100+，正好在云层（y=128）附近。
 * 原版云在正交投影下被渲染为半透明方块覆盖地形，严重影响视线。
 * 同时雨水/雷电粒子在鸟瞰视角下也会大量出现，干扰操作。
 *
 * <p>RoN 方案：RTS 鸟瞰模式下完全关闭云、天气和天空渲染，
 * 只保留地形和实体，保证清晰视野。
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    /**
     * 关闭云渲染。
     * 目标: LevelRenderer.renderClouds(PoseStack, Matrix4f, float, double, double, double)
     */
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void nujo$cancelClouds(
            PoseStack poseStack, Matrix4f projectionMatrix,
            float partialTick, double camX, double camY, double camZ,
            CallbackInfo ci) {
        if (BirdviewClientEvent.isBirdseyeActive()) {
            ci.cancel();
        }
    }

    /**
     * 关闭雨雪渲染。
     * 目标: LevelRenderer.renderSnowAndRain(LightTexture, float, double, double, double)
     */
    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void nujo$cancelWeather(
            LightTexture lightTexture, float partialTick,
            double camX, double camY, double camZ,
            CallbackInfo ci) {
        if (BirdviewClientEvent.isBirdseyeActive()) {
            ci.cancel();
        }
    }

    /**
     * 关闭天空渲染（可选，保留天空可以让背景色更自然）。
     * 如果用户觉得天空颜色干扰，可以取消注释。
     * 目标: LevelRenderer.renderSky(PoseStack, Matrix4f, float, Camera, boolean, Runnable)
     */
    // @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    // private void nujo$cancelSky(
    //         PoseStack poseStack, Matrix4f projectionMatrix,
    //         float partialTick, Camera camera,
    //         boolean isFoggy, Runnable skyFogSetup,
    //         CallbackInfo ci) {
    //     if (BirdviewClientEvent.isBirdseyeActive()) {
    //         ci.cancel();
    //     }
    // }
}
