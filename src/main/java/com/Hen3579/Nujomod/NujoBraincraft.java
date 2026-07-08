package com.Hen3579.Nujomod;

import com.Hen3579.Nujomod.Client.Events.BirdviewCursorOverlay;
import com.Hen3579.Nujomod.Client.Events.ClientEventHandler;
import com.Hen3579.Nujomod.Inits.*;
import com.Hen3579.Nujomod.Network.BirdviewNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import software.bernie.geckolib.GeckoLib;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(NujoBraincraft.MODID)
public class NujoBraincraft
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "nujobraincraft";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    public NujoBraincraft()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        // 在 mod 主类的 FMLClientSetupEvent 或类似客户端初始化方法中注册
        FMLJavaModLoadingContext.get().getModEventBus().addListener((RenderLevelStageEvent.RegisterStageEvent event) -> {
            // 注册名为 "last" 的自定义阶段（RenderType 设为 null 表示需手动触发）
            RenderLevelStageEvent.Stage customLastStage = event.register(new ResourceLocation(MODID, "last"), null);
        });
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);
        // 注册声音事件

        GeckoLib.initialize();
        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(ClientEventHandler.class);
        // 注册音乐处理器
        InitSounds.SOUND_EVENTS.register(FMLJavaModLoadingContext.get().getModEventBus());
        //MinecraftForge.EVENT_BUS.register(MusicHandler.class);
        InitEntity.ENTITIES.register(modEventBus); // 新增实体注册

        MinecraftForge.EVENT_BUS.addListener(PlayerLoggedInHandler::onLoggedIn);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);



        InitItems.ITEMS.register(modEventBus);
        InitBlocks.BLOCKS.register(modEventBus);
        CreativeTabReg.TABS.register(modEventBus);

    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        // 注册网络通道（客户端→服务器鸟瞰状态同步）
        event.enqueueWork(BirdviewNetwork::register);
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        if(event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(InitItems.IMAGINATIONAL_CORE);
            // event.accept(InitBlocks.BLANK_ZONE_BLOCK);
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(InitItems.MAGIC_PAINTBRUSH);
        }
    }



    public static class PlayerLoggedInHandler {
        public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            // 检查到玩家登录后，向玩家发送一条欢迎提醒
            var player = event.getEntity();
            player.sendSystemMessage(Component.translatable("message.nujobraincraft.welcome").append(Component.translatable("message.nujobraincraft.modname").withStyle(ChatFormatting.YELLOW)));
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {}

        @SubscribeEvent
        public static void registerGuiOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
            event.registerAboveAll(BirdviewCursorOverlay.ID.getPath(), new BirdviewCursorOverlay());
        }
    }
}
