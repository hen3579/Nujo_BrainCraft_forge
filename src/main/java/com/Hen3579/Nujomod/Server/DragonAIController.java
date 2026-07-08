package com.Hen3579.Nujomod.Server;

import com.Hen3579.Nujomod.Server.DragonServerState.DragonPhase;
import com.Hen3579.Nujomod.Server.DragonServerState.DragonRuntimeState;
import com.Hen3579.Nujomod.Server.DragonServerState.DragonSubState;
import com.Hen3579.Nujomod.Server.DragonServerState.C3SkillType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.UUID;

/**
 * 末影龙俯视角状态机控制器
 *
 * 每服务端 tick 调用一次（从 DragonMixin.aiStep RETURN），
 * 负责阶段检测、子状态选择、移动行为和基础攻击。
 *
 * 三阶段不可逆转换：
 * A（护水晶）→ B（狂暴战）→ C（落地终极）
 *
 * 子状态选择优先级：
 * - 全局约束 > 阶段锁定 > 水晶事件 > 回血/近战 > 冲锋 > 巡逻/环绕
 */
public class DragonAIController {

    private static final Logger LOGGER = LogManager.getLogger("NujoDragonAI");

    /** 主入口：每 tick 调用 */
    public static void tick(EnderDragon dragon) {
        if (dragon.level().isClientSide()) return;
        if (!(dragon.level() instanceof ServerLevel serverLevel)) return;

        DragonRuntimeState state = DragonServerState.getOrCreateState(dragon.getUUID());

        // 0. 僵直检查：僵直时跳过所有行为，仅保留硬约束（Y 锁定等由 DragonMixin 处理）
        if (state.stunTimer > 0) {
            state.stunTimer--;
            // 僵直时完全停止移动
            dragon.setDeltaMovement(0, 0, 0);
            // 僵直视觉特效（每 10 tick 播放一次，避免粒子过密）
            if (state.stunTimer % 10 == 0) {
                DragonSkillEffects.playStunEffect(dragon, serverLevel);
            }
            updateCooldowns(state);
            state.stateTimer++;
            return;
        }

        // 1. 阶段更新（不可逆）+ 检测阶段转换触发特效
        DragonPhase prevPhase = state.phase;
        updatePhase(dragon, serverLevel, state);
        if (state.phase != prevPhase || state.lastTransitionPhase != state.phase) {
            if (state.lastTransitionPhase != state.phase) {
                DragonSkillEffects.playPhaseTransition(dragon, serverLevel);
                state.lastTransitionPhase = state.phase;
                LOGGER.info("Dragon phase transition visual: {} → {}", prevPhase, state.phase);
            }
        }

        // 2. 子状态选择
        selectSubState(dragon, serverLevel, state);

        // 3. 执行子状态行为
        executeBehavior(dragon, serverLevel, state);

        // 4. 更新冷却和计时器
        updateCooldowns(state);

        state.stateTimer++;
    }

    // ===== 阶段更新 =====

    private static void updatePhase(EnderDragon dragon, ServerLevel level, DragonRuntimeState state) {
        DragonPhase oldPhase = state.phase;

        // C → 不可逆转，一旦进入 C 永远留在 C
        if (state.phase == DragonPhase.PHASE_C) return;

        // 检测进入 C 的条件：HP <= 50%
        if (dragon.getHealth() <= dragon.getMaxHealth() * DragonServerState.PHASE_C_HP_THRESHOLD) {
            state.phase = DragonPhase.PHASE_C;
            state.subState = DragonSubState.C1_LANDING;
            state.stateTimer = 0;
            state.landingTimer = 0;
            LOGGER.info("Dragon {} → PHASE_C (HP: {}/{})",
                    dragon.getUUID(), dragon.getHealth(), dragon.getMaxHealth());
            return;
        }

        // 检测进入 B 的条件：水晶全毁
        if (state.phase == DragonPhase.PHASE_A) {
            int crystalCount = DragonServerState.countAliveCrystals(level);

            // 水晶数量变化时记录
            if (crystalCount != state.lastCrystalCount) {
                LOGGER.info("Crystal count: {} → {}", state.lastCrystalCount, crystalCount);
                state.lastCrystalCount = crystalCount;
            }

            if (crystalCount == 0) {
                state.phase = DragonPhase.PHASE_B;
                state.subState = DragonSubState.B1_CIRCLE;
                state.stateTimer = 0;
                LOGGER.info("Dragon {} → PHASE_B (all crystals destroyed)",
                        dragon.getUUID());
            }
        }
    }

