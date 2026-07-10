package com.Hen3579.Nujomod.Client.Events;

import com.Hen3579.Nujomod.Client.gui.Menu.Screen.CustomMainMenuScreen;
import com.Hen3579.Nujomod.Client.Utils.CameraAccess;
import com.Hen3579.Nujomod.Network.BirdviewNetwork;
import com.Hen3579.Nujomod.Network.BirdviewStatePacket;
import com.Hen3579.Nujomod.NujoBraincraft;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
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
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
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
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.Hen3579.Nujomod.NujoBraincraft.MODID;

@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class ClientEventHandler {
    public static final String GENERAL = MODID + ".general";
    public static final KeyMapping TOGGLE_FIRST_PERSON = createKeyMapping("toggle_first_person", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping TOGGLE_THIRD_PERSON_FRONT = createKeyMapping("toggle_third_person_front", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping TOGGLE_THIRD_PERSON_BACK = createKeyMapping("toggle_third_person_back", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping TOGGLE_SECTION_VIEW = createKeyMapping("toggle_section_view", GLFW.GLFW_KEY_PERIOD);
    public static final KeyMapping CAMERA_ROTATE_LEFT  = createKeyMapping("camera_rotate_left",  GLFW.GLFW_KEY_LEFT_BRACKET);
    public static final KeyMapping CAMERA_ROTATE_RIGHT = createKeyMapping("camera_rotate_right", GLFW.GLFW_KEY_RIGHT_BRACKET);

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
                BirdviewClientEvent.resetOcclusionHeight();

                // 如果剖视图曾激活，标记附近区块需要重编译以恢复被剔除的方块
                if (SectionViewCuller.isActive()) {
                    int px = mc.player.blockPosition().getX();
                    int pz = mc.player.blockPosition().getZ();
                    int range = 48;
                    mc.levelRenderer.setBlocksDirty(
                        px - range, mc.level.getMinBuildHeight(), pz - range,
                        px + range, mc.level.getMaxBuildHeight(), pz + range
                    );
                }

                SectionViewCuller.reset();
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

        // 鸟瞰模式下：右键 → 解除Tab锁敌 + 点击移动（二合一）
        if (BirdviewClientEvent.isBirdseyeActive() && options.keyUse.consumeClick()) {
            if (LockTargetSystem.isLocked()) {
                LockTargetSystem.unlockTarget();
            }
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

        // 鸟瞰模式：V 键切换剖视图手动开关
        // 实际状态更新与区块重建在 handleEndPhase 中完成，避免 active 未更新就触发重编
        if (BirdviewClientEvent.isBirdseyeActive() && TOGGLE_SECTION_VIEW.consumeClick()) {
            SectionViewCuller.toggleManualEnable();
            boolean enabled = SectionViewCuller.isManuallyEnabled();
            mc.player.displayClientMessage(
                Component.literal(enabled
                    ? "§7[剖视图] §a已开启"
                    : "§7[剖视图] §c已关闭"),
                true
            );
        }

        // 鸟瞰模式：[ / ] 键旋转相机朝向（每次 ±45°）
        if (BirdviewClientEvent.isBirdseyeActive()) {
            if (CAMERA_ROTATE_LEFT.consumeClick()) {
                BirdviewClientEvent.rotateCameraYaw(45.0);
            }
            if (CAMERA_ROTATE_RIGHT.consumeClick()) {
                BirdviewClientEvent.rotateCameraYaw(-45.0);
            }
        }

        // 鸟瞰模式：WASD 按下时立即清除寻路路径，再设置 yaw 对齐 + 平视
        // 必须在 applyCameraAlignedInput 之前清除，否则同一 tick 内两个系统
        // 同时 setDeltaMovement，导致抖动
        if (BirdviewClientEvent.isBirdseyeActive()) {
            boolean wasdPressed = mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                    || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
            if (wasdPressed && BirdviewClientEvent.hasMoveTarget()) {
                BirdviewClientEvent.clearMoveTarget();
            }
            applyCameraAlignedInput(mc);
        }
    }

    /** Phase.END：更新悬停方块、相机对齐 WASD、点击移动控制 */
    private static void handleEndPhase(Minecraft mc) {
        if (!BirdviewClientEvent.isBirdseyeActive()) {
            return;
        }

        // === 建筑遮挡自适应高度：扫描头顶方块 → 平滑下压/回升相机 ===
        if (mc.level != null && mc.player != null) {
            // 先更新天空可见性：露天环境不压低相机，避免悬崖/山坡误判为洞穴天花板
            BirdviewClientEvent.updateSkyVisibility(mc.level, mc.player.position());

            double target = BirdviewClientEvent.calculateOcclusionHeight(mc.level, mc.player.position());
            BirdviewClientEvent.setTargetBirdseyeHeight(target);
            BirdviewClientEvent.updateOcclusionSmoothing();

            // === 墙壁推离：六向射线 → 平滑推离相机远离墙面 ===
            // 使用当前平滑后的相机位置做射线检测源（近似，下一帧更精确）
            double h = BirdviewClientEvent.getEffectiveBirdseyeHeight();
            double offset = h / Math.tan(Math.toRadians(BirdviewClientEvent.BIRDSEYE_PITCH));
            double yawRad = Math.toRadians(BirdviewClientEvent.getFixedYaw());
            Vec3 approxCamPos = new Vec3(
                mc.player.getX() - Math.sin(yawRad) * offset + BirdviewClientEvent.getEffectivePushOffset().x,
                mc.player.getY() + h + BirdviewClientEvent.getEffectivePushOffset().y,
                mc.player.getZ() + Math.cos(yawRad) * offset + BirdviewClientEvent.getEffectivePushOffset().z
            );
            Vec3 playerEye = mc.player.getEyePosition();
            Vec3 pushTarget = BirdviewClientEvent.calculateWallPush(mc.level, approxCamPos, playerEye);
            BirdviewClientEvent.setTargetPushOffset(pushTarget);
            BirdviewClientEvent.updateWallPushSmoothing();

            // === 建筑剖视图：每 tick 检测封闭空间 + 状态变化时触发区块重编译 ===
            SectionViewCuller.update(mc.level, mc.player.position());
            if (SectionViewCuller.stateJustChanged()) {
                // 区块刚刚进入/退出剖视状态 → 标记附近整柱区块需要重编译
                // 使用世界 Min/Max Build Height 作为 Y 范围，避免屋顶/地下室漏重建
                int px = mc.player.blockPosition().getX();
                int pz = mc.player.blockPosition().getZ();
                int range = 48; // 4 个区块范围
                mc.levelRenderer.setBlocksDirty(
                    px - range, mc.level.getMinBuildHeight(), pz - range,
                    px + range, mc.level.getMaxBuildHeight(), pz + range
                );
                SectionViewCuller.confirmStateChange();
            }
        }

        // 更新悬停方块/生物（virtCursor 已由 overlay 在 60 FPS 下实时更新）
        updateHoveredBlock(mc);

        // 轮询异步寻路结果：后台线程完成时提取路径
        List<Vec3> asyncResult = BirdviewClientEvent.pollAsyncPathResult();
        if (asyncResult != null && asyncResult.size() >= 2) {
            BirdviewClientEvent.setPath(asyncResult);
        }

        // 有移动目标时执行点击移动
        if (BirdviewClientEvent.hasMoveTarget()) {
            handleMoveToTarget(mc);
        }

        // 检查 WASD 是否按下（用于自动跳跃和取消寻路）
        boolean wasdPressed = mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();

        // 自动跳跃：WASD 按下时，在地面被阻挡（水平速度极小）则自动跳
        if (wasdPressed && mc.player.onGround()
                && mc.player.getDeltaMovement().horizontalDistanceSqr() < 0.004) {
            mc.player.jumpFromGround();
        }

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
     * WASD 移动时：身体朝向始终同步实际移动方向。
     * W=前方  S=后方  A=左方  D=右方，斜向按键自动合成对角线方向。
     * 移动方式：始终朝面对方向直行，用 setDeltaMovement 覆盖 MC 默认 moveRelative，
     * 否则按 S 时 forwardImpulse=-1 会导致实际移动方向与身体朝向差 180°。
     * 不按任何方向键时：不碰 yaw 和速度，鼠标通过 turnPlayer() 自由控制玩家转身。
     * 始终平视（防止玩家抬头低头）。
     */
    private static void applyCameraAlignedInput(Minecraft mc) {
        float lookYaw = BirdviewClientEvent.getCameraLookYaw();

        boolean w = mc.options.keyUp.isDown();
        boolean s = mc.options.keyDown.isDown();
        boolean a = mc.options.keyLeft.isDown();
        boolean d = mc.options.keyRight.isDown();

        // 用向量合成替代角度平均，避免 0/360° 边界问题（如 W+A 平均 0+270=135 而非 315）
        float forward = 0f;  // W=+1, S=-1
        if (w && !s) forward = 1f;
        else if (s && !w) forward = -1f;

        float right = 0f;   // A=+1, D=-1（屏幕右侧=世界东方，沿-lookYaw旋转后X分量需反向）
        if (a && !d) right = 1f;
        else if (d && !a) right = -1f;

        boolean hasInput = forward != 0f || right != 0f;

        if (hasInput) {
            // 旋转 (forward, right) 向量到世界空间
            double rad = Math.toRadians(lookYaw);
            double worldX = -Math.sin(rad) * forward + Math.cos(rad) * right;
            double worldZ = Math.cos(rad) * forward + Math.sin(rad) * right;

            // 归一化（斜向按键时为 √2，归一化后保持速度一致）
            double len = Math.sqrt(worldX * worldX + worldZ * worldZ);
            worldX /= len;
            worldZ /= len;

            float moveYaw = (float) Math.toDegrees(Math.atan2(-worldX, worldZ));
            mc.player.setYRot(moveYaw);
            mc.player.yBodyRot = moveYaw;
            mc.player.yHeadRot = moveYaw;

            // 覆盖移动：始终朝面对方向直行（所有 WASD 键都等效）
            float speed = mc.player.getSpeed() * getTerminalVelocityMultiplier(mc);
            if (mc.options.keySprint.isDown()) speed *= 1.3f;

            if (mc.player.onGround()) {
                mc.player.setDeltaMovement(worldX * speed, mc.player.getDeltaMovement().y, worldZ * speed);
            } else {
                mc.player.setDeltaMovement(
                    Mth.lerp(0.3f, mc.player.getDeltaMovement().x, worldX * speed),
                    mc.player.getDeltaMovement().y,
                    Mth.lerp(0.3f, mc.player.getDeltaMovement().z, worldZ * speed)
                );
            }
        }

        // 始终平视
        mc.player.setXRot(0);

        // 冲刺
        mc.player.setSprinting(mc.options.keySprint.isDown() && hasInput);
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
            team.setCollisionRule(Team.CollisionRule.ALWAYS);
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

            // === A* 寻路：距离 >= 3 格时异步计算智能路径 ===
            double directDist = mc.player.position().distanceTo(targetPos);
            BirdviewClientEvent.clearPath(); // 先清除旧路径
            if (directDist >= 3.0) {
                BlockPos startBlock = mc.player.blockPosition();
                BlockPos targetBlock = new BlockPos((int) targetPos.x, (int) targetPos.y, (int) targetPos.z);
                // 捕获 level 引用（ClientLevel.getBlockState 在已加载区块内线程安全）
                Level levelRef = mc.level;
                BirdviewClientEvent.submitAsyncPath(
                    CompletableFuture.supplyAsync(() -> AStarPathfinder.findPath(levelRef, startBlock, targetBlock, 3000, 0))
                );
            }
        }
        // 如果没有命中方块（点击天空），不设置目标
    }

    /** 每帧向移动目标移动：优先走寻路路径，无路径时直线移动 */
    private static void handleMoveToTarget(Minecraft mc) {
        Vec3 target = BirdviewClientEvent.getMoveTarget();
        if (target == null) return;

        Vec3 playerPos = mc.player.position();
        double distToTarget = target.distanceTo(playerPos);

        // 到达目标
        if (distToTarget < 0.6) {
            BirdviewClientEvent.clearMoveTarget();
            mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0);
            return;
        }

        // === 优先 A* 路径导航 ===
        if (BirdviewClientEvent.hasPath()) {
            if (navigatePath(mc, target)) return;
            // navigatePath 返回 true 时表示路径导航已完成处理
            // 返回 false 时回退到直线移动
        }

        // === 直线移动（无路径或路径导航回退） ===
        navigateStraight(mc, target, playerPos, distToTarget);
    }

    /**
     * 沿 A* 路径点导航。返回 true 表示已处理完毕（由路径接管），
     * 返回 false 表示路径失效，应回退到直线移动。
     */
    private static boolean navigatePath(Minecraft mc, Vec3 finalTarget) {
        Vec3 waypoint = BirdviewClientEvent.getCurrentWaypoint();
        if (waypoint == null) {
            BirdviewClientEvent.clearPath();
            return false;
        }

        Vec3 playerPos = mc.player.position();
        double wpDist = playerPos.distanceTo(waypoint);

        // waypoint 到达判定：水平距离 < 0.75 格
        if (wpDist < 0.75) {
            BirdviewClientEvent.advanceWaypoint();
            BirdviewClientEvent.resetStuckDetection();
            if (BirdviewClientEvent.isPathComplete()) {
                BirdviewClientEvent.clearPath();
                return false; // 路径走完，让直线移动接手到最终目标
            }
            waypoint = BirdviewClientEvent.getCurrentWaypoint();
            if (waypoint == null) return false;
            wpDist = playerPos.distanceTo(waypoint);
        }

        // 如果玩家离最终目标比离当前 waypoint 更近，跳过中间 waypoint
        double distToFinal = finalTarget.distanceTo(playerPos);
        if (distToFinal < wpDist && distToFinal < 2.0) {
            BirdviewClientEvent.clearPath();
            return false;
        }

        // === 计算移动方向 ===
        Vec3 dir = new Vec3(waypoint.x - playerPos.x, 0, waypoint.z - playerPos.z).normalize();
        float moveYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));

        float speed = mc.player.getSpeed() * getTerminalVelocityMultiplier(mc);
        if (mc.options.keySprint.isDown()) speed *= 1.3f;

        // === 自动爬坡检测 ===
        BlockPos playerBlock = mc.player.blockPosition();
        BlockPos frontBlock = new BlockPos(
            (int) Math.floor(playerPos.x + dir.x * 0.6),
            playerBlock.getY(),
            (int) Math.floor(playerPos.z + dir.z * 0.6)
        );

        if (mc.player.onGround()) {
            boolean needClimb = detectFrontObstacle(mc, dir);

            if (needClimb) {
                // 尝试爬坡：检测前方方块是否可踏上
                if (canStepUp(mc, dir)) {
                    // 走上台阶（不跳，自然走上去）
                    mc.player.setDeltaMovement(dir.x * speed, 0.15, dir.z * speed);
                } else {
                    // 1 格高障碍 → 跳跃
                    mc.player.jumpFromGround();
                    mc.player.setDeltaMovement(dir.x * speed * 0.7, mc.player.getDeltaMovement().y, dir.z * speed * 0.7);
                }
            } else {
                // 无障碍 → 正常行走
                mc.player.setDeltaMovement(dir.x * speed, mc.player.getDeltaMovement().y, dir.z * speed);
            }
        }

        // 空中保持水平动量
        if (!mc.player.onGround()) {
            mc.player.setDeltaMovement(
                Mth.lerp(0.3, mc.player.getDeltaMovement().x, dir.x * speed),
                mc.player.getDeltaMovement().y,
                Mth.lerp(0.3, mc.player.getDeltaMovement().z, dir.z * speed)
            );
        }

        // 面向移动方向（锁定目标时不覆盖，由 chaseTick 控制朝向）
        if (!LockTargetSystem.isLocked()) {
            mc.player.setYRot(moveYaw);
            mc.player.yBodyRot = moveYaw;
            mc.player.yHeadRot = moveYaw;
            mc.player.setXRot(0);
        }

        // === 卡住检测 ===
        BirdviewClientEvent.updateStuckDetection(playerPos, mc.player.getDeltaMovement().horizontalDistance());
        if (BirdviewClientEvent.isStuck()) {
            // 路径点卡住 → 跳过该点，尝试下一个
            BirdviewClientEvent.advanceWaypoint();
            BirdviewClientEvent.resetStuckDetection();

            if (BirdviewClientEvent.isPathComplete()) {
                BirdviewClientEvent.clearPath();
                return false;
            }
        }

        return true;
    }

    /**
     * 检测玩家前方是否有需要爬坡的障碍物。
     * 扫描规则：前方 0.3~0.8 格范围内，脚底 + 脚底上方一格的方块。
     */
    private static boolean detectFrontObstacle(Minecraft mc, Vec3 dir) {
        if (mc.level == null) return false;
        Vec3 pos = mc.player.position();

        // 检测两个高度：脚底 + 脚底上方
        for (int dy = 0; dy <= 1; dy++) {
            double checkX = pos.x + dir.x * 0.5;
            double checkY = pos.y + dy;
            double checkZ = pos.z + dir.z * 0.5;
            BlockPos bp = new BlockPos((int) Math.floor(checkX), (int) Math.floor(checkY), (int) Math.floor(checkZ));
            BlockState state = mc.level.getBlockState(bp);
            if (!state.isAir() && !state.canBeReplaced()) {
                if (dy == 0) return true; // 脚底有方块 = 障碍
                // dy=1 时，只当方块是完整固体时才算障碍（楼梯/半砖顶不是障碍）
                if (state.isSolidRender(mc.level, bp)) return true;
            }
        }
        return false;
    }

    /**
     * 判断前方障碍是否可"走上"而非跳跃。
     * 可走上：楼梯、底层半砖、地毯等（方块高度 < 1 格）。
     */
    private static boolean canStepUp(Minecraft mc, Vec3 dir) {
        if (mc.level == null) return false;
        Vec3 pos = mc.player.position();

        double checkX = pos.x + dir.x * 0.5;
        double checkZ = pos.z + dir.z * 0.5;
        BlockPos bp = new BlockPos((int) Math.floor(checkX), (int) Math.floor(pos.y), (int) Math.floor(checkZ));
        BlockState state = mc.level.getBlockState(bp);
        if (state.isAir() || state.canBeReplaced()) return false;

        // 低矮方块：楼梯、半砖等 → 直接走上
        if (state.getBlock() instanceof net.minecraft.world.level.block.StairBlock
            || state.getBlock() instanceof net.minecraft.world.level.block.SlabBlock) {
            return true;
        }

        // 碰撞箱高度 < 1.0 格的方块
        if (state.getCollisionShape(mc.level, bp).max(net.minecraft.core.Direction.Axis.Y) < 0.6) {
            return true;
        }

        return false;
    }

    /** 直线移动（回退方案） */
    private static void navigateStraight(Minecraft mc, Vec3 target, Vec3 playerPos, double dist) {
        Vec3 toTarget = target.subtract(playerPos);
        Vec3 dir = new Vec3(toTarget.x, 0, toTarget.z).normalize();
        float moveYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));

        if (mc.player.onGround()) {
            float speed = mc.player.getSpeed() * getTerminalVelocityMultiplier(mc);
            if (mc.options.keySprint.isDown()) speed *= 1.3f;

            mc.player.setDeltaMovement(dir.x * speed, mc.player.getDeltaMovement().y, dir.z * speed);

            // 自动跳跃：速度严重下降 → 前方有障碍
            double hSpeed = mc.player.getDeltaMovement().horizontalDistance();
            if (hSpeed < speed * 0.3) {
                mc.player.jumpFromGround();
            }
        }

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

        // === 屏幕空间拾取：空中/滞空实体检测（纯数学投影，不依赖缓存） ===
        // 对于不在地面附近的飞行生物（如 Phantom、Allay、Ghast），
        // 用纯数学投影做屏幕空间命中判定，支持左键点击空中怪物直接攻击
        // 不依赖缓存的渲染矩阵/相机数据，首帧即可使用
        {
            Vec3 playerPos = mc.player.position();
            int sw = window.getWidth();
            int sh = window.getHeight();
            double zoom = OrthoviewClientEvent.getZoom();
            if (zoom <= 0) zoom = OrthoviewClientEvent.ZOOM_DEFAULT;
            double pixelsPerBlock = sh / (2.0 * zoom);

            double bestScreenDist = Double.MAX_VALUE; // 最佳命中实体的屏幕距离
            Entity bestAirEntity = null;
            double bestAirDepth = Double.MAX_VALUE;

            // 在玩家周围搜索所有活体（排除玩家自己）
            AABB searchBox = new AABB(
                playerPos.x - 48, playerPos.y - 48, playerPos.z - 48,
                playerPos.x + 48, playerPos.y + 48, playerPos.z + 48
            );
            for (Entity entity : mc.level.getEntities(mc.player, searchBox,
                    e -> (e instanceof LivingEntity || e instanceof EndCrystal) && e.isAlive())) {

                // 投影实体中心（偏上 60% 高度，更好点中）到屏幕
                Vec3 entityCenter = new Vec3(
                    entity.getX(),
                    entity.getY() + entity.getBbHeight() * 0.6,
                    entity.getZ()
                );
                double[] screen = BirdviewClientEvent.worldToScreenPureMath(entityCenter, sw, sh, playerPos);
                if (screen == null) continue; // 投影失败

                // 超出屏幕范围（留 50px 余量）则跳过
                if (screen[0] < -50 || screen[0] > sw + 50 || screen[1] < -50 || screen[1] > sh + 50) continue;

                double dx = screen[0] - mouseX;
                double dy = screen[1] - mouseY;
                double dist = Math.sqrt(dx * dx + dy * dy);

                // 动态命中半径：实体在屏幕上越大，命中半径越大
                // 小实体（僵尸/幻翼）→ 固定 20px；大实体（恶魂/末影龙）→ 按视觉大小缩放
                double entityScreenRadius = entity.getBbWidth() * pixelsPerBlock * 0.4;
                double hitRadius = Math.max(20.0, entityScreenRadius);

                if (dist < hitRadius) {
                    double depth = entity.distanceToSqr(playerPos);
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

        BirdviewClientEvent.setHoveredHitResult(bestHit);
    }

    // ===== 剖视图调试信息 HUD =====

    /** 在屏幕左上角显示剖视图 + 洞穴系数的实时状态，用于排查视觉异常 */
    @SubscribeEvent
    public static void onRenderDebugOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!BirdviewClientEvent.isBirdseyeActive()) return;
        // 剖视图未手动开启时不显示状态文本
        if (!SectionViewCuller.isManuallyEnabled()) return;

        var font = mc.font;
        int x = 5;
        int y = 5;
        int lineHeight = 10;

        // 剖视图状态
        boolean active = SectionViewCuller.isActive();
        String sectionStatus = active
            ? "§a激活"
            : "§7未激活";
        event.getGuiGraphics().drawString(font,
            "§f剖视图: " + sectionStatus + "  §8(yOff=" + SectionViewCuller.getDynamicRoofOffset() + ")",
            x, y, 0xFFFFFFFF);
        y += lineHeight;

        // 洞穴系数
        float cave = BirdviewClientEvent.getCaveFactor();
        String caveColor = cave > 0.05f ? "§e" : "§7";
        event.getGuiGraphics().drawString(font,
            "§f洞穴系数: " + caveColor + String.format("%.3f", cave),
            x, y, 0xFFFFFFFF);
        y += lineHeight;

        // 玩家坐标
        Vec3 pp = mc.player.position();
        event.getGuiGraphics().drawString(font,
            "§7玩家: " + (int)pp.x + " " + (int)pp.y + " " + (int)pp.z,
            x, y, 0xFFFFFFFF);
        y += lineHeight;

        // 相机坐标（近似）
        double h = BirdviewClientEvent.getEffectiveBirdseyeHeight();
        double ho = h / Math.tan(Math.toRadians(BirdviewClientEvent.BIRDSEYE_PITCH));
        double yawRad = Math.toRadians(BirdviewClientEvent.getFixedYaw());
        Vec3 pushOff = BirdviewClientEvent.getEffectivePushOffset();
        event.getGuiGraphics().drawString(font,
            "§7相机: " + (int)(pp.x - Math.sin(yawRad)*ho + pushOff.x) + " "
                       + (int)(pp.y + h + pushOff.y) + " "
                       + (int)(pp.z + Math.cos(yawRad)*ho + pushOff.z),
            x, y, 0xFFFFFFFF);
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
            Entity rangedTarget = LockTargetSystem.getRangedTarget();
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
            Minecraft mc = Minecraft.getInstance();
            // 如果剖视图曾激活，标记附近区块需要重编译以恢复被剔除的方块
            if (SectionViewCuller.isActive() && mc.levelRenderer != null) {
                int px = mc.player.blockPosition().getX();
                int pz = mc.player.blockPosition().getZ();
                int range = 48;
                mc.levelRenderer.setBlocksDirty(
                    px - range, mc.level.getMinBuildHeight(), pz - range,
                    px + range, mc.level.getMaxBuildHeight(), pz + range
                );
            }
            BirdviewClientEvent.reset();
            BirdviewClientEvent.resetOcclusionHeight();
            BirdviewClientEvent.resetWallPushOffset();
            SectionViewCuller.reset();
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

        double height = BirdviewClientEvent.getEffectiveBirdseyeHeight();

        // 水平偏移量 = 高度 / tan(参考俯仰角)
        double horizontalOffset = height / Math.tan(Math.toRadians(BirdviewClientEvent.BIRDSEYE_PITCH));
        // 使用进入鸟瞰时锁定的固定世界 yaw（鼠标不再改变相机朝向）
        double fixedYaw = BirdviewClientEvent.getFixedYaw();
        double yawRad = Math.toRadians(fixedYaw);

        // 相机位置：玩家斜后方固定高度（固定世界方向，不随玩家旋转）
        Vec3 pushOffset = BirdviewClientEvent.getEffectivePushOffset();
        Vec3 cameraPos = new Vec3(
                mc.player.getX() - Math.sin(yawRad) * horizontalOffset + pushOffset.x,
                mc.player.getY() + height + pushOffset.y,
                mc.player.getZ() + Math.cos(yawRad) * horizontalOffset + pushOffset.z
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
     * 地面：系数 = 1 / (1 - 方块摩擦 * 0.91)
     * 空中：使用固定值 2.0，避免 setDeltaMovement 绕过空气阻力导致冲太猛
     */
    private static float getTerminalVelocityMultiplier(Minecraft mc) {
        if (!mc.player.onGround()) {
            return 2.0f; // 空中降低倍率，避免跳跃冲太猛
        }
        float friction = 0.91F;
        BlockPos groundPos = mc.player.blockPosition().below();
        float blockFriction = mc.level.getBlockState(groundPos).getBlock().getFriction();
        friction = blockFriction * 0.91F;
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