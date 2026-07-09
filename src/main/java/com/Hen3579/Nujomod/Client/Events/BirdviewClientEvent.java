package com.Hen3579.Nujomod.Client.Events;

import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 视角循环状态管理器
 * 扩展原版 F5 三视角为四视角：第一人称 → 第三人称(背) → 第三人称(前) → 鸟瞰
 * 同时管理鸟瞰模式的相机对齐移动、点击移动和方块高亮
 */
public class BirdviewClientEvent {

    private static int currentPerspective = 0;  // 0:第一人称 1:第三人称背 2:第三人称前 3:鸟瞰
    private static boolean birdseyeActive = false;

    /** 鸟瞰视角高度（玩家上方格数） */
    public static final double BIRDSEYE_HEIGHT = 10.0;

    /** 鸟瞰视角俯仰角（0=平视, 90=垂直向下），RTS 风格约 45°（参考 Reign of Nether） */
    public static final float BIRDSEYE_PITCH = 45.0F;

    // ===== 建筑遮挡自适应高度 =====

    /** 当前实际渲染使用的平滑高度（每帧 lerp 插值） */
    private static double currentBirdseyeHeight = BIRDSEYE_HEIGHT;
    /** 目标高度（遮挡检测后的值，平滑到该值） */
    private static double targetBirdseyeHeight = BIRDSEYE_HEIGHT;

    /** 玩家是否能看到天空（露天/开放环境） */
    private static boolean canSeeSky = true;

    /** 高度平滑插值速度（每 tick lerp 因子，约 0.3 秒到达目标） */
    private static final double HEIGHT_LERP_SPEED = 0.15;
    /** 发现天花板后，相机下压到天花板以下的安全距离（格数） */
    private static final double CEILING_CLEARANCE = 0.6;

    /** 天花板过低时相机的最小水平偏移距离（格），防止室内贴脸 */
    private static final double MIN_HORIZONTAL_OFFSET = 5.0;
    /** 最小水平偏移对应的相机高度（MIN_OFFSET × tan(pitch)，pitch=45°时=5.0） */
    private static final double MIN_HEIGHT = MIN_HORIZONTAL_OFFSET * Math.tan(Math.toRadians(BIRDSEYE_PITCH));
    /** 相机→玩家线段多点列扫描采样数（覆盖天花板和墙壁） */
    private static final int SCAN_COLUMNS = 7;

    /**
     * 扫描相机→玩家视线路径上的遮挡方块。
     *
     * <p>两阶段检测（按优先级递减）：
     * <ol>
     *   <li><b>多点列扫描</b>——沿相机→玩家水平线段采样 SCAN_COLUMNS 个列，
     *       每列从相机 Y 向下扫描到玩家眼睛 Y，找到最高的 isSolidRender 方块，
     *       取最低天花板作为候选高度。同时覆盖天花板和墙壁。</li>
     *   <li><b>射线步进</b>——从相机位置向玩家眼睛做 DDA 步进（步长 0.5 格），
     *       填补列扫描采样点之间的缝隙（如薄墙壁、栅栏等单片方块）。</li>
     * </ol>
     *
     * <p>返回值钳制在 [{@link #MIN_HEIGHT}, {@link #BIRDSEYE_HEIGHT}] 范围内，
     * 保证室内相机不会贴到玩家脸上，室外保持默认鸟瞰视野。
     *
     * @param level     当前世界
     * @param playerPos 玩家脚下位置
     * @return 推荐的无遮挡相机 Y 偏移（玩家脚下到相机的高度）
     */
    public static double calculateOcclusionHeight(Level level, Vec3 playerPos) {
        // === 露天环境：玩家能看到天空时，不压低相机 ===
        // 侧面山坡/悬崖会被遮挡检测误判为"天花板"，导致相机压到 MIN_HEIGHT，
        // 视野变成陡峭俯冲并出现黑块。只要玩家头顶有天空，就保持默认鸟瞰高度。
        if (canSeeSky) {
            return BIRDSEYE_HEIGHT;
        }

        // 用当前平滑高度近似相机位置（遮挡检测先用当前值近似，下帧渲染时已平滑到目标值）
        double approxHeight = currentBirdseyeHeight;
        double offset = approxHeight / Math.tan(Math.toRadians(BIRDSEYE_PITCH));
        double yawRad = Math.toRadians(DEFAULT_FIXED_YAW);
        double camX = playerPos.x - Math.sin(yawRad) * offset;
        double camZ = playerPos.z + Math.cos(yawRad) * offset;

        double bestHeight = BIRDSEYE_HEIGHT; // 最优高度 = 无遮挡时的默认值
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();

        // === 第 1 层：多点列扫描（天花板 + 墙壁统一处理） ===
        for (int i = 0; i < SCAN_COLUMNS; i++) {
            double t = (SCAN_COLUMNS == 1) ? 0.0 : i / (double) (SCAN_COLUMNS - 1);
            double sx = camX + (playerPos.x - camX) * t;
            double sz = camZ + (playerPos.z - camZ) * t;

            mPos.setX((int) Math.floor(sx));
            mPos.setZ((int) Math.floor(sz));

            int scanTop = (int) (playerPos.y + approxHeight);
            int scanBot = (int) (playerPos.y + 1.0); // 玩家头部以上
            for (int y = scanTop; y >= scanBot; y--) {
                mPos.setY(y);
                BlockState state = level.getBlockState(mPos);
                // P0: isSolidRender() 覆盖完整立方体 + 上半砖、楼梯、活板门等视觉遮挡方块
                if (state.isSolidRender(level, mPos)) {
                    double h = (y + 1 + CEILING_CLEARANCE) - playerPos.y;
                    if (h < bestHeight) bestHeight = h;
                    break; // 该列已找到最高障碍物，跳到下一列
                }
            }
        }

        // === 第 2 层：射线步进墙壁检测（填充列采样点之间的缝隙） ===
        double playerEyeY = playerPos.y + 1.62;
        double camY = playerPos.y + approxHeight;

        double dx = playerPos.x - camX;
        double dz = playerPos.z - camZ;
        double dy = playerEyeY - camY;
        double rayDist = Math.sqrt(dx * dx + dz * dz + dy * dy);

        if (rayDist > 0.5) {
            double stepX = dx / rayDist * RAY_STEP;
            double stepY = dy / rayDist * RAY_STEP;
            double stepZ = dz / rayDist * RAY_STEP;
            double rx = camX, ry = camY, rz = camZ;
            int maxSteps = (int) (rayDist / RAY_STEP);

            for (int i = 0; i < maxSteps; i++) {
                rx += stepX;
                ry += stepY;
                rz += stepZ;
                mPos.set((int) Math.floor(rx), (int) Math.floor(ry), (int) Math.floor(rz));
                BlockState state = level.getBlockState(mPos);
                if (state.isSolidRender(level, mPos)) {
                    double h = (mPos.getY() + 1 + CEILING_CLEARANCE) - playerPos.y;
                    if (h < bestHeight) bestHeight = h;
                    break;
                }
            }
        }

        // 钳制：不低于最小高度（防贴脸），不高于默认高度（防超范围）
        return Math.max(MIN_HEIGHT, Math.min(BIRDSEYE_HEIGHT, bestHeight));
    }

