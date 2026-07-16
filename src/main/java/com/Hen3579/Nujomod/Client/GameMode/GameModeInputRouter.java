package com.Hen3579.Nujomod.Client.GameMode;

import com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.Hen3579.Nujomod.NujoBraincraft.MODID;

@Mod.EventBusSubscriber(modid = MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public class GameModeInputRouter {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (event.phase == TickEvent.Phase.START) {
            GameModeState mode = GameModeManager.getCurrentState();
            switch (mode) {
                case VANILLA:
                    handleVanillaInputStart(mc);
                    break;
                case COMBAT:
                    handleCombatInputStart(mc);
                    break;
                case BUILD:
                    handleBuildInputStart(mc);
                    break;
            }
        }
    }

    @SubscribeEvent
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return;

        GameModeState mode = GameModeManager.getCurrentState();
        switch (mode) {
            case VANILLA:
                if (handleVanillaMouseButtonPre(mc, event)) {
                    event.setCanceled(true);
                }
                break;
            case COMBAT:
                if (handleCombatMouseButtonPre(mc, event)) {
                    event.setCanceled(true);
                }
                break;
            case BUILD:
                if (handleBuildMouseButtonPre(mc, event)) {
                    event.setCanceled(true);
                }
                break;
        }
    }

    private static void handleVanillaInputStart(Minecraft mc) {
    }

    private static void handleCombatInputStart(Minecraft mc) {
    }

    private static void handleBuildInputStart(Minecraft mc) {
    }

    private static boolean handleVanillaMouseButtonPre(Minecraft mc, InputEvent.MouseButton.Pre event) {
        return false;
    }

    private static boolean handleCombatMouseButtonPre(Minecraft mc, InputEvent.MouseButton.Pre event) {
        return false;
    }

    private static boolean handleBuildMouseButtonPre(Minecraft mc, InputEvent.MouseButton.Pre event) {
        return false;
    }
}