    // ===== 子状态选择 =====

    private static void selectSubState(EnderDragon dragon, ServerLevel level, DragonRuntimeState state) {
        ServerPlayer target = getLockedTarget(dragon, level, state);
        if (target == null) return;

        switch (state.phase) {
            case PHASE_A -> selectPhaseA(dragon, level, state, target);
            case PHASE_B -> selectPhaseB(dragon, level, state, target);
            case PHASE_C -> selectPhaseC(dragon, level, state, target);
        }
    }

    // --- 阶段 A 子状态选择 ---
    private static void selectPhaseA(EnderDragon dragon, ServerLevel level, DragonRuntimeState state, ServerPlayer target) {
        int crystals = DragonServerState.countAliveCrystals(level);
        double hpRatio = dragon.getHealth() / dragon.getMaxHealth();

        // A3 回血：HP < 80% + 水晶附近 + 3秒无攻击 + 无回血锁定
        if (hpRatio < DragonServerState.A3_HEAL_HP_THRESHOLD
                && state.healLockoutTimer <= 0
                && state.stateTimer > 60  // 至少在当前状态待了 3 秒
                && dragon.hurtTime == 0   // 3秒内未受击（hurtTime 衰减为 0）
                && isNearCrystal(dragon, level, 10.0)) {

            if (state.subState != DragonSubState.A3_HEAL) {
                transitionTo(state, DragonSubState.A3_HEAL, "HP low + near crystal + no damage");
            }
            return;
        }

        // A2 护水晶：玩家靠近水晶或攻击水晶
        boolean playerNearCrystal = isPlayerNearCrystal(target, level, DragonServerState.PROTECT_CRYSTAL_RANGE);
        boolean dragonWasHit = dragon.hurtTime > 0;

        if (playerNearCrystal && crystals > 0) {
            // 被打时僵直 0.5 秒（10 tick），但仍然留在 A2
            if (state.subState != DragonSubState.A2_PROTECT) {
                transitionTo(state, DragonSubState.A2_PROTECT, "player near crystal");
            }
            return;
        }

        // A1 巡逻（默认）
        if (state.subState != DragonSubState.A1_PATROL
                && state.subState != DragonSubState.A2_PROTECT
                && state.subState != DragonSubState.A3_HEAL) {
            transitionTo(state, DragonSubState.A1_PATROL, "default patrol");
        }

        // 从 A2/A3 回到 A1 的条件
        if ((state.subState == DragonSubState.A2_PROTECT && !playerNearCrystal)
                || (state.subState == DragonSubState.A3_HEAL && (dragon.hurtTime > 0 || hpRatio >= DragonServerState.A3_HEAL_HP_THRESHOLD))) {

            if (state.subState == DragonSubState.A3_HEAL && dragon.hurtTime > 0) {
                // 回血被打断 → 10 秒锁定
                state.healLockoutTimer = DragonServerState.CD_HEAL_LOCKOUT;
                LOGGER.info("Dragon heal interrupted! Lockout {} ticks", DragonServerState.CD_HEAL_LOCKOUT);
            }
            transitionTo(state, DragonSubState.A1_PATROL, "return to patrol");
        }
    }

    // --- 阶段 B 子状态选择 ---
    private static void selectPhaseB(EnderDragon dragon, ServerLevel level, DragonRuntimeState state, ServerPlayer target) {
        double distSq = dragon.distanceToSqr(target);

        // B3 扇形龙息：玩家贴身
        if (distSq < DragonServerState.BREATH_RANGE * DragonServerState.BREATH_RANGE) {
            // 龙息进行中不中断
            if (state.subState != DragonSubState.B3_BREATH) {
                if (state.breathCD <= 0) {
                    transitionTo(state, DragonSubState.B3_BREATH, "player in breath range");
                }
            }
            return;
        }

        // B3 执行完毕回到 B1
        if (state.subState == DragonSubState.B3_BREATH && state.stateTimer > 60) {
            state.breathCD = DragonServerState.CD_BREATH;
            transitionTo(state, DragonSubState.B1_CIRCLE, "breath finished");
            return;
        }

        // B2 平面冲锋：玩家中距离
        double chargeMinSq = DragonServerState.CHARGE_MIN_RANGE * DragonServerState.CHARGE_MIN_RANGE;
        double chargeMaxSq = DragonServerState.CHARGE_MAX_RANGE * DragonServerState.CHARGE_MAX_RANGE;

        if (distSq > chargeMinSq && distSq < chargeMaxSq) {
            if (state.subState != DragonSubState.B2_CHARGE && state.chargeCD <= 0) {
                transitionTo(state, DragonSubState.B2_CHARGE, "player at charge range");
                state.chargeDuration = DragonServerState.CHARGE_DURATION;
            }
            return;
        }

        // B2 冲锋完成
        if (state.subState == DragonSubState.B2_CHARGE) {
            if (state.chargeDuration <= 0) {
                state.chargeCD = DragonServerState.CD_CHARGE;
                transitionTo(state, DragonSubState.B1_CIRCLE, "charge finished");
            }
            return;
        }

        // B1 环绕玩家（默认）
        if (state.subState != DragonSubState.B1_CIRCLE
                && state.subState != DragonSubState.B2_CHARGE
                && state.subState != DragonSubState.B3_BREATH) {
            transitionTo(state, DragonSubState.B1_CIRCLE, "default circle");
        }
    }

