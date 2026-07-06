package com.Hen3579.Nujomod.Client.Utils;

import net.minecraft.world.phys.Vec3;

/**
 * Camera 位置设置的访问接口 — 用于在 ViewportEvent 中转型调用
 * 注意：必须放在 Mixins 包之外，否则 Mixin 框架会阻止直接引用。
 */
public interface CameraAccess {
    void nujo$setCameraPosition(Vec3 position);
}