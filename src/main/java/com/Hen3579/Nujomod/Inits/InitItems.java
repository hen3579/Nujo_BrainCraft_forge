package com.Hen3579.Nujomod.Inits;

import com.mojang.realmsclient.client.Request;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.Hen3579.Nujomod.Items.Materials.*;
import com.Hen3579.Nujomod.Items.Tools.*;
import org.spongepowered.asm.util.IConsumer;


import java.util.Set;
import java.util.function.Consumer;
import  net.minecraft.client.multiplayer.ClientLevel;
import static com.Hen3579.Nujomod.NujoBraincraft.MODID;

public class InitItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,MODID);

    public static void register(IEventBus eventBus){ITEMS.register(eventBus);
    }
    public static final RegistryObject<Item> MAGIC_PAINTBRUSH = ITEMS.register("magic_paintbrush", () -> new Item(new Item.Properties()));
    // 修改原有注册项
    public static final RegistryObject<Item> SMART_PHONE = ITEMS.register("smart_phone",
            () -> new SmartPhone(new Item.Properties())); // 使用自定义的SmartPhone类
    public static final RegistryObject<Item> IMAGINATIONAL_CORE = ITEMS.register("imaginational_core", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> NUJO_THINKER_SPAWN_EGG = ITEMS.register("nujo_thinker_spawn_egg",
            () -> new ForgeSpawnEggItem(InitEntity.NUJO_THINKER,
                    0x3A2C28, // 主要颜色（深棕色）
                    0xE3DAC9, // 次要颜色（米色）
                    new Item.Properties()));

}
