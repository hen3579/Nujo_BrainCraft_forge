package com.Hen3579.Nujomod.Client.Events;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * 鸟瞰模式 Tab 锁定目标系统。
 * Tab 键会自动搜索 64 格内最近的敌对生物并锁定，
 * 锁定后左键攻击直接攻击锁定目标，无需鼠标对准。
 * 支持自动切换弓箭满弦瞄准射击。
 */
public class LockTargetSystem {

    /** 锁定搜索范围（方块距离） */
    public static final double SEARCH_RANGE = 64.0;

    /** 近战攻击距离阈值（超过此距离则自动用弓箭） */
    public static final double MELEE_RANGE = 4.0;

    /** 弓箭完全蓄力所需刻数（20 ticks = 1秒） */
    public static final int BOW_CHARGE_TICKS = 20;

    /** 自动近战攻击的冷却刻数（10 ticks = 0.5 秒） */
    private static final int MELEE_COOLDOWN_TICKS = 10;

    @Nullable
    private static Entity lockedTarget = null;

    /** 最近锁定时间戳（毫秒，用于脉动动画计时） */
    private static long lockTimeMs = 0;

    // ===== 弓箭自动蓄力状态 =====

    private static boolean bowCharging = false;
    private static int bowChargeStartTick = -1;

    /** 上次左键攻击时是否已射出箭（防同一帧多次触发） */
    private static boolean shotFiredThisPress = false;

    // ===== 自动近战冷却 =====

    private static int meleeCooldownTicks = 0;

    // ===== 近战追杀状态 =====

    private static boolean isChasing = false;

    // ===== 锁定状态查询 =====

    /** 是否处于锁定状态 */
    public static boolean isLocked() {
        if (lockedTarget != null && (!lockedTarget.isAlive() || lockedTarget.isRemoved())) {
            unlockTarget();
        }
        return lockedTarget != null;
    }

    /** 获取当前锁定目标 */
    @Nullable
    public static Entity getLockedTarget() {
        return lockedTarget;
    }

    /** 获取锁定时长（毫秒） */
    public static long getLockDurationMs() {
        if (!isLocked()) return 0;
        return System.currentTimeMillis() - lockTimeMs;
    }

    // ===== 锁定/解锁操作 =====

