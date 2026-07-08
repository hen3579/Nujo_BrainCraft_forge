package com.Hen3579.Nujomod.Client.Events;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * 鸟瞰模式 Tab 锁定目标系统 + 左键攻击（近战/远程）系统。
 */
public class LockTargetSystem {

    /** 锁定搜索范围（方块距离） */
    public static final double SEARCH_RANGE = 64.0;

    /** 近战攻击距离阈值 */
    public static final double MELEE_RANGE = 4.0;

    /** 自动近战攻击的冷却刻数（10 ticks = 0.5 秒） */
    private static final int MELEE_COOLDOWN_TICKS = 10;

    @Nullable
    private static Entity lockedTarget = null;

    /** 最近锁定时间戳（毫秒，用于脉动动画计时） */
    private static long lockTimeMs = 0;

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
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (isLocked()) {
            // 已锁定 → 切换到下一个敌对生物
            cycleToNextHostile(mc);
            return;
        }

        // 未锁定 → 锁定最近的敌对生物
        Entity nearest = findNearestHostile(mc.player);
        if (nearest != null) {
            lockToTarget(nearest);
            if (nearest instanceof LivingEntity living) {
                startMeleeChase(mc, living);
            }
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

    /** 切换到下一个敌对生物（按距离排序循环） */
    private static void cycleToNextHostile(Minecraft mc) {
        if (mc.player == null) return;
        List<Entity> sorted = findAllHostileSorted(mc.player);
        if (sorted.isEmpty()) {
            unlockTarget();
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§7[锁定] §f没有敌对生物"), true
            );
            return;
        }

        // 找当前锁定目标在列表中的位置
        int currentIdx = -1;
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i) == lockedTarget) {
                currentIdx = i;
                break;
            }
        }

        if (currentIdx < 0) {
            // 当前目标已不在列表中（可能已死亡）→ 锁定最近的
            lockToTarget(sorted.get(0));
            if (sorted.get(0) instanceof LivingEntity living0) {
                startMeleeChase(mc, living0);
            }
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§b[切换] §f锁定 §e" + sorted.get(0).getDisplayName().getString()
                    ), true
            );
            return;
        }

        // 循环到下一个
        int nextIdx = (currentIdx + 1) % sorted.size();
        if (nextIdx == currentIdx) {
            // 只有一个目标
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§7[锁定] §f仅此一个敌对生物 §e" + sorted.get(currentIdx).getDisplayName().getString()
                    ), true
            );
            return;
        }

        Entity next = sorted.get(nextIdx);
        stopChasing(); // 先停止旧的追杀
        lockToTarget(next);
        if (next instanceof LivingEntity livingNext) {
            startMeleeChase(mc, livingNext);
        }
        mc.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                        "§b[切换] §f切换到 §e" + next.getDisplayName().getString()
                ), true
        );
    }

    public static void lockToTarget(Entity entity) {
        lockedTarget = entity;
        lockTimeMs = System.currentTimeMillis();
    }

    public static void unlockTarget() {
        stopChasing();
        lockedTarget = null;
        lockTimeMs = 0;
    }

    // ===== 敌对生物搜索 =====

    @Nullable
    public static Entity findNearestHostile(Player player) {
        List<Entity> sorted = findAllHostileSorted(player);
        return sorted.isEmpty() ? null : sorted.get(0);
    }

    /** 获取按距离排序的所有敌对生物列表 */
    private static List<Entity> findAllHostileSorted(Player player) {
        AABB searchBox = player.getBoundingBox().inflate(SEARCH_RANGE);
        List<Entity> candidates = player.level().getEntities(player, searchBox, HOSTILE_FILTER);
        candidates.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        return candidates;
    }

    private static final Predicate<Entity> HOSTILE_FILTER = entity -> {
        if (!entity.isAlive() || entity.isRemoved()) return false;
        if (entity instanceof EndCrystal) return true;
        if (entity instanceof EnderDragon || entity instanceof WitherBoss) return true;
        if (entity instanceof Enemy) return true;
        if (entity instanceof IronGolem golem) return golem.getTarget() != null;
        if (entity instanceof Wolf wolf) return wolf.isAngry();
        return false;
    };

    // ===== 近战追杀系统 =====

    /** 是否正在追杀中 */
    public static boolean isChasing() {
        return isChasing;
    }

    /** 开始近战追杀（由外部通过 Tab 锁敌后触发） */
    public static boolean startMeleeChase(Minecraft mc, Entity target) {
        if (!(target instanceof LivingEntity)) return false;
        isChasing = true;
        meleeCooldownTicks = 0;
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

    /** 每刻追杀逻辑（在 handleEndPhase 中调用） */
    public static void chaseTick(Minecraft mc) {
        if (!isChasing || !isLocked() || mc.player == null) return;

        Entity target = getLockedTarget();
        if (target == null || !target.isAlive()) {
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§7[追杀] §f目标已消灭"), true
            );
            stopChasing();
            unlockTarget();
            return;
        }

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

    // ===== 辅助方法 =====

    /** 让玩家面向目标实体（用于近战攻击时瞄准） */
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

    // ======================================================================
    //  远程攻击系统
    //  在鸟瞰模式下，左键点击悬停的生物 → 自动瞄准 + 发射 + 弹道计算
    // ======================================================================

    /** 弓蓄力/远程攻击冷却进度指示状态 */
    public enum RangedState {
        IDLE,       // 无远程动作
        BOW_CHARGING, // 弓正在蓄力（等待自动释放）
    }

    private static RangedState rangedState = RangedState.IDLE;

    /** 当前远程攻击的目标（可能被多个子系统引用） */
    @Nullable
    private static Entity rangedTarget = null;

    /** 弓蓄力已过刻数 */
    private static int bowChargeTicks = 0;

    /** 弓达到此刻数后自动释放（10 tick = 0.5 秒，power = 0.5 / 箭速 1.5 blocks/tick） */
    private static final int BOW_CHARGE_TICKS_NEEDED = 10;

    /** 远程攻击全局冷却刻数（防止连射滥发） */
    private static int globalRangedCooldownTicks = 0;
    private static final int GLOBAL_RANGED_COOLDOWN_TICKS = 5; // 0.25s

    /**
     * 弓释放当 tick 是否跳过 pitch 重置。
     * 服务端在处理 RELEASE_USE_ITEM 包时读取 player.getXRot()，
     * 如果在同一 tick 被重置为 0，箭就会水平飞出。
     */
    private static boolean skipPitchReset = false;

    // ===== 远程武器检测 =====

    /** 检测手持物品是否为远程武器 */
    public static boolean isRangedWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        // 弓、弩、三叉戟
        if (item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem) return true;
        // 可投掷物：雪球、鸡蛋、末影珍珠、经验瓶、喷溅药水
        if (item instanceof SnowballItem || item instanceof EggItem
                || item instanceof ThrowablePotionItem || item instanceof ExperienceBottleItem) return true;
        // 末影珍珠 Item 类名在 1.20.1 映射中用实际注册对象判断
        if (item == net.minecraft.world.item.Items.ENDER_PEARL) return true;
        return false;
    }

    /** 获取该远程武器的弹丸初速（blocks/tick），用于弹道计算 */
    private static float getProjectileSpeed(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof BowItem) {
            // 弓的弹速 = power * 3.0，满蓄力时 3.0
            return 3.0f;
        }
        if (item instanceof CrossbowItem) {
            return 3.15f; // 弩固定 3.15
        }
        if (item instanceof TridentItem) {
            return 2.5f;
        }
        if (item instanceof SnowballItem || item instanceof EggItem
                || item == net.minecraft.world.item.Items.ENDER_PEARL) {
            return 1.5f;
        }
        if (item instanceof ThrowablePotionItem) {
            return 0.75f;
        }
        if (item instanceof ExperienceBottleItem) {
            return 0.7f;
        }
        return 1.5f; // 默认
    }

    // ===== 弹道计算（抛物线轨迹，考虑重力） =====

    /**
     * 精确弹道计算。
     * Minecraft 箭重力加速度 g = 0.05 blocks/tick²。
     * 使用二次方程求解最优仰角，使弹丸准确落至目标位置。
     *
     * @return [yaw, pitch] 玩家应旋转到的角度
     */
    public static float[] calculateBallisticAim(Player player, Entity target, float projectileSpeed) {
        // 玩家眼睛位置
        Vec3 eyePos = player.getEyePosition(1.0f);
        // 目标瞄准点：LivingEntity → 眼睛位置；非 LivingEntity → 中心
        Vec3 targetPos;
        if (target instanceof LivingEntity living) {
            targetPos = living.getEyePosition(1.0f);
        } else {
            targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        }

        Vec3 toTarget = targetPos.subtract(eyePos);
        double dxz = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        double dy = toTarget.y;

        // 水平偏航角（始终正确）
        float yaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));

        float pitch;
        if (dxz < 0.01) {
            // 目标在正上方/下方 → 直接指向
            pitch = (float) Math.toDegrees(-Math.atan2(dy, 0.01));
        } else if (projectileSpeed <= 0) {
            // 无重力弹道
            pitch = (float) Math.toDegrees(-Math.atan2(dy, dxz));
        } else {
            // === 带重力的抛物线弹道 ===
            double g = 0.05;
            double v = projectileSpeed;
            double v2 = v * v;

            // 二次方程: k * tan²(θ) - dxz * tan(θ) + (k + dy) = 0
            // 其中 k = 0.5 * g * dxz² / v²
            double k = 0.5 * g * dxz * dxz / v2;

            // 判别式 Δ = dxz² - 4k(k + dy)
            double discriminant = dxz * dxz - 4 * k * (k + dy);

            if (discriminant >= 0) {
                // 有两个解，选较小的 tan(θ) → 较平直的弹道（更可靠）
                double sqrtDisc = Math.sqrt(discriminant);
                double tanPitch = (dxz - sqrtDisc) / (2 * k);
                pitch = (float) Math.toDegrees(Math.atan(tanPitch));
            } else {
                // 判别式 < 0 → 无法用抛物线命中（目标太远/太高）
                // 回退：直接瞄准目标（弹道会有下坠，但误差不大）
                pitch = (float) Math.toDegrees(-Math.atan2(dy, dxz));
            }
        }

        return new float[]{yaw, pitch};
    }

    /** 获取弹丸重力加速度（blocks/tick²），根据武器类型变化 */
    private static double getProjectileGravity(ItemStack stack) {
        if (stack.isEmpty()) return 0.05;
        Item item = stack.getItem();
        if (item instanceof SnowballItem || item instanceof EggItem
                || item instanceof ThrowablePotionItem
                || item == net.minecraft.world.item.Items.ENDER_PEARL) {
            return 0.03; // 投掷物标准重力
        }
        if (item instanceof ExperienceBottleItem) {
            return 0.07; // 经验瓶重力较大
        }
        return 0.05; // 箭/三叉戟
    }

    /** 设置玩家面向远程目标（含弹道补偿） */
    public static void faceEntityForRanged(Player player, Entity target, float projectileSpeed) {
        float[] aim = calculateBallisticAim(player, target, projectileSpeed);
        player.setYRot(aim[0]);
        player.yRotO = aim[0];
        player.setXRot(aim[1]);
        player.xRotO = aim[1];
        player.setYHeadRot(aim[0]);
        player.yBodyRot = aim[0];
    }

    // ===== 远程攻击执行 =====

    /** 获取当前远程攻击目标 */
    @Nullable
    public static Entity getRangedTarget() {
        return rangedTarget;
    }

    /** 获取当前远程攻击状态 */
    public static RangedState getRangedState() {
        return rangedState;
    }

    /** 弓蓄力进度 [0, 1] */
    public static float getBowChargeProgress() {
        if (rangedState != RangedState.BOW_CHARGING) return 0;
        return Math.min(1.0f, (float) bowChargeTicks / BOW_CHARGE_TICKS_NEEDED);
    }

    /** 是否应跳过本 tick 的 pitch 重置（弓已释放但服务端尚未处理包） */
    public static boolean shouldSkipPitchReset() {
        return skipPitchReset;
    }

    /** 标记 pitch 重置已处理（由 ClientEventHandler 调用） */
    public static void clearSkipPitchReset() {
        skipPitchReset = false;
    }

    /**
     * 统一的左键攻击入口。
     * 手持远程武器 → 远程攻击；手持近战武器 → 近战攻击。
     * 由 ClientEventHandler 在左键消费时调用。
     *
     * @return true = 成功发起攻击
     */
    public static boolean tryAttackOnHover(Minecraft mc) {
        if (mc.player == null) return false;

        // 从悬停检测结果获取目标
        HitResult hoverHit = BirdviewClientEvent.getHoveredHitResult();
        if (hoverHit == null || hoverHit.getType() != HitResult.Type.ENTITY) return false;

        Entity target = ((EntityHitResult) hoverHit).getEntity();
        if (!target.isAlive()) return false;

        ItemStack heldItem = mc.player.getMainHandItem();

        // 远程武器 → 远程攻击
        if (isRangedWeapon(heldItem)) {
            return tryRangedAttack(mc, target, heldItem);
        }

        // 近战武器/空手 → 近战攻击
        return tryMeleeAttack(mc, target);
    }

    // ===== 近战攻击 =====

    /**
     * 左键近战攻击悬停的生物。
     * 如果目标是敌对生物，自动锁定并开始追杀。
     */
    private static boolean tryMeleeAttack(Minecraft mc, Entity target) {
        if (globalRangedCooldownTicks > 0) return false;

        Player player = mc.player;

        // LivingEntity: 敌对生物自动锁定并追杀
        if (target instanceof LivingEntity living) {
            if (!isLocked() && HOSTILE_FILTER.test(living)) {
                lockToTarget(target);
                startMeleeChase(mc, living);
            } else if (getLockedTarget() == target && !isChasing) {
                startMeleeChase(mc, living);
            }
        }

        // 让玩家面向目标
        Vec3 toTarget = target.position().subtract(player.position());
        float targetYaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        player.setYRot(targetYaw);
        player.yRotO = targetYaw;
        player.setYHeadRot(targetYaw);

        // 触发玩家攻击动作（对任何 Entity 有效，包括 EndCrystal）
        mc.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND);

        startRangedCooldown();
        return true;
    }

    // ===== 远程攻击 =====

    /**
     * 尝试对指定目标发起远程攻击。
     */
    private static boolean tryRangedAttack(Minecraft mc, Entity target, ItemStack heldItem) {
        // 冷却检测
        if (globalRangedCooldownTicks > 0) return false;
        if (rangedState != RangedState.IDLE) return false;

        Player player = mc.player;
        if (player == null) return false;

        Item item = heldItem.getItem();

        // 计算弹丸速度
        float projectileSpeed = getProjectileSpeed(heldItem);

        // 面向目标（精确弹道计算）
        faceEntityForRanged(player, target, projectileSpeed);

        if (item instanceof BowItem) {
            // === 弓：需要蓄力后释放 ===
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            rangedState = RangedState.BOW_CHARGING;
            bowChargeTicks = 0;
            rangedTarget = target;
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§6[弓] §e蓄力中..."), true
            );

        } else if (item instanceof CrossbowItem) {
            // === 弩：专用流程，直接创建箭矢实体 ===
            fireCrossbowOnServer(mc, player, target, heldItem, projectileSpeed);
            skipPitchReset = true;
            startRangedCooldown();
            rangedTarget = target;

        } else if (item instanceof TridentItem) {
            // === 三叉戟：直接创建投掷三叉戟实体 ===
            fireTridentOnServer(mc, player, target, heldItem, projectileSpeed);
            skipPitchReset = true;
            startRangedCooldown();
            rangedTarget = target;

        } else {
            // === 投掷物（雪球、鸡蛋、末影珍珠、药水、经验瓶） ===
            fireThrowableOnServer(mc, player, target, heldItem, projectileSpeed);
            skipPitchReset = true;
            startRangedCooldown();
            rangedTarget = target;
        }

        return true;
    }

    /**
     * 弩专用：直接在服务端创建箭矢实体，完全绕过 vanilla CrossbowItem 流程。
     *
     * 单机：直接操控 ServerPlayer + ServerLevel 生成精确箭矢。
     * 联机 fallback：强制上膛客户端 → 发 useItem 包。
     */
    private static void fireCrossbowOnServer(Minecraft mc, Player player, Entity target,
                                               ItemStack heldItem, float projectileSpeed) {
        MinecraftServer server = mc.getSingleplayerServer();

        if (server != null) {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
            if (serverPlayer != null && serverPlayer.level() instanceof ServerLevel serverLevel) {
                // 同步旋转到服务端
                float[] aim = calculateBallisticAim(player, target, projectileSpeed);
                serverPlayer.setYRot(aim[0]); serverPlayer.yRotO = aim[0];
                serverPlayer.setXRot(aim[1]); serverPlayer.xRotO = aim[1];
                serverPlayer.setYHeadRot(aim[0]); serverPlayer.yBodyRot = aim[0];

                // 在服务端创建箭矢，应用弩附魔效果
                ItemStack crossbowStack = serverPlayer.getMainHandItem();
                net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(
                        serverLevel, serverPlayer);
                Vec3 eyePos = serverPlayer.getEyePosition(1.0f);
                arrow.setPos(eyePos.x, eyePos.y - 0.1, eyePos.z);
                arrow.setOwner(serverPlayer);
                arrow.setCritArrow(true);

                // 应用弩伤害加成（CrossbowItem 默认箭矢伤害更高）
                arrow.setBaseDamage(arrow.getBaseDamage() + 1.0); // 弩比弓伤害略高

                // 应用穿透附魔
                int piercing = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PIERCING, crossbowStack);
                if (piercing > 0) {
                    arrow.setPierceLevel((byte) piercing);
                }

                // 精确抛物线弹道
                Vec3 targetPos = target.getEyePosition(1.0f);
                Vec3 toTarget = targetPos.subtract(arrow.position());
                double dxz = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
                double dy = toTarget.y;
                double v = projectileSpeed;
                double g = 0.05;
                Vec3 motion;
                if (dxz < 0.1) {
                    motion = new Vec3(0, v, 0);
                } else {
                    double k = 0.5 * g * dxz * dxz / (v * v);
                    double disc = dxz * dxz - 4 * k * (k + dy);
                    if (disc >= 0) {
                        double tanP = (dxz - Math.sqrt(disc)) / (2 * k);
                        double pitch = Math.atan(tanP);
                        double horiz = v * Math.cos(pitch);
                        motion = new Vec3(toTarget.x / dxz * horiz, v * Math.sin(pitch), toTarget.z / dxz * horiz);
                    } else {
                        motion = toTarget.normalize().scale(v);
                    }
                }
                arrow.setDeltaMovement(motion);
                arrow.hasImpulse = true;
                arrow.setYRot((float) Math.toDegrees(Math.atan2(-motion.x, motion.z)));
                arrow.setXRot((float) Math.toDegrees(-Math.atan2(motion.y, Math.sqrt(motion.x * motion.x + motion.z * motion.z))));

                serverLevel.addFreshEntity(arrow);
                consumeCrossbowAmmo(serverPlayer, heldItem);

                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§d[弩] §a命中！"), true
                );
                return;
            }
        }

        // ===== 联机 fallback =====
        if (!CrossbowItem.isCharged(heldItem)) CrossbowItem.setCharged(heldItem, true);
        mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        player.swing(InteractionHand.MAIN_HAND);
        mc.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§d[弩] §a发射！"), true
        );
    }

    /** 消耗弩的弹药（移除背包中一根箭矢，创造模式跳过） */
    private static void consumeCrossbowAmmo(ServerPlayer player, ItemStack crossbow) {
        if (player.getAbilities().instabuild) return;
        ItemStack ammo = player.getProjectile(crossbow);
        if (!ammo.isEmpty()) ammo.shrink(1);
    }

    /**
     * 三叉戟专用：直接在服务端创建 ThrownTrident 实体，绕过 vanilla TridentItem 流程。
     *
     * 单机：创建投掷三叉戟并设精确弹道。
     * 联机 fallback：直接发 useItem 包。
     */
    private static void fireTridentOnServer(Minecraft mc, Player player, Entity target,
                                             ItemStack heldItem, float projectileSpeed) {
        MinecraftServer server = mc.getSingleplayerServer();

        if (server != null) {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
            if (serverPlayer != null && serverPlayer.level() instanceof ServerLevel serverLevel) {
                float[] aim = calculateBallisticAim(player, target, projectileSpeed);
                serverPlayer.setYRot(aim[0]); serverPlayer.yRotO = aim[0];
                serverPlayer.setXRot(aim[1]); serverPlayer.xRotO = aim[1];
                serverPlayer.setYHeadRot(aim[0]); serverPlayer.yBodyRot = aim[0];

                // 创建投掷三叉戟
                net.minecraft.world.entity.projectile.ThrownTrident trident =
                        new net.minecraft.world.entity.projectile.ThrownTrident(serverLevel, serverPlayer, heldItem);
                Vec3 eyePos = serverPlayer.getEyePosition(1.0f);
                trident.setPos(eyePos.x, eyePos.y - 0.1, eyePos.z);
                trident.setOwner(serverPlayer);

                // 抛物线弹道（g=0.05）
                Vec3 targetPos = target.getEyePosition(1.0f);
                Vec3 toTarget = targetPos.subtract(trident.position());
                double dxz = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
                double dy = toTarget.y;
                double v = projectileSpeed;
                double g = 0.05;
                Vec3 motion;
                if (dxz < 0.1) {
                    motion = new Vec3(0, v, 0);
                } else {
                    double k = 0.5 * g * dxz * dxz / (v * v);
                    double disc = dxz * dxz - 4 * k * (k + dy);
                    if (disc >= 0) {
                        double tanP = (dxz - Math.sqrt(disc)) / (2 * k);
                        double pitch = Math.atan(tanP);
                        double horiz = v * Math.cos(pitch);
                        motion = new Vec3(toTarget.x / dxz * horiz, v * Math.sin(pitch), toTarget.z / dxz * horiz);
                    } else {
                        motion = toTarget.normalize().scale(v);
                    }
                }
                trident.setDeltaMovement(motion);
                trident.hasImpulse = true;
                trident.setYRot((float) Math.toDegrees(Math.atan2(-motion.x, motion.z)));
                trident.setXRot((float) Math.toDegrees(-Math.atan2(motion.y, Math.sqrt(motion.x * motion.x + motion.z * motion.z))));

                serverLevel.addFreshEntity(trident);

                // 消耗三叉戟耐久
                if (!serverPlayer.getAbilities().instabuild) {
                    heldItem.hurtAndBreak(1, serverPlayer, p -> {});
                }

                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§3[三叉戟] §b投掷！"), true
                );
                player.swing(InteractionHand.MAIN_HAND);
                return;
            }
        }

        // 联机 fallback
        mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        player.swing(InteractionHand.MAIN_HAND);
    }

    /**
     * 投掷物通用：根据物品类型创建对应的 ThrowableProjectile 实体。
     * 支持：雪球、鸡蛋、末影珍珠、喷溅药水、滞留药水、经验瓶。
     *
     * 单机：直接创建弹射物实体到服务端。
     * 联机 fallback：发 useItem 包。
     */
    private static void fireThrowableOnServer(Minecraft mc, Player player, Entity target,
                                               ItemStack heldItem, float projectileSpeed) {
        MinecraftServer server = mc.getSingleplayerServer();

        if (server != null) {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
            if (serverPlayer != null && serverPlayer.level() instanceof ServerLevel serverLevel) {
                float[] aim = calculateBallisticAim(player, target, projectileSpeed);
                serverPlayer.setYRot(aim[0]); serverPlayer.yRotO = aim[0];
                serverPlayer.setXRot(aim[1]); serverPlayer.xRotO = aim[1];
                serverPlayer.setYHeadRot(aim[0]); serverPlayer.yBodyRot = aim[0];

                // 根据物品类型创建对应弹射物
                net.minecraft.world.entity.Entity projectile = createThrowableEntity(serverLevel, serverPlayer, heldItem);
                if (projectile == null) return;

                Vec3 eyePos = serverPlayer.getEyePosition(1.0f);
                projectile.setPos(eyePos.x, eyePos.y - 0.1, eyePos.z);

                // 抛物线弹道
                double g = getProjectileGravity(heldItem);
                Vec3 targetPos = target.getEyePosition(1.0f);
                Vec3 toTarget = targetPos.subtract(projectile.position());
                double dxz = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
                double dy = toTarget.y;
                double v = projectileSpeed;
                Vec3 motion;
                if (dxz < 0.1) {
                    motion = new Vec3(0, v, 0);
                } else {
                    double k = 0.5 * g * dxz * dxz / (v * v);
                    double disc = dxz * dxz - 4 * k * (k + dy);
                    if (disc >= 0) {
                        double tanP = (dxz - Math.sqrt(disc)) / (2 * k);
                        double pitch = Math.atan(tanP);
                        double horiz = v * Math.cos(pitch);
                        motion = new Vec3(toTarget.x / dxz * horiz, v * Math.sin(pitch), toTarget.z / dxz * horiz);
                    } else {
                        motion = toTarget.normalize().scale(v);
                    }
                }
                projectile.setDeltaMovement(motion);
                projectile.hasImpulse = true;

                serverLevel.addFreshEntity(projectile);

                // 消耗物品
                if (!serverPlayer.getAbilities().instabuild) {
                    heldItem.shrink(1);
                }

                String name = getThrowableName(heldItem);
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§a[投掷] §f" + name), true
                );
                player.swing(InteractionHand.MAIN_HAND);
                return;
            }
        }

        // 联机 fallback
        mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        player.swing(InteractionHand.MAIN_HAND);
    }

    /** 根据物品类型创建对应的弹射物实体 */
    @Nullable
    private static net.minecraft.world.entity.Entity createThrowableEntity(ServerLevel level, ServerPlayer player, ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof SnowballItem) {
            return new net.minecraft.world.entity.projectile.Snowball(level, player);
        }
        if (item instanceof EggItem) {
            return new net.minecraft.world.entity.projectile.ThrownEgg(level, player);
        }
        if (item == net.minecraft.world.item.Items.ENDER_PEARL) {
            return new net.minecraft.world.entity.projectile.ThrownEnderpearl(level, player);
        }
        if (item instanceof ThrowablePotionItem) {
            return new net.minecraft.world.entity.projectile.ThrownPotion(level, player);
        }
        if (item instanceof ExperienceBottleItem) {
            return new net.minecraft.world.entity.projectile.ThrownExperienceBottle(level, player);
        }
        return null;
    }

    /** 获取投掷物显示名称 */
    private static String getThrowableName(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof SnowballItem) return "雪球";
        if (item instanceof EggItem) return "鸡蛋";
        if (item == net.minecraft.world.item.Items.ENDER_PEARL) return "末影珍珠";
        if (item instanceof ThrowablePotionItem) return "喷溅药水";
        if (item instanceof ExperienceBottleItem) return "经验瓶";
        return "投掷物";
    }

    /** 启动远程攻击全局冷却 */
    private static void startRangedCooldown() {
        globalRangedCooldownTicks = GLOBAL_RANGED_COOLDOWN_TICKS;
    }

    /**
     * 远程攻击每刻更新（在 handleEndPhase 中调用）。
     * 处理：
     * 1. 弓蓄力计时 → 自动释放
     * 2. 冷却递减
     * 3. 目标死亡/消失时取消动作
     */
    public static void rangedTick(Minecraft mc) {
        if (mc.player == null) return;

        // 冷却递减
        if (globalRangedCooldownTicks > 0) {
            globalRangedCooldownTicks--;
        }

        // 根据当前状态处理
        switch (rangedState) {
            case BOW_CHARGING -> tickBowCharging(mc);
            case IDLE -> {}
        }
    }

    /** 弓蓄力每刻 */
    private static void tickBowCharging(Minecraft mc) {
        // 目标死亡/消失 → 取消蓄力
        if (rangedTarget == null || !rangedTarget.isAlive()) {
            cancelBowCharge(mc, "§7[弓] §f目标已消失");
            return;
        }

        // 玩家切换物品 → 取消
        ItemStack heldItem = mc.player.getMainHandItem();
        if (!(heldItem.getItem() instanceof BowItem)) {
            cancelBowCharge(mc, "§7[弓] §f已切换武器");
            return;
        }

        // 保留客户端蓄力状态（不管 handleKeybinds 是否中断了使用）
        bowChargeTicks++;

        // 每 tick 重新瞄准（目标可能移动）
        faceEntityForRanged(mc.player, rangedTarget, getProjectileSpeed(heldItem));

        // 蓄力进度提示
        if (bowChargeTicks % 5 == 0) {
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§6[弓] §e蓄力中 " + "▓".repeat(bowChargeTicks / 2) + "░".repeat((BOW_CHARGE_TICKS_NEEDED - bowChargeTicks) / 2)
                    ), true
            );
        }

        // 达到蓄力阈值 → 直接通过集成服务端发射箭矢
        if (bowChargeTicks >= BOW_CHARGE_TICKS_NEEDED) {
            // 确保客户端旋转正确
            faceEntityForRanged(mc.player, rangedTarget, getProjectileSpeed(heldItem));

            // 在集成服务端（单机）直接调用 serverPlayer.releaseUsingItem()，
            // 绕过 handleKeybinds 的 USE 键释放检测
            fireBowOnServer(mc);

            rangedState = RangedState.IDLE;
            rangedTarget = null;
            startRangedCooldown();
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§6[弓] §a发射！"), true
            );
        }
    }

    /**
     * 直接在集成服务端触发弓释放，精确控制箭矢弹道实现 100% 命中。
     *
     * 流程：
     * 1. 同步旋转到 ServerPlayer
     * 2. 调用 serverPlayer.releaseUsingItem() 让 vanilla 生成箭矢（含附魔等属性）
     * 3. 立即修正箭矢速度向量：去掉随机散布，用精确抛物线弹道命中目标
     */
    private static void fireBowOnServer(Minecraft mc) {
        if (rangedTarget == null || !rangedTarget.isAlive()) return;

        // 通过集成服务端获取 ServerPlayer
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            // 联机模式 → 退回到包模式
            mc.gameMode.releaseUsingItem(mc.player);
            mc.player.releaseUsingItem();
            return;
        }

        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(mc.player.getUUID());
        if (serverPlayer == null) return;

        // 在服务端线程执行，避免跨线程访问 LegacyRandomSource 导致崩溃
        // 关键：必须捕获 rangedTarget 到局部变量，因为调用方在 fireBowOnServer 返回后
        // 会立刻把 rangedTarget 置为 null，而 lambda 要等到下一个 server tick 才执行
        final ServerPlayer sp = serverPlayer;
        final Entity target = rangedTarget;
        server.execute(() -> {
            // 0. 安全检查（目标可能在排队期间死亡）
            if (!target.isAlive()) return;

            // 1. 重新计算精确弹道角度（此时目标可能已移动）
            ItemStack heldItem = sp.getMainHandItem();
            float projectileSpeed = getProjectileSpeed(heldItem);
            float[] aim = calculateBallisticAim(sp, target, projectileSpeed);

            // 2. 同步角度到服务端玩家
            sp.setYRot(aim[0]);
            sp.yRotO = aim[0];
            sp.setXRot(aim[1]);
            sp.xRotO = aim[1];
            sp.setYHeadRot(aim[0]);
            sp.setYBodyRot(aim[0]);

            // 3. 让服务端创建箭矢（获得所有附魔/属性效果）
            sp.startUsingItem(InteractionHand.MAIN_HAND);
            try {
                java.lang.reflect.Field remainingField = net.minecraft.world.entity.LivingEntity.class
                        .getDeclaredField("useItemRemaining");
                remainingField.setAccessible(true);
                remainingField.setInt(sp, 0);
            } catch (Exception e) {
                sp.stopUsingItem();
                sp.startUsingItem(InteractionHand.MAIN_HAND);
            }
            sp.releaseUsingItem();

            // 4. 找到刚生成的箭矢，用精确弹道覆盖其速度 → 100% 命中
            correctProjectileToTarget(sp, target, projectileSpeed);
        });
    }

    /**
     * 找到服务端刚刚生成的箭矢，覆盖其速度为精确弹道向量。
     * 消除了 vanilla BowItem 的 shootFromRotation 中的随机散布。
     */
    private static void correctProjectileToTarget(ServerPlayer player, Entity target, float speed) {
        Vec3 playerEye = player.getEyePosition(1.0f);

        // 找刚生成（tickCount ≤ 1）且归属于自己的弹射物，逐一修正
        for (net.minecraft.world.entity.projectile.AbstractArrow arrow :
                player.serverLevel().getEntitiesOfClass(
                        net.minecraft.world.entity.projectile.AbstractArrow.class,
                        player.getBoundingBox().inflate(3),
                        a -> a.tickCount <= 1
                                && a.getOwner() == player
                                && a.position().distanceToSqr(playerEye) < 9)) {

            // 重新计算弹道速度向量
            Vec3 targetPos = target.getEyePosition(1.0f);
            Vec3 toTarget = targetPos.subtract(arrow.position());
            double dxz = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
            double dy = toTarget.y;
            double v = speed;
            double g = 0.05;

            Vec3 motion;
            if (dxz < 0.1) {
                motion = new Vec3(0, v, 0);
            } else {
                double k = 0.5 * g * dxz * dxz / (v * v);
                double discriminant = dxz * dxz - 4 * k * (k + dy);

                if (discriminant >= 0) {
                    double sqrtDisc = Math.sqrt(discriminant);
                    double tanPitch = (dxz - sqrtDisc) / (2 * k);
                    double pitch = Math.atan(tanPitch);
                    double horiz = v * Math.cos(pitch);
                    motion = new Vec3(
                            toTarget.x / dxz * horiz,
                            v * Math.sin(pitch),
                            toTarget.z / dxz * horiz
                    );
                } else {
                    motion = toTarget.normalize().scale(v);
                }
            }

            // 覆盖速度（消除随机散布）
            arrow.setDeltaMovement(motion);
            arrow.setYRot((float) Math.toDegrees(Math.atan2(-motion.x, motion.z)));
            arrow.setXRot((float) Math.toDegrees(-Math.atan2(motion.y, Math.sqrt(motion.x * motion.x + motion.z * motion.z))));
            arrow.hasImpulse = true;
        }
    }

    /** 取消弓蓄力 */
    private static void cancelBowCharge(Minecraft mc, String message) {
        // 停止使用物品
        if (mc.player.isUsingItem()) {
            mc.player.stopUsingItem();
        }
        rangedState = RangedState.IDLE;
        rangedTarget = null;
        if (message != null) {
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(message), true
            );
        }
    }

    /** 是否正在执行远程攻击动作 */
    public static boolean isRanging() {
        return rangedState != RangedState.IDLE;
    }

    /** 当退出鸟瞰或需要清理时调用 */
    public static void clearRangedState() {
        if (rangedState == RangedState.BOW_CHARGING) {
            // 清理但不发消息
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.isUsingItem()) {
                Minecraft.getInstance().player.stopUsingItem();
            }
        }
        rangedState = RangedState.IDLE;
        rangedTarget = null;
        bowChargeTicks = 0;
    }
}
