package com.Hen3579.Nujomod.Items.Tools;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class SmartPhone extends Item {
   public SmartPhone(Properties properties) {
        super(new Properties().stacksTo(1));

    }

    // 添加库存监听事件
    @SubscribeEvent
    public static void onItemInventoryTick(PlayerEvent.ItemPickupEvent event) {
        ItemStack stack = event.getStack();
        if (stack.getItem() instanceof SmartPhone) {
            stack.getOrCreateTag().putInt("CustomModelData", 0); // 重置为默认模型
        }
    }



    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!world.isClientSide) {
            stack.getOrCreateTag().putInt("CustomModelData", 1); // 激活手持模型
        }
        return InteractionResultHolder.success(stack);
    }





    // 触发动画方法

}
