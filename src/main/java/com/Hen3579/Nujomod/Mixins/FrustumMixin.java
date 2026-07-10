package com.Hen3579.Nujomod.Mixins;

import com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Frustum Mixin — 鸟瞰视角下关闭视锥体剔除，防止地形被截断露出天空。
 *
 * <p>参考 Reign of Nether 的 FrustumMixin 方案：在鸟瞰/正交投影模式下，
 * 让 {@link Frustum#isVisible(AABB)} 总是返回 true，这样远处的 chunk 不会因为
 * 正交投影的 right/left 面截断而被剔除。</p>
 *
 * <p>性能影响：只在鸟瞰视角时生效，且只会尝试渲染渲染距离内的 chunk，
 * 超出 GPU 裁剪范围的内容会被硬件丢弃，不会显著影响性能。</p>
 */
@Mixin(Frustum.class)
public class FrustumMixin {

    /**
     * 鸟瞰视角下短路 Frustum 剔除。
     * 当 BirdviewClientEvent.isBirdseyeActive() 为 true 时，
     * 所有 AABB（包括 chunk 和实体）都视为可见。
     */
    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    private void nujo$skipFrustumCull(AABB aabb, CallbackInfoReturnable<Boolean> cir) {
        if (BirdviewClientEvent.isBirdseyeActive()) {
            cir.setReturnValue(true);
        }
    }
}
