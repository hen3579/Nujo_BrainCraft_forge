package com.Hen3579.Nujomod.Mixins;

import com.Hen3579.Nujomod.Client.Utils.CameraAccess;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Camera Mixin — 通过 @Shadow 字段暴露私有的 position 设置接口
 * 不使用 @Accessor（会破坏 Camera 类导致纹理透明）。
 * 配合 CameraAccess 接口供编译期转型。
 */
@Mixin(Camera.class)
public abstract class CameraMixin implements CameraAccess {

    @Shadow
    private Vec3 position;

    @Override
    public void nujo$setCameraPosition(Vec3 pos) {
        this.position = pos;
    }
}