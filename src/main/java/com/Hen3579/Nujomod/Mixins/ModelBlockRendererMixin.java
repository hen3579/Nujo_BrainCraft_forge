package com.Hen3579.Nujomod.Mixins;

import com.Hen3579.Nujomod.fogofwar.FogOfWarEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * 战争迷雾顶点亮度修改。
 *
 * 在区块编译（chunk rebuild）阶段拦截 ModelBlockRenderer.putQuadData，
 * 将非明亮区块的顶点亮度乘以迷雾倍率，使远处/未探索区域呈现暗色。
 */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {

    @Unique
    private static final ThreadLocal<Float> nujo$fogBrightness = ThreadLocal.withInitial(() -> 1.0F);

    /**
     * 在 putQuadData 入口捕获 BlockPos → 计算迷雾亮度，存入 ThreadLocal。
     * 必须匹配完整方法签名（15 个参数 + CallbackInfo），Mixin 0.8.5 不允许缩写。
     */
    @Inject(method = "putQuadData", at = @At("HEAD"), require = 0)
    private void nujo$captureFogBrightness(
            BlockAndTintGetter level,
            BlockState state,
            BlockPos pos,
            VertexConsumer consumer,
            PoseStack.Pose pose,
            BakedQuad quad,
            float f0, float f1, float f2, float f3,
            int i0, int i1, int i2, int i3, int i4,
            CallbackInfo ci) {
        nujo$fogBrightness.set(FogOfWarEvents.getPosBrightness(pos.getX(), pos.getY(), pos.getZ()));
    }

    /**
     * 修改 putBulkData 的 brightnesses 数组（args index 2），
     * 将所有顶点亮度乘以迷雾倍率。
     */
    @ModifyArgs(
        method = "putQuadData",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(" +
                     "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;" +
                     "Lnet/minecraft/client/renderer/block/model/BakedQuad;" +
                     "[FFFFF[IIZ)V"
        ),
        require = 0
    )
    private void nujo$darkenFogVertices(Args args) {
        float br = nujo$fogBrightness.get();
        if (br < 1.0F) {
            float[] brightnesses = args.get(2);
            if (brightnesses != null) {
                for (int i = 0; i < brightnesses.length; i++) {
                    brightnesses[i] *= br;
                }
            }
        }
    }
}
