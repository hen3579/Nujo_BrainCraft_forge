package com.Hen3579.Nujomod.Story;

import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * 快捷访问玩家剧情进度的工具类。
 * <p>
 * 使用方式：
 * <pre>
 * StoryProgress.get(player).ifPresent(cap -> {
 *     String stage = cap.getCurrentStage();
 *     ...
 * });
 * </pre>
 */
public final class StoryProgress {

    private StoryProgress() {}

    /**
     * 获取玩家的剧情进度 Capability
     */
    public static Optional<StoryCapability> get(Player player) {
        return player.getCapability(StoryCapabilityProvider.STORY_CAP).resolve();
    }

    /**
     * 获取玩家的剧情进度 Capability，如果不存在则返回 null
     */
    public static StoryCapability getOrNull(Player player) {
        return player.getCapability(StoryCapabilityProvider.STORY_CAP).orElse(null);
    }

    /**
     * 获取玩家当前剧情阶段
     */
    public static String getStage(Player player) {
        return get(player).map(StoryCapability::getCurrentStage).orElse(StoryCapability.DEFAULT_STAGE);
    }
}
