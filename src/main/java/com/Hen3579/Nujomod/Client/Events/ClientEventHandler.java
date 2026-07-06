package com.Hen3579.Nujomod.Client.Events;

import com.Hen3579.Nujomod.Client.gui.Menu.Screen.CustomMainMenuScreen;
import com.Hen3579.Nujomod.Client.Utils.CameraAccess;
import com.Hen3579.Nujomod.NujoBraincraft;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

import static com.Hen3579.Nujomod.NujoBraincraft.MODID;

@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class ClientEventHandler {
    public static final String GENERAL = MODID + ".general";
    public static final KeyMapping TOGGLE_FIRST_PERSON = createKeyMapping("toggle_first_person", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping TOGGLE_THIRD_PERSON_FRONT = createKeyMapping("toggle_third_person_front", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping TOGGLE_THIRD_PERSON_BACK = createKeyMapping("toggle_third_person_back", InputConstants.UNKNOWN.getValue());

    /**
     * 客户端刻事件：
     * - Phase.START：拦截 F5 切换视角、右键点击移动
     * - Phase.END：相机对齐 WASD 移动、点击移动控制、更新悬停方块
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (event.phase == TickEvent.Phase.START) {
            handleStartPhase(mc);
        } else if (event.phase == TickEvent.Phase.END) {
            handleEndPhase(mc);
        }
    }

    /** Phase.START：视角切换、右键点击移动、设置相机对齐 yaw */
    private static void handleStartPhase(Minecraft mc) {
        Options options = mc.options;

        // 拦截 F5 切换视角
        while (options.keyTogglePerspective.consumeClick()) {
            int prevPerspective = BirdviewClientEvent.getCurrentPerspective();
            BirdviewClientEvent.cyclePerspective();
            int newPerspective = BirdviewClientEvent.getCurrentPerspective();

            // 进入鸟瞰模式时锁定相机朝向
            if (newPerspective == 3 && prevPerspective != 3) {
                BirdviewClientEvent.onEnterBirdseye(mc.player.getYRot());
            }

            CameraType cameraType = BirdviewClientEvent.getCameraType();
            options.setCameraType(cameraType);
            mc.player.displayClientMessage(
                    Component.literal("§7[视角] §f" + BirdviewClientEvent.getPerspectiveName()),
                    true
            );
        }

        // 鸟瞰模式下：消费右键点击移动（阻止原版放置方块）
        if (BirdviewClientEvent.isBirdseyeActive() && options.keyUse.consumeClick()) {
            handleClickToMove(mc);
        }

        // 鸟瞰模式下：左键单击生物 → 近身攻击
        if (BirdviewClientEvent.isBirdseyeActive() && options.keyAttack.consumeClick()) {
            handleClickToAttack(mc);
        }

        // 鸟瞰模式：设置 yaw 对齐 + 平视
        if (BirdviewClientEvent.isBirdseyeActive()) {
            applyCameraAlignedInput(mc);
        }
    }

    /** Phase.END：更新悬停方块、相机对齐 WASD、点击移动控制 */
    private static void handleEndPhase(Minecraft mc) {
        if (!BirdviewClientEvent.isBirdseyeActive()) {
            return;
        }

        // 更新悬停方块/生物（virtCursor 已由 overlay 在 60 FPS 下实时更新）
        updateHoveredBlock(mc);

        // 检查 WASD 是否按下（用于清除点击目标和自动跳跃）
        boolean wasdPressed = mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();

        // 按了 WASD 就取消点击移动目标
        if (wasdPressed) {
            BirdviewClientEvent.clearMoveTarget();
        }

        // 有移动目标时执行点击移动
        if (BirdviewClientEvent.hasMoveTarget()) {
            handleMoveToTarget(mc);
        }

        // 自动跳跃：WASD 按下时，在地面被阻挡（水平速度极小）则自动跳
        if (wasdPressed && mc.player.onGround()
                && mc.player.getDeltaMovement().horizontalDistanceSqr() < 0.004) {
            mc.player.jumpFromGround();
        }

        // 再次设置 yaw，确保渲染使用正确的朝向
        applyCameraAlignedInput(mc);
    }

    // ===== 相机对齐输入 =====

    /**
     * LOL 风格：
     * - 按 WASD 时：锁定 yaw 到 cameraLookYaw（屏幕上方方向），实现屏幕对齐移动
     * - 不按 WASD 时：不碰 yaw，鼠标通过 turnPlayer() 自由控制玩家转身（用于瞄准生物）
     * - 始终平视（防止玩家抬头低头）
     */
    private static void applyCameraAlignedInput(Minecraft mc) {
        boolean wasdPressed = mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();

        if (wasdPressed) {
            // cameraLookYaw = 相机看向玩家的方向 = 屏幕中心方向
            // 按 W 时玩家朝这个方向走 = 屏幕上方（远离相机）
            float lookYaw = BirdviewClientEvent.getCameraLookYaw();
            mc.player.setYRot(lookYaw);
            mc.player.yBodyRot = lookYaw;
            mc.player.yHeadRot = lookYaw;
        }

        // 始终平视
        mc.player.setXRot(0);

        // 冲刺
        mc.player.setSprinting(mc.options.keySprint.isDown() && wasdPressed);
    }

    // ===== 射线检测位置（overlay 60 FPS 更新 virtCursor，直接读取即可） =====

    /**
     * 读取当前 virtCursor 位置用于射线检测。
     * virtCursor 已由 overlay 在每个渲染帧（60 FPS）中通过自跟踪 delta 更新，
     * 无需额外叠加 raw delta。
     */
    private static double[] getRaycastCursorPos(Minecraft mc) {
        double[] virt = BirdviewClientEvent.getVirtCursorPos();
        if (virt == null) return null;
        return new double[]{virt[0], virt[1]};
    }

    // ===== 点击移动 =====

    /** 右键点击：用鼠标偏移+垂直下射线检测目标位置 */
    private static void handleClickToMove(Minecraft mc) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        Window window = mc.getWindow();

        // 从临时光标位置读取（virtCursor + raw delta）
        double[] rayPos = getRaycastCursorPos(mc);
        if (rayPos == null) return;
        double mouseX = rayPos[0];
        double mouseY = rayPos[1];

        // 屏幕中心点（物理像素坐标）
        double centerX = window.getWidth() / 2.0;
        double centerY = window.getHeight() / 2.0;

        // 归一化鼠标偏移 [-1, 1]，用 half-height 做归一化以保持宽高比
        double normDX = (mouseX - centerX) / centerY;
        double normDY = -(mouseY - centerY) / centerY;  // 屏幕 Y 轴方向反转

        // 相机朝向的水平分量（鸟瞰模式下由 look-at 固定）
        float yaw = camera.getYRot();
        double yawRad = Math.toRadians(yaw);

        // 相机水平 forward / right 向量
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad));
        Vec3 right   = new Vec3(-Math.cos(yawRad), 0, -Math.sin(yawRad));

        // 鸟瞰相机到玩家的水平距离（用于计算 worldScale）
        double horizontalOffset = BirdviewClientEvent.BIRDSEYE_HEIGHT
                / Math.tan(Math.toRadians(BirdviewClientEvent.BIRDSEYE_PITCH));

        // 根据 FOV 将屏幕偏移映射为世界偏移，以玩家位置为原点
        float fov = mc.options.fov().get().floatValue();
        double fovScale = Math.tan(Math.toRadians(fov / 2.0));
        double worldScale = horizontalOffset * fovScale;

        // 鼠标偏移对应的世界坐标（以玩家位置为原点，确保屏幕中心对准玩家）
        Vec3 playerPos = mc.player.position();
        double worldX = playerPos.x + (forward.x * normDY + right.x * normDX) * worldScale;
        double worldZ = playerPos.z + (forward.z * normDY + right.z * normDX) * worldScale;

        // === 垂直向下射线检测方块 ===
        Vec3 from = new Vec3(worldX, camPos.y + 5, worldZ);
        Vec3 to   = new Vec3(worldX, mc.level.getMinBuildHeight(), worldZ);

        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player);
        BlockHitResult hit = mc.level.clip(ctx);

        if (hit.getType() == HitResult.Type.BLOCK) {
            // 点击到方块 → 移动到方块表面（沿点击面水平偏移一格）
            Vec3 hitPos = hit.getLocation();
            Vec3 targetPos = hitPos.add(hit.getDirection().getStepX() * 0.5, 0, hit.getDirection().getStepZ() * 0.5);
            BirdviewClientEvent.setMoveTarget(targetPos);
        }
        // 如果没有命中方块（点击天空），不设置目标
    }

    /** 左键单击：近身攻击悬停的生物 */
    private static void handleClickToAttack(Minecraft mc) {
        HitResult hoverHit = BirdviewClientEvent.getHoveredHitResult();
        if (hoverHit == null || hoverHit.getType() != HitResult.Type.ENTITY) {
            return;
        }

        Entity target = ((net.minecraft.world.phys.EntityHitResult) hoverHit).getEntity();
        // 只对 LivingEntity 攻击
        if (!(target instanceof net.minecraft.world.entity.LivingEntity living)) {
            return;
        }

        // 让玩家面向目标
        Vec3 toTarget = living.position().subtract(mc.player.position());
        float targetYaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        mc.player.setYRot(targetYaw);
        mc.player.yRotO = targetYaw;
        mc.player.setYHeadRot(targetYaw);

        // 触发玩家攻击动作（minecraft.player.attack(entity)）
        mc.gameMode.attack(mc.player, living);

        // 播放挥动手和挥击音效
        mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    /** 每帧向移动目标移动 */
    private static void handleMoveToTarget(Minecraft mc) {
        Vec3 target = BirdviewClientEvent.getMoveTarget();
        if (target == null) return;

        Vec3 toTarget = target.subtract(mc.player.position());
        double dist = toTarget.horizontalDistance();

        if (dist < 0.5) {
            // 到达目标
            BirdviewClientEvent.clearMoveTarget();
            return;
        }

        // 计算方向
        Vec3 dir = new Vec3(toTarget.x, 0, toTarget.z).normalize();
        float moveYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));

        // 地上：覆盖水平速度 + 自动跳跃
        if (mc.player.onGround()) {
            float speed = mc.player.getSpeed() * getTerminalVelocityMultiplier(mc);
            if (mc.options.keySprint.isDown()) speed *= 1.3f;

            mc.player.setDeltaMovement(
                    dir.x * speed,
                    mc.player.getDeltaMovement().y,
                    dir.z * speed
            );

            // 自动跳跃：检测到碰撞导致速度降低时跳
            double hSpeed = mc.player.getDeltaMovement().horizontalDistance();
            if (hSpeed < speed * 0.3) {
                // 严重减速 → 前方有障碍 → 自动跳跃
                mc.player.jumpFromGround();
            }

            // 连续被卡住超过半秒 → 向侧面跳一次（尝试绕过障碍）
            // (通过观察是否在相同位置停留过久来实现)
        }

        // 无论空中还是地上：面向移动方向，平视不低头
        mc.player.setYRot(moveYaw);
        mc.player.yBodyRot = moveYaw;
        mc.player.yHeadRot = moveYaw;
        mc.player.setXRot(0);
    }

    // ===== 悬停方块更新 =====

    /** 每帧从相机位置垂直向下射线 + 鼠标屏幕偏移，判断光标指向的方块/生物 */
    private static void updateHoveredBlock(Minecraft mc) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        Window window = mc.getWindow();

        // 直接从 virtCursor 读取（已在 handleEndPhase 中更新，是最新位置）
        double[] virt = BirdviewClientEvent.getVirtCursorPos();
        if (virt == null) return;
        double mouseX = virt[0];
        double mouseY = virt[1];

        // 屏幕中心点（物理像素坐标）
        double centerX = window.getWidth() / 2.0;
        double centerY = window.getHeight() / 2.0;

        // 归一化鼠标偏移 [-1, 1]，用 half-height 做归一化以保持宽高比
        double normDX = (mouseX - centerX) / centerY;
        double normDY = -(mouseY - centerY) / centerY;  // 屏幕 Y 轴方向反转

        // 相机朝向的水平分量（鸟瞰模式下由 look-at 固定）
        float yaw = camera.getYRot();
        double yawRad = Math.toRadians(yaw);

        // 相机水平 forward / right 向量
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad));
        Vec3 right   = new Vec3(-Math.cos(yawRad), 0, -Math.sin(yawRad));

        // 鸟瞰相机到玩家的水平距离（用于计算 worldScale）
        double horizontalOffset = BirdviewClientEvent.BIRDSEYE_HEIGHT
                / Math.tan(Math.toRadians(BirdviewClientEvent.BIRDSEYE_PITCH));

        // 根据 FOV 将屏幕偏移映射为世界偏移，以玩家位置为原点
        float fov = mc.options.fov().get().floatValue();
        double fovScale = Math.tan(Math.toRadians(fov / 2.0));
        double worldScale = horizontalOffset * fovScale;

        // 鼠标偏移对应的世界坐标（以玩家位置为原点，确保屏幕中心对准玩家）
        Vec3 playerPos = mc.player.position();
        double worldX = playerPos.x + (forward.x * normDY + right.x * normDX) * worldScale;
        double worldZ = playerPos.z + (forward.z * normDY + right.z * normDX) * worldScale;

        // === 垂直向下射线检测方块 ===
        Vec3 from = new Vec3(worldX, camPos.y + 5, worldZ);
        Vec3 to   = new Vec3(worldX, mc.level.getMinBuildHeight(), worldZ);

        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player);
        BlockHitResult blockHit = mc.level.clip(ctx);

        HitResult bestHit = null;
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            bestHit = blockHit;
            BirdviewClientEvent.setHoveredBlockPos(blockHit.getBlockPos());
        } else {
            BirdviewClientEvent.setHoveredBlockPos(null);
        }

        // === 实体检测：在垂直射线命中点附近查找 ===
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            Vec3 hitPos = blockHit.getLocation();
            AABB entityBox = new AABB(
                    hitPos.x - 1.5, hitPos.y - 1.0, hitPos.z - 1.5,
                    hitPos.x + 1.5, hitPos.y + 4.0, hitPos.z + 1.5
            );
            for (Entity entity : mc.level.getEntities(mc.player, entityBox, Entity::isAlive)) {
                AABB bb = entity.getBoundingBox().inflate(0.5);
                if (bb.contains(hitPos)) {
                    // 实体包围盒包含了光标命中点 → 悬停在该实体上
                    bestHit = new EntityHitResult(entity, hitPos);
                    break;
                }
            }
        }

        BirdviewClientEvent.setHoveredHitResult(bestHit);
    }

    // ===== 方块高亮渲染 =====

    /** 渲染悬停方块的亮色边框 */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (!BirdviewClientEvent.isBirdseyeActive()) return;

        // 缓存当前渲染矩阵（供 overlay 做 3D→屏幕投影）
        BirdviewClientEvent.setCachedMatrices(
                RenderSystem.getProjectionMatrix(),
                RenderSystem.getModelViewMatrix()
        );

        Minecraft mc = Minecraft.getInstance();
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();

        // 渲染方块高亮
        BlockPos pos = BirdviewClientEvent.getHoveredBlockPos();
        if (pos != null) {
            poseStack.pushPose();
            poseStack.translate(pos.getX() - camPos.x, pos.getY() - camPos.y, pos.getZ() - camPos.z);

            VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(RenderType.LINES);
            LevelRenderer.renderLineBox(
                    poseStack, consumer,
                    Shapes.block().bounds(),
                    1.0f, 0.85f, 0.0f, 1.0f
            );

            poseStack.popPose();
        }

        // 渲染生物轮廓高亮
        HitResult hoverHit = BirdviewClientEvent.getHoveredHitResult();
        if (hoverHit != null && hoverHit.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((net.minecraft.world.phys.EntityHitResult) hoverHit).getEntity();
            AABB entityBB = entity.getBoundingBox();

            // 转换到相对相机坐标
            double minX = entityBB.minX - camPos.x;
            double minY = entityBB.minY - camPos.y;
            double minZ = entityBB.minZ - camPos.z;
            double maxX = entityBB.maxX - camPos.x;
            double maxY = entityBB.maxY - camPos.y;
            double maxZ = entityBB.maxZ - camPos.z;

            poseStack.pushPose();
            VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(RenderType.LINES);

            // 红色高亮（LOL 风格敌人指示）
            float r = 1.0f, g = 0.2f, b = 0.2f;
            LevelRenderer.renderLineBox(
                    poseStack, consumer,
                    minX, minY, minZ, maxX, maxY, maxZ,
                    r, g, b, 1.0f
            );

            poseStack.popPose();
        }

        // ===== 渲染 3D 光标：在命中点绘制十字准星，真正"贴在地面" =====
        Vec3 cursorWorldPos = null;
        if (hoverHit != null && hoverHit.getType() != HitResult.Type.MISS) {
            cursorWorldPos = hoverHit.getLocation();
        } else if (mc.player != null) {
            // 无命中时使用玩家所在位置的地面
            cursorWorldPos = mc.player.position().add(0, -0.5, 0);
        }
        if (cursorWorldPos != null) {
            poseStack.pushPose();
            poseStack.translate(
                    cursorWorldPos.x - camPos.x,
                    cursorWorldPos.y - camPos.y,
                    cursorWorldPos.z - camPos.z
            );

            VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(RenderType.LINES);
            float len = 0.18f;
            float thick = 0.02f;

            // X 轴方向臂（金色 wireframe 小方块)
            LevelRenderer.renderLineBox(poseStack, consumer,
                    -len, -thick, -thick, len, thick, thick,
                    1.0f, 0.85f, 0.0f, 1.0f);
            // Z 轴方向臂
            LevelRenderer.renderLineBox(poseStack, consumer,
                    -thick, -thick, -len, thick, thick, len,
                    1.0f, 0.85f, 0.0f, 1.0f);
            // 中心高亮点
            LevelRenderer.renderLineBox(poseStack, consumer,
                    -thick * 2, -thick * 2, -thick * 2,
                    thick * 2, thick * 2, thick * 2,
                    1.0f, 1.0f, 1.0f, 1.0f);

            poseStack.popPose();
        }
    }

    // ===== 玩家复活 =====

    /**
     * 玩家复活时重置为第一人称（避免死在鸟瞰视角下卡住）
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity().level().isClientSide) {
            BirdviewClientEvent.reset();
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    // ===== 相机角度 =====

    /**
     * 视口事件：相机角度计算完成后，若处于鸟瞰模式则覆盖相机位置和旋转
     * 使用 look-at 方式让相机始终对准玩家，确保玩家在画面正中央
     */
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!BirdviewClientEvent.isBirdseyeActive()) return;

        Camera camera = event.getCamera();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double height = BirdviewClientEvent.BIRDSEYE_HEIGHT;

        // 水平偏移量 = 高度 / tan(参考俯仰角)
        double horizontalOffset = height / Math.tan(Math.toRadians(BirdviewClientEvent.BIRDSEYE_PITCH));
        // 使用进入鸟瞰时锁定的固定世界 yaw（鼠标不再改变相机朝向）
        double fixedYaw = BirdviewClientEvent.getFixedYaw();
        double yawRad = Math.toRadians(fixedYaw);

        // 相机位置：玩家斜后方固定高度（固定世界方向，不随玩家旋转）
        Vec3 cameraPos = new Vec3(
                mc.player.getX() - Math.sin(yawRad) * horizontalOffset,
                mc.player.getY() + height,
                mc.player.getZ() + Math.cos(yawRad) * horizontalOffset
        );

        ((CameraAccess) camera).nujo$setCameraPosition(cameraPos);

        // 计算从相机指向玩家的方向向量 → 自动推导 pitch/yaw（look-at 方式）
        Vec3 target = mc.player.position().add(0, mc.player.getEyeHeight() * 0.5, 0);
        double dx = target.x - cameraPos.x;
        double dy = target.y - cameraPos.y;
        double dz = target.z - cameraPos.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float lookYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float lookPitch = (float) Math.toDegrees(-Math.atan2(dy, horizontalDist));

        // 保存 look-at yaw 供相机对齐 WASD 使用
        BirdviewClientEvent.setCameraLookYaw(lookYaw);

        event.setPitch(lookPitch);
        event.setYaw(lookYaw);
    }

    // ===== 主菜单 =====

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
            event.setNewScreen(new CustomMainMenuScreen(
                    Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                    Minecraft.getInstance().getWindow().getGuiScaledHeight()
            ));
        }
    }

    // ===== 鸟瞰缩放 =====

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (BirdviewClientEvent.isBirdseyeActive()) {
            OrthoviewClientEvent.adjustZoom(event.getScrollDelta());
            event.setCanceled(true);
        }
    }

    private static @NotNull KeyMapping createKeyMapping(String key, int keyCode) {
        return new KeyMapping("key." + MODID + "." + key, keyCode, GENERAL);
    }

    /**
     * 计算终端速度系数：
     * 原版 getSpeed() 是加速度值，通过 moveRelative() 每帧累加再经过摩擦力衰减达到终端速度。
     * 直接 setDeltaMovement() 时需要乘以此系数以匹配原版步行速度。
     * 系数 = 1 / (1 - 摩擦力)
     */
    private static float getTerminalVelocityMultiplier(Minecraft mc) {
        float friction = 0.91F; // 默认空气摩擦
        if (mc.player.onGround()) {
            BlockPos groundPos = mc.player.blockPosition().below();
            float blockFriction = mc.level.getBlockState(groundPos).getBlock().getFriction();
            friction = blockFriction * 0.91F;
        }
        return 1.0f / (1.0f - friction);
    }
}