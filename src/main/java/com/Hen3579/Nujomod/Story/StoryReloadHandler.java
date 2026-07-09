package com.Hen3579.Nujomod.Story;

import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 注册服务器资源重载监听器
 * <p>
 * 当执行 /reload 或服务器启动时，StoryLoader 会重新加载所有剧情 JSON。
 */
@Mod.EventBusSubscriber(modid = com.Hen3579.Nujomod.NujoBraincraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StoryReloadHandler {

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(StoryLoader.INSTANCE);
    }
}
