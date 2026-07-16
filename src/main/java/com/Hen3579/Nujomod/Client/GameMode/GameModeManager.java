package com.Hen3579.Nujomod.Client.GameMode;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;

/**
 * 游戏模式状态管理器。
 *
 * 支持淡入淡出过渡动画：
 * - 切换时记录时间戳和旧模式
 * - 提供 getFadeAlpha() 方法计算当前透明度
 * - 淡出阶段：旧 HUD 从 1.0 → 0.0
 * - 淡入阶段：新 HUD 从 0.0 → 1.0
 */
public class GameModeManager {
    private static GameModeState currentState = GameModeState.VANILLA;

    /** 正在淡出的旧模式（null 表示无过渡） */
    private static GameModeState fadingOutMode = null;

    /** 过渡开始时间戳（毫秒，System.currentTimeMillis()） */
    private static long transitionStartTime = 0L;

    /** 过渡动画总时长（毫秒） */
    private static final long TRANSITION_DURATION_MS = 500;

    public static GameModeState getCurrentState() {
        return currentState;
    }

    public static void setState(GameModeState newState) {
        if (currentState == newState) return;
        GameModeState oldState = currentState;
        currentState = newState;

        // 开始淡出过渡
        fadingOutMode = oldState;
        transitionStartTime = System.currentTimeMillis();

        MinecraftForge.EVENT_BUS.post(new GameModeSwitchEvent(oldState, newState));
    }

    public static void cycleMode() {
        setState(currentState.next());
    }

    public static boolean isVanilla() {
        return currentState == GameModeState.VANILLA;
    }

    public static boolean isCombat() {
        return currentState == GameModeState.COMBAT;
    }

    public static boolean isBuild() {
        return currentState == GameModeState.BUILD;
    }

    // ===== 淡入淡出动画支持 =====

    /**
     * 判断指定模式是否正在淡出。
     * @param mode 要检查的模式
     * @return true 表示该模式正在淡出（需要以透明度 < 1 渲染）
     */
    public static boolean isFadingOut(GameModeState mode) {
        if (fadingOutMode != mode) return false;
        return getTransitionProgress() < 1.0f;
    }

    /**
     * 获取当前过渡进度 [0, 1]。
     * 0 = 刚开始切换，1 = 过渡完成
     */
    public static float getTransitionProgress() {
        if (fadingOutMode == null) return 1.0f;
        long elapsed = System.currentTimeMillis() - transitionStartTime;
        float progress = (float) elapsed / TRANSITION_DURATION_MS;
        return Math.max(0f, Math.min(1f, progress));
    }

    /**
     * 获取指定模式的当前透明度 [0, 1]。
     *
     * - 如果是当前模式：淡入（0 → 1）
     * - 如果是正在淡出的模式：淡出（1 → 0）
     * - 否则：0（不渲染）
     *
     * @param mode 要查询的模式
     * @return 透明度，0 表示完全透明，1 表示完全可见
     */
    public static float getFadeAlpha(GameModeState mode) {
        float progress = getTransitionProgress();

        if (mode == currentState) {
            // 新模式：淡入
            return progress;
        }

        if (mode == fadingOutMode && progress < 1.0f) {
            // 旧模式：淡出
            return 1.0f - progress;
        }

        // 其他模式：不渲染
        return 0f;
    }

    /**
     * 清除过渡状态（过渡完成后调用）。
     */
    public static void clearTransition() {
        if (getTransitionProgress() >= 1.0f) {
            fadingOutMode = null;
        }
    }

    // ===== 消息提示 =====

    public static void sendModeMessage() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                Component.literal("§7[HUD] §f已切换至 " + getModeColor() + currentState.getDisplayName() + " §f模式"),
                true
            );
        }
    }

    private static String getModeColor() {
        switch (currentState) {
            case VANILLA: return "§7";
            case COMBAT: return "§c";
            case BUILD: return "§a";
            default: return "§f";
        }
    }
}