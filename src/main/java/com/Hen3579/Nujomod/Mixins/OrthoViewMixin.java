package com.Hen3579.Nujomod.Mixins;

import com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent;
import com.Hen3579.Nujomod.Client.Events.OrthoviewClientEvent;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LevelRenderer Mixin — 替换透视投影为正交投影（RTS 风格）。
 *
 * 四重注入策略：
 * 1. @ModifyVariable(ordinal=0) 替换 renderLevel 的 projectionMatrix 参数
 *    → 影响实际渲染投影、Forge 事件分发
 * 2. @ModifyVariable(ordinal=0) 替换 prepareCullFrustum 的 projectionMatrix 参数
 *    → 使裁剪视锥体与正交渲染范围一致，消除近端三角形剔除缺口
 * 3. @Redirect 跳过 offsetToFullyIncludeCameraCube
 *    → 正交投影下 viewVector 被大幅缩放(>500x)，导致该方法的 for 循环永不终止
 * 4. @Inject at HEAD 覆盖 RenderSystem 的全局投影矩阵
 *    → 影响所有 shader 渲染（方块、实体、线条等）
 *
 * 参考 Reign of Nether 的 OrthoviewMixin 方案。
 */
@Mixin(LevelRenderer.class)
public class OrthoViewMixin {

    /**
     * 替换 renderLevel 方法的 projectionMatrix 参数。
     * 影响实际渲染投影、Frustum 裁剪体构建、RenderLevelStageEvent 分发的矩阵等。
     */
    @ModifyVariable(method = "renderLevel", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Matrix4f nujo$modifyProjectionMatrix(Matrix4f projectionMatrix) {
        if (BirdviewClientEvent.isBirdseyeActive()) {
            return OrthoviewClientEvent.getOrthoMatrix();
        }
        return projectionMatrix;
    }

    /**
     * 替换 prepareCullFrustum 的 projectionMatrix 参数。
     * GameRenderer.renderLevel() 在调用 LevelRenderer.renderLevel() 之前，
     * 会先用透视投影矩阵调用 prepareCullFrustum 构建裁剪视锥体。
     * 透视裁剪体近端窄、远端宽，与正交渲染的等宽可见范围不匹配，
     * 导致近端屏幕边缘的 chunk 被错误剔除，形成倒三角形缺口。
     * 此注入将裁剪用的投影矩阵同步替换为正交矩阵，消除缺口。
     */
    @ModifyVariable(method = "prepareCullFrustum", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Matrix4f nujo$modifyCullFrustumProjection(Matrix4f pProjectionMatrix) {
        if (BirdviewClientEvent.isBirdseyeActive()) {
            return OrthoviewClientEvent.getOrthoMatrix();
        }
        return pProjectionMatrix;
    }

    /**
     * 跳过 setupRender 中的 offsetToFullyIncludeCameraCube。
     * 正交投影矩阵的逆转置会大幅缩放 viewVector（~500x），
     * 导致 offsetToFullyIncludeCameraCube 的 for 循环每次移动 ~2000 格，
     * 目标 AABB 永远无法被 frustum 包含 → 无限循环。
     * 鸟瞰模式下相机离地 10+ 格，不需要此偏移来防止近处裁剪。
     */
    @Redirect(
        method = "setupRender",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/culling/Frustum;offsetToFullyIncludeCameraCube(I)Lnet/minecraft/client/renderer/culling/Frustum;"
        )
    )
    private Frustum nujo$skipOffsetToFullyIncludeCameraCube(Frustum frustum, int offset) {
        if (BirdviewClientEvent.isBirdseyeActive()) {
            return frustum;
        }
        return frustum.offsetToFullyIncludeCameraCube(offset);
    }

    /**
     * 覆盖 RenderSystem 的全局投影矩阵。
     * 影响所有 shader 渲染（方块、实体、线条等）。
     * renderLevel 内部不调用 setProjectionMatrix，所以此覆盖持续有效
     * 直到被 GameRenderer 中后续渲染步骤（粒子/天气/半透明）覆盖。
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