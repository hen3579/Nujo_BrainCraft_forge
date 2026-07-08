package com.Hen3579.Nujomod.Client.Events;

import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

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

    /** 鸟瞰模式的固定世界 yaw（进入鸟瞰时锁定，鼠标不再改变相机朝向） */
    private static double fixedYaw = 0.0;

    /** 当前相机的 look-at yaw，用于计算相机对齐移动方向 */
    private static float cameraLookYaw = 0f;

    /** 当前相机的 look-at pitch，用于 screenPosToWorldPos 精确反算 */
    private static float cameraLookPitch = BIRDSEYE_PITCH;

    /** 点击移动的目标位置（null = 无目标） */
    @Nullable
    private static Vec3 moveTarget = null;

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
        OrthoviewClientEvent.clearMarkers();
        LockTargetSystem.unlockTarget();
        LockTargetSystem.clearRangedState();
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

    /** 清除移动目标 */
    public static void clearMoveTarget() {
        moveTarget = null;
    }

    /** 是否有移动目标 */
    public static boolean hasMoveTarget() {
        return moveTarget != null;
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