    /** 射线墙壁检测步长（格） */
    private static final double RAY_STEP = 0.5;

    /** 每 tick 调用：当前高度向目标高度平滑插值 */
    public static void updateOcclusionSmoothing() {
        currentBirdseyeHeight += (targetBirdseyeHeight - currentBirdseyeHeight) * HEIGHT_LERP_SPEED;
        if (Math.abs(targetBirdseyeHeight - currentBirdseyeHeight) < 0.03) {
            currentBirdseyeHeight = targetBirdseyeHeight;
        }
    }

    /** 获取当前有效鸟瞰高度（已平滑） */
    public static double getEffectiveBirdseyeHeight() {
        return currentBirdseyeHeight;
    }

    /** 设置目标鸟瞰高度（遮挡检测计算得到） */
    public static void setTargetBirdseyeHeight(double height) {
        targetBirdseyeHeight = Math.max(1.5, Math.min(BIRDSEYE_HEIGHT, height));
    }

    /**
     * 更新玩家是否能看到天空。
     * 由客户端 tick 调用，用于区分露天环境与洞穴/遮挡区域。
     *
     * <p>检测位置：玩家脚底方块 + 上方一格。只要其中一格能直接看到天空，
     * 就视为露天环境（避免相机被侧面山坡错误压低）。
     */
    public static void updateSkyVisibility(Level level, Vec3 playerPos) {
        BlockPos feet = BlockPos.containing(playerPos);
        canSeeSky = level.canSeeSky(feet) || level.canSeeSky(feet.above());
    }

    /** 玩家当前是否能看到天空（露天环境） */
    public static boolean canSeeSky() {
        return canSeeSky;
    }

    /** 退出鸟瞰时重置高度到默认值 */
    public static void resetOcclusionHeight() {
        currentBirdseyeHeight = BIRDSEYE_HEIGHT;
        targetBirdseyeHeight = BIRDSEYE_HEIGHT;
        canSeeSky = true;
    }

    /**
     * 洞穴因子 [0, 1]：衡量相机被天花板压低到了什么程度。
     * 0 = 室外（高度 ≥ 默认 10 格）→ 正常视野
     * 1 = 深洞（高度 ≤ MIN_HEIGHT 5 格）→ 最大洞穴适配
     *
     * <p>该值被正交投影近裁剪面动态调整和洞穴雾效共用。
     */
    public static float getCaveFactor() {
        if (canSeeSky) return 0.0f; // 露天环境不触发洞穴效果
        double height = currentBirdseyeHeight;
        if (height >= BIRDSEYE_HEIGHT) return 0.0f;
        if (height <= MIN_HEIGHT) return 1.0f;
        return (float) ((BIRDSEYE_HEIGHT - height) / (BIRDSEYE_HEIGHT - MIN_HEIGHT));
    }

