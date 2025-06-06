package com.Hen3579.Nujomod.Inits;

import com.Hen3579.Nujomod.Entities.NujoThinker.NujoThinkerEntity;
import com.Hen3579.Nujomod.NujoBraincraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class InitEntity {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, NujoBraincraft.MODID);

    public static final RegistryObject<EntityType<NujoThinkerEntity>> NUJO_THINKER =
            ENTITIES.register("nujo_thinker", () ->
                    EntityType.Builder.of(NujoThinkerEntity::new, MobCategory.CREATURE)
                            .sized(0.6f, 1.8f) // 设置生物碰撞箱尺寸
                            .build("nujo_thinker"));

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}