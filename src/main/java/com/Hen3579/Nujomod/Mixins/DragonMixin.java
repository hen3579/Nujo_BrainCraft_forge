package com.Hen3579.Nujomod.Mixins;

import com.Hen3579.Nujomod.Server.DragonAIController;
import com.Hen3579.Nujomod.Server.DragonServerState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 末影龙俯视角约束 Mixin
 *
 * 注入点：EnderDragon.aiStep() HEAD + cancellable
 * 完全取消 vanilla aiStep，由我们接管：
 * 1. 调用自定义状态机 (DragonAIController.tick) 设置 deltaMovement
 * 2. Y 轴平滑锁定（velocity-based，不硬 snap）
 * 3. 范围拉回
 * 4. 应用移动 + 摩擦力
 * 5. 旋转：身体跟随移动方向，头部朝向玩家
 *
 * 关键改进（修复抽搐）：
 * - 不再在 RETURN 覆盖 vanilla 的值，而是完全取消 vanilla aiStep
 * - 不强制 yRotO/yBodyRotO = targetYaw（消除插值冲突）
 * - 手动保存 yBodyRotO/yHeadRotO（vanilla LivingEntity.aiStep 被取消后不会自动保存）
 * - yRotO/xRotO 由 Entity.baseTick() 自动保存（在 aiStep 之前执行）
 *
 * 客户端不受影响：shouldConstrainDragon 过滤了客户端，vanilla aiStep 在客户端正常运行，
 * 负责身体部件定位（基于服务端发来的 position/rotation 数据包）。
 */
@Mixin(EnderDragon.class)
public abstract class DragonMixin {

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void nujo$dragonBirdviewControl(CallbackInfo ci) {
        EnderDragon dragon = (EnderDragon) (Object) this;

        // 龙死亡时清理状态
        if (!dragon.isAlive() && !dragon.level().isClientSide()) {
            DragonServerState.clearDragon(dragon.getUUID());
        }

        // 仅在服务器端 + 鸟瞰玩家存在 + 龙存活时接管
        if (!DragonServerState.shouldConstrainDragon(dragon)) {
            return; // 不取消 → vanilla aiStep 正常运行
        }

        ServerPlayer birdviewPlayer = DragonServerState.getBirdviewPlayerForDragon(dragon);
        if (birdviewPlayer == null) return;

        // ===== 1. 状态机 tick（设置 deltaMovement） =====
        DragonAIController.tick(dragon);

        Vec3 delta = dragon.getDeltaMovement();

        // ===== 2. Y 轴锁定（平滑接近，不硬 snap） =====
        double targetY = DragonServerState.getLockedY(dragon.getUUID());
        double yDiff = targetY - dragon.getY();
        if (Math.abs(yDiff) > DragonServerState.Y_TOLERANCE) {
            // 距离目标较远：以 10%/tick 速度平滑接近
            delta = new Vec3(delta.x, yDiff * 0.1, delta.z);
        } else {
            // 已到位：锁定 Y 速度为零
            delta = new Vec3(delta.x, 0, delta.z);
        }

        // ===== 3. 范围检查：超出 30 格强制拉回中心 =====
        double dxFromCenter = dragon.getX() - DragonServerState.CENTER_X;
        double dzFromCenter = dragon.getZ() - DragonServerState.CENTER_Z;
        double distSq = dxFromCenter * dxFromCenter + dzFromCenter * dzFromCenter;
        if (distSq > DragonServerState.MAX_RANGE_SQ) {
            double dist = Math.sqrt(distSq);
            double pullX = -(dxFromCenter / dist) * DragonServerState.PULL_BACK_SPEED * 0.1;
            double pullZ = -(dzFromCenter / dist) * DragonServerState.PULL_BACK_SPEED * 0.1;
            delta = new Vec3(delta.x + pullX, delta.y, delta.z + pullZ);
        }

        // ===== 4. 应用移动 + 摩擦力 =====
        dragon.setPos(dragon.getX() + delta.x, dragon.getY() + delta.y, dragon.getZ() + delta.z);
        dragon.setDeltaMovement(delta.x * 0.98, 0, delta.z * 0.98);

        // ===== 5. 旋转 =====
        // 保存 old 值用于客户端插值
        // (yRotO/xRotO 由 Entity.baseTick() 在 aiStep 之前保存，无需手动处理)
        // (yBodyRotO/yHeadRotO 由 LivingEntity.aiStep() 保存，但我们取消了它，需手动保存)
        dragon.yBodyRotO = dragon.yBodyRot;
        dragon.yHeadRotO = dragon.yHeadRot;

        // 身体朝向移动方向（不强制朝向玩家，让龙自然飞行）
        double speedSq = delta.x * delta.x + delta.z * delta.z;
        if (speedSq > 0.001) {
            float moveYaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
            dragon.setYRot(moveYaw);
            dragon.setYBodyRot(moveYaw);
        }

        // 头部朝向玩家（龙头追踪玩家，身体跟随飞行方向）
        double dxPlayer = birdviewPlayer.getX() - dragon.getX();
        double dzPlayer = birdviewPlayer.getZ() - dragon.getZ();
        float headYaw = (float) Math.toDegrees(Math.atan2(-dxPlayer, dzPlayer));
        dragon.setYHeadRot(headYaw);
        dragon.setXRot(0);

        // 取消 vanilla aiStep —— 我们已经完成了所有工作
        ci.cancel();
    }
}
