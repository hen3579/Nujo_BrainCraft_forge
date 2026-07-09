package com.Hen3579.Nujomod.Story;

import com.Hen3579.Nujomod.NujoBraincraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Capability 注册 & 事件处理
 * <p>
 * - 注册 Capability 接口
 * - 将 Capability 挂载到 Player 实体
 * - 玩家死亡/维度切换时保持数据（不丢失）
 * - 玩家登录时同步到客户端
 */
public class StoryCapabilityRegistration {

    public static final ResourceLocation CAP_ID =
            new ResourceLocation(NujoBraincraft.MODID, "story_progress");

    /**
     * 注册 Capability（MOD bus）
     */
    public static void register(RegisterCapabilitiesEvent event) {
        event.register(StoryCapability.class);
    }

    /**
     * 挂载 Capability 到 Player（FORGE bus）
     */
    @Mod.EventBusSubscriber(modid = NujoBraincraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEvents {

        @SubscribeEvent
        public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Player) {
                if (!event.getObject().getCapability(StoryCapabilityProvider.STORY_CAP).isPresent()) {
                    event.addCapability(CAP_ID, new StoryCapabilityProvider());
                }
            }
        }

        /**
         * 玩家死亡后复活 / 从末地返回 — 拷贝旧数据到新实体
         */
        @SubscribeEvent
        public static void onPlayerClone(PlayerEvent.Clone event) {
            if (event.isWasDeath()) {
                // 死亡复活：拷贝剧情进度（不丢失）
                event.getOriginal().reviveCaps();
                event.getOriginal().getCapability(StoryCapabilityProvider.STORY_CAP).ifPresent(oldCap -> {
                    event.getEntity().getCapability(StoryCapabilityProvider.STORY_CAP).ifPresent(newCap -> {
                        newCap.copyFrom(oldCap);
                    });
                });
                event.getOriginal().invalidateCaps();
            } else {
                // 维度切换（如下界传送门）：Forge 会自动保留，但保险起见也拷贝
                event.getOriginal().reviveCaps();
                event.getOriginal().getCapability(StoryCapabilityProvider.STORY_CAP).ifPresent(oldCap -> {
                    event.getEntity().getCapability(StoryCapabilityProvider.STORY_CAP).ifPresent(newCap -> {
                        newCap.copyFrom(oldCap);
                    });
                });
                event.getOriginal().invalidateCaps();
            }
        }
    }
}