    // --- 阶段 C 子状态选择 ---
    private static void selectPhaseC(EnderDragon dragon, ServerLevel level, DragonRuntimeState state, ServerPlayer target) {
        // C1 落地过渡
        if (state.subState == DragonSubState.C1_LANDING) {
            if (state.landingTimer >= DragonServerState.LANDING_DURATION) {
                transitionTo(state, DragonSubState.C2_STATIONARY, "landing complete");
            }
            return;
        }

        // C2 静止 → 蓄力完成后触发 C3 技能
        if (state.subState == DragonSubState.C2_STATIONARY) {
            // C2 蓄力时间 >= 6 秒（120 tick）后触发
            if (state.stateTimer >= DragonServerState.CD_ULTIMATE_TRIGGER) {
                // 轮换选择终极技能类型
                C3SkillType[] skills = C3SkillType.values();
                state.c3SkillType = skills[state.c3SkillCount % skills.length];
                state.c3SkillCount++;
                transitionTo(state, DragonSubState.C3_ULTIMATE,
                        "ultimate skill: " + state.c3SkillType.name());
            }
            return;
        }

        // C3 终极技能执行完毕
        if (state.subState == DragonSubState.C3_ULTIMATE
                && state.stateTimer >= DragonServerState.C3_SKILL_DURATION) {
            transitionTo(state, DragonSubState.C2_STATIONARY, "ultimate skill finished");
        }
    }

    // ===== 行为执行 =====

    private static void executeBehavior(EnderDragon dragon, ServerLevel level, DragonRuntimeState state) {
        ServerPlayer target = getLockedTarget(dragon, level, state);

        switch (state.subState) {
            case A1_PATROL -> doPatrol(dragon, state);
            case A2_PROTECT -> doProtectCrystal(dragon, level, state, target);
            case A3_HEAL -> doHeal(dragon, level, state);
            case B1_CIRCLE -> doCirclePlayer(dragon, state, target);
            case B2_CHARGE -> doCharge(dragon, state, target);
            case B3_BREATH -> doFanBreath(dragon, state, target);
            case C1_LANDING -> doForcedLanding(dragon, state);
            case C2_STATIONARY -> doStationary(dragon, state, target);
            case C3_ULTIMATE -> doUltimateSkill(dragon, level, state, target);
        }
    }

    // --- A1: 环形巡逻 ---
    private static void doPatrol(EnderDragon dragon, DragonRuntimeState state) {
        // 方向切换
        if (state.patrolSwitchTimer >= DragonServerState.PATROL_SWITCH_INTERVAL) {
            state.patrolDirection *= -1;
            state.patrolSwitchTimer = 0;
            LOGGER.info("Dragon patrol direction → {}",
                    state.patrolDirection > 0 ? "clockwise" : "counter-clockwise");
        }
        state.patrolSwitchTimer++;

        // 角度递增：speed / radius
        double angleStep = (DragonServerState.SPEED_PATROL / DragonServerState.PATROL_RADIUS) * state.patrolDirection;
        state.patrolAngle += angleStep;

        // 目标位置
        double targetX = DragonServerState.CENTER_X + Math.sin(state.patrolAngle) * DragonServerState.PATROL_RADIUS;
        double targetZ = DragonServerState.CENTER_Z + Math.cos(state.patrolAngle) * DragonServerState.PATROL_RADIUS;

        setMoveToward(dragon, targetX, targetZ, DragonServerState.SPEED_PATROL);
    }

