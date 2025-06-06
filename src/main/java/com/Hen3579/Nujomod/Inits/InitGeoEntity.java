package com.Hen3579.Nujomod.Inits;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.Hen3579.Nujomod.Entities.NujoThinker.NujoThinkerRenderer;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class InitGeoEntity {
    @SubscribeEvent
    public static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(InitEntity.NUJO_THINKER.get(), NujoThinkerRenderer::new);
    }
}
