package com.Hen3579.Nujomod.Mixins;

import net.minecraft.world.entity.ai.control.MoveControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * MoveControl 字段访问器
 *
 * 通过 @Accessor 暴露 MoveControl 的 protected wantedY 字段，
 * 供 BirdviewServerEventHandler 在 LivingTickEvent 中读取/修改 AI 目标 Y 坐标。
 *
 * 适用于所有 MoveControl 子类（FlyingMoveControl、PhantomMoveControl 等），
 * 因为它们都继承自 MoveControl，不重写字段。
 */
@Mixin(MoveControl.class)
public interface MoveControlAccessor {

    /** 读取 AI 目标 Y 坐标 */
    @Accessor("wantedY")
    double nujo$getWantedY();

    /** 设置 AI 目标 Y 坐标（用于钳制） */
    @Accessor("wantedY")
    void nujo$setWantedY(double y);
}