    // ===== 墙壁推离（Wall Push-Out） =====

    /** 当前平滑后的相机推离偏移量 */
    private static Vec3 currentPushOffset = Vec3.ZERO;
    /** 目标推离偏移量（射线检测后设定） */
    private static Vec3 targetPushOffset = Vec3.ZERO;
    /** 推离平滑插值速度（略快于高度平滑，因为需要及时避开墙面） */
    private static final double PUSH_LERP_SPEED = 0.25;
    /** 射线检测长度（格），检测此范围内是否有墙壁 */
    private static final double WALL_RAY_LENGTH = 2.5;
    /** 相机希望与墙壁保持的安全距离（格） */
    private static final double SAFE_DISTANCE = 1.8;

    /**
     * 六方向射线检测墙面距离，计算推离向量。
     *
     * <p>从相机位置向 ±X、±Y、±Z 六个方向各发一条 {@link #WALL_RAY_LENGTH} 格射线。
     * 每条命中墙壁的射线产生一个背离墙面的推力，强度与距墙距离成反比。
     * 六方向结果合并后钳制到 [-SAFE_DISTANCE, SAFE_DISTANCE] 每轴。
     *
     * <p>推离后做视线验证：从推离后的相机位置向玩家眼睛位置再做一次射线检测，
     * 如果推离后的相机仍然看不到玩家（被墙挡住），则缩小推离量至恢复视线。
     *
     * @param level     当前世界
     * @param cameraPos 当前（未推离）的相机世界坐标
     * @param playerEye 玩家眼睛位置（用于视线验证）
     * @return 推离偏移向量（与 cameraPos 相加得到安全位置）
     */
    public static Vec3 calculateWallPush(Level level, Vec3 cameraPos, Vec3 playerEye) {
        Vec3 push = Vec3.ZERO;
        // 六方向：±X, ±Y, ±Z
        double[][] dirs = {
            { 1, 0, 0}, {-1, 0, 0},
            { 0, 1, 0}, { 0,-1, 0},
            { 0, 0, 1}, { 0, 0,-1}
        };

        for (double[] d : dirs) {
            double hitDist = rayCastToWall(level, cameraPos, d[0], d[1], d[2]);
            if (hitDist < SAFE_DISTANCE) {
                // 离墙越近，推力越强；刚好在 SAFE_DISTANCE 时推力为 0
                double strength = (SAFE_DISTANCE - hitDist) / SAFE_DISTANCE;
                // 推力方向 = 背离墙面
                push = push.add(-d[0] * strength, -d[1] * strength, -d[2] * strength);
            }
        }

        // 每轴钳制，防止极端角落出现过大偏移
        Vec3 clamped = new Vec3(
            clampAxis(push.x),
            clampAxis(push.y) * 0.4, // 垂直推离权重降低：天花板/地面由 occlusion 处理
            clampAxis(push.z)
        );

        // === 视线验证：推离后相机能否看到玩家 ===
        Vec3 pushedCam = cameraPos.add(clamped);
        if (isOccluded(level, pushedCam, playerEye)) {
            // 二分查找：找到最大可接受推离量（不遮挡视线）
            return findMaxVisiblePush(level, cameraPos, playerEye, clamped);
        }

        return clamped;
    }

    /** 单方向射线检测：返回命中墙壁的距离，无墙则返回 WALL_RAY_LENGTH + 1 */
    private static double rayCastToWall(Level level, Vec3 origin, double dx, double dy, double dz) {
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        for (double d = 0.15; d <= WALL_RAY_LENGTH; d += 0.25) {
            mPos.set(
                (int) Math.floor(origin.x + dx * d),
                (int) Math.floor(origin.y + dy * d),
                (int) Math.floor(origin.z + dz * d)
            );
            BlockState state = level.getBlockState(mPos);
            if (state.isSolidRender(level, mPos)) {
                return d;
            }
        }
        return WALL_RAY_LENGTH + 1; // 无墙
    }

    /** DDA 步进检测 from→to 之间是否有固体方块遮挡 */
    private static boolean isOccluded(Level level, Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.1) return false;

