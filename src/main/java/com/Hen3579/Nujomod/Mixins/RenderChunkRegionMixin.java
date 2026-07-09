package com.Hen3579.Nujomod.Mixins;

import com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent;
import com.Hen3579.Nujomod.Client.Events.SectionViewCuller;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chunk 重建阶段剖视图剔除。
 *
 * <p>在区块编译（RenderChunkRegion 被用于读取方块状态）时，若玩家处于鸟瞰剖视图模式，
 * 将需要隐藏的屋顶和近侧墙壁方块返回为空气。这样这些方块不会生成任何渲染顶点，
 * 从视觉上真正消失，而不是像之前的遮罩方案那样只是变暗。
 *
 * <p>与遮罩方案相比的优势：
 * <ul>
 *   <li>屋顶/墙壁完全不可见，没有“半透明面片”造成的轮廓和斜截面感</li>
 *   <li>不会破坏后续透明/水体渲染的深度状态，避免黑色空洞</li>
 *   <li>从 NW 看向 SE 时，保留的南墙和东墙会自然遮挡天空，不易露天空</li>
 * </ul>
 */
@Mixin(RenderChunkRegion.class)
public class RenderChunkRegionMixin {

    /** 墙壁判定缓冲（格） */
    private static final double WALL_PLANE_BUFFER = 0.75;

    /**
     * 直接引用 RenderChunkRegion 的底层 Level。
     * 通过 level.getBlockState() 读取邻块可绕过 Mixin 递归：
     * 若邻块也是被裁剪目标，self.getBlockState() 会返回 AIR → 误判为"面向室内"。
     */
    @Shadow
    @Final
    private Level level;

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void nujo$hideSectionViewBlocks(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        // 防御性门控：非鸟瞰模式下绝不干预区块编译
        if (!BirdviewClientEvent.isBirdseyeActive()) return;
        if (!SectionViewCuller.isActive() || !SectionViewCuller.shouldCull(pos)) {
            return;
        }

        int dy = pos.getY() - (int) SectionViewCuller.getPlayerY();
        int roofOffset = SectionViewCuller.getDynamicRoofOffset();

        // === 屋顶：天花板及以上全部隐藏 ===
        if (dy >= roofOffset) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
            return;
        }

        // === 近侧墙壁：只隐藏面向房间内部的北墙 / 西墙 ===
        // 鸟瞰相机固定在玩家 NW 侧，看向 SE。因此需要隐藏的是北侧墙和西墙。
        // 判断依据：方块在玩家北侧/西侧，且其朝向室内的一侧（南/东）邻块是空气。
        //
        // 邻块检查使用 level.getBlockState() 绕过 Mixin 递归：
        // 若邻块也是被裁剪墙壁，Mixin 递归会返回 AIR → 误判为"面向室内"。
        //
        // 邻块判定使用 blocksMotion() 而非 isSolidRender()：
        // 此处的目的是判断"墙面是否暴露于室内空间"——如果墙和室内之间有
        // 玻璃、铁栏杆、栅栏门等半透明方块，该墙面并不直接暴露，不应隐藏。
        // blocksMotion() 恰好能过滤掉这些"缝隙填充物"，isSolidRender() 则不能。

        // 北墙：方块在玩家北侧，南侧是室内
        boolean isNorth = pos.getZ() + 0.5 < SectionViewCuller.getPlayerZ() - WALL_PLANE_BUFFER;
        if (isNorth) {
            BlockState south = level.getBlockState(pos.south());
            if (south.isAir() || !south.blocksMotion()) {
                cir.setReturnValue(Blocks.AIR.defaultBlockState());
                return;
            }
        }

        // 西墙：方块在玩家西侧，东侧是室内
        boolean isWest = pos.getX() + 0.5 < SectionViewCuller.getPlayerX() - WALL_PLANE_BUFFER;
        if (isWest) {
            BlockState east = level.getBlockState(pos.east());
            if (east.isAir() || !east.blocksMotion()) {
                cir.setReturnValue(Blocks.AIR.defaultBlockState());
            }
        }
    }
}
