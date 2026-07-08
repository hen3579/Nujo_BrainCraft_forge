package com.Hen3579.Nujomod.Mixins;

import com.Hen3579.Nujomod.Server.BirdviewServerState;
import com.Hen3579.Nujomod.Server.FlyingMobRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Layer 1 — AI 目标 Y 钳制
 *
 * 注入点：Mob.serverAiStep() 中、MoveControl.tick() 调用之前。
 * 此时 AI 目标已由 goalSelector 设定完毕（wantedY 已有值），
 * 钳制后 MoveControl.tick() 会使用修正后的 wantedY 执行移动。
 *
 * 约束规则：
 * - wantedY > yMax → 钳制到 yMax-1（水平盘旋 + 缓慢下降）
 * - wantedY < diveFloor → 钳制到 diveFloor（俯冲最低 = 玩家头部平齐）
 * - 俯冲超时（冷却结束）→ 强制 wantedY = yMin+1（自动上浮回安全高度）
 *
 * serverAiStep() 是 final 方法，所有 Mob 子类共用此逻辑，
 * 覆盖 FlyingMoveControl、PhantomMoveControl 等所有 MoveControl 子类。
 */
@Mixin(Mob.class)
public abstract class MobServerAiStepMixin {

    @Inject(
            method = "serverAiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/control/MoveControl;tick()V")
    )
    private void nujo$clampWantedYBeforeMoveControl(CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;

        // 仅服务器端执行（客户端 mob 位置由服务器同步）
        if (mob.level().isClientSide()) return;

        // 仅约束飞行生物
        if (!FlyingMobRegistry.isFlyingMob(mob)) return;

        // 仅在附近有鸟瞰模式玩家时约束
        ServerPlayer birdviewPlayer = BirdviewServerState.getNearestBirdviewPlayer(mob);
        if (birdviewPlayer == null) return;

        // ===== 计算约束区间 =====
        double playerHeadY = birdviewPlayer.getY() + birdviewPlayer.getEyeHeight();
        double yMin = playerHeadY + 1;          // 玩家头顶 +1（安全高度下限）
        double yMax = playerHeadY + 5;          // 玩家头顶 +5（安全高度上限）
        double diveFloor = playerHeadY;         // 俯冲下限（玩家头部平齐）

        // 绝对高度缓冲：最低离地 1 格（不会落地）
        int groundH = mob.level().getHeight(
                Heightmap.Types.MOTION_BLOCKING,
                mob.blockPosition().getX(),
                mob.blockPosition().getZ()
        );
        yMin = Math.max(yMin, groundH + 1);

        // 绝对高度缓冲：最高不超过地形天花板 10 格
        yMax = Math.min(yMax, mob.level().getMaxBuildHeight() - 10);

        double mobY = mob.getY();

        // ===== 俯冲冷却管理 =====
        if (mobY < yMin) {
            // 进入俯冲区：启动俯冲计时（2 秒后强制上浮）
            if (!BirdviewServerState.isDiving(mob.getUUID())) {
                BirdviewServerState.startDive(mob.getUUID());
            }
        } else if (mobY >= yMin + 0.5) {
            // 回到安全高度：清除俯冲状态（准备好下次俯冲）
            BirdviewServerState.clearDive(mob.getUUID());
        }
        boolean isDiving = BirdviewServerState.isDiving(mob.getUUID());

        // ===== AI 目标 Y 钳制 =====
        MoveControl ctrl = mob.getMoveControl();
        if (ctrl instanceof MoveControlAccessor accessor) {
            double wantedY = accessor.nujo$getWantedY();

            if (wantedY > yMax) {
                // 目标过高：钳制到 yMax-1（水平盘旋 + 缓慢下降）
                // 不改 wantedX/wantedZ → 水平移动继续 → 盘旋效果
                accessor.nujo$setWantedY(yMax - 1);
            } else if (wantedY < diveFloor) {
                // 俯冲目标低于玩家头部：钳制到 diveFloor
                // 俯冲攻击最低只落到玩家头部平齐，不会贴地面
                accessor.nujo$setWantedY(diveFloor);
            } else if (mobY < yMin && !isDiving) {
                // 俯冲超时（冷却结束）：强制上浮回安全高度
                // 俯冲结束自动上浮回安全高度
                accessor.nujo$setWantedY(yMin + 1);
            }
            // else: wantedY 在 [diveFloor, yMax] 范围内，允许正常移动
        }
    }
}
