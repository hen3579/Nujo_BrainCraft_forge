package com.Hen3579.Nujomod.Mixins;

import com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MouseHandler Mixin — 鸟瞰模式下阻止 turnPlayer() 调用。
 * 
 * GLFW_CURSOR_NORMAL 模式下鼠标自由移动，但 MC 原版的 turnPlayer() 会
 * 把鼠标 delta 映射为玩家 yaw/pitch 旋转。在鸟瞰模式下不需要这个行为：
 * - 相机朝向由 fixedYaw 固定（不随鼠标转动）
 * - 玩家 yaw 由 WASD 对齐或手动设置（不随鼠标转动）
 * - 鸟瞰下鼠标的作用是选择目标/移动光标，不是控制视角
 * 
 * 参考 Reign of Nether：它们也在鸟瞰/正交模式下阻止了鼠标 turnPlayer。
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    /**
     * 在 turnPlayer 方法头部注入：鸟瞰模式下直接跳过（cancel）。
     * turnPlayer 会在 GLFW_CURSOR_NORMAL 模式下处理鼠标移动 delta，
     * 把它映射为玩家视角旋转。鸟瞰模式下我们不需要这个行为。
     */
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true, require = 0)
    private void nujo$cancelTurnPlayerInBirdview(CallbackInfo ci) {
        if (BirdviewClientEvent.isBirdseyeActive()) {
            ci.cancel();
        }
    }
}
