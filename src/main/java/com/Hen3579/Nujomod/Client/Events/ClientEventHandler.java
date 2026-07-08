package com.Hen3579.Nujomod.Client.Events;

import com.Hen3579.Nujomod.Client.gui.Menu.Screen.CustomMainMenuScreen;
import com.Hen3579.Nujomod.Client.Utils.CameraAccess;
import com.Hen3579.Nujomod.Network.BirdviewNetwork;
import com.Hen3579.Nujomod.Network.BirdviewStatePacket;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import static com.Hen3579.Nujomod.NujoBraincraft.MODID;

@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class ClientEventHandler {
    public static final String GENERAL = MODID + ".general";
    public static final KeyMapping TOGGLE_FIRST_PERSON = createKeyMapping("toggle_first_person", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping TOGGLE_THIRD_PERSON_FRONT = createKeyMapping("toggle_third_person_front", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping TOGGLE_THIRD_PERSON_BACK = createKeyMapping("toggle_third_person_back", InputConstants.UNKNOWN.getValue());

    /** 每 tick 标记：MouseButton.Pre 是否已消费了左键远程攻击（防止 consumeClick 双重触发） */
    private static boolean rangedAttackConsumedThisTick = false;

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
                // 通知服务器：鸟瞰模式开启（激活飞行生物 Y 轴约束）
                BirdviewNetwork.INSTANCE.sendToServer(new BirdviewStatePacket(true));
            }

            // 退出鸟瞰时：关闭发光描边
            if (prevPerspective == 3 && newPerspective != 3) {
                clearBirdviewGlow(mc);
                // 通知服务器：鸟瞰模式关闭（解除飞行生物 Y 轴约束）
                BirdviewNetwork.INSTANCE.sendToServer(new BirdviewStatePacket(false));
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

        // 鸟瞰模式：Tab 锁定/解锁最近的敌对生物
        if (BirdviewClientEvent.isBirdseyeActive() && options.keyPlayerList.consumeClick()) {
            LockTargetSystem.toggleLock();
        }

        // 鸟瞰模式：消费左键点击 → 攻击悬停的生物（近战/远程自动判断）
        // 使用标志位防止 MouseButton.Pre 和 consumeClick 双重触发
        if (BirdviewClientEvent.isBirdseyeActive() && options.keyAttack.consumeClick()) {
            if (!rangedAttackConsumedThisTick) {
                LockTargetSystem.tryAttackOnHover(mc);
            }
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

        // 近战追杀每刻更新（左键触发后的自动追击）
        LockTargetSystem.chaseTick(mc);

        // 远程攻击每刻更新（弓蓄力释放、冷却递减等）
        LockTargetSystem.rangedTick(mc);

        // 以上方法内部会调用 faceEntity/calculateBallisticAim 设置瞄准 pitch 以便
        // 释放箭/近战攻击时弹道正确。释放/攻击完成后重置 pitch 为 0，
        // 确保鸟瞰模式下玩家模型始终平视（不低头抬头）
        // 但跳过弓刚释放的 tick：正发送 RELEASE_USE_ITEM 包给服务端，
        // 此时 player.getXRot() 必须保持在弹道瞄准角度，服务端才能正确射出箭。
        if (!LockTargetSystem.shouldSkipPitchReset()) {
            mc.player.setXRot(0);
            mc.player.xRotO = 0;
        } else {
            LockTargetSystem.clearSkipPitchReset();
        }

        // 重置每 tick 远程攻击消费标志（为下个 tick 准备）
        rangedAttackConsumedThisTick = false;
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

    /** 右键点击：用正交投影纯数学 screenPosToWorldPos + 垂直射线检测目标位置 */
    private static void handleClickToMove(Minecraft mc) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        Window window = mc.getWindow();

        // 从虚拟光标读取位置
        double[] rayPos = getRaycastCursorPos(mc);
        if (rayPos == null) return;
        double mouseX = rayPos[0];
        double mouseY = rayPos[1];

        // ===== 正交投影纯数学：屏幕坐标 → 世界地面 XZ =====
        double[] worldXZ = BirdviewClientEvent.screenPosToWorldPos(
                mouseX, mouseY, window.getWidth(), window.getHeight(), mc.player.position());
        if (worldXZ == null) return;
        double worldX = worldXZ[0];
        double worldZ = worldXZ[1];

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

    /** 每帧用正交投影纯数学 screenPosToWorldPos + 垂直射线，判断光标指向的方块/生物 */
    private static void updateHoveredBlock(Minecraft mc) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        Window window = mc.getWindow();

        // 直接从 virtCursor 读取（已在 overlay 中更新，是最新位置）
        double[] virt = BirdviewClientEvent.getVirtCursorPos();
        if (virt == null) return;
        double mouseX = virt[0];
        double mouseY = virt[1];

        // ===== 正交投影纯数学：屏幕坐标 → 世界地面 XZ（参考 Reign of Nether） =====
        double[] worldXZ = BirdviewClientEvent.screenPosToWorldPos(
                mouseX, mouseY, window.getWidth(), window.getHeight(), mc.player.position());
        if (worldXZ == null) return;
        double worldX = worldXZ[0];
        double worldZ = worldXZ[1];

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

        // === 屏幕空间拾取：空中/滞空实体检测 ===
        // 对于不在地面附近的飞行生物（如 Phantom、Allay、Ghast），
        // 用投影矩阵做屏幕空间命中判定，支持左键点击空中怪物直接攻击
        {
            Vec3 camPosScreen = BirdviewClientEvent.getCachedCameraPos();
            if (camPosScreen != null) {
                int sw = window.getWidth();
                int sh = window.getHeight();
                double bestScreenDist = 20.0; // 20像素命中半径
                Entity bestAirEntity = null;
                double bestAirDepth = Double.MAX_VALUE;

                // 在相机周围搜索所有活体（排除玩家自己）
                AABB searchBox = new AABB(
                    camPosScreen.x - 48, camPosScreen.y - 48, camPosScreen.z - 48,
                    camPosScreen.x + 48, camPosScreen.y + 48, camPosScreen.z + 48
                );
                for (Entity entity : mc.level.getEntities(mc.player, searchBox,
                        e -> e instanceof LivingEntity && e.isAlive())) {

                    // 投影实体中心（偏上 60% 高度，更好点中）到屏幕
                    Vec3 entityCenter = new Vec3(
                        entity.getX(),
                        entity.getY() + entity.getBbHeight() * 0.6,
                        entity.getZ()
                    );
                    double[] screen = BirdviewClientEvent.worldToScreen(entityCenter, sw, sh);
                    if (screen == null) continue; // 在屏幕外或投影失败

                    double dx = screen[0] - mouseX;
                    double dy = screen[1] - mouseY;
                    double dist = Math.sqrt(dx * dx + dy * dy);

                    if (dist < bestScreenDist) {
                        double depth = entity.distanceToSqr(camPosScreen);
                        // 屏幕距离明显更近(>2px)，或距离接近时选深度更近的
                        if (bestAirEntity == null
                            || dist < bestScreenDist - 2.0
                            || (Math.abs(dist - bestScreenDist) < 2.0 && depth < bestAirDepth)) {
                            bestScreenDist = dist;
                            bestAirDepth = depth;
                            bestAirEntity = entity;
                        }
                    }
                }

                if (bestAirEntity != null) {
                    // 空中实体命中 → 覆盖之前的 hitResult
                    // 无论之前是方块命中还是地面实体命中，空中实体优先
                    bestHit = new EntityHitResult(bestAirEntity, bestAirEntity.position());
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

        // ===== 渲染远程攻击目标指示 =====
        if (LockTargetSystem.isRanging()) {
            LivingEntity rangedTarget = LockTargetSystem.getRangedTarget();
            if (rangedTarget != null && rangedTarget.isAlive()) {
                AABB targetBB = rangedTarget.getBoundingBox();

                double minX = targetBB.minX - camPos.x;
                double minY = targetBB.minY - camPos.y;
                double minZ = targetBB.minZ - camPos.z;
                double maxX = targetBB.maxX - camPos.x;
                double maxY = targetBB.maxY - camPos.y;
                double maxZ = targetBB.maxZ - camPos.z;

                VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(RenderType.LINES);

                // 橙色发光边框（与近战锁定目标的青色区分）
                float expand = 0.15f;
                poseStack.pushPose();
                LevelRenderer.renderLineBox(poseStack, consumer,
                        minX - expand, minY - expand, minZ - expand,
                        maxX + expand, maxY + expand, maxZ + expand,
                        1.0f, 0.6f, 0.0f, 0.9f);
                poseStack.popPose();

                // 头顶十字瞄准标记
                double headY = targetBB.maxY - camPos.y + 0.8;
                double centX = (targetBB.minX + targetBB.maxX) / 2.0 - camPos.x;
                double centZ = (targetBB.minZ + targetBB.maxZ) / 2.0 - camPos.z;
                float crossSize = 0.25f;
                poseStack.pushPose();
                poseStack.translate(centX, headY, centZ);
                // 横线
                LevelRenderer.renderLineBox(poseStack, consumer,
                        -crossSize, -0.015f, -0.015f,
                        crossSize, 0.015f, 0.015f,
                        1.0f, 0.7f, 0.0f, 1.0f);
                // 竖线
                LevelRenderer.renderLineBox(poseStack, consumer,
                        -0.015f, -crossSize, -0.015f,
                        0.015f, crossSize, 0.015f,
                        1.0f, 0.7f, 0.0f, 1.0f);
                poseStack.popPose();

                // 如果正在弓蓄力，绘制弹道预测线（从玩家到目标的弧形轨迹）
                if (LockTargetSystem.getRangedState() == LockTargetSystem.RangedState.BOW_CHARGING) {
                    float pt = mc.getFrameTime();
                    Vec3 playerEye = mc.player.getEyePosition(pt)
                            .subtract(camPos);
                    Vec3 targetEye = rangedTarget.getEyePosition(pt)
                            .subtract(camPos);
                    drawBallisticTrajectory(poseStack, consumer, playerEye, targetEye,
                            LockTargetSystem.getBowChargeProgress());
                }
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
        // 保存 look-at pitch 供 screenPosToWorldPos 精确反算使用
        BirdviewClientEvent.setCameraLookPitch(lookPitch);

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

    /**
     * 鸟瞰模式左键拦截（备用方案）：
     * 直接裸拦截 GLFW 鼠标按钮事件，不受 KeyMapping consumeClick 时序问题影响。
     * 左键按下 → 尝试远程攻击悬停的生物
     */
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!BirdviewClientEvent.isBirdseyeActive()) return;
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_1) return; // 左键

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        // 防止在 GUI 打开时触发
        if (mc.screen != null) return;

        if (LockTargetSystem.tryAttackOnHover(mc)) {
            rangedAttackConsumedThisTick = true;
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

    // ===== 弹道预测线绘制 =====

    /**
     * 绘制一条抛射轨迹预测线（从玩家眼睛到目标的弧形），
     * 显示子弹/箭矢在重力影响下的飞行路径。
     */
    private static void drawBallisticTrajectory(PoseStack poseStack, VertexConsumer consumer,
                                                 Vec3 fromRel, Vec3 toRel, float chargeProgress) {
        // 使用 chargeProgress 估算弹丸速度 [0.25 ~ 1.0] 对应的倍数
        double power = 0.25 + chargeProgress * 0.75; // 0.25→1.0
        double v = 3.0 * power; // 箭的初速
        double g = 0.05; // 重力

        Vec3 diff = toRel.subtract(fromRel);
        double dxz = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        double dy = diff.y;

        if (dxz < 0.5) return;

        // 估算飞行时间
        double time = dxz / (v * 0.8); // 粗略估算（cos(俯仰)≈0.8）

        // 绘制 20 个线段
        int segments = 20;
        double dt = time / segments;

        poseStack.pushPose();
        for (int i = 0; i < segments; i++) {
            double t1 = i * dt;
            double t2 = (i + 1) * dt;

            // 水平位置：匀速
            double x1 = fromRel.x + diff.x * (t1 / time);
            double z1 = fromRel.z + diff.z * (t1 / time);
            double x2 = fromRel.x + diff.x * (t2 / time);
            double z2 = fromRel.z + diff.z * (t2 / time);

            // 垂直位置：抛物线
            double y1 = fromRel.y + dy * (t1 / time) - 0.5 * g * t1 * t1;
            double y2 = fromRel.y + dy * (t2 / time) - 0.5 * g * t2 * t2;

            // 透明度随进度变化：从半透明到完全可见
            float alpha = 0.3f + 0.7f * ((float) i / segments);

            // 颜色：橙色渐变为金色
            float r = 1.0f;
            float gColor = 0.5f + 0.3f * ((float) i / segments);
            float b = 0.0f;

            LevelRenderer.renderLineBox(poseStack, consumer,
                    Math.min(x1, x2) - 0.02f, Math.min(y1, y2) - 0.02f, Math.min(z1, z2) - 0.02f,
                    Math.max(x1, x2) + 0.02f, Math.max(y1, y2) + 0.02f, Math.max(z1, z2) + 0.02f,
                    r, gColor, b, alpha);
        }
        poseStack.popPose();
    }
}