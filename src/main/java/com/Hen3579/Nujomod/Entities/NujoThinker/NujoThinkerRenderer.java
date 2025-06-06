package com.Hen3579.Nujomod.Entities.NujoThinker;

import com.Hen3579.Nujomod.Entities.NujoThinker.NujoThinkerEntity;
import com.Hen3579.Nujomod.Entities.NujoThinker.NujoThinkerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class NujoThinkerRenderer extends GeoEntityRenderer<NujoThinkerEntity> {
    public NujoThinkerRenderer(EntityRendererProvider.Context context) {
        super(context, new NujoThinkerModel());
    }
}
