package com.Hen3579.Nujomod.Mixins;

import com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent;
import com.Hen3579.Nujomod.Client.Events.OrthoviewClientEvent;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LevelRenderer Mixin — 替换透视投影为正交投影（RTS 风格）。
 *
 * 双重注入策略：
 * 1. @ModifyVariable 替换 renderLevel 的 projectionMatrix 参数
 *    → 影响 Frustum 裁剪、Forge 事件分发等所有内部使用
 * 2. @Inject at HEAD 覆盖 RenderSystem 的全局投影矩阵
 *    → 影响所有 shader 渲染（方块、实体、线条等）
 *
 * 参考 Reign of Nether 的 OrthoviewMixin 方案。
 */
@Mixin(LevelRenderer.class)
public class OrthoViewMixin {

    /**
     * 替换 renderLevel 方法的 projectionMatrix 参数。
     * 影响 Frustum 裁剪、RenderLevelStageEvent 分发的矩阵等内部使用。
     */
    @ModifyVariable(method = "renderLevel", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Matrix4f nujo$modifyProjectionMatrix(Matrix4f projectionMatrix) {
        if (BirdviewClientEvent.isBirdseyeActive()) {
            return OrthoviewClientEvent.getOrthoMatrix();
        }
        return projectionMatrix;
    }

    /**
     * 覆盖 RenderSystem 的全局投影矩阵。
     * 影响所有 shader 渲染（方块、实体、线条等）。
     * 因为 renderLevel 内部不调用 setProjectionMatrix，所以此覆盖持续有效。
     */
    @Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
    private void nujo$onRenderLevelHead(CallbackInfo ci) {
        if (BirdviewClientEvent.isBirdseyeActive()) {
            RenderSystem.setProjectionMatrix(
                    OrthoviewClientEvent.getOrthoMatrix(),
                    VertexSorting.byDistance(0.0f, 0.0f, 0.0f)
            );
        }
    }
}