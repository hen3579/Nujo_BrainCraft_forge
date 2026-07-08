package com.Hen3579.Nujomod.Mixins;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 末影龙俯视角石柱世界生成 Mixin
 *
 * 替换原版随机高度尖刺为固定 8 根矮柱环形布局：
 * - 8 根柱子，正八边形环绕祭坛（半径 25 格）
 * - 统一高度 Y=68-72（4 格高 + 基底）
 * - 3×3 底座 + 中间空心（Y=69-71 可穿箭）+ 顶部平台
 * - 水晶统一 Y=73，俯视角全可见
 * - 2 个水晶带矮铁笼（侧面开口，箭可射入）
 */
@Mixin(SpikeFeature.class)
public abstract class DragonSpikeMixin {

    // ===== 柱子布局常量 =====

    /** 柱子距祭坛中心的半径 */
    private static final int PILLAR_RADIUS = 25;
    /** 柱子底座 Y 坐标 */
    private static final int BASE_Y = 68;
    /** 柱子顶部 Y 坐标 */
    private static final int TOP_Y = 72;
    /** 水晶 Y 坐标 */
    private static final int CRYSTAL_Y = 73;
    /** 柱子截面半径（3×3 = radius 1） */
    private static final int PILLAR_CROSS_RADIUS = 1;
    /** EndSpike height 参数（用于 topBoundingBox 计算，水晶 Y = 0 + height） */
    private static final int END_SPIKE_HEIGHT = CRYSTAL_Y; // 73

    /** 8 根柱子的位置（正八边形，半径 25） */
    private static final int[][] PILLAR_POSITIONS = {
            {25, 0}, {18, 18}, {0, 25}, {-18, 18},
            {-25, 0}, {-18, -18}, {0, -25}, {18, -18}
    };

    /** 带铁笼的柱子索引（第 2 根和第 6 根） */
    private static final int[] GUARDED_INDICES = {1, 5};

    /** 缓存的自定义石柱列表 */
    private static List<SpikeFeature.EndSpike> cachedSpikes = null;

    /**
     * 替换 getSpikesForLevel：返回固定 8 根矮柱
     * 原版返回随机高度/半径的石柱，这里改为统一布局。
     */
    @Inject(method = "getSpikesForLevel", at = @At("HEAD"), cancellable = true)
    private static void nujo$getCustomSpikes(WorldGenLevel level,
                                              CallbackInfoReturnable<List<SpikeFeature.EndSpike>> cir) {
        if (cachedSpikes == null) {
            cachedSpikes = new ArrayList<>();
            for (int i = 0; i < PILLAR_POSITIONS.length; i++) {
                boolean guarded = isGuarded(i);
                cachedSpikes.add(new SpikeFeature.EndSpike(
                        PILLAR_POSITIONS[i][0],  // centerX
                        PILLAR_POSITIONS[i][1],  // centerZ
                        PILLAR_CROSS_RADIUS,     // radius (1 = 3×3)
                        END_SPIKE_HEIGHT,         // height (crystal at Y=73)
                        guarded                   // guarded (iron cage)
                ));
            }
        }
        cir.setReturnValue(cachedSpikes);
    }

    /**
     * 替换 placeSpike：建空心 3×3 结构 + 顶部平台 + 铁笼 + 水晶
     * 原版建实心黑曜石柱，这里改为矮平空心结构。
     */
    @Inject(method = "placeSpike", at = @At("HEAD"), cancellable = true)
    private void nujo$placeCustomSpike(ServerLevelAccessor level, RandomSource random,
                                        SpikeConfiguration config, SpikeFeature.EndSpike spike,
                                        CallbackInfo ci) {
        ci.cancel(); // 取消原版实心柱生成

        int cx = spike.getCenterX();
        int cz = spike.getCenterZ();
        boolean guarded = spike.isGuarded();

        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        BlockState ironBars = Blocks.IRON_BARS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        // ===== Y=68: 3×3 实心底座 =====
        for (int dx = -PILLAR_CROSS_RADIUS; dx <= PILLAR_CROSS_RADIUS; dx++) {
            for (int dz = -PILLAR_CROSS_RADIUS; dz <= PILLAR_CROSS_RADIUS; dz++) {
                level.setBlock(new BlockPos(cx + dx, BASE_Y, cz + dz), obsidian, 3);
            }
        }

        // ===== Y=69-71: 3×3 环形（中心空心，箭可穿过） =====
        for (int y = BASE_Y + 1; y < TOP_Y; y++) {
            for (int dx = -PILLAR_CROSS_RADIUS; dx <= PILLAR_CROSS_RADIUS; dx++) {
                for (int dz = -PILLAR_CROSS_RADIUS; dz <= PILLAR_CROSS_RADIUS; dz++) {
                    if (dx == 0 && dz == 0) {
                        // 中心保持空气（空心）
                        level.setBlock(new BlockPos(cx, y, cz), air, 3);
                    } else {
                        // 外围放黑曜石
                        level.setBlock(new BlockPos(cx + dx, y, cz + dz), obsidian, 3);
                    }
                }
            }
        }

        // ===== Y=72: 3×3 实心顶部平台 =====
        for (int dx = -PILLAR_CROSS_RADIUS; dx <= PILLAR_CROSS_RADIUS; dx++) {
            for (int dz = -PILLAR_CROSS_RADIUS; dz <= PILLAR_CROSS_RADIUS; dz++) {
                level.setBlock(new BlockPos(cx + dx, TOP_Y, cz + dz), obsidian, 3);
            }
        }

        // ===== 生成末影水晶（Y=73） =====
        ServerLevel serverLevel = level.getLevel();
        EndCrystal crystal = new EndCrystal(serverLevel, cx + 0.5, CRYSTAL_Y, cz + 0.5);

        if (config.isCrystalInvulnerable()) {
            crystal.setInvulnerable(true);
        }
        if (config.getCrystalBeamTarget() != null) {
            crystal.setBeamTarget(config.getCrystalBeamTarget());
        }
        level.addFreshEntity(crystal);

        // ===== 铁笼（仅 guarded 柱子） =====
        // 1 格高矮笼，侧面开口（北面开口，箭可射入）
        if (guarded) {
            // 铁笼位置：水晶四周（北/南/东/西），1 格高
            // 北面 (z-1) 不放铁栏 → 开口
            level.setBlock(new BlockPos(cx, CRYSTAL_Y, cz + 1), ironBars, 3); // 南
            level.setBlock(new BlockPos(cx + 1, CRYSTAL_Y, cz), ironBars, 3); // 东
            level.setBlock(new BlockPos(cx - 1, CRYSTAL_Y, cz), ironBars, 3); // 西
            // 北面 (cz-1) 开口，不放铁栏
        }
    }

    /** 判断指定索引的柱子是否带铁笼 */
    private static boolean isGuarded(int index) {
        for (int guarded : GUARDED_INDICES) {
            if (guarded == index) return true;
        }
        return false;
    }
}
