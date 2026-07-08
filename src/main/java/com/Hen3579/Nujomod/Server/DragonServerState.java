package com.Hen3579.Nujomod.Server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 末影龙俯视角战斗状态管理
 *
 * 管理每只末影龙的当前阶段、子状态、冷却和目标锁定。
 *
 * 阶段 A（护水晶巡逻）: Y=74, 水晶存活 >=1
 *   A1 环形巡逻 / A2 护水晶 / A3 平面回血
 * 阶段 B（平面狂暴战）: Y=74, 水晶全毁, HP >50%
 *   B1 环绕玩家 / B2 平面冲锋 / B3 扇形龙息
 * 阶段 C（落地终极战）: Y=68, HP <=50%
 *   C1 强制落地 / C2 终极静止 / C3 终极技能
 *
 * 全局约束：
 * - 高度锁定：禁止自主上升/下降，Y 轴硬钳到目标高度
 * - 范围限制：祭坛中心 (0,0) 半径 30 格，超出强制拉回
 * - 旋转锁定：Yaw 仅水平朝向玩家，XRot=0（永远水平朝前）
 */
public class DragonServerState {

    // ===== 龙的战斗阶段 =====

    public enum DragonPhase {
        PHASE_A, // 护水晶巡逻（水晶存活 >=1）
        PHASE_B, // 平面狂暴战（水晶全毁，血量 >50%）
        PHASE_C  // 落地终极战（血量 <=50%，强制落地）
    }

    // ===== 子状态 =====

    public enum DragonSubState {
        // 阶段 A
        A1_PATROL,      // 环形巡逻
        A2_PROTECT,     // 护水晶
        A3_HEAL,        // 平面回血
        // 阶段 B
        B1_CIRCLE,      // 环绕玩家
        B2_CHARGE,      // 平面冲锋
        B3_BREATH,      // 扇形龙息
        // 阶段 C
        C1_LANDING,     // 强制落地
        C2_STATIONARY,  // 终极静止
        C3_ULTIMATE     // 终极技能
    }

    // ===== C3 终极技能类型 =====

    public enum C3SkillType {
        RING_AOE,      // 环形扩散 AOE
        METEOR_SHOWER, // 流星雨
        SHOCKWAVE      // 冲击波
    }

    // ===== 每龙运行时状态 =====

    public static class DragonRuntimeState {
        public DragonPhase phase = DragonPhase.PHASE_A;
        public DragonSubState subState = DragonSubState.A1_PATROL;

        /** 在当前子状态中已经停留的 tick 数 */
        public int stateTimer = 0;

        /** 巡逻角度（弧度） */
        public double patrolAngle = 0;
        /** 巡逻方向：1=顺时针, -1=逆时针 */
        public int patrolDirection = 1;
        /** 巡逻方向切换计时器 */
        public int patrolSwitchTimer = 0;

        /** 冲锋剩余时长（tick） */
        public int chargeDuration = 0;

        // ===== 冷却（tick） =====
        public int fireballCD = 0;
        public int chargeCD = 0;
        public int breathCD = 0;
        public int aoeCD = 0;
        public int healCD = 0;          // A3 回血触发冷却
        public int healLockoutTimer = 0; // 被打断后的锁定时间

        /** 目标锁定 UUID */
        public UUID lockedTargetUUID = null;
        /** 目标切换冷却（防抖） */
        public int targetSwitchCD = 0;

        /** C1 落地进度计时器 */
        public int landingTimer = 0;

        /** 上次水晶存活数（用于检测水晶被摧毁） */
        public int lastCrystalCount = -1;

        /** 僵直计时器（>0 时龙无法移动和攻击，每 tick 递减） */
        public int stunTimer = 0;

        // ===== C3 终极技能状态 =====

        /** 当前 C3 技能类型（轮换使用） */
        public C3SkillType c3SkillType = C3SkillType.RING_AOE;
        /** C3 技能已执行的 C3 次数（用于轮换） */
        public int c3SkillCount = 0;
        /** 陨石冷却 */
        public int meteorCD = 0;
        /** 冲击波冷却 */
        public int shockwaveCD = 0;
        /** 上次触发阶段转换的阶段（防重复触发） */
        public DragonPhase lastTransitionPhase = null;
    }

    private static final Map<UUID, DragonRuntimeState> dragonStates = new ConcurrentHashMap<>();

    // ===== 锁定参数 =====

    /** 阶段 A/B 的锁定高度（高于柱顶 Y=73，避免穿模） */
    public static final double PHASE_AB_Y = 74.0;

    /** 阶段 C 的锁定高度（低空但高于地面 Y=63） */
    public static final double PHASE_C_Y = 68.0;

    /** 允许的高度偏差 */
    public static final double Y_TOLERANCE = 1.0;

    /** 祭坛中心 */
    public static final double CENTER_X = 0.0;
    public static final double CENTER_Z = 0.0;

    /** 最大活动半径（格） */
    public static final double MAX_RANGE = 30.0;
    public static final double MAX_RANGE_SQ = MAX_RANGE * MAX_RANGE;

    /** 拉回速度 */
    public static final double PULL_BACK_SPEED = 2.0;

