package com.Hen3579.Nujomod.Client.Events;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 鸟瞰模式正交投影和缩放控制。
 * 参考 Reign of Nether 的 RTS 正交投影方案：
 * - 用 OrthoViewMixin 替换 LevelRenderer 的透视投影为正交投影
 * - Zoom 控制可见范围（block 数），默认 30，范围 10–60
 * - 右键点击移动的快捷标记痕迹
 */
public class OrthoviewClientEvent {

    // ========== 缩放 ==========

    /** 默认 zoom：30 格半高 → 约 6-7 个 chunk 可见 */
    public static double ZOOM_DEFAULT = 30.0;
    public static double ZOOM_MIN = 10.0;
    public static double ZOOM_MAX = 60.0;
    /** 每格滚轮的缩放步进 */
    public static double ZOOM_STEP = 3.0;

    private static double zoom = ZOOM_DEFAULT;

    public static double getZoom() {
        return zoom;
    }

    /** 在 birdview 进入/退出时调用 */
    public static void resetZoom() {
        zoom = ZOOM_DEFAULT;
    }

    /**
     * 处理鼠标滚轮缩放。
     * @param delta 滚轮滚动量（正=放大/拉近，负=缩小/拉远；物理滚轮通常 ±1 或 ±3）
     */
    public static void adjustZoom(double delta) {
        double newZoom = zoom - delta * ZOOM_STEP; // 滚轮向上（+）→ zoom 减小 → 画面拉近
        zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, newZoom));
    }

    // ========== 正交投影矩阵 ==========

    /** 室外近裁剪面（格），-3000 参考 RoN 方案，把近裁剪面推到相机后方防止任何近处裁剪 */
    public static float NEAR_PLANE_OUTDOOR = -3000.0f;
    /** 深洞穴近裁剪面（格），-3000 与室外一致 */
    public static float NEAR_PLANE_CAVE = -3000.0f;
    /** 远裁剪面，固定 3000 格（配合 near=-3000，形成 6000 格宽的渲染范围） */
    public static float FAR_PLANE = 3000.0f;

    /**
     * 计算正交投影矩阵。
     * 可见范围以 zoom 为半高（block 数），半宽根据屏幕宽高比自动适配。
     * 近裁剪面固定为 -3000，远裁剪面固定为 +3000，形成 6000 格宽的渲染范围，
     * 彻底消除任何近处裁剪（建筑侧面、岩壁、地面边缘）。
     */
    public static Matrix4f getOrthoMatrix() {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        if (window == null) {
            return new Matrix4f().setOrtho(-30, 30, -30, 30, NEAR_PLANE_OUTDOOR, FAR_PLANE);
        }

        float aspect = (float) window.getWidth() / (float) window.getHeight();
        float halfHeight = (float) zoom;
        float halfWidth = halfHeight * aspect;

        return new Matrix4f().setOrtho(
                -halfWidth, halfWidth,
                -halfHeight, halfHeight,
                NEAR_PLANE_OUTDOOR, FAR_PLANE
        );
    }

    // ========== 右键点击标记痕迹 ==========

    /** 标记持续时间（毫秒） */
    public static long MARKER_DURATION_MS = 1000;

    /** 单次标记的数据 */
    public static class ClickMarker {
        public final Vec3 position;
        public final long createdTime;

        public ClickMarker(Vec3 position) {
            this.position = position;
            this.createdTime = System.currentTimeMillis();
        }

        /** 获取已过时间比例 [0, 1]，1 = 应移除 */
        public float getAge() {
            return Math.min(1.0f, (System.currentTimeMillis() - createdTime) / (float) MARKER_DURATION_MS);
        }

        /** 是否已过期 */
        public boolean isExpired() {
            return System.currentTimeMillis() - createdTime >= MARKER_DURATION_MS;
        }
    }

    private static final List<ClickMarker> clickMarkers = new ArrayList<>();

    /** 添加一个点击标记 */
    public static void addClickMarker(Vec3 position) {
        clickMarkers.add(new ClickMarker(position));
    }

    /** 获取所有尚未过期的标记（同时清理过期标记） */
    public static List<ClickMarker> getActiveMarkers() {
        Iterator<ClickMarker> it = clickMarkers.iterator();
        while (it.hasNext()) {
            if (it.next().isExpired()) {
                it.remove();
            }
        }
        return clickMarkers;
    }

    /** 退出鸟瞰时清除所有标记 */
    public static void clearMarkers() {
        clickMarkers.clear();
    }
}