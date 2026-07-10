package com.Hen3579.Nujomod.Client.Events;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 鸟瞰模式——建筑剖视图裁剪判定器。
 *
 * <h2>设计目的</h2>
 * 当玩家进入小型封闭建筑（村庄房屋等人工建筑），自动在 Chunk 重建阶段隐藏
 * 屋顶和靠近相机一侧的墙壁，使画面呈现建筑剖面效果：地面、地面物品、
 * 远离镜头的墙壁和墙饰清晰可见。
 *
 * <h2>建筑 vs 洞穴</h2>
 * 通过检测天花板方块材质区分：天然石材/泥土/砂岩等 → 地下洞穴，不触发剖视图；
 * 木板/砖块/羊毛等人工材料 → 建筑内部，触发剖视图。这确保地下洞穴中
 * 洞穴壁和天花板不被误切，雾效也不会被剖视图房间雾覆盖。
 *
 * <h2>裁剪规则（粗筛）</h2>
 * 真正剔除在 {@link com.Hen3579.Nujomod.Mixins.RenderChunkRegionMixin} 中完成。
 * <ol>
 *   <li><b>屋顶裁剪</b>：{@code blockY >= playerY + ROOF_OFFSET} 且位于玩家 8×8 水平范围内的方块隐藏</li>
 *   <li><b>地面保护</b>：{@code blockY <= playerY} 的方块永不裁剪（保证地板可见）</li>
 *   <li><b>近侧墙壁粗筛</b>：playerY+1 ~ playerY+ROOF_OFFSET-1 范围内，
 *       位于玩家北侧（Z 更小）或西侧（X 更小）的方块进入精筛</li>
 * </ol>
 *
 * <h2>相机侧判定</h2>
 * 鸟瞰相机固定位于玩家 NW 方向（yaw=135°），从 NW 看向 SE。
 * 因此需要隐藏的是北侧墙（Z 更小）和西侧墙（X 更小）。
 * 两轴独立判定，不做 X+Z 对角线合并，避免 NW 角被一刀切。
 *
 * <h2>线程安全</h2>
 * 玩家坐标 ({@link #playerX}, {@link #playerY}, {@link #playerZ}) 为 volatile，
 * 由客户端 tick 写入，由渲染编译线程读取，确保可见性。
 * {@link #shouldCull(BlockPos)} 纯坐标运算，无世界访问，可在任意线程调用。
 */
public class SectionViewCuller {

    private static final Logger LOGGER = LoggerFactory.getLogger("NujoBraincraft.SectionView");

    // ===== 参数常量 =====

    /** 默认屋顶偏移量（回退用，动态检测失败时使用） */
    private static final int DEFAULT_ROOF_OFFSET = 2;

    /** 天花板检测：每层至少需要的固体方块数（3×3=9 中 ≥3） */
    private static final int CEILING_SOLID_THRESHOLD = 3;

    /** 裁剪平面缓冲量（格），防止紧贴平面的方块闪烁 */
    private static final double PLANE_BUFFER = 0.75;

    /** 封闭空间检测：头顶检测最大距离（格） */
    private static final int DETECT_CEILING_RANGE = 6;

    /** 封闭空间检测：水平墙壁检测最大距离（格） */
    private static final int DETECT_WALL_RANGE = 6;

    /** 封闭空间判定：需要的最少被阻挡方向数（上 + 东西南北 + 四角 = 9，≥4 即激活） */
    private static final int MIN_BLOCKED_DIRS = 4;

    /** 天花板检测 3×3 网格偏移（以玩家为中心的 9 个采样柱） */
    private static final int[][] CEILING_GRID = {
        {-1, -1}, {0, -1}, {1, -1},
        {-1,  0}, {0,  0}, {1,  0},
        {-1,  1}, {0,  1}, {1,  1}
    };

    // ===== 状态变量（volatile 保证线程间可见） =====

    /** 剖视图是否激活（仅鸟瞰 + 封闭空间同时满足时为 true） */
    private static volatile boolean active = false;

    /** 上一帧的激活状态，用于检测变化触发区块重编译 */
    private static boolean wasActive = false;

    /** 动态天花板偏移：天花板最低固体层相对于玩家脚底的 Y 偏移（≥1） */
    private static volatile int dynamicRoofOffset = DEFAULT_ROOF_OFFSET;

    /** 缓存的玩家 X 坐标 */
    private static volatile double playerX;

    /** 缓存的玩家 Y 坐标（脚底） */
    private static volatile double playerY;

    /** 缓存的玩家 Z 坐标 */
    private static volatile double playerZ;

    /** 每 tick 计数器，用于检测冷却 */
    private static int tickCounter = 0;

    /** 封闭空间检测间隔（tick），约 0.5s 重检一次 */
    private static final int DETECT_INTERVAL = 10;

    /** 手动开启标志：false（默认）= 剖视图关闭，true = 用户手动开启后进行自动检测 */
    private static boolean manualEnable = false;

    /** 强制下次 update 立即执行检测（手动开启切换后需要即时响应） */
    private static boolean forceDetectNextTick = false;

    // ===== 每 tick 更新入口 =====

    /**
     * 每客户端 tick 调用一次。
     * 重新检测封闭空间状态、更新玩家坐标缓存、在状态变化时触发区块重编译。
     *
     * @param level     当前客户端世界
     * @param playerPos 玩家脚底坐标
     */
    public static void update(Level level, Vec3 playerPos) {
        // 每 tick 更新玩家坐标缓存（volatile 写入，渲染编译线程可见）
        playerX = playerPos.x;
        playerY = playerPos.y;
        playerZ = playerPos.z;

        // === 封闭空间检测加冷却（每 10 tick / 0.5s 重检一次） ===
        // detectEnclosedSpace() + detectCeilingHeight() 合计约 200 次方块查询，
        // 每 tick 执行没必要——玩家 0.5s 内不会跨房间，冷却降为 ~20 次/tick
        tickCounter++;
        boolean shouldRunDetect = (tickCounter % DETECT_INTERVAL == 0) || forceDetectNextTick;
        if (!shouldRunDetect) return;
        forceDetectNextTick = false;

        // === 动态天花板高度检测 ===
        dynamicRoofOffset = detectCeilingHeight(level, BlockPos.containing(playerPos));

        // 条件：鸟瞰模式 + 手动开启 + 封闭空间检测通过 → 激活剖视图
        boolean newActive = BirdviewClientEvent.isBirdseyeActive()
                && manualEnable
                && detectEnclosedSpace(level, playerPos);

        if (newActive != active) {
            if (newActive) {
                LOGGER.info("剖视图 激活！玩家位于封闭空间内 (pos={},{},{})",
                    (int)playerPos.x, (int)playerPos.y, (int)playerPos.z);
            } else {
                LOGGER.info("剖视图 关闭——玩家离开封闭空间");
            }
        }
        active = newActive;
    }

    /** 退出鸟瞰时强制关闭剖视图，重置所有状态 */
    public static void reset() {
        active = false;
        wasActive = false;
        tickCounter = 0;
        manualEnable = false;
        forceDetectNextTick = false;
    }

    /** 切换手动开启模式（快捷键触发）。仅在鸟瞰模式下有效。 */
    public static void toggleManualEnable() {
        manualEnable = !manualEnable;
        forceDetectNextTick = true; // 强制下一 tick 立即更新 active
        LOGGER.info("剖视图手动开关: {}", manualEnable ? "开启" : "关闭");
    }

    /** 手动开启是否激活（即用户手动开启了剖视图） */
    public static boolean isManuallyEnabled() {
        return manualEnable;
    }

    /** 剖视图是否当前激活 */
    public static boolean isActive() {
        return active;
    }

    /** 检测状态是否刚刚发生了变化（用于触发区块重编译） */
    public static boolean stateJustChanged() {
        return active != wasActive;
    }

    /** 确认状态变化已处理（防止重复触发区块重编译） */
    public static void confirmStateChange() {
        wasActive = active;
    }

    /** 缓存的玩家 X 坐标（供渲染线程读取） */
    public static double getPlayerX() { return playerX; }
    /** 缓存的玩家 Y 坐标（供渲染线程读取） */
    public static double getPlayerY() { return playerY; }
    /** 缓存的玩家 Z 坐标（供渲染线程读取） */
    public static double getPlayerZ() { return playerZ; }
    /** 动态天花板偏移（供渲染线程和透明遮罩器读取） */
    public static int getDynamicRoofOffset() { return dynamicRoofOffset; }

    // ===== 裁剪判定（可在任意线程调用） =====

    /**
     * 判断给定方块位置是否应被裁剪（粗筛）。
     *
     * <p>可在渲染编译线程调用，仅访问 volatile 坐标缓存，不做任何世界查询。
     * 真正的“是否墙面”精筛在 {@link RenderChunkRegionMixin} 中通过邻块空气检查完成。
     *
     * @param pos 方块坐标
     * @return true = 该方块位于需要考虑的裁剪范围内
     */
    public static boolean shouldCull(BlockPos pos) {
        if (!active) return false;

        int by = pos.getY();
        double py = playerY;
        int dy = by - (int) py;

        // === 规则 1：地面保护 —— 玩家脚底及以下永不裁剪 ===
        if (dy <= 0) {
            return false;
        }

        int offset = dynamicRoofOffset;
        int dx = pos.getX() - (int) playerX;
        int dz = pos.getZ() - (int) playerZ;

        // === 规则 2：屋顶裁剪 —— 天花板及以上（限制在玩家周围 8 格）===
        if (dy >= offset) {
            return Math.abs(dx) <= 8 && Math.abs(dz) <= 8 && dy <= offset + 8;
        }

        // === 规则 3：近侧墙壁粗筛 —— 位于玩家北侧或西侧的方块 ===
        // 精筛在 RenderChunkRegionMixin 中完成（邻块空气验证）
        if (Math.abs(dx) > 6 || Math.abs(dz) > 6) return false;
        boolean isNorthWall = pos.getZ() + 0.5 < playerZ - PLANE_BUFFER;
        boolean isWestWall  = pos.getX() + 0.5 < playerX - PLANE_BUFFER;
        return isNorthWall || isWestWall;
    }

    // ===== 封闭空间检测 =====

    /**
     * 封闭空间 + 建筑材质判定。
     *
     * <p>检测逻辑：
     * <ol>
     *   <li>上方 3×3 天花板扫描（同时记录材质类型）</li>
     *   <li>水平 8 方向墙壁扫描（东西南北 + 四角，双高度）</li>
     *   <li>至少 {@link #MIN_BLOCKED_DIRS} 方向被阻挡 → 判定为封闭空间</li>
     *   <li>天花板材质检查：若天花板主要由天然方块构成（石材/泥土/砂岩等）
     *       → 判定为地下洞穴 → 返回 false，不触发剖视图</li>
     * </ol>
     *
     * @param level     客户端世界
     * @param playerPos 玩家脚底坐标
     * @return true = 玩家位于人工建筑内，应激活剖视图
     */
    private static boolean detectEnclosedSpace(Level level, Vec3 playerPos) {
        BlockPos center = BlockPos.containing(playerPos);
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        int blocked = 0;

        // === 上方：3×3 网格天花板检测 + 材质采样 ===
        // 尖顶房/楼梯屋檐的斜坡容易让单列扫描漏过，3×3=9 根柱子任意一根命中即算阻挡
        boolean ceilingBlocked = false;
        int naturalCeiling = 0;   // 天花板层中天然方块计数
        int artificialCeiling = 0; // 天花板层中人工方块计数
        int ceilingY = 1;         // 记录天花板命中的 Y 偏移

        for (int[] gridOff : CEILING_GRID) {
            for (int dy = 1; dy <= DETECT_CEILING_RANGE; dy++) {
                mPos.set(
                    center.getX() + gridOff[0],
                    center.getY() + dy,
                    center.getZ() + gridOff[1]
                );
                BlockState state = level.getBlockState(mPos);
                if (!state.isAir() && state.isSolidRender(level, mPos)) {
                    ceilingBlocked = true;
                    ceilingY = dy;
                    // 材质采样：检查该命中方块是天然还是人工
                    if (isNaturalBlock(state)) {
                        naturalCeiling++;
                    } else {
                        artificialCeiling++;
                    }
                    break; // 该柱子命中，换下一根
                }
            }
        }
        if (ceilingBlocked) {
            blocked++;
            // === 洞穴判定：天花板以天然方块为主 → 这是地下洞穴，不触发剖视图 ===
            if (naturalCeiling > artificialCeiling && naturalCeiling >= 2) {
                return false;
            }
        }

        // === 水平：东西南北 + 四角，双高度扫描 ===
        // 墙壁可能在头部高度（playerY+1）或胸口高度（playerY+2）
        // 对角方向覆盖门洞/窗户空隙，防止 4 方向漏检
        int[][] horizDirs = {
            { 1, 0}, {-1, 0}, { 0, 1}, { 0,-1},  // 东西南北
            { 1, 1}, { 1,-1}, {-1, 1}, {-1,-1}    // 四角
        };
        for (int[] d : horizDirs) {
            boolean dirBlocked = false;
            for (int dist = 1; dist <= DETECT_WALL_RANGE; dist++) {
                // 双高度扫描
                for (int dyOff = 1; dyOff <= 2; dyOff++) {
                    mPos.set(
                        center.getX() + d[0] * dist,
                        center.getY() + dyOff,
                        center.getZ() + d[1] * dist
                    );
                    BlockState state = level.getBlockState(mPos);
                    if (!state.isAir() && state.isSolidRender(level, mPos)) {
                        dirBlocked = true;
                        break;
                    }
                }
                if (dirBlocked) break;
            }
            if (dirBlocked) blocked++;
        }

        boolean result = blocked >= MIN_BLOCKED_DIRS;
        if (result) {
            LOGGER.debug("剖视图 激活（人工建筑），天花板天然方块={} 人工方块={} (pos={},{},{})",
                naturalCeiling, artificialCeiling, center.getX(), center.getY(), center.getZ());
        }
        return result;
    }

    /**
     * 判断方块是否为天然/自然生成材质（石材、泥土、矿物等）。
     * 用于区分地下洞穴和人工建筑：天然天花板不应触发剖视图。
     */
    private static boolean isNaturalBlock(BlockState state) {
        return state.is(Tags.Blocks.STONE)
            || state.is(BlockTags.BASE_STONE_OVERWORLD)
            || state.is(BlockTags.BASE_STONE_NETHER)
            || state.is(BlockTags.DIRT)
            || state.is(BlockTags.SAND)
            || state.is(BlockTags.TERRACOTTA)
            || state.is(BlockTags.ICE);
    }

    // ===== 动态天花板高度检测 =====

    /**
     * 动态检测玩家头顶天花板的最低固体层高度。
     *
     * <p>使用 3×3 网格向上扫描，找到第一个"至少 {@link #CEILING_SOLID_THRESHOLD} 个
     * 固体方块"的 Y 层，返回该层相对于玩家脚底的偏移量。
     *
     * <p>这使不同建筑自动适配：
     * <ul>
     *   <li>低矮小屋（楼梯屋顶从 playerY+2 开始）→ offset=2</li>
     *   <li>高大厅堂（天花板在 playerY+5）→ offset=5</li>
     *   <li>户外的树冠遮挡 → offset 由最近的树叶层决定</li>
     * </ul>
     *
     * @param level  客户端世界
     * @param center 玩家脚底方块坐标
     * @return 天花板偏移（格），≥1；若未找到则返回 {@link #DEFAULT_ROOF_OFFSET}
     */
    private static int detectCeilingHeight(Level level, BlockPos center) {
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= DETECT_CEILING_RANGE; dy++) {
            int solidCount = 0;
            for (int[] gridOff : CEILING_GRID) {
                mPos.set(
                    center.getX() + gridOff[0],
                    center.getY() + dy,
                    center.getZ() + gridOff[1]
                );
                BlockState state = level.getBlockState(mPos);
                if (!state.isAir() && state.isSolidRender(level, mPos)) {
                    solidCount++;
                }
            }
            // 该层有足够固体方块 → 识别为天花板
            if (solidCount >= CEILING_SOLID_THRESHOLD) {
                return dy;
            }
        }
        return DEFAULT_ROOF_OFFSET;
    }
}
