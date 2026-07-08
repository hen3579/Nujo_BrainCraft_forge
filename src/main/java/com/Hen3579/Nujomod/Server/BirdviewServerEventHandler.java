package com.Hen3579.Nujomod.Server;

import com.Hen3579.Nujomod.NujoBraincraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 飞行生物 Y 轴约束 — 服务器端事件处理器
 *
 * 职责：
 * - Layer 3（兜底巡检）：LivingTickEvent 中硬钳制位置
 *   捕获 Layer 1（MobServerAiStepMixin）和 Layer 2（FlyingMobMoveMixin）
 *   遗漏的极端情况（传送、爆炸击退、生成位置异常等）
 * - 俯冲冷却全局递减：ServerTickEvent.START 中执行
 * - 玩家退出清理：清除鸟瞰状态
 *
 * 注意：Layer 1（AI wantedY 钳制）在 MobServerAiStepMixin 中实现，
 *       Layer 2（物理位移后 Y 钳制）在 FlyingMobMoveMixin 中实现。
 */
@Mod.EventBusSubscriber(modid = NujoBraincraft.MODID)
public class BirdviewServerEventHandler {

    /**
     * Layer 3：兜底硬钳制 — 每 tick 检查飞行生物的 Y 是否超出范围
     *
     * 在 LivingTickEvent 中执行（AI + 移动之后），
     * 捕获 Layer 2 遗漏的极端情况。
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (mob.level().isClientSide()) return;
        if (!FlyingMobRegistry.isFlyingMob(mob)) return;

        ServerPlayer birdviewPlayer = BirdviewServerState.getNearestBirdviewPlayer(mob);
        if (birdviewPlayer == null) return;

        // ===== 计算约束区间 =====
        double playerHeadY = birdviewPlayer.getY() + birdviewPlayer.getEyeHeight();
        double yMin = playerHeadY + 1;
        double yMax = playerHeadY + 5;
        double diveFloor = playerHeadY;

        // 绝对高度缓冲
        int groundH = mob.level().getHeight(
                Heightmap.Types.MOTION_BLOCKING,
                mob.blockPosition().getX(),
                mob.blockPosition().getZ()
        );
        yMin = Math.max(yMin, groundH + 1);
        yMax = Math.min(yMax, mob.level().getMaxBuildHeight() - 10);

        // ===== 硬钳制（兜底） =====
        double mobY = mob.getY();
        if (mobY > yMax + 2) {
            // 远超天花板：硬拉回
            mob.setPos(mob.getX(), yMax, mob.getZ());
            Vec3 delta = mob.getDeltaMovement();
            mob.setDeltaMovement(delta.x, -0.1, delta.z);
        } else if (mobY < diveFloor - 2) {
            // 远低于俯冲下限：硬拉回
            mob.setPos(mob.getX(), diveFloor, mob.getZ());
            Vec3 delta = mob.getDeltaMovement();
            mob.setDeltaMovement(delta.x, 0.2, delta.z);
        }
    }

    /**
     * 俯冲冷却全局递减（每 tick 执行）
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            BirdviewServerState.tickDiveCooldowns();
        }
    }

    /**
     * 玩家退出时清除鸟瞰状态
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BirdviewServerState.clearPlayer(event.getEntity().getUUID());
    }
}
