package com.Hen3579.Nujomod.Entities.NujoThinker;


import com.Hen3579.Nujomod.Entities.NujoThinker.NujoThinkerEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

import static com.Hen3579.Nujomod.NujoBraincraft.MODID;

public class NujoThinkerModel extends DefaultedEntityGeoModel<NujoThinkerEntity> {
    public NujoThinkerModel() {
        super(new ResourceLocation(MODID, "nujo_thinker"));
    }
}
