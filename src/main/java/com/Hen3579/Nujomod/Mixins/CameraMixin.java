package com.Hen3579.Nujomod.Mixins;

import com.Hen3579.Nujomod.Client.Utils.CameraAccess;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera Mixin — 通过 @Shadow 字段暴露私有的 position 设置接口，
 * 并修复 setAnglesInternal 不更新 rotation 四元数的问题。
 * 不使用 @Accessor（会破坏 Camera 类导致纹理透明）。
 * 配合 CameraAccess 接口供编译期转型。
 */
@Mixin(Camera.class)
public abstract class CameraMixin implements CameraAccess {

    @Shadow
    private Vec3 position;

    @Shadow
    private Quaternionf rotation;

    @Shadow
    private Vector3f forwards;

    @Shadow
    private Vector3f up;

    @Shadow
    private Vector3f left;

    @Override
    public void nujo$setCameraPosition(Vec3 pos) {
        this.position = pos;
    }

    /**
     * setAnglesInternal 只更新 xRot/yRot，不更新 rotation 四元数和方向向量。
     * 这导致粒子公告牌 (SingleQuadParticle) 使用 Camera.rotation() 时拿到的是
     * Camera.setup() 阶段的旧朝向（玩家视角），而不是 ComputeCameraAngles 事件
     * 覆盖后的鸟瞰视角。在正交投影下，这个 45° 的 pitch 偏差表现为粒子歪斜。
     *
     * 修复：在 setAnglesInternal 尾部同步更新 rotation/forwards/up/left。
     */
    @Inject(method = "setAnglesInternal", at = @At("TAIL"), remap = false)
    private void nujo$syncRotationAfterSetAngles(float yaw, float pitch, CallbackInfo ci) {
        this.rotation.rotationYXZ(
            -yaw * ((float) Math.PI / 180F),
            pitch * ((float) Math.PI / 180F),
            0.0F
        );
        this.forwards.set(0.0F, 0.0F, 1.0F).rotate(this.rotation);
        this.up.set(0.0F, 1.0F, 0.0F).rotate(this.rotation);
        this.left.set(1.0F, 0.0F, 0.0F).rotate(this.rotation);
    }
}