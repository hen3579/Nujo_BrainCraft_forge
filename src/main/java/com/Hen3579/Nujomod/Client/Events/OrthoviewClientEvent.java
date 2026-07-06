package com.Hen3579.Nujomod.Client.Events;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

/**
 * 鸟瞰模式正交投影和缩放控制。
 * 参考 Reign of Nether 的 RTS 正交投影方案：
 * - 用 OrthoViewMixin 替换 LevelRenderer 的透视投影为正交投影
 * - Zoom 控制可见范围（block 数），默认 30，范围 10–90
 */
public class OrthoviewClientEvent {

    // ========== 缩放 ==========

    /** 默认 zoom：30 × 2 = 60 格垂直可见 */
    public static final double ZOOM_DEFAULT = 30.0;
    public static final double ZOOM_MIN = 10.0;
    public static final double ZOOM_MAX = 90.0;
    /** 每格滚轮的缩放步进 */
    public static final double ZOOM_STEP = 3.0;

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

    /**
     * 计算正交投影矩阵。
     * 可见范围以 zoom 为半高（block 数），半宽根据屏幕宽高比自动适配。
     * 默认 zoom=30 → 垂直可见 60 格，水平可见 60 × aspect 格。
     */
    public static Matrix4f getOrthoMatrix() {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        if (window == null) {
            return new Matrix4f().setOrtho(-30, 30, -30, 30, 0.1f, 1000.0f);
        }

        float aspect = (float) window.getWidth() / (float) window.getHeight();
        float halfHeight = (float) zoom;
        float halfWidth = halfHeight * aspect;

        return new Matrix4f().setOrtho(
                -halfWidth, halfWidth,
                -halfHeight, halfHeight,
                0.1f, 1000.0f
        );
    }
}