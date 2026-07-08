package com.Hen3579.Nujomod.Server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务器端鸟瞰模式状态管理
 *
 * 存储每个玩家的鸟瞰开关状态，并提供：
 * - 查找最近的鸟瞰玩家（供 Mixin/事件处理器使用）
 * - 飞行生物俯冲冷却管理（控制俯冲时长，到期后强制上浮）
 */
public class BirdviewServerState {

    /** 鸟瞰模式激活的玩家 UUID 集合 */
    private static final Set<UUID> birdviewPlayers = ConcurrentHashMap.newKeySet();

    /** 飞行生物俯冲冷却：mobUUID → 剩余 tick 数（>0 表示正在俯冲，允许低于 yMin） */
    private static final Map<UUID, Integer> diveCooldowns = new ConcurrentHashMap<>();

    /** 鸟瞰有效范围（格） */
    private static final double BIRDVIEW_RANGE = 64.0;
    private static final double BIRDVIEW_RANGE_SQ = BIRDVIEW_RANGE * BIRDVIEW_RANGE;

    // ===== 鸟瞰状态 =====

    /** 设置某玩家的鸟瞰模式状态 */
    public static void setBirdviewActive(UUID playerUUID, boolean active) {
        if (active) {
            birdviewPlayers.add(playerUUID);
        } else {
            birdviewPlayers.remove(playerUUID);
        }
    }

    /** 某玩家是否处于鸟瞰模式 */
    public static boolean isBirdviewActive(UUID playerUUID) {
        return birdviewPlayers.contains(playerUUID);
    }

    /** 是否有任意玩家处于鸟瞰模式 */
    public static boolean isAnyBirdviewActive() {
        return !birdviewPlayers.isEmpty();
    }

    /** 玩家退出/断开时清除状态 */
    public static void clearPlayer(UUID playerUUID) {
        birdviewPlayers.remove(playerUUID);
    }

    // ===== 最近鸟瞰玩家查找 =====

    /**
     * 查找距离实体最近的鸟瞰模式玩家（在 64 格范围内）。
     * 仅在服务器端有效，客户端返回 null。
     *
     * @param entity 查找中心的实体
     * @return 最近的鸟瞰玩家，或 null
     */
    public static ServerPlayer getNearestBirdviewPlayer(Entity entity) {
        if (entity == null || entity.level().isClientSide()) return null;
        if (!(entity.level() instanceof ServerLevel serverLevel)) return null;
        if (birdviewPlayers.isEmpty()) return null;

        MinecraftServer server = serverLevel.getServer();
        if (server == null) return null;

        ServerPlayer nearest = null;
        double nearestDistSq = BIRDVIEW_RANGE_SQ;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!birdviewPlayers.contains(player.getUUID())) continue;
            if (player.level() != serverLevel) continue;

            double distSq = player.distanceToSqr(entity);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = player;
            }
        }

        return nearest;
    }

    /**
     * 查找同一维度中任意鸟瞰模式玩家（无距离限制）。
     * 用于末影龙等 Boss 级生物——只要同一维度有鸟瞰玩家就生效，
     * 不受 64 格范围限制（龙在 Y=68，玩家在地面 Y=0 时距离已超 64 格）。
     *
     * @param level 查找的维度
     * @return 任意一个鸟瞰玩家，或 null
     */
    public static ServerPlayer getAnyBirdviewPlayerInLevel(ServerLevel level) {
        if (level == null || level.isClientSide()) return null;
        if (birdviewPlayers.isEmpty()) return null;

        MinecraftServer server = level.getServer();
        if (server == null) return null;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!birdviewPlayers.contains(player.getUUID())) continue;
            if (player.level() != level) continue;
            return player; // 找到第一个即返回
        }
        return null;
    }

    // ===== 俯冲冷却管理 =====

    /** 俯冲持续时间（tick）：2 秒内允许低于 yMin 俯冲攻击 */
    public static final int DIVE_DURATION = 40;

    /**
     * 开始/刷新某飞行生物的俯冲冷却。
     * 在飞行生物 Y 低于 yMin 时调用。
     */
    public static void startDive(UUID mobUUID) {
        diveCooldowns.put(mobUUID, DIVE_DURATION);
    }

    /** 获取俯冲剩余 tick 数（>0 表示正在俯冲） */
    public static int getDiveCooldown(UUID mobUUID) {
        return diveCooldowns.getOrDefault(mobUUID, 0);
    }

    /** 俯冲是否仍在冷却中（允许低于 yMin） */
    public static boolean isDiving(UUID mobUUID) {
        return diveCooldowns.getOrDefault(mobUUID, 0) > 0;
    }

    /** 重置俯冲冷却（飞行生物回到安全高度后调用） */
    public static void clearDive(UUID mobUUID) {
        diveCooldowns.remove(mobUUID);
    }

    /** 每 tick 递减所有俯冲冷却（在 ServerTickEvent.START 中调用） */
    public static void tickDiveCooldowns() {
        if (diveCooldowns.isEmpty()) return;

        Iterator<Map.Entry<UUID, Integer>> it = diveCooldowns.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                it.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    /** 清除所有状态（服务器停止时调用） */
    public static void clearAll() {
        birdviewPlayers.clear();
        diveCooldowns.clear();
    }
}
