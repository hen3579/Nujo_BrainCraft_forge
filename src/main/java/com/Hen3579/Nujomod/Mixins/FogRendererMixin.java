package com.Hen3579.Nujomod.Mixins;

import com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent;
import com.Hen3579.Nujomod.Client.Events.SectionViewCuller;
import com.Hen3579.Nujomod.NujoBraincraft;
import com.Hen3579.Nujomod.Server.WorldBorderHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FogRenderer Mixin — 鸟瞰双模式雾效。
 *
 * <h2>模式 1：洞穴雾（caveFactor 驱动）</h2>
 * 鸟瞰相机被天花板压低进入洞穴时，覆盖 MC 默认雾为密集短距深色雾，
 * 使玩家只看清所在层，上下层洞穴和地表被雾自然遮蔽。
 *
 * <h2>模式 2：剖视图房间雾（SectionViewCuller 驱动）</h2>
 * 玩家进入封闭建筑/房间时，设置柔和中距雾（start=20, end=45），
 * 雾色为偏亮的棕灰，柔化房间外部边界，避免远处变成纯黑块。
 * 由于屋顶和近侧墙已在 Chunk 重建阶段被真正剔除，雾不再需要“隐藏屋顶”，
 * 只负责营造室内氛围与远景过渡。
 *
 * <p>优先级：剖视图雾 > 洞穴雾 > MC 默认雾。
 */
@Mixin(FogRenderer.class)
public class FogRendererMixin {

    // ===== 洞穴雾参数 =====
    private static final float CAVE_FOG_START = 8.0f;
    private static final float CAVE_FOG_END = 18.0f;
    private static final float CAVE_FOG_R = 0.04f;
    private static final float CAVE_FOG_G = 0.04f;
    private static final float CAVE_FOG_B = 0.06f;

    // ===== 剖视图房间雾参数 =====
    /** 房间雾起点：距相机 20 格内完全清晰，保证房间内部无雾 */
    private static final float ROOM_FOG_START = 20.0f;
    /** 房间雾终点：45 格外逐渐遮蔽，柔化房间外部边界，避免大黑块 */
    private static final float ROOM_FOG_END = 45.0f;
    /** 房间雾色（中棕灰，比遮罩更亮，避免远处变成纯黑） */
    private static final float ROOM_FOG_R = 0.14f;
    private static final float ROOM_FOG_G = 0.12f;
    private static final float ROOM_FOG_B = 0.10f;

    // ===== WorldBorder 边界雾参数 =====
    /** 边界雾过渡带宽度（格）：在此距离内雾逐渐变浓 */
    private static final float BORDER_FADE_DISTANCE = 30.0f;
    /** 边界雾最浓时的起点（格）：相机距边界 0 格时的雾起始距离 */
    private static final float BORDER_FOG_START_MAX = 5.0f;
    /** 边界雾最浓时的终点（格）：相机距边界 0 格时的雾终止距离 */
    private static final float BORDER_FOG_END_MAX = 20.0f;
    /** 边界雾色（极暗蓝灰，模拟"世界尽头黑暗"） */
    private static final float BORDER_FOG_R = 0.03f;
    private static final float BORDER_FOG_G = 0.02f;
    private static final float BORDER_FOG_B = 0.05f;

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void nujo$overrideCaveFog(
            Camera camera, FogRenderer.FogMode fogMode,
            float farPlaneDistance, boolean thickFog, float partialTick,
            CallbackInfo ci) {

        if (!BirdviewClientEvent.isBirdseyeActive()) return;

        // === 优先级 1：WorldBorder 边界雾 ===
        // 在 brain_world 维度中，当相机靠近/超出 WorldBorder 时，
        // 设置密集暗色雾，遮蔽边界外的地下虚空和矿洞。
        // 这是最重要的边界视觉处理：硬 cutoff 由 RenderChunkRegionMixin 处理，
        // 柔和过渡由这里处理。
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.dimension() == WorldBorderHandler.getBrainWorldKey()) {
            float borderFactor = getBorderFogFactor(camera);
            if (borderFactor > 0.01f) {
                float fogStart = Mth.lerp(borderFactor, 9999.0f, BORDER_FOG_START_MAX);
                float fogEnd = Mth.lerp(borderFactor, 9999.0f, BORDER_FOG_END_MAX);
                float r = BORDER_FOG_R * borderFactor;
                float g = BORDER_FOG_G * borderFactor;
                float b = BORDER_FOG_B * borderFactor;

                RenderSystem.setShaderFogStart(fogStart);
                RenderSystem.setShaderFogEnd(fogEnd);
                RenderSystem.setShaderFogColor(r, g, b, 1.0f);
                return; // 边界雾优先级最高，覆盖其他雾效
            }
        }

        // === 优先级 2：剖视图房间雾 ===
        if (SectionViewCuller.isActive()) {
            RenderSystem.setShaderFogStart(ROOM_FOG_START);
            RenderSystem.setShaderFogEnd(ROOM_FOG_END);
            RenderSystem.setShaderFogColor(ROOM_FOG_R, ROOM_FOG_G, ROOM_FOG_B, 1.0f);
            return;
        }

        // === 优先级 2：洞穴雾 ===
        // 防御：若玩家头顶可见天空，说明在露天环境（如山坡旁），不触发洞穴雾
        // caveFactor 由相机高度压低程度计算，但压低原因可能是侧面山坡而非天花板，
        // 此时玩家能看到天空 → 不是洞穴 → 跳过雾效。
        if (camera.getEntity() != null
                && camera.getEntity().level().canSeeSky(camera.getEntity().blockPosition())) {
            return;
        }

        float caveFactor = BirdviewClientEvent.getCaveFactor();
        if (caveFactor <= 0.01f) return;

        float fogStart = Mth.lerp(caveFactor, 9999.0f, CAVE_FOG_START);
        float fogEnd = Mth.lerp(caveFactor, 9999.0f, CAVE_FOG_END);
        float r = CAVE_FOG_R * caveFactor;
        float g = CAVE_FOG_G * caveFactor;
        float b = CAVE_FOG_B * caveFactor;

        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
        RenderSystem.setShaderFogColor(r, g, b, 1.0f);
    }

    /**
     * 计算 WorldBorder 边界雾因子 [0, 1+]。
     *
     * <p>基于相机位置到最近 WorldBorder 边缘的距离：
     * <ul>
     *   <li>距离 ≥ BORDER_FADE_DISTANCE (30格)：factor = 0，无雾</li>
     *   <li>距离 = 0（在边界上）：factor = 1，完全浓雾</li>
     *   <li>距离 < 0（在边界外）：factor > 1，极浓雾（几乎全黑）</li>
     * </ul>
     *
     * <p>factor 被 clamp 到 [0, 1.5]，防止极端值导致渲染异常。
     */
    private static float getBorderFogFactor(Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0.0f;

        WorldBorder wb = mc.level.getWorldBorder();
        Vec3 camPos = camera.getPosition();

        // 计算相机到最近边界边缘的距离
        double minX = wb.getMinX();
        double maxX = wb.getMaxX();
        double minZ = wb.getMinZ();
        double maxZ = wb.getMaxZ();

        double distToNearestEdge = Math.min(
                Math.min(camPos.x - minX, maxX - camPos.x),
                Math.min(camPos.z - minZ, maxZ - camPos.z)
        );

        // 正值 = 在边界内，负值 = 在边界外
        float factor = (float) (1.0 - distToNearestEdge / BORDER_FADE_DISTANCE);
        return Mth.clamp(factor, 0.0f, 1.5f);
    }

}
