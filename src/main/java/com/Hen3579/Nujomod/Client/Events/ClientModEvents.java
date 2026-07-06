package com.Hen3579.Nujomod.Client.Events;

import com.Hen3579.Nujomod.Entities.NujoThinker.NujoThinkerEntity;
import com.Hen3579.Nujomod.Inits.InitItems;
import com.Hen3579.Nujomod.Inits.InitSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraft.sounds.Music;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.gui.screens.ProgressScreen;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import static com.Hen3579.Nujomod.Inits.InitEntity.NUJO_THINKER;
import static com.Hen3579.Nujomod.NujoBraincraft.MODID;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {


    // 添加状态跟踪字段
    private static boolean isLoadingState = false;
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 设置默认全屏
        Minecraft.getInstance().options.fullscreen().set(true);
        Minecraft.getInstance().options.save();
        Minecraft.getInstance().resizeDisplay();
    }
    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event){
        event.put(NUJO_THINKER.get(), NujoThinkerEntity.createAttributes().build());
    }
}