        double stepX = dx / dist * 0.4;
        double stepY = dy / dist * 0.4;
        double stepZ = dz / dist * 0.4;
        double rx = from.x, ry = from.y, rz = from.z;
        int steps = (int) (dist / 0.4);
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < steps; i++) {
            rx += stepX; ry += stepY; rz += stepZ;
            mPos.set((int) Math.floor(rx), (int) Math.floor(ry), (int) Math.floor(rz));
            if (level.getBlockState(mPos).isSolidRender(level, mPos)) {
                return true;
            }
        }
        return false;
    }

    /** 二分查找最大可见推离量（0 = 不推离，scale = 全量推离） */
    private static Vec3 findMaxVisiblePush(Level level, Vec3 cameraPos, Vec3 playerEye, Vec3 fullPush) {
        double lo = 0.0, hi = 1.0;
        for (int i = 0; i < 8; i++) { // 8 次二分，精度 < 0.5%
            double mid = (lo + hi) * 0.5;
            Vec3 testPos = cameraPos.add(fullPush.multiply(mid, mid, mid));
            if (isOccluded(level, testPos, playerEye)) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return fullPush.multiply(lo, lo, lo);
    }

    private static double clampAxis(double v) {
        return Math.max(-SAFE_DISTANCE, Math.min(SAFE_DISTANCE, v));
    }

    /** 每 tick 调用：当前推离向目标推离平滑插值 */
    public static void updateWallPushSmoothing() {
        double dx = (targetPushOffset.x - currentPushOffset.x) * PUSH_LERP_SPEED;
        double dy = (targetPushOffset.y - currentPushOffset.y) * PUSH_LERP_SPEED;
        double dz = (targetPushOffset.z - currentPushOffset.z) * PUSH_LERP_SPEED;
        currentPushOffset = currentPushOffset.add(dx, dy, dz);
        // 差值极小 → 直接到位
        if (Math.abs(targetPushOffset.x - currentPushOffset.x) < 0.02
         && Math.abs(targetPushOffset.y - currentPushOffset.y) < 0.02
         && Math.abs(targetPushOffset.z - currentPushOffset.z) < 0.02) {
            currentPushOffset = targetPushOffset;
        }
    }

    /** 获取当前有效推离偏移量（已平滑） */
    public static Vec3 getEffectivePushOffset() {
        return currentPushOffset;
    }

    /** 设置目标推离偏移量 */
    public static void setTargetPushOffset(Vec3 offset) {
        targetPushOffset = offset;
    }

    /** 退出鸟瞰时重置推离 */
    public static void resetWallPushOffset() {
        currentPushOffset = Vec3.ZERO;
        targetPushOffset = Vec3.ZERO;
    }

    /** 鸟瞰模式的固定世界 yaw（进入鸟瞰时锁定，鼠标不再改变相机朝向） */
    private static double fixedYaw = 0.0;

    /** 当前相机的 look-at yaw，用于计算相机对齐移动方向 */
    private static float cameraLookYaw = 0f;

    /** 当前相机的 look-at pitch，用于 screenPosToWorldPos 精确反算 */
    private static float cameraLookPitch = BIRDSEYE_PITCH;

    /** 点击移动的目标位置（null = 无目标） */
    @Nullable
    private static Vec3 moveTarget = null;

    /** A* 寻路路径点列表（null = 无路径/直线移动） */
    @Nullable
    private static List<Vec3> currentPath = null;

    /** 当前路径点索引 */
    private static int currentWaypointIndex = 0;

    /** 异步寻路的 CompletableFuture（null = 无挂起的寻路请求） */
    @Nullable
    private static volatile CompletableFuture<List<Vec3>> pendingPathFuture = null;

    /** 是否已取消挂起的寻路请求 */
    private static volatile boolean pendingPathCancelled = false;

    /** 卡住计时器：连续减速超过此阈值时触发重新寻路或绕路 */
    private static int stuckTimer = 0;
    private static Vec3 stuckPosition = Vec3.ZERO;

    /** 鼠标悬浮的方块坐标（用于高亮） */
    @Nullable
    private static BlockPos hoveredBlockPos = null;

    /** 鼠标射线检测的完整结果（用于判断是否悬停生物） */
    @Nullable
    private static HitResult hoveredHitResult = null;

    // ===== 视角循环 =====

    /**
     * 循环到下一个视角模式，返回新的模式索引
     */
    public static int cyclePerspective() {
        currentPerspective = (currentPerspective + 1) % 4;
        birdseyeActive = (currentPerspective == 3);
        if (!birdseyeActive) {
            moveTarget = null; // 退出鸟瞰时清除移动目标
            clearPath();      // 清除寻路路径
            OrthoviewClientEvent.clearMarkers(); // 清除点击标记
            LockTargetSystem.unlockTarget(); // 清除锁定目标
            LockTargetSystem.clearRangedState(); // 清除远程攻击状态
        }
        return currentPerspective;
    }

    /**
     * 将当前模式索引映射为 Minecraft 的 CameraType
     */
    public static CameraType getCameraType() {
        switch (currentPerspective) {
            case 0:  return CameraType.FIRST_PERSON;
            case 1:  return CameraType.THIRD_PERSON_BACK;
            case 2:  return CameraType.THIRD_PERSON_FRONT;
            case 3:  return CameraType.THIRD_PERSON_BACK;  // 鸟瞰复用第三人称背渲染（显示玩家模型）
            default: return CameraType.FIRST_PERSON;
        }
    }

    /** 是否处于鸟瞰模式 */
    public static boolean isBirdseyeActive() {
        return birdseyeActive;
    }

    /** 获取当前模式索引 */
    public static int getCurrentPerspective() {
        return currentPerspective;
    }

    /** 重置为第一人称 */
    public static void reset() {
        currentPerspective = 0;
        birdseyeActive = false;
        moveTarget = null;
        clearPath();
        cancelPendingPath();
        OrthoviewClientEvent.clearMarkers();
        LockTargetSystem.unlockTarget();
        LockTargetSystem.clearRangedState();
        resetOcclusionHeight();
        resetWallPushOffset();
        SectionViewCuller.reset();
    }

    /** 获取当前视角的中文名称（用于动作栏提示） */
    public static String getPerspectiveName() {
        switch (currentPerspective) {
            case 0:  return "第一人称";
            case 1:  return "第三人称(背)";
            case 2:  return "第三人称(前)";
            case 3:  return "§b鸟瞰视角";
            default: return "未知";
        }
    }

    // ===== 固定相机朝向 =====

    /** 鸟瞰模式固定相机朝向（Reign of Nether 风格，NE 135° 对角线视角） */
    public static final double DEFAULT_FIXED_YAW = 135.0;

    /** 进入鸟瞰模式时锁定相机朝向（固定为 135° NE 方向，参考 Reign of Nether） */
    public static void onEnterBirdseye(double playerYaw) {
        fixedYaw = DEFAULT_FIXED_YAW;
        OrthoviewClientEvent.resetZoom();
    }

    /** 获取鸟瞰模式的固定世界 yaw */
    public static double getFixedYaw() {
        return fixedYaw;
    }

    /**
     * 旋转鸟瞰模式的固定相机朝向。
     * yaw 钳制在 [0, 360)，保持合法角度范围。
     *
     * @param delta 旋转角度增量（正=顺时针/右，负=逆时针/左）
     */
    public static void rotateCameraYaw(double delta) {
        fixedYaw = (fixedYaw + delta) % 360.0;
        if (fixedYaw < 0) fixedYaw += 360.0;
    }

    // ===== 相机 look-at yaw（用于相机对齐移动） =====

    /** 保存当前相机的 look-at yaw */
    public static void setCameraLookYaw(float yaw) {
        cameraLookYaw = yaw;
    }

    /** 获取当前相机的 look-at yaw（相机看向玩家的方向） */
    public static float getCameraLookYaw() {
        return cameraLookYaw;
    }

    /** 保存当前相机的 look-at pitch（由 onComputeCameraAngles 算出） */
    public static void setCameraLookPitch(float pitch) {
        cameraLookPitch = pitch;
    }

    /** 获取当前相机的 look-at pitch */
    public static float getCameraLookPitch() {
        return cameraLookPitch;
    }

    // ===== 点击移动目标 =====

    /** 设置移动目标 */
    public static void setMoveTarget(@Nullable Vec3 target) {
        moveTarget = target;
    }

    /** 获取移动目标 */
    @Nullable
    public static Vec3 getMoveTarget() {
        return moveTarget;
    }

    /** 清除移动目标（同时清除寻路路径和异步寻路请求） */
    public static void clearMoveTarget() {
        moveTarget = null;
        clearPath();
        cancelPendingPath();
    }

    /** 是否有移动目标 */
    public static boolean hasMoveTarget() {
        return moveTarget != null;
    }

    // ===== A* 寻路路径点导航 =====

    /** 设置寻路路径，重置 waypoint 索引和卡住计时器 */
    public static void setPath(@Nullable List<Vec3> path) {
        currentPath = path;
        currentWaypointIndex = 0;
        stuckTimer = 0;
        stuckPosition = Vec3.ZERO;
    }

    /** 获取当前路径点（null = 路径结束或无路径） */
    @Nullable
    public static Vec3 getCurrentWaypoint() {
        if (currentPath == null || currentWaypointIndex >= currentPath.size()) return null;
        return currentPath.get(currentWaypointIndex);
    }

    /** 前进到下一个路径点 */
    public static void advanceWaypoint() {
        currentWaypointIndex++;
    }

    /** 是否还有未到达的路径点 */
    public static boolean hasPath() {
        return currentPath != null && currentWaypointIndex < currentPath.size();
    }

    /** 路径点是否已全部到达 */
    public static boolean isPathComplete() {
        return currentPath != null && currentWaypointIndex >= currentPath.size();
    }

    /** 获取路径点总数（含已通过的） */
    public static int getTotalWaypoints() {
        return currentPath != null ? currentPath.size() : 0;
    }

    /** 获取剩余路径点数量 */
    public static int getRemainingWaypoints() {
        return hasPath() ? currentPath.size() - currentWaypointIndex : 0;
    }

    /** 清除路径（不清除 moveTarget，允许回退到直线移动） */
    public static void clearPath() {
        currentPath = null;
        currentWaypointIndex = 0;
        stuckTimer = 0;
    }

    // ===== 异步寻路 =====

    /** 提交异步 A* 寻路请求 */
    public static void submitAsyncPath(CompletableFuture<List<Vec3>> future) {
        cancelPendingPath();
        pendingPathCancelled = false;
        pendingPathFuture = future;
    }

    /** 检查异步寻路是否完成。若完成，提取结果并清除 future */
    @Nullable
    public static List<Vec3> pollAsyncPathResult() {
        CompletableFuture<List<Vec3>> f = pendingPathFuture;
        if (f == null) return null;
        if (!f.isDone()) return null;
        pendingPathFuture = null;
        if (pendingPathCancelled) {
            pendingPathCancelled = false;
            return null;
        }
        try {
            return f.getNow(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** 取消挂起的异步寻路请求 */
    public static void cancelPendingPath() {
        CompletableFuture<List<Vec3>> f = pendingPathFuture;
        if (f != null) {
            pendingPathCancelled = true;
            f.cancel(true);
            pendingPathFuture = null;
        }
    }

    /** 是否有挂起的异步寻路请求 */
    public static boolean hasPendingPath() {
        return pendingPathFuture != null && !pendingPathFuture.isDone();
    }

    // ===== 卡住检测 =====

    /** 更新卡住检测计时器。若玩家水平位移极小，累计 tick */
    public static void updateStuckDetection(Vec3 currentPos, double speed) {
        if (stuckTimer == 0) {
            stuckPosition = currentPos;
        }
        if (speed < 0.01) {
            stuckTimer++;
        } else if (currentPos.distanceToSqr(stuckPosition) > 0.04) {
            // 移动了，重置
            stuckTimer = 0;
            stuckPosition = currentPos;
        } else {
            stuckTimer++;
        }
    }

    /** 是否连续卡住超过阈值（~30 ticks = 1.5 秒） */
    public static boolean isStuck() {
        return stuckTimer > 30;
    }

    /** 重置卡住检测 */
    public static void resetStuckDetection() {
        stuckTimer = 0;
        stuckPosition = Vec3.ZERO;
    }

    // ===== 悬停方块高亮 =====

    /** 设置鼠标悬浮的方块坐标 */
    public static void setHoveredBlockPos(@Nullable BlockPos pos) {
        hoveredBlockPos = pos;
    }

    /** 获取鼠标悬浮的方块坐标 */
    @Nullable
    public static BlockPos getHoveredBlockPos() {
        return hoveredBlockPos;
    }

    // ===== 悬停射线结果 =====

    /** 设置鼠标射线检测结果 */
    public static void setHoveredHitResult(@Nullable HitResult result) {
        hoveredHitResult = result;
    }

    /** 获取鼠标射线检测结果 */
    @Nullable
    public static HitResult getHoveredHitResult() {
        return hoveredHitResult;
    }

    // ===== 虚拟光标位置（物理像素坐标，由 Overlay 每帧更新，供 raycast 使用） =====

    @Nullable
    private static double[] virtCursorPos = null;

    /** 设置虚拟光标位置（物理像素坐标） */
    public static void setVirtCursorPos(double x, double y) {
        virtCursorPos = new double[]{x, y};
    }

    /** 获取虚拟光标位置（物理像素坐标），未设置时返回 null */
    @Nullable
    public static double[] getVirtCursorPos() {
        return virtCursorPos;
    }

    /** 进入鸟瞰时重置虚拟光标为屏幕中心 */
    public static void resetVirtCursorToCenter(double screenW, double screenH) {
        virtCursorPos = new double[]{screenW / 2.0, screenH / 2.0};
    }

    /** 退出鸟瞰时清除虚拟光标 */
    public static void clearVirtCursor() {
        virtCursorPos = null;
    }

    // ===== 缓存的投影矩阵（用于 3D 世界→屏幕坐标投射） =====

    @Nullable
    private static Matrix4f cachedProjMatrix = null;
    @Nullable
    private static Matrix4f cachedModelViewMatrix = null;

    /** 保存渲染矩阵（由 onRenderLevelStage 每帧更新） */
    public static void setCachedMatrices(@Nullable Matrix4f proj, @Nullable Matrix4f modelView) {
        cachedProjMatrix = proj != null ? new Matrix4f(proj) : null;
        cachedModelViewMatrix = modelView != null ? new Matrix4f(modelView) : null;
    }

    /** 获取缓存的投影矩阵 */
    @Nullable
    public static Matrix4f getCachedProjMatrix() {
        return cachedProjMatrix;
    }

    /** 获取缓存的 ModelView 矩阵 */
    @Nullable
    public static Matrix4f getCachedModelViewMatrix() {
        return cachedModelViewMatrix;
    }

    // ===== 缓存的相机位置和旋转（用于 GUI overlay 投影） =====

    @Nullable
    private static Vec3 cachedCameraPos = null;
    @Nullable
    private static Quaternionf cachedCameraRot = null;

    /** 保存相机数据（由 onRenderLevelStage 每帧更新） */
    public static void cacheCameraData(Vec3 pos, Quaternionf rot) {
        cachedCameraPos = pos;
        cachedCameraRot = new Quaternionf(rot);
    }

    @Nullable
    public static Vec3 getCachedCameraPos() {
        return cachedCameraPos;
    }

    @Nullable
    public static Quaternionf getCachedCameraRot() {
        return cachedCameraRot;
    }

    /**
     * 将世界坐标投影到屏幕坐标（使用缓存的投影矩阵和相机数据）。
     * 正交投影下仍然有效（ortho 矩阵 w=1，透视除法退化为 identity）。
     * @return [screenX, screenY] 或 null（投影失败、不在视口内）
     */
    @Nullable
    public static double[] worldToScreen(Vec3 worldPos, int screenWidth, int screenHeight) {
        if (cachedProjMatrix == null || cachedCameraPos == null || cachedCameraRot == null) return null;

        // 1. 世界位置 → 相机空间（平移 + 旋转）
        Vec3 relative = worldPos.subtract(cachedCameraPos);
        Vector4f camSpace = new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1.0f);
        // 应用相机旋转的逆（世界→相机）
        Quaternionf invRot = new Quaternionf(cachedCameraRot).conjugate();
        camSpace.rotate(invRot); // 等价于乘以 rotation matrix

        // 2. 相机空间 → 裁剪空间（投影矩阵）
        Vector4f clipSpace = cachedProjMatrix.transform(camSpace);

        // 3. 裁剪 → NDC → 屏幕坐标
        if (clipSpace.w == 0) return null;
        float ndcX = clipSpace.x / clipSpace.w;
        float ndcY = clipSpace.y / clipSpace.w;
        if (ndcX < -1 || ndcX > 1 || ndcY < -1 || ndcY > 1) return null; // 不在视口内

        double screenX = (ndcX + 1.0) * 0.5 * screenWidth;
        double screenY = (1.0 - ndcY) * 0.5 * screenHeight;
        return new double[]{screenX, screenY};
    }

    // ===== 纯数学世界→屏幕投影（正交投影逆运算，不依赖缓存） =====

    /**
     * 纯数学世界坐标→屏幕坐标投影（正交投影）。
     * 这是 screenPosToWorldPos 的逆运算，不依赖任何缓存的渲染矩阵或相机数据。
     * 仅使用已知参数：zoom、fixedYaw、cameraLookPitch、玩家位置。
     *
     * 原理：正交投影中，世界→屏幕是线性映射，无需透视除法。
     * 相机右轴和上轴可由 fixedYaw + cameraLookPitch 纯三角函数推导，
     * 与 screenPosToWorldPos 使用完全相同的坐标轴定义。
     *
     * @param worldPos 要投影的世界坐标
     * @param screenWidth 窗口物理像素宽度
     * @param screenHeight 窗口物理像素高度
     * @param playerPos 玩家当前位置（屏幕中心参考点）
     * @return [screenX, screenY] 屏幕坐标，或 null（参数无效）
     */
    @Nullable
    public static double[] worldToScreenPureMath(Vec3 worldPos, int screenWidth, int screenHeight, Vec3 playerPos) {
        double zoom = OrthoviewClientEvent.getZoom();
        if (zoom <= 0) return null;

        // 相对玩家位置的偏移
        double relX = worldPos.x - playerPos.x;
        double relY = worldPos.y - playerPos.y;
        double relZ = worldPos.z - playerPos.z;

        // 相机轴（与 screenPosToWorldPos 使用完全相同的定义）
        double yawRad = Math.toRadians(fixedYaw);
        double pitchRad = Math.toRadians(cameraLookPitch);

        // 相机右方向（水平）：right = (cos(yaw), 0, sin(yaw))
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        // 相机上方向（3D）：up = right × forward_3d
        // forward_3d = (sin(yaw)*cos(pitch), -sin(pitch), -cos(yaw)*cos(pitch))
        // up = (sin(yaw)*sin(pitch), cos(pitch), -cos(yaw)*sin(pitch))
        double upX = Math.sin(yawRad) * Math.sin(pitchRad);
        double upY = Math.cos(pitchRad);
        double upZ = -Math.cos(yawRad) * Math.sin(pitchRad);

        // 投影到相机右轴和上轴
        double camRight = relX * rightX + relZ * rightZ;
        double camUp = relX * upX + relY * upY + relZ * upZ;

        // 正交投影：blocks → pixels（线性映射，与 screenPosToWorldPos 完全对称）
        double pixelsPerBlock = screenHeight / (2.0 * zoom);

        double screenX = screenWidth / 2.0 + camRight * pixelsPerBlock;
        double screenY = screenHeight / 2.0 - camUp * pixelsPerBlock;

        return new double[]{screenX, screenY};
    }

    // ===== 正交投影纯数学反算：屏幕坐标 → 世界坐标（参考 Reign of Nether） =====

    /**
     * 正交投影下的屏幕坐标 → 世界地面 XZ 坐标。
     * 纯数学 pixelsToBlocks 反算，不依赖任何渲染缓存数据（投影矩阵/相机四元数）。
     * 参考 Reign of Nether 的 screenPosToWorldPos 方案。
     *
     * 原理：正交投影中，屏幕偏移和世界偏移是线性对应的：
     *   pixelsToBlocks = screenHeight / (2 × zoom) ← 正交投影线性映射
     *   屏幕中心对应玩家位置
     *   水平偏移 → 相机右方向的世界偏移
     *   垂直偏移 → 相机前方向的世界偏移（经俯仰角修正）
     *
     * @param mouseX 鼠标物理像素 X（glfwGetCursorPos 或虚拟光标）
     * @param mouseY 鼠标物理像素 Y
     * @param screenWidth 窗口物理像素宽度
     * @param screenHeight 窗口物理像素高度
     * @param playerPos 玩家当前位置
     * @return 世界地面 [worldX, worldZ]，null 表示参数无效
     */
    @Nullable
    public static double[] screenPosToWorldPos(double mouseX, double mouseY,
                                                int screenWidth, int screenHeight,
                                                Vec3 playerPos) {
        double zoom = OrthoviewClientEvent.getZoom();
        if (zoom <= 0) return null;

        // pixelsPerBlock = 每个世界方块对应多少屏幕像素
        // ortho 矩阵 halfHeight = zoom → 覆盖 screenHeight 像素
        double pixelsPerBlock = screenHeight / (2.0 * zoom);

        // 屏幕偏移 → 相机空间偏移（blocks）
        double camRightOffset = (mouseX - screenWidth / 2.0) / pixelsPerBlock;   // 相机右方向偏移
        double camUpOffset = (screenHeight / 2.0 - mouseY) / pixelsPerBlock;     // 相机上方向偏移（屏幕Y反转）

        // ===== 从已知参数推导相机 3D 轴（不需要缓存四元数） =====
        // 相机 yaw = fixedYaw（进入鸟瞰时锁定）
        // 相机 pitch = cameraLookPitch（由 onComputeCameraAngles look-at 算法实时缓存）
        // 两者都是已知参数，不需要投影矩阵或四元数

        // 相机 forward = 从相机指向玩家的方向
        // 水平 yaw = fixedYaw (相机看向玩家)
        // forward 水平分量 = -sin(fixedYaw), cos(fixedYaw)（从相机→玩家）
        double yawRad = Math.toRadians(fixedYaw);

        // 相机前方向（水平分量，从相机指向玩家）
        // 注意：fixedYaw 是 Minecraft 的 yaw（实体面朝方向），但相机从后方看向玩家，
        // 所以"相机→玩家"方向 = 反转 Minecraft look 方向 = (sin(yaw), -cos(yaw))
        // 之前的 bug 用了 (-sin(yaw), cos(yaw)) = 玩家→相机方向，导致中心对称
        double forwardX = Math.sin(yawRad);
        double forwardZ = -Math.cos(yawRad);

        // 相机右方向（水平分量，垂直于前方向）
        // 与 forward 同理：实际相机 lookYaw = fixedYaw + 180°，
        // 所以 right = (-cos(lookYaw), -sin(lookYaw)) = (cos(fixedYaw), sin(fixedYaw))
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        // 俯仰角修正：正交投影鸟瞰视角下，地面距离在屏幕上被 sin(pitch) 压缩
        // 反算时必须除以 sin(pitch) 还原真实地面距离（参考 Reign of Nether 的 z/sin(camRotY)）
        // 例如 pitch=45°: sin=0.707，地面10格在屏幕上只占7.07格 → 反算: 7.07/0.707=10 ✓
        // 之前的 bug 是乘以 sin(pitch)，反而进一步压缩 → 离中心越远偏移越大
        double pitchRad = Math.toRadians(cameraLookPitch);

        // 屏幕垂直偏移 → 地面前进偏移（除以 sin(pitch) 还原压缩）
        double groundForwardDist = camUpOffset / Math.sin(pitchRad);

        // 世界坐标 = 玩家位置 + 右方向偏移 + 前方向偏移
        double worldX = playerPos.x + camRightOffset * rightX + groundForwardDist * forwardX;
        double worldZ = playerPos.z + camRightOffset * rightZ + groundForwardDist * forwardZ;

        return new double[]{worldX, worldZ};
    }
}