package com.Hen3579.Nujomod.Client.Events;

import com.Hen3579.Nujomod.Client.gui.Menu.Screen.CustomMainMenuScreen;
import com.Hen3579.Nujomod.Client.Utils.CameraAccess;
import com.Hen3579.Nujomod.NujoBraincraft;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
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
import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
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

            // 进入鸟瞰模式时锁定相机朝向 + 开启玩家发光描边
            if (newPerspective == 3 && prevPerspective != 3) {
                BirdviewClientEvent.onEnterBirdseye(mc.player.getYRot());
                setupBirdviewGlow(mc);
            }

            // 退出鸟瞰时：关闭发光描边
            if (prevPerspective == 3 && newPerspective != 3) {
                clearBirdviewGlow(mc);
            }

            // 退出鸟瞰时立即恢复光标为 DISABLED（第一人称状态），
            // 不等 overlay 渲染（防止光标在切换瞬间暴露）
            if (!BirdviewClientEvent.isBirdseyeActive()) {
                Window window = mc.getWindow();
                if (window != null) {
                    GLFW.glfwSetInputMode(window.getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                }
                BirdviewClientEvent.clearVirtCursor();
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

        // 鸟瞰模式：Tab 锁定/解锁最近的敌对生物
        if (BirdviewClientEvent.isBirdseyeActive() && options.keyPlayerList.consumeClick()) {
            LockTargetSystem.toggleLock();
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

        // 弓箭蓄力每刻更新（左键触发后的满弦自动释放）
        LockTargetSystem.tickBowCharge(mc);

        // 近战追杀每刻更新（左键触发后的自动追击）
        LockTargetSystem.chaseTick(mc);

        // 以上两个方法内部会调用 faceEntity/faceEntityForBow 设置瞄准 pitch 以便
        // 释放箭/近战攻击时弹道正确。释放/攻击完成后重置 pitch 为 0，
        // 确保鸟瞰模式下玩家模型始终平视（不低头抬头）
        mc.player.setXRot(0);
        mc.player.xRotO = 0;
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

    // ===== 玩家发光描边（通过 EntityRenderDispatcher 手动渲染触发 outline） =====

    /** 进入鸟瞰时：设置青色 team + 基础 glowing 字段 */
    private static void setupBirdviewGlow(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        // 创建青色 team（outline 颜色来源）
        Scoreboard scoreboard = mc.level.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam("nujo_bv_glow");
        if (team == null) {
            team = scoreboard.addPlayerTeam("nujo_bv_glow");
            team.setColor(ChatFormatting.AQUA);
            team.setNameTagVisibility(Team.Visibility.NEVER);
            team.setDeathMessageVisibility(Team.Visibility.NEVER);
            team.setCollisionRule(Team.CollisionRule.NEVER);
            team.setAllowFriendlyFire(true);
        }
        PlayerTeam currentTeam = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
        if (currentTeam != null && !currentTeam.getName().equals("nujo_bv_glow")) {
            scoreboard.removePlayerFromTeam(mc.player.getScoreboardName(), currentTeam);
        }
        if (!team.getPlayers().contains(mc.player.getScoreboardName())) {
            scoreboard.addPlayerToTeam(mc.player.getScoreboardName(), team);
        }
        mc.player.setGlowingTag(true);
    }

    /** 退出鸟瞰时：关闭发光 + 移除 team */
    private static void clearBirdviewGlow(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        Scoreboard scoreboard = mc.level.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam("nujo_bv_glow");
        if (team != null && team.getPlayers().contains(mc.player.getScoreboardName())) {
            scoreboard.removePlayerFromTeam(mc.player.getScoreboardName(), team);
        }
        mc.player.setGlowingTag(false);
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
            // 添加点击标记痕迹
            OrthoviewClientEvent.addClickMarker(targetPos);
        }
        // 如果没有命中方块（点击天空），不设置目标
    }

    /** 左键单击：自动锁定目标并攻击（近战追杀/弓箭蓄力），持弓时无悬停也自动索敌 */
    private static void handleClickToAttack(Minecraft mc) {
        // 已锁定：直接攻击（近战追杀或弓箭蓄力）
        if (LockTargetSystem.isLocked()) {
            LockTargetSystem.handleLockedAttack(mc);
            return;
        }

        boolean holdingBow = mc.player.getMainHandItem().getItem() instanceof BowItem;

        // 尝试获取攻击目标
        Entity target = null;
        HitResult hoverHit = BirdviewClientEvent.getHoveredHitResult();
        if (hoverHit != null && hoverHit.getType() == HitResult.Type.ENTITY) {
            target = ((EntityHitResult) hoverHit).getEntity();
        }

        // 持弓时：没有悬停生物则自动搜索最近的敌对生物
        if (holdingBow && (target == null || !(target instanceof LivingEntity))) {
            target = LockTargetSystem.findNearestHostile(mc.player);
        }

        if (!(target instanceof LivingEntity living)) {
            return;
        }

        // 自动锁定并攻击（handleLockedAttack 根据武器类型：
        // 弓→startBowAttack 蓄力射击，近战→startMeleeChase 追杀致死）
        LockTargetSystem.lockToTarget(target);
        mc.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                        "§b[锁定] §f已锁定 §e" + target.getDisplayName().getString()
                ), true
        );
        LockTargetSystem.handleLockedAttack(mc);
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

        // 缓存当前渲染矩阵和相机数据（供 GUI overlay 做 3D→屏幕投影）
        BirdviewClientEvent.setCachedMatrices(
                RenderSystem.getProjectionMatrix(),
                RenderSystem.getModelViewMatrix()
        );
        BirdviewClientEvent.cacheCameraData(
                event.getCamera().getPosition(),
                event.getCamera().rotation()
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

        // ===== 渲染右键点击移动痕迹 =====
        for (OrthoviewClientEvent.ClickMarker marker : OrthoviewClientEvent.getActiveMarkers()) {
            float age = marker.getAge(); // [0, 1]
            // 颜色过渡：青(0) → 绿(0.33) → 黄(0.66) → 红(0.9) → 淡出(1)
            float r, g, b, a;
            if (age < 0.33f) {
                float t = age / 0.33f;
                r = 0; g = 1; b = 1 - t; a = 1.0f;
            } else if (age < 0.66f) {
                float t = (age - 0.33f) / 0.33f;
                r = t; g = 1; b = 0; a = 1.0f;
            } else if (age < 0.9f) {
                float t = (age - 0.66f) / 0.24f;
                r = 1; g = 1 - t; b = 0; a = 1.0f;
            } else {
                float t = (age - 0.9f) / 0.1f;
                r = 1; g = 0; b = 0; a = 1.0f - t;
            }

            poseStack.pushPose();
            poseStack.translate(
                    marker.position.x - camPos.x,
                    marker.position.y + 0.05 - camPos.y,
                    marker.position.z - camPos.z
            );

            VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(RenderType.LINES);
            float thick = 0.03f;

            // 环缩小动画：从大缩小到小
            float halfSize = 1.0f - age * 0.9f; // 1.0 → 0.1

            // 内圈
            LevelRenderer.renderLineBox(poseStack, consumer,
                    -halfSize, -thick, -halfSize,
                    halfSize, thick, halfSize,
                    r, g, b, a);

            // 外圈（跟随缩小，略大一点）
            float outer = halfSize * 1.2f;
            float outerThick = thick * (0.5f + 0.5f * (1 - age));
            LevelRenderer.renderLineBox(poseStack, consumer,
                    -outer, -outerThick, -outer,
                    outer, outerThick, outer,
                    r * 0.6f, g * 0.6f, b * 0.6f, a * 0.5f);

            poseStack.popPose();
        }

        // ===== 渲染锁定目标视觉反馈 =====
        if (LockTargetSystem.isLocked()) {
            Entity locked = LockTargetSystem.getLockedTarget();
            if (locked != null) {
                AABB lockedBB = locked.getBoundingBox();
                double lockAgeSec = LockTargetSystem.getLockDurationMs() / 1000.0;
                double pulse = 0.15 * Math.sin(lockAgeSec * Math.PI * 2.0); // 1Hz 脉动

                double minX = lockedBB.minX - camPos.x;
                double minY = lockedBB.minY - camPos.y;
                double minZ = lockedBB.minZ - camPos.z;
                double maxX = lockedBB.maxX - camPos.x;
                double maxY = lockedBB.maxY - camPos.y;
                double maxZ = lockedBB.maxZ - camPos.z;

                VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(RenderType.LINES);

                // 1. 青色发光边框（略放大）
                float expand = 0.1f;
                poseStack.pushPose();
                LevelRenderer.renderLineBox(poseStack, consumer,
                        minX - expand, minY - expand, minZ - expand,
                        maxX + expand, maxY + expand, maxZ + expand,
                        0.0f, 0.9f, 1.0f, 0.8f);
                poseStack.popPose();

                // 2. 地面脉动方环
                double groundY = lockedBB.minY - camPos.y;
                float ringSize = 0.8f + (float) pulse;
                float ringThick = 0.04f;
                poseStack.pushPose();
                poseStack.translate(
                        (lockedBB.minX + lockedBB.maxX) / 2.0 - camPos.x,
                        groundY + 0.05,
                        (lockedBB.minZ + lockedBB.maxZ) / 2.0 - camPos.z
                );
                LevelRenderer.renderLineBox(poseStack, consumer,
                        -ringSize, -ringThick, -ringSize,
                        ringSize, ringThick, ringSize,
                        0.0f, 0.9f, 1.0f, 0.6f);
                // 外圈略大
                LevelRenderer.renderLineBox(poseStack, consumer,
                        -ringSize * 1.25f, -ringThick * 0.6f, -ringSize * 1.25f,
                        ringSize * 1.25f, ringThick * 0.6f, ringSize * 1.25f,
                        0.0f, 0.7f, 0.8f, 0.35f);
                poseStack.popPose();

                // 3. 头顶菱形标记
                double headY = lockedBB.maxY - camPos.y + 0.5;
                double centerX = (lockedBB.minX + lockedBB.maxX) / 2.0 - camPos.x;
                double centerZ = (lockedBB.minZ + lockedBB.maxZ) / 2.0 - camPos.z;
                float diamondSize = 0.2f + (float) pulse * 0.5f;
                poseStack.pushPose();
                poseStack.translate(centerX, headY, centerZ);
                // 环形排列的短线段，形成菱形
                float[][] diamondPoints = {
                        {0, 0, diamondSize}, {0, 0, -diamondSize},
                        {diamondSize, 0, 0}, {-diamondSize, 0, 0},
                        {0, diamondSize, 0}, {0, -diamondSize, 0},
                };
                // 水平菱形
                for (int i = 0; i < 4; i++) {
                    float[] p1 = diamondPoints[i];
                    float[] p2 = diamondPoints[(i + 1) % 4];
                    LevelRenderer.renderLineBox(poseStack, consumer,
                            Math.min(p1[0], p2[0]) - 0.01f, -0.01f, Math.min(p1[2], p2[2]) - 0.01f,
                            Math.max(p1[0], p2[0]) + 0.01f, 0.01f, Math.max(p1[2], p2[2]) + 0.01f,
                            0.0f, 1.0f, 0.8f, 0.9f);
                }
                // 垂直方向标记
                LevelRenderer.renderLineBox(poseStack, consumer,
                        -0.01f, -diamondSize, -0.01f,
                        0.01f, diamondSize, 0.01f,
                        0.0f, 1.0f, 0.8f, 0.9f);
                poseStack.popPose();
            }
        }

        // ===== 玩家脚下脉冲方环和头顶ID（始终显示） =====
        Vec3 pPos = mc.player.position();

        // 脚下青色脉冲方环（RTS 选中样式）
        poseStack.pushPose();
        poseStack.translate(pPos.x - camPos.x, pPos.y - 0.02 - camPos.y, pPos.z - camPos.z);
        VertexConsumer ringConsumer = mc.renderBuffers().bufferSource().getBuffer(RenderType.LINES);
        double pulseRing = 0.1 * Math.sin((System.currentTimeMillis() % 2000) / 1000.0 * Math.PI * 2);
        float ring = 0.7f + (float) pulseRing;
        float ringThick = 0.06f;
        // 内圈（亮青色）
        LevelRenderer.renderLineBox(poseStack, ringConsumer,
                -ring, -ringThick, -ring, ring, ringThick, ring,
                0.0f, 1.0f, 0.85f, 1.0f);
        // 外圈（淡青色，略大）
        LevelRenderer.renderLineBox(poseStack, ringConsumer,
                -ring * 1.3f, -ringThick * 0.5f, -ring * 1.3f,
                ring * 1.3f, ringThick * 0.5f, ring * 1.3f,
                0.0f, 0.7f, 0.6f, 0.6f);
        poseStack.popPose();

        // 2. 头顶高亮ID标签（已改用 GUI Overlay 渲染，见 BirdviewCursorOverlay）
    }

    /**
     * 玩家复活时重置为第一人称（避免死在鸟瞰视角下卡住）
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity().level().isClientSide) {
            BirdviewClientEvent.reset();
            Minecraft mc = Minecraft.getInstance();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            clearBirdviewGlow(mc);
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