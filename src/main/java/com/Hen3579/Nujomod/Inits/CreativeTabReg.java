package com.Hen3579.Nujomod.Inits;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import static com.Hen3579.Nujomod.Inits.InitItems.*;
import static com.Hen3579.Nujomod.Inits.InitItems.IMAGINATIONAL_CORE;
import static com.Hen3579.Nujomod.Inits.InitItems.MAGIC_PAINTBRUSH;
import static com.Hen3579.Nujomod.NujoBraincraft.MODID;

public class CreativeTabReg {
    public static final DeferredRegister<CreativeModeTab> TABS =DeferredRegister.create(Registries.CREATIVE_MODE_TAB,MODID);
    public static final RegistryObject<CreativeModeTab> NUJO_GROUP_ITEM_TABS = TABS.register("nujo_item_group",() -> CreativeModeTab.builder()
            .title(Component.translatable("item.nujobraincraft.nujo_item_group"))
            .icon(()-> new ItemStack(IMAGINATIONAL_CORE.get()))
            .displayItems((par, output) -> {
                output.accept(IMAGINATIONAL_CORE.get());
                output.accept(NUJO_THINKER_SPAWN_EGG.get());
            }).build());

    public static final RegistryObject<CreativeModeTab> NUJO_GROUP_TOOL_TABS = TABS.register("nujo_tool_group",() -> CreativeModeTab.builder()
            .title(Component.translatable("item.nujobraincraft.nujo_tool_group"))
            .icon(()-> MAGIC_PAINTBRUSH.get().getDefaultInstance())
            .displayItems((par, output) -> {
                output.accept(MAGIC_PAINTBRUSH.get());
                output.accept(SMART_PHONE.get());
            }).build());


}