    /** Y 轴强制归零速度阈值 */
    public static final double Y_VELOCITY_THRESHOLD = 0.01;

    // ===== 移动速度常量 =====
    public static final double SPEED_PATROL = 2.5;
    public static final double SPEED_CIRCLE = 3.5;
    public static final double SPEED_CHARGE = 6.0;
    public static final double SPEED_RAGE = 3.5;

    // ===== 巡逻参数 =====
    public static final double PATROL_RADIUS = 25.0;
    public static final int PATROL_SWITCH_INTERVAL = 200; // 10秒

    // ===== 环绕玩家参数 =====
    public static final double CIRCLE_MIN_RADIUS = 15.0;
    public static final double CIRCLE_MAX_RADIUS = 20.0;

    // ===== 冷却时间（tick, 20tick=1秒） =====
    public static final int CD_FIREBALL_A = 80;      // 4秒
    public static final int CD_FIREBALL_B = 60;      // 3秒
    public static final int CD_CHARGE = 100;         // 5秒
    public static final int CD_BREATH = 80;          // 4秒
    public static final int CD_AOE = 120;            // 6秒
    public static final int CD_CLOSE_BREATH = 100;   // 5秒
    public static final int CD_HEAL_LOCKOUT = 200;   // 10秒
    public static final int CD_TARGET_SWITCH = 100;  // 5秒目标防抖

    // ===== C3 终极技能参数 =====
    public static final int CD_METEOR = 200;         // 流星雨冷却 10 秒
    public static final int CD_SHOCKWAVE = 160;       // 冲击波冷却 8 秒
    public static final int CD_ULTIMATE_TRIGGER = 120; // C2 → C3 触发间隔 6 秒
    public static final int C3_SKILL_DURATION = 100;  // C3 技能持续 5 秒
    public static final double RING_AOE_MAX_RADIUS = 12.0; // 环形 AOE 最大半径
    public static final double SHOCKWAVE_MAX_RADIUS = 15.0; // 冲击波最大半径

    // ===== 阶段转换阈值 =====
    public static final double PHASE_C_HP_THRESHOLD = 0.5; // 50% HP
    public static final double A3_HEAL_HP_THRESHOLD = 0.8; // 80% HP
    public static final double A3_HEAL_RATE = 0.02;        // 2%/s = 0.1%/tick

    // ===== 距离阈值 =====
    public static final double PROTECT_CRYSTAL_RANGE = 10.0;
    public static final double CHARGE_MIN_RANGE = 10.0;
    public static final double CHARGE_MAX_RANGE = 30.0;
    public static final double BREATH_RANGE = 10.0;
    public static final double AOE_RADIUS = 8.0;
    public static final int CHARGE_DURATION = 30;     // 1.5秒
    public static final int LANDING_DURATION = 40;    // 2秒

    // ===== 状态管理 =====

    /** 获取或创建龙的运行时状态 */
    public static DragonRuntimeState getOrCreateState(UUID dragonUUID) {
        return dragonStates.computeIfAbsent(dragonUUID, k -> new DragonRuntimeState());
    }

    /** 获取龙的当前阶段 */
    public static DragonPhase getPhase(UUID dragonUUID) {
        DragonRuntimeState state = dragonStates.get(dragonUUID);
        return state != null ? state.phase : DragonPhase.PHASE_A;
    }

    /** 获取龙当前阶段的锁定 Y */
    public static double getLockedY(UUID dragonUUID) {
        DragonPhase phase = getPhase(dragonUUID);
        return phase == DragonPhase.PHASE_C ? PHASE_C_Y : PHASE_AB_Y;
    }

    /** 清除龙的状态（龙死亡/卸载时调用） */
    public static void clearDragon(UUID dragonUUID) {
        dragonStates.remove(dragonUUID);
    }

    // ===== 鸟瞰玩家查找 =====

    /**
     * 查找与龙同一维度中的任意鸟瞰玩家（无距离限制）。
     */
    public static ServerPlayer getBirdviewPlayerForDragon(EnderDragon dragon) {
        if (dragon.level().isClientSide()) return null;
        if (!(dragon.level() instanceof ServerLevel serverLevel)) return null;
        return BirdviewServerState.getAnyBirdviewPlayerInLevel(serverLevel);
    }

    /**
     * 判断龙是否应被俯视角约束。
     */
    public static boolean shouldConstrainDragon(EnderDragon dragon) {
        if (dragon == null) return false;
        if (dragon.level().isClientSide()) return false;
        if (!dragon.isAlive()) return false;
        if (dragon.getHealth() <= 0) return false;
        return getBirdviewPlayerForDragon(dragon) != null;
    }

    // ===== 水晶计数 =====

    /** 计算服务器端存活的末影水晶数量（祭坛中心 60 格范围内） */
    public static int countAliveCrystals(ServerLevel level) {
        return level.getEntitiesOfClass(EndCrystal.class,
                net.minecraft.world.phys.AABB.ofSize(
                        net.minecraft.world.phys.Vec3.ZERO,
                        120, 120, 120),
                e -> e.isAlive()).size();
    }
}
