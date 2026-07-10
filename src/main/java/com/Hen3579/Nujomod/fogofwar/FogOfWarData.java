package com.Hen3579.Nujomod.fogofwar;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * 战争迷雾客户端数据。
 *
 * 三层可见性：
 *   BRIGHT_VISIBLE  = 1.00 — 玩家视野范围内的区块（完整亮度）
 *   BRIGHT_DARK     = 0.35 — 曾经探索过、但当前不在视野内的区块（昏暗）
 *   BRIGHT_UNEXPLORED = 0.10 — 从未探索过的区块（近乎全黑）
 *
 * 持久化：退出世界时保存到 saves/<world>/nujobraincraft_fog.nbt，进入时自动加载。
 */
public class FogOfWarData {

    // ======== 亮度常量 ========

    /** 可见区块（完全亮度） */
    public static final float BRIGHT_VISIBLE = 1.00f;
    /** 曾探索但不在视野内（昏暗） */
    public static final float BRIGHT_DARK = 0.35f;
    /** 从未探索（近乎全黑） */
    public static final float BRIGHT_UNEXPLORED = 0.10f;

    // ======== 视野配置 ========

    /** 玩家周围揭示半径（区块数） */
    public static final int REVEAL_RADIUS = 4;
    /** 更新间隔（tick），每 0.5 秒更新一次 */
    public static final int UPDATE_INTERVAL_TICKS = 10;

    // ======== 内部状态 ========

    /** 曾揭示过的所有区块（跨会话持久化） */
    private static final Set<Long> revealedChunks = new HashSet<>();

    /** 当前帧在视野内的区块（每 tick 重新计算） */
    private static final Set<Long> brightChunks = new HashSet<>();

    /** 上次更新时的玩家区块位置 */
    @Nullable
    private static ChunkPos lastPlayerChunk = null;

    /** 当前保存/加载用的存档路径 */
    @Nullable
    private static Path savePath = null;

    // ======== 揭示管理 ========

    /** 将一个区块标记为已揭示 */
    public static void revealChunk(int chunkX, int chunkZ) {
        revealedChunks.add(ChunkPos.asLong(chunkX, chunkZ));
    }

    /** 区块是否曾经揭示过 */
    public static boolean isRevealed(int chunkX, int chunkZ) {
        return revealedChunks.contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    /** 区块是否曾经揭示过（BlockPos 版本） */
    public static boolean isRevealed(BlockPos pos) {
        return isRevealed(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** 区块当前是否在明亮视野内 */
    public static boolean isBright(int chunkX, int chunkZ) {
        return brightChunks.contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    /** 获取指定坐标的亮度倍率 [0, 1] */
    public static float getBrightness(int x, int y, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        long key = ChunkPos.asLong(cx, cz);

        if (brightChunks.contains(key)) return BRIGHT_VISIBLE;
        if (revealedChunks.contains(key)) return BRIGHT_DARK;
        return BRIGHT_UNEXPLORED;
    }

    /** 获取指定坐标的亮度倍率（BlockPos 版本） */
    public static float getBrightness(BlockPos pos) {
        return getBrightness(pos.getX(), pos.getY(), pos.getZ());
    }

    // ======== Tick 更新 ========

    /**
     * 每 tick 调用，根据玩家当前位置更新视野。
     * @return 如果视野发生变化则返回 true（需要标记周边区块重建）
     */
    public static boolean update(ChunkPos playerChunk) {
        if (playerChunk.equals(lastPlayerChunk)) return false;
        lastPlayerChunk = playerChunk;

        // 揭示 REVEAL_RADIUS 内的所有区块
        int r = REVEAL_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= r * r) {
                    revealChunk(playerChunk.x + dx, playerChunk.z + dz);
                }
            }
        }

        // 刷新当前明亮区块
        brightChunks.clear();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= r * r) {
                    brightChunks.add(ChunkPos.asLong(playerChunk.x + dx, playerChunk.z + dz));
                }
            }
        }

        return true;
    }

    // ======== 持久化 ========

    /** 设置保存路径（进入世界时调用） */
    public static void setSavePath(Path path) {
        savePath = path;
    }

    /** 从存档加载已揭示区块 */
    public static void loadFromDisk() {
        if (savePath == null) return;
        if (!Files.exists(savePath)) return;

        try {
            CompoundTag tag = NbtIo.readCompressed(savePath.toFile());
            deserialize(tag);
        } catch (IOException e) {
            System.err.println("[FogOfWar] Failed to load fog data: " + e.getMessage());
        }
    }

    /** 保存已揭示区块到存档 */
    public static void saveToDisk() {
        if (savePath == null) return;

        try {
            Files.createDirectories(savePath.getParent());
            CompoundTag tag = serialize();
            NbtIo.writeCompressed(tag, savePath.toFile());
        } catch (IOException e) {
            System.err.println("[FogOfWar] Failed to save fog data: " + e.getMessage());
        }
    }

    /** 序列化为 NBT */
    private static CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (long key : revealedChunks) {
            list.add(LongTag.valueOf(key));
        }
        tag.put("revealed", list);
        return tag;
    }

    /** 从 NBT 反序列化 */
    private static void deserialize(CompoundTag tag) {
        revealedChunks.clear();
        if (tag.contains("revealed")) {
            ListTag list = tag.getList("revealed", 4); // TAG_Long = 4
            for (int i = 0; i < list.size(); i++) {
                revealedChunks.add(((LongTag) list.get(i)).getAsLong());
            }
        }
    }

    /** 重置所有迷雾数据（新世界或调试） */
    public static void reset() {
        revealedChunks.clear();
        brightChunks.clear();
        lastPlayerChunk = null;
        savePath = null;
    }

    // ======== 剧情集成 ========

    /**
     * 剧情触发：强制揭示指定区块（如关键剧情地点）。
     * 剧情系统可在特定阶段调用此方法，确保玩家能看到目标区域。
     */
    public static void storyReveal(int chunkX, int chunkZ, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    revealChunk(chunkX + dx, chunkZ + dz);
                }
            }
        }
        // 持久化保存
        saveToDisk();
    }

    /** 剧情触发：强制揭示指定坐标周围的区块 */
    public static void storyReveal(BlockPos center, int radius) {
        storyReveal(center.getX() >> 4, center.getZ() >> 4, radius);
    }
}
