package com.Hen3579.Nujomod.Mixins;

import com.Hen3579.Nujomod.Server.BirdviewServerState;
import com.Hen3579.Nujomod.Server.DragonServerState;
import com.Hen3579.Nujomod.Server.DragonSkillEffects;
import com.Hen3579.Nujomod.Server.DragonServerState.DragonRuntimeState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 末影龙战斗管理器 Mixin — 水晶摧毁事件
 *
 * 注入点：EndDragonFight.onCrystalDestroyed() TAIL
 * 在原版水晶计数更新之后，追加自定义效果：
 *
 * 仅在有鸟瞰玩家时触发：
 * 1. 龙掉 10% 最大血量
 * 2. 龙僵直 2 秒（40 tick）
 * 3. 中断回血，10 秒内不能再回血
 */
@Mixin(EndDragonFight.class)
public abstract class EndDragonFightMixin {

    private static final Logger LOGGER = LogManager.getLogger("NujoDragonCrystal");

    /** 水晶被摧毁时龙掉血的百分比（10% 最大血量） */
    private static final float CRYSTAL_DESTROY_HP_LOSS_RATIO = 0.10f;

    /** 僵直时间（40 tick = 2 秒） */
    private static final int STUN_TICKS = 40;

    /** 回血锁定时间（200 tick = 10 秒） */
    private static final int HEAL_LOCKOUT_TICKS = 200;

    @Inject(method = "onCrystalDestroyed", at = @At("TAIL"))
    private void nujo$onCrystalDestroyed(EndCrystal crystal, DamageSource source, CallbackInfo ci) {
        // 从水晶获取世界
        if (!(crystal.level() instanceof ServerLevel serverLevel)) return;

        // 仅在有鸟瞰玩家时触发自定义效果
        ServerPlayer birdviewPlayer = BirdviewServerState.getAnyBirdviewPlayerInLevel(serverLevel);
        if (birdviewPlayer == null) return;

        // 播放水晶摧毁增强特效
        DragonSkillEffects.playCrystalDestroy(crystal, serverLevel);

        // 查找附近的末影龙（水晶 60 格范围内）
        AABB searchBox = AABB.ofSize(crystal.position(), 120, 60, 120);
        List<EnderDragon> dragons = serverLevel.getEntitiesOfClass(EnderDragon.class, searchBox,
                EnderDragon::isAlive);

        if (dragons.isEmpty()) {
            LOGGER.warn("Crystal destroyed but no dragon found nearby");
            return;
        }

        for (EnderDragon dragon : dragons) {
            // 检查是否应该被俯视角约束（确保只在鸟瞰模式下生效）
            if (!DragonServerState.shouldConstrainDragon(dragon)) continue;

            DragonRuntimeState state = DragonServerState.getOrCreateState(dragon.getUUID());

            // 1. 扣 10% 最大血量
            float damage = dragon.getMaxHealth() * CRYSTAL_DESTROY_HP_LOSS_RATIO;
            dragon.hurt(dragon.damageSources().magic(), damage);
            LOGGER.info("Crystal destroyed! Dragon {} took {} damage (10% max HP)",
                    dragon.getUUID(), damage);

            // 2. 僵直 2 秒
            state.stunTimer = STUN_TICKS;
            LOGGER.info("Dragon stunned for {} ticks", STUN_TICKS);

            // 3. 中断回血 + 10 秒锁定
            state.healLockoutTimer = HEAL_LOCKOUT_TICKS;
            // 如果正在回血，强制切回巡逻
            if (state.subState == DragonServerState.DragonSubState.A3_HEAL) {
                state.subState = DragonServerState.DragonSubState.A1_PATROL;
                state.stateTimer = 0;
                LOGGER.info("Dragon heal interrupted by crystal destruction!");
            }
        }
    }
}
