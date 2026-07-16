package com.Hen3579.Nujomod.Mixins;

import com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LevelRenderer Mixin — 鸟瞰模式下的渲染优化与修复。
 *
 * 包含以下功能：
 * 1. 取消原版云和天气渲染（白色方块遮挡 + 粒子干扰）
 * 2. 跳过 updateRenderChunks 中的高级裁剪（ray marching），
 *    防止地面层 chunk 被错误裁剪导致"穿透"
 * 3. 每帧强制更新视锥体，确保 renderChunksInFrustum 始终包含
 *    当前相机位置下的所有可见 chunk（参考 RoN 方案）
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Shadow
    private AtomicBoolean needsFrustumUpdate;

    /**
     * 鸟瞰模式下取消云渲染。
     * 原版云在正交投影下显示为白色半透明方块，严重遮挡地形视线。
     */
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void nujo$cancelCloudsInBirdview(PoseStack poseStack, Matrix4f projectionMatrix,
                                              float partialTick, double camX, double camY, double camZ,
                                              CallbackInfo ci) {
        if (BirdviewClientEvent.isBirdseyeActive()) {
            ci.cancel();
        }
    }

    /**
     * 鸟瞰模式下取消雨雪渲染。
     * 雨雪粒子在 RTS 视角下会干扰精细操作，且正交投影中粒子效果不佳。
     */
    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void nujo$cancelWeatherInBirdview(LightTexture lightTexture, float partialTick,
                                               double camX, double camY, double camZ,
                                               CallbackInfo ci) {
        if (BirdviewClientEvent.isBirdseyeActive()) {
            ci.cancel();
        }
    }

    // ========================================================================
    //  修复 1：跳过 updateRenderChunks 中的高级裁剪（ray marching）
    // ========================================================================

    /**
     * 鸟瞰模式下强制关闭 BFS 遍历中的高级裁剪。
     *
     * <p>问题根源：{@link LevelRenderer#updateRenderChunks} 在 BFS 遍历 chunk 时，
     * 对距相机超过 60 格的 chunk 执行 ray marching 可见性检测（第 984-1062 行）。
     * 鸟瞰相机在 Y=77 时，相机对齐到 16 格边界后 blockpos.getY()=64，
     * 所有地面层 chunk（Y=0）的 |0-64|=64 > 60 → 触发高级裁剪。
     * 而 ray marching 的采样点要求 chunk 已在 pInfoMap 中（BFS 未到达的部分），
     * 导致地面层 chunk 被错误跳过，显示为"穿透"。
     *
     * <p>修复方案：将 {@code pShouldCull} 参数强制设为 {@code false}，
     * 跳过整个高级裁剪块（包括 facesCanSeeEachOther 和 ray marching），
     * 让所有 chunk 直接进入 BFS 队列。FrustumMixin 已关闭视锥体裁剪，
     * 因此跳过 BFS 裁剪是安全的。
     *
     * <p>注意：不要配合强制同步编译使用——进入鸟瞰时 setBlocksDirty
     * 标记了约 6,144 个 section，若全部同步编译会导致渲染线程阻塞数秒，
     * 触发 Java "未响应" 对话框。异步编译延迟 1-2 帧显示完全可接受。
     */
    @ModifyVariable(
        method = "updateRenderChunks",
        at = @At("HEAD"),
        ordinal = 0,
        argsOnly = true
    )
    private boolean nujo$disableRayMarchingCullingInBirdview(boolean pShouldCull) {
        if (BirdviewClientEvent.isBirdseyeActive()) {
            return false;
        }
        return pShouldCull;
    }

    // ========================================================================
    //  修复 2：每帧强制更新视锥体（参考 RoN 方案）
    // ========================================================================

    /**
     * 鸟瞰模式下每帧强制更新视锥体，消除地形边缘截断。
     *
     * <p>问题根源：{@link LevelRenderer#setupRender} 中，{@code applyFrustum}
     * 仅在 {@code needsFrustumUpdate} 为 true 或相机旋转变化时调用（第 891 行）。
     * 鸟瞰模式下相机旋转固定（135° yaw, 45° pitch），而 {@code needsFrustumUpdate}
     * 仅在后台 BFS 任务完成时设为 true（第 866 行）。BFS 每 8 格移动才触发一次
     * （约 27 帧/0.5 秒），导致大部分帧里 {@code renderChunksInFrustum} 列表过时，
     * 新进入视口的 chunk 不在列表中 → 不被编译 → 显示为黑色虚空。
     *
     * <p>修复方案：在 {@code setupRender} 头部强制设 {@code needsFrustumUpdate = true}，
     * 确保每帧都调用 {@code applyFrustum}。由于 {@link FrustumMixin} 已使
     * {@code isVisible} 始终返回 true，{@code applyFrustum} 会将所有已加载 chunk
     * 加入 {@code renderChunksInFrustum}，编译后即可渲染。
     *
     * <p>参考：Reign of Nether (RoN) 在 {@code LevelRendererMixin.setupRender} 中
     * 同样强制 {@code needsFrustumUpdate = true}。
     */
    @Inject(method = "setupRender", at = @At("HEAD"))
    private void nujo$forceFrustumUpdateInBirdview(Camera pCamera, Frustum pFrustum,
                                                    boolean pHasCapturedFrustum, boolean pIsSpectator,
                                                    CallbackInfo ci) {
        if (BirdviewClientEvent.isBirdseyeActive()) {
            this.needsFrustumUpdate.set(true);
        }
    }
}