    // --- A2: 护水晶 ---
    private static void doProtectCrystal(EnderDragon dragon, ServerLevel level, DragonRuntimeState state, ServerPlayer target) {
        EndCrystal crystal = findNearestCrystal(dragon, level);
        if (crystal == null) {
            // 没有水晶了，回到巡逻
            doPatrol(dragon, state);
            return;
        }

        double distToCrystal = Math.sqrt(
                Math.pow(dragon.getX() - crystal.getX(), 2) +
                Math.pow(dragon.getZ() - crystal.getZ(), 2));

        if (distToCrystal > 5.0) {
            // 飞向水晶
            setMoveToward(dragon, crystal.getX(), crystal.getZ(), DragonServerState.SPEED_PATROL);
        } else {
            // 到了，盘旋 + 射火球
            setMoveToward(dragon, crystal.getX() + Math.cos(state.stateTimer * 0.1) * 5,
                          crystal.getZ() + Math.sin(state.stateTimer * 0.1) * 5,
                          DragonServerState.SPEED_PATROL * 0.5);

            if (target != null && state.fireballCD <= 0) {
                shootFireball(dragon, level, target);
                state.fireballCD = DragonServerState.CD_FIREBALL_A;
            }
        }
    }

    // --- A3: 平面回血 ---
    private static void doHeal(EnderDragon dragon, ServerLevel level, DragonRuntimeState state) {
        EndCrystal crystal = findNearestCrystal(dragon, level);
        if (crystal == null) {
            transitionTo(state, DragonSubState.A1_PATROL, "no crystal for heal");
            return;
        }

        double distToCrystal = Math.sqrt(
                Math.pow(dragon.getX() - crystal.getX(), 2) +
                Math.pow(dragon.getZ() - crystal.getZ(), 2));

        if (distToCrystal > 5.0) {
            setMoveToward(dragon, crystal.getX(), crystal.getZ(), DragonServerState.SPEED_PATROL * 0.7);
        } else {
            // 原地盘旋 + 回血
            dragon.setDeltaMovement(0, 0, 0);

            double maxHP = dragon.getMaxHealth();
            double hpRatio = dragon.getHealth() / maxHP;
            if (hpRatio < DragonServerState.A3_HEAL_HP_THRESHOLD) {
                dragon.heal((float) (maxHP * DragonServerState.A3_HEAL_RATE / 20.0)); // 2%/s = 0.1%/tick

                // 回血视觉特效（每 10 tick）
                if (state.stateTimer % 10 == 0) {
                    DragonSkillEffects.playHealEffect(dragon, (ServerLevel) dragon.level());
                }
            }
        }
    }

    // --- B1: 环绕玩家 ---
    private static void doCirclePlayer(EnderDragon dragon, DragonRuntimeState state, ServerPlayer target) {
        if (target == null) return;

        // 用玩家位置做圆心
        double angleStep = (DragonServerState.SPEED_CIRCLE / 18.0) * state.patrolDirection;
        state.patrolAngle += angleStep;

        double radius = 18.0;
        double targetX = target.getX() + Math.sin(state.patrolAngle) * radius;
        double targetZ = target.getZ() + Math.cos(state.patrolAngle) * radius;

        setMoveToward(dragon, targetX, targetZ, DragonServerState.SPEED_CIRCLE);

        // 狂暴能量光环（每 5 tick）
        if (state.stateTimer % 5 == 0) {
            DragonSkillEffects.playEnergyAura(dragon, (ServerLevel) dragon.level());
        }

        // 持续火球攻击
        if (state.fireballCD <= 0) {
            shootFireball(dragon, (ServerLevel) dragon.level(), target);
            state.fireballCD = DragonServerState.CD_FIREBALL_B;
        }
    }

    // --- B2: 平面冲锋 ---
    private static void doCharge(EnderDragon dragon, DragonRuntimeState state, ServerPlayer target) {
        if (target == null) return;

        // 直线冲向玩家
        setMoveToward(dragon, target.getX(), target.getZ(), DragonServerState.SPEED_CHARGE);
        state.chargeDuration--;

        // 冲锋尾迹粒子
        DragonSkillEffects.playChargeTrail(dragon, (ServerLevel) dragon.level());

        // 冲锋中造成碰撞伤害
        if (dragon.distanceToSqr(target) < 4.0) {
            target.hurt(dragon.damageSources().mobAttack(dragon), 8.0f);
        }
    }

