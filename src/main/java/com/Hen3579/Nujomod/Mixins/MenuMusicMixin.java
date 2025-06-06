package com.Hen3579.Nujomod.Mixins;

import com.Hen3579.Nujomod.Inits.InitSounds;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author : IMG
 * @create : 2024/10/25
 */
@Mixin(Musics.class)
public abstract class MenuMusicMixin {

    @Mutable
    @Shadow @Final public static Music MENU;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void init(CallbackInfo ci) {
        MENU = new Music(InitSounds.MENU_TITLE_MUSIC.getHolder().get(), 50, 50, true);
    }
}
