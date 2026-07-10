package com.Hen3579.Nujomod.Mixins;

import com.Hen3579.Nujomod.Server.BirdviewServerState;
import com.Hen3579.Nujomod.Server.FlyingMobRegistry;
import com.Hen3579.Nujomod.Config.Config;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Layer 2 — 物理层 Y 轴硬限
 *
 * Mixin 拦截 Entity.move()，在位移应用后检查飞行生物的 Y 坐标：
 * - 超过 yMax：钳制到天花板 + 反弹 + 缓慢下降
 * - 低于 diveFloor（玩家头部）：钳制到俯冲下限 + 反弹上浮
 * - 在 dive zone 且俯冲已结束：施加轻微上浮速度
 *
 * 覆盖所有飞行生物（无论使用什么 MoveControl 子类），
 * 因为所有位移最终都经过 Entity.move()。
 */
@Mixin(Entity.class)
public abstract class FlyingMobMoveMixin {

    /** 飞行生物 Y 轴约束常量（由 Config 烘焙） */
    public static int Y_MIN_OFFSET = 1;
    public static int Y_MAX_OFFSET = 5;

    @Inject(method = "move", at = @At("RETURN"))
    private void nujo$clampFlyingMobY(MoverType type, Vec3 pos, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;

        // 快速过滤：非 Mob 实体直接跳过（物品、弹射物等）
        if (!(self instanceof Mob mob)) return;
        if (!FlyingMobRegistry.isFlyingMob(mob)) return;

        ServerPlayer birdviewPlayer = BirdviewServerState.getNearestBirdviewPlayer(mob);
        if (birdviewPlayer == null) return;

        // ===== 计算约束区间 =====
        double playerHeadY = birdviewPlayer.getY() + birdviewPlayer.getEyeHeight();
        double yMin = playerHeadY + Y_MIN_OFFSET;  // 最低安全高度（玩家头顶 + offset）
        double yMax = playerHeadY + Y_MAX_OFFSET;  // 最高允许高度（玩家头顶 + offset）
        double diveFloor = playerHeadY;         // 俯冲下限（玩家头部平齐）

        // 绝对高度缓冲：最低离地 1 格
        int groundH = mob.level().getHeight(
                Heightmap.Types.MOTION_BLOCKING,
                mob.blockPosition().getX(),
                mob.blockPosition().getZ()
        );
        yMin = Math.max(yMin, groundH + 1);

        // 绝对高度缓冲：最高不超过地形天花板 10 格
        yMax = Math.min(yMax, mob.level().getMaxBuildHeight() - 10);

        // ===== 物理层钳制 =====
        double y = mob.getY();
        Vec3 delta = mob.getDeltaMovement();

        if (y > yMax) {
            // 超过天花板：钳制位置 + 反弹 + 缓慢下降
            mob.setPos(mob.getX(), yMax, mob.getZ());
            mob.setDeltaMovement(delta.x, Math.min(delta.y, -0.08), delta.z);
        } else if (y < diveFloor) {
            // 低于俯冲下限：钳制到玩家头部 + 反弹上浮
            mob.setPos(mob.getX(), diveFloor, mob.getZ());
            mob.setDeltaMovement(delta.x, Math.max(delta.y, 0.15), delta.z);
        } else if (y < yMin && !BirdviewServerState.isDiving(mob.getUUID())) {
            // 在俯冲区但俯冲已结束：施加轻微上浮速度（自动上浮回安全高度）
            mob.setDeltaMovement(delta.x, Math.max(delta.y, 0.05), delta.z);
        }
    }
}