    // --- B3: 扇形龙息 ---
    private static void doFanBreath(EnderDragon dragon, DragonRuntimeState state, ServerPlayer target) {
        if (target == null) return;
        ServerLevel level = (ServerLevel) dragon.level();

        // 停止移动，面向玩家
        dragon.setDeltaMovement(0, 0, 0);

        // 扇形龙息粒子 + 酸雾区域伤害
        DragonSkillEffects.playFanBreath(dragon, level, target, state.stateTimer);
    }

    // --- C1: 强制落地 ---
    private static void doForcedLanding(EnderDragon dragon, DragonRuntimeState state) {
        // 移向祭坛中心 (0, 64, 0)
        double dx = DragonServerState.CENTER_X - dragon.getX();
        double dz = DragonServerState.CENTER_Z - dragon.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist > 2.0) {
            setMoveToward(dragon, DragonServerState.CENTER_X, DragonServerState.CENTER_Z,
                    DragonServerState.SPEED_PATROL);
        } else {
            dragon.setDeltaMovement(0, 0, 0);
        }

        state.landingTimer++;
    }

    // --- C2: 终极静止 ---
    private static void doStationary(EnderDragon dragon, DragonRuntimeState state, ServerPlayer target) {
        // 完全静止，仅面向玩家
        dragon.setDeltaMovement(0, 0, 0);

        // 蓄力视觉特效（能量光环，每 5 tick）
        if (state.stateTimer % 5 == 0) {
            DragonSkillEffects.playEnergyAura(dragon, (ServerLevel) dragon.level());
        }

        // 蓄力后半段（最后 2 秒）增加粒子密度（预兆技能即将释放）
        if (state.stateTimer > DragonServerState.CD_ULTIMATE_TRIGGER - 40 && state.stateTimer % 3 == 0) {
            DragonSkillEffects.playEnergyAura(dragon, (ServerLevel) dragon.level());
        }
    }

    // --- C3: 终极技能（三选一轮换） ---
    private static void doUltimateSkill(EnderDragon dragon, ServerLevel level, DragonRuntimeState state, ServerPlayer target) {
        dragon.setDeltaMovement(0, 0, 0);

        int tick = state.stateTimer;

        switch (state.c3SkillType) {
            case RING_AOE -> executeRingAOE(dragon, level, state, tick);
            case METEOR_SHOWER -> executeMeteorShower(dragon, level, state, tick);
            case SHOCKWAVE -> executeShockwave(dragon, level, state, tick);
        }
    }

    // --- C3 技能 1: 环形 AOE ---
    private static void executeRingAOE(EnderDragon dragon, ServerLevel level, DragonRuntimeState state, int tick) {
        // 0~50 tick: 扩散粒子环
        if (tick <= 50) {
            double progress = tick / 50.0;
            double radius = progress * DragonServerState.RING_AOE_MAX_RADIUS;
            DragonSkillEffects.playRingAOE(dragon, level, tick, radius);
        }

        // tick=50 时执行伤害判定
        if (tick == 50) {
            DragonSkillEffects.applyRingAOEDamage(dragon, level, DragonServerState.RING_AOE_MAX_RADIUS);
            state.aoeCD = DragonServerState.CD_AOE;
            LOGGER.info("Dragon used Ring AOE!");
        }
    }

    // --- C3 技能 2: 流星雨 ---
    private static void executeMeteorShower(EnderDragon dragon, ServerLevel level, DragonRuntimeState state, int tick) {
        DragonSkillEffects.playMeteorShower(dragon, level, tick);

        if (tick == 0) {
            LOGGER.info("Dragon used Meteor Shower!");
        }
    }

    // --- C3 技能 3: 冲击波 ---
    private static void executeShockwave(EnderDragon dragon, ServerLevel level, DragonRuntimeState state, int tick) {
        // 0~30 tick: 扩散冲击波
        if (tick <= 30) {
            double progress = tick / 30.0;
            double radius = progress * DragonServerState.SHOCKWAVE_MAX_RADIUS;
            DragonSkillEffects.playShockwave(dragon, level, tick, radius);
        }

        // tick=30 时执行伤害+击退
        if (tick == 30) {
            DragonSkillEffects.applyShockwaveDamage(dragon, level, DragonServerState.SHOCKWAVE_MAX_RADIUS);
            state.shockwaveCD = DragonServerState.CD_SHOCKWAVE;
            LOGGER.info("Dragon used Shockwave!");
        }
    }

    // ===== 辅助方法 =====

    /** 设置龙的水平移动方向，朝向目标点 */
    private static void setMoveToward(EnderDragon dragon, double targetX, double targetZ, double speed) {
        double dx = targetX - dragon.getX();
        double dz = targetZ - dragon.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist < 0.1) {
            dragon.setDeltaMovement(0, dragon.getDeltaMovement().y, 0);
            return;
        }

        double vx = (dx / dist) * speed * 0.1; // 乘 0.1 转换为/tick 速度
        double vz = (dz / dist) * speed * 0.1;
        dragon.setDeltaMovement(vx, dragon.getDeltaMovement().y, vz);
    }

    /** 发射龙火球 */
    private static void shootFireball(EnderDragon dragon, ServerLevel level, ServerPlayer target) {
        double dx = target.getX() - dragon.getX();
        double dy = target.getEyeY() - (dragon.getY() + 2.0);
        double dz = target.getZ() - dragon.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist < 0.1) return;

        // 归一化 × 火球速度
        double speed = 1.5;
        double vx = (dx / dist) * speed;
        double vy = (dy / dist) * speed;
        double vz = (dz / dist) * speed;

        DragonFireball fireball = new DragonFireball(level, dragon, vx, vy, vz);
        fireball.setPos(dragon.getX(), dragon.getY() + 2.0, dragon.getZ());
        level.addFreshEntity(fireball);

        LOGGER.info("Dragon fired fireball at {}", target.getName().getString());
    }

    /** 获取锁定的目标玩家（带防抖） */
    private static ServerPlayer getLockedTarget(EnderDragon dragon, ServerLevel level, DragonRuntimeState state) {
        ServerPlayer birdviewPlayer = DragonServerState.getBirdviewPlayerForDragon(dragon);
        if (birdviewPlayer == null) return null;

        // 首次或目标失效
        if (state.lockedTargetUUID == null
                || !birdviewPlayer.getUUID().equals(state.lockedTargetUUID)
                || state.targetSwitchCD <= 0) {

            // 目标切换：仅当当前锁定失效或 CD 到期
            if (state.lockedTargetUUID == null
                    || !birdviewPlayer.isAlive()
                    || state.targetSwitchCD <= 0) {
                state.lockedTargetUUID = birdviewPlayer.getUUID();
                state.targetSwitchCD = DragonServerState.CD_TARGET_SWITCH;
            }
        }

        // 查找锁定玩家（鸟瞰模式只有一个玩家，直接返回）
        return birdviewPlayer;
    }

    /** 查找最近的末影水晶 */
    private static EndCrystal findNearestCrystal(EnderDragon dragon, ServerLevel level) {
        List<EndCrystal> crystals = level.getEntitiesOfClass(EndCrystal.class,
                AABB.ofSize(dragon.position(), 120, 60, 60),
                EndCrystal::isAlive);

        EndCrystal nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (EndCrystal crystal : crystals) {
            double dist = dragon.distanceToSqr(crystal);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = crystal;
            }
        }
        return nearest;
    }

    /** 判断龙是否在水晶附近 */
    private static boolean isNearCrystal(EnderDragon dragon, ServerLevel level, double range) {
        return !level.getEntitiesOfClass(EndCrystal.class,
                AABB.ofSize(dragon.position(), range * 2, range * 2, range * 2),
                EndCrystal::isAlive).isEmpty();
    }

    /** 判断玩家是否在水晶附近 */
    private static boolean isPlayerNearCrystal(ServerPlayer player, ServerLevel level, double range) {
        return !level.getEntitiesOfClass(EndCrystal.class,
                AABB.ofSize(player.position(), range * 2, range * 2, range * 2),
                EndCrystal::isAlive).isEmpty();
    }

    /** 状态转换 + 日志 */
    private static void transitionTo(DragonRuntimeState state, DragonSubState newSubState, String reason) {
        LOGGER.info("Dragon {} → {} (reason: {}, prev: {}, timer: {})",
                state.phase, newSubState, reason, state.subState, state.stateTimer);
        state.subState = newSubState;
        state.stateTimer = 0;
    }

    /** 更新所有冷却计时器 */
    private static void updateCooldowns(DragonRuntimeState state) {
        if (state.fireballCD > 0) state.fireballCD--;
        if (state.chargeCD > 0) state.chargeCD--;
        if (state.breathCD > 0) state.breathCD--;
        if (state.aoeCD > 0) state.aoeCD--;
        if (state.healCD > 0) state.healCD--;
        if (state.healLockoutTimer > 0) state.healLockoutTimer--;
        if (state.targetSwitchCD > 0) state.targetSwitchCD--;
        if (state.meteorCD > 0) state.meteorCD--;
        if (state.shockwaveCD > 0) state.shockwaveCD--;
    }
}
