package com.Hen3579.Nujomod.Client.Utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public class FreeCamera {
    public static boolean isActive = false;
    private static Vec3 cameraPosition = Vec3.ZERO;
    private static float cameraYaw = 0;
    private static float cameraPitch = 0;

    public static void toggle() {
        isActive = !isActive;
        Minecraft mc = Minecraft.getInstance();

        if (isActive && mc.player != null) {
            // 保存当前视角位置和旋转
            cameraPosition = mc.player.getEyePosition();
            cameraYaw = mc.player.getYRot();
            cameraPitch = mc.player.getXRot();
        }
    }

    public static void update(LocalPlayer player) {
        if (!isActive || player == null) return;

        // 保持相机在玩家上方一定距离
        double distance = 10.0;
        cameraPosition = player.getPosition(1.0f)
                .add(0, distance * 0.7, 0)
                .subtract(Math.sin(Math.toRadians(cameraYaw)) * distance, 0,
                        Math.cos(Math.toRadians(cameraYaw)) * distance);
    }

    public static Vec3 getCameraPosition() {
        return cameraPosition;
    }

    public static float getCameraYaw() {
        return cameraYaw;
    }

    public static float getCameraPitch() {
        return cameraPitch;
    }

    public static void setCameraRotation(float yaw, float pitch) {
        cameraYaw = yaw;
        cameraPitch = pitch;
    }
}