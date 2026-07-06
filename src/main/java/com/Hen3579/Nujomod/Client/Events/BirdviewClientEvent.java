package com.Hen3579.Nujomod.Client.Events;

import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * 视角循环状态管理器
 * 扩展原版 F5 三视角为四视角：第一人称 → 第三人称(背) → 第三人称(前) → 鸟瞰
 * 同时管理鸟瞰模式的相机对齐移动、点击移动和方块高亮
 */
public class BirdviewClientEvent {

    private static int currentPerspective = 0;  // 0:第一人称 1:第三人称背 2:第三人称前 3:鸟瞰
    private static boolean birdseyeActive = false;

    /** 鸟瞰视角高度（玩家上方格数） */
    public static final double BIRDSEYE_HEIGHT = 20.0;

    /** 鸟瞰视角俯仰角（0=平视, 90=垂直向下），RTS 风格约 45°（参考 Reign of Nether） */
    public static final float BIRDSEYE_PITCH = 45.0F;

    /** 鸟瞰模式的固定世界 yaw（进入鸟瞰时锁定，鼠标不再改变相机朝向） */
    private static double fixedYaw = 0.0;

    /** 当前相机的 look-at yaw，用于计算相机对齐移动方向 */
    private static float cameraLookYaw = 0f;

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
}