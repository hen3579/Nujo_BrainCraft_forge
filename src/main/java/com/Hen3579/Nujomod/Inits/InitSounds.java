package com.Hen3579.Nujomod.Inits;

import com.Hen3579.Nujomod.NujoBraincraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class InitSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, NujoBraincraft.MODID);


    public static RegistryObject<SoundEvent> MENU_TITLE_MUSIC = register("main_title_music");
    public static RegistryObject<SoundEvent> SINGLEPLAYER_START = register("singleplayer_start");
    // 添加单人游戏按钮音效注册
    // public static final RegistryObject<SoundEvent> LOADING = register("loading");
    public static RegistryObject<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(NujoBraincraft.MODID, name)));
    }
}
