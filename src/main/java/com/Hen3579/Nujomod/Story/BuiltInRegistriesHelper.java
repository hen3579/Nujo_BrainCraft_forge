package com.Hen3579.Nujomod.Story;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * 注册表查询辅助工具
 */
public final class BuiltInRegistriesHelper {

    private BuiltInRegistriesHelper() {}

    /**
     * 获取物品的注册 id（如 "nujobraincraft:magic_paintbrush"）
     */
    public static String getItemId(Item item) {
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
        return rl != null ? rl.toString() : null;
    }
}