    public static void toggleLock() {
        if (isLocked()) {
            unlockTarget();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Entity nearest = findNearestHostile(mc.player);
        if (nearest != null) {
            lockToTarget(nearest);
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§b[锁定] §f已锁定 §e" + nearest.getDisplayName().getString()
                    ), true
            );
        } else {
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§7[锁定] §f64 格内没有敌对生物"
                    ), true
            );
        }
    }

    public static void lockToTarget(Entity entity) {
        lockedTarget = entity;
        lockTimeMs = System.currentTimeMillis();
    }

    public static void unlockTarget() {
        cancelBowCharge();
        stopChasing();
        lockedTarget = null;
        lockTimeMs = 0;
    }

    // ===== 敌对生物搜索 =====

    @Nullable
    public static Entity findNearestHostile(Player player) {
        AABB searchBox = player.getBoundingBox().inflate(SEARCH_RANGE);
        List<Entity> candidates = player.level().getEntities(player, searchBox, HOSTILE_FILTER);

        return candidates.stream()
                .min(Comparator.comparingDouble(
                        e -> e.distanceToSqr(player)))
                .orElse(null);
    }

    private static final Predicate<Entity> HOSTILE_FILTER = entity -> {
        if (!entity.isAlive() || entity.isRemoved()) return false;
        if (entity instanceof EnderDragon || entity instanceof WitherBoss) return true;
        if (entity instanceof Enemy) return true;
        if (entity instanceof IronGolem golem) return golem.getTarget() != null;
        if (entity instanceof Wolf wolf) return wolf.isAngry();
        return false;
    };

    // ===== 弓箭自动射击 =====

    /** 是否正在蓄力弓箭 */
    public static boolean isBowCharging() {
        return bowCharging;
    }

    /** 左键攻击：根据主手武器类型自动选择弓箭射击或近战追杀 */
    public static boolean handleLockedAttack(Minecraft mc) {
        if (!isLocked()) return false;
        Entity target = getLockedTarget();
        if (target == null) return false;

        // 停止上次的追杀（重新点击左键重新触发）
        stopChasing();

        // 判断主手武器类型
        boolean holdingBow = mc.player.getMainHandItem().getItem() instanceof BowItem;

        if (holdingBow) {
            // 弓 → 自动蓄力射一箭
            if (!hasBowInHotbar(mc.player)) {
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§c[锁定] §f快捷栏中没有弓，无法远程攻击"),
                        true
                );
                return false;
            }
            return startBowAttack(mc, target);
        } else {
            // 近战武器/空手 → 自动追杀目标致死
            return startMeleeChase(mc, target);
        }
    }

    /** 近战攻击 */
    private static boolean doMeleeAttack(Minecraft mc, Entity target) {
        if (!(target instanceof LivingEntity living)) return false;
        faceEntity(mc.player, living);
        mc.gameMode.attack(mc.player, living);
        mc.player.swing(InteractionHand.MAIN_HAND);
        shotFiredThisPress = true;
        return true;
    }

    /** 开始弓箭攻击（切换到弓、瞄准、开始蓄力） */
    private static boolean startBowAttack(Minecraft mc, Entity target) {
        cancelBowCharge(); // 取消可能残留的旧蓄力

        // 切换到弓
        if (!switchToBow(mc.player)) return false;

        // 面向目标（计算 yaw + pitch 以便箭准确命中）
        faceEntityForBow(mc.player, target);

        // 开始使用弓（模拟右键点击）
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);

        // 立即在客户端设置使用物品状态（即时视觉反馈，不等服务器确认）
        mc.player.startUsingItem(InteractionHand.MAIN_HAND);

        // 记录蓄力起始刻
        bowCharging = true;
        bowChargeStartTick = mc.player.tickCount;
        shotFiredThisPress = true;

        return true;
    }

    /** 每刻更新弓箭蓄力状态（在 handleEndPhase 中调用） */
    public static void tickBowCharge(Minecraft mc) {
        if (!bowCharging || mc.player == null) return;

        Entity target = getLockedTarget();
        if (target == null || !target.isAlive()) {
            cancelBowCharge();
            return;
        }

        // 每刻都面向目标（防止玩家转动导致脱靶）
        faceEntityForBow(mc.player, target);

        int elapsedTicks = mc.player.tickCount - bowChargeStartTick;
        if (elapsedTicks >= BOW_CHARGE_TICKS) {
            // 满弦 → 释放射击

            // 1. 客户端动画清理
            mc.player.releaseUsingItem();

            // 2. 发送 ServerboundPlayerActionPacket(RELEASE_USE_ITEM) 到服务器
            //    服务器收到后会调用 serverPlayer.releaseUsingItem() → 生成箭矢实体
            if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                        BlockPos.ZERO,
                        Direction.DOWN
                ));
            }

            bowCharging = false;
        }
    }

    /** 取消弓箭蓄力 */
    public static void cancelBowCharge() {
        if (bowCharging) {
            bowCharging = false;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.isUsingItem()) {
                mc.player.releaseUsingItem(); // 释放当前物品
            }
        }
    }

    // ===== 近战追杀系统 =====

    /** 是否正在追杀中 */
    public static boolean isChasing() {
        return isChasing;
    }

    /** 开始近战追杀目标 */
    private static boolean startMeleeChase(Minecraft mc, Entity target) {
        if (!(target instanceof LivingEntity)) return false;
        isChasing = true;
        meleeCooldownTicks = 0; // 立即可以攻击

        // 设置鼠标移动目标到锁定目标位置，让点击移动系统驱动玩家走向目标
        if (target.isAlive()) {
            BirdviewClientEvent.setMoveTarget(target.position());
        }

        mc.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§c[追杀] §f开始追杀 §e" + target.getDisplayName().getString()),
                true
        );
        return true;
    }

    /** 停止追杀 */
    public static void stopChasing() {
        isChasing = false;
        BirdviewClientEvent.clearMoveTarget();
        meleeCooldownTicks = 0;
    }

    /**
     * 每刻追杀逻辑（在 handleEndPhase 中调用）。
     * 1. 更新移动目标到锁定目标的当前位置
     * 2. 进入近战范围时自动攻击
     * 3. 目标死亡时自动停止追杀并解锁
     */
    public static void chaseTick(Minecraft mc) {
        if (!isChasing || !isLocked() || mc.player == null) return;

        Entity target = getLockedTarget();
        if (target == null || !target.isAlive()) {
            // 目标死亡 → 停止追杀并解锁
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§7[追杀] §f目标已消灭"),
                    true
            );
            stopChasing();
            unlockTarget();
            return;
        }

        // 每刻更新移动目标到锁定目标的当前位置（目标会移动）
        BirdviewClientEvent.setMoveTarget(target.position());

        double dist = mc.player.distanceTo(target);

        if (dist <= MELEE_RANGE && --meleeCooldownTicks <= 0) {
            if (target instanceof LivingEntity living) {
                faceEntity(mc.player, living);
                mc.gameMode.attack(mc.player, living);
                mc.player.swing(InteractionHand.MAIN_HAND);
                meleeCooldownTicks = MELEE_COOLDOWN_TICKS;
            }
        }
    }

    /** 重置射击触发标记（每次左键按下时重置） */
    public static void resetShotFiredFlag() {
        shotFiredThisPress = false;
    }

    /** 是否已在本轮左键触发时射出 */
    public static boolean isShotFired() {
        return shotFiredThisPress;
    }

    // ===== 辅助方法 =====

    /** 让玩家面向目标实体（水平 + 垂直） */
    private static void faceEntity(Player player, Entity target) {
        Vec3 toTarget = target.position().subtract(player.position());
        float yaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(toTarget.y, Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z)));
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setXRot(pitch);
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
    }

    /** 面向目标，使用弓箭瞄准（考虑瞄准目标的中心偏上位置） */
    private static void faceEntityForBow(Player player, Entity target) {
        // 瞄准目标的中心偏上（约眼部高度），确保箭能命中
        AABB bb = target.getBoundingBox();
        double aimY = (bb.minY + bb.maxY) * 0.6; // 略低于头顶

        // 根据距离略微抬高仰角（补偿箭的下坠）
        double dist = player.distanceTo(target);
        double heightDiff = aimY - player.getEyeY();
        double pitchAdjust = 0;
        if (dist > 15) {
            pitchAdjust = Math.toDegrees(Math.atan2(dist * 0.02, dist)); // 约 1.15° 补偿
        } else if (dist > 30) {
            pitchAdjust = Math.toDegrees(Math.atan2(dist * 0.05, dist)); // 约 2.86° 补偿
        }

        Vec3 toTarget = new Vec3(target.getX() - player.getX(), aimY - player.getEyeY(), target.getZ() - player.getZ());
        float yaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(toTarget.y, Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z)));

        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setXRot(pitch - (float) pitchAdjust);
        player.xRotO = pitch - (float) pitchAdjust;
        player.setYHeadRot(yaw);
    }

    /** 检查快捷栏或手中是否有弓 */
    public static boolean hasBowInHotbar(Player player) {
        if (player.getMainHandItem().getItem() instanceof BowItem) return true;
        if (player.getOffhandItem().getItem() instanceof BowItem) return true;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).getItem() instanceof BowItem) {
                return true;
            }
        }
        return false;
    }

    /** 切换到快捷栏中的弓 */
    public static boolean switchToBow(Player player) {
        if (player.getMainHandItem().getItem() instanceof BowItem) return true;
        if (player.getOffhandItem().getItem() instanceof BowItem) return true;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).getItem() instanceof BowItem) {
                player.getInventory().selected = i;
                return true;
            }
        }
        return false;
    }
}