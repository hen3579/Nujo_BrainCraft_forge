package com.Hen3579.Nujomod.Mixins;

import com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 鸟瞰模式下关闭视锥体剔除（Frustum Culling）。
 *
 * 正交投影与透视投影的 Frustum 计算方式不同，导致使用正交投影时
 * 视锥体范围与渲染范围不匹配，屏幕边缘的 chunk 被错误剔除。
 *
 * 参考 Reign of Nether 的 Frustum 短路方案：
 * 在鸟瞰模式下让 isVisible 始终返回 true，关闭所有视锥体裁剪，
 * 确保所有可见范围内的 chunk 和实体都能被渲染。
 */
@Mixin(Frustum.class)
public class FrustumMixin {

    /**
     * 鸟瞰模式下让 isVisible 永远返回 true。
     * 关闭视锥体剔除，防止屏幕边缘的 chunk 被错误裁剪。
     */
    @Inject(method = "isVisible(Lnet/minecraft/world/phys/AABB;)Z", at = @At("HEAD"), cancellable = true)
    private void nujo$bypassFrustumCulling(AABB aabb, CallbackInfoReturnable<Boolean> cir) {
        if (BirdviewClientEvent.isBirdseyeActive()) {
            cir.setReturnValue(true);
        }
    }
}