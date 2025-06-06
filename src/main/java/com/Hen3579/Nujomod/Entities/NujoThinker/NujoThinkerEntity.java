package com.Hen3579.Nujomod.Entities.NujoThinker;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class NujoThinkerEntity extends Mob implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public NujoThinkerEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 在此处添加动画控制器
        controllers.add(new AnimationController<>(this, "walk_controller", 10, this::handleWalkAnim));
        controllers.add(new AnimationController<>(this, "base_controller", 10, this::handleBaseAnim));
        // 新增独立的眨眼控制器
        controllers.add(new AnimationController<>(this, "blink_controller", 10, this::handleShutEyesAnim));

    }
    private PlayState handleWalkAnim(AnimationState<NujoThinkerEntity> state) {
        if (isMoving()) { // 需要实现移动状态检测
            return state.setAndContinue(RawAnimation.begin().thenPlay("animation.nujo_thinker.walk"));
        }
        return PlayState.STOP;
    }
    private PlayState handleBaseAnim(AnimationState<NujoThinkerEntity> state) {
        // 组合基础动画（呼吸+眨眼）
        return state.setAndContinue(
                RawAnimation.begin()
                        .thenLoop("animation.nujo_thinker.breath")
                        .thenLoop("animation.nujo_thinker.shuteyes")
        );
    }
    // 新增眨眼动画控制器方法
    private PlayState handleShutEyesAnim(AnimationState<NujoThinkerEntity> state) {
        // 每10秒（200 tick）播放一次眨眼动画
        if (this.tickCount % 200 == 0) {
            return state.setAndContinue(RawAnimation.begin().thenPlay("animation.nujo_thinker.shuteyes"));
        }
        return PlayState.STOP;
    }
    // 需要添加移动状态检测方法（示例）
    private boolean isMoving() {
        return this.zza > 0 || this.xxa > 0; // 根据实际移动参数调整
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
