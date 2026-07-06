package com.Hen3579.Nujomod.Entities.NujoThinker;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class NujoThinkerEntity extends PathfinderMob implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public NujoThinkerEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.navigation = new GroundPathNavigation(this, level);
        this.lookControl = new LookControl(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.JUMP_STRENGTH, 0.5D) // 新增跳跃能力属性
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 在此处添加动画控制器
        controllers.add(new AnimationController<>(this, "walk_controller", 0, this::handleWalkAnim));
        controllers.add(new AnimationController<>(this, "base_controller", 5, this::handleBaseAnim));
        // 新增独立的眨眼控制器
        controllers.add(new AnimationController<>(this, "blink_controller", 10, this::handleShutEyesAnim));

    }
    private PlayState handleWalkAnim(AnimationState<NujoThinkerEntity> state) {
        // 移动时强制停止其他运动相关动画
        if (isMoving()) {
            return state.setAndContinue(RawAnimation.begin().thenLoop("animation.nujo_thinker.walk"));
        }
        return PlayState.STOP;
    }
    private PlayState handleBaseAnim(AnimationState<NujoThinkerEntity> state) {
        // 仅在静止时播放呼吸动画
        if (!isMoving()) {
            return state.setAndContinue(RawAnimation.begin().thenLoop("animation.nujo_thinker.breath"));
        }
        return PlayState.STOP;
    }
    // 新增眨眼动画控制器方法
// 修改眨眼控制器逻辑
    private PlayState handleShutEyesAnim(AnimationState<NujoThinkerEntity> state) {
        // 静止时且每200tick触发眨眼
        if (!isMoving() && this.tickCount % 200 == 0) {
            return state.setAndContinue(RawAnimation.begin().thenPlay("animation.nujo_thinker.shuteyes"));
        }
        return PlayState.STOP;
    }
    // 需要添加移动状态检测方法（示例）
    private boolean isMoving() {
        return this.getNavigation().isInProgress()
                || (this.xxa != 0 || this.zza != 0);
    }
    @Override
    protected void registerGoals() {
        super.registerGoals();
        // 添加更智能的移动目标
        this.goalSelector.addGoal(1, new RandomStrollGoal(this, 1.5D) {
            /*@Override
            public boolean canUse() {
                return super.canUse() && this.mob.onGround();
            }*/

            @Override
            public void start() {
                // 设置最小移动距离
                Vec3 target = DefaultRandomPos.getPos(this.mob, 5, 3);
                if (target != null) {
                    this.mob.getNavigation().moveTo(target.x, target.y, target.z, this.speedModifier);
                }
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
   /*     if (!this.level().isClientSide && this.tickCount % 100 == 0) {
            // 每5秒（100tick）尝试随机移动
            this.getNavigation().moveTo(
                    this.getX() + (this.random.nextDouble() - 0.5) * 10,
                    this.getY(),
                    this.getZ() + (this.random.nextDouble() - 0.5) * 10,
                    1.0D
            );
        }*/
    }


    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
