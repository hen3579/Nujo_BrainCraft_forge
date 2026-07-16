package com.Hen3579.Nujomod.Config;

import com.Hen3579.Nujomod.NujoBraincraft;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = NujoBraincraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    // =========================================================================
    //  客户端配置（CLIENT）：画面、缩放、功能开关等本地选项
    // =========================================================================

    // ---- 鸟瞰视角 ----
    public static final ForgeConfigSpec.DoubleValue BIRDSEYE_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue BIRDSEYE_PITCH;
    public static final ForgeConfigSpec.DoubleValue HEIGHT_LERP_SPEED;
    public static final ForgeConfigSpec.DoubleValue CEILING_CLEARANCE;
    public static final ForgeConfigSpec.DoubleValue MIN_HORIZONTAL_OFFSET;
    public static final ForgeConfigSpec.IntValue SCAN_COLUMNS;
    public static final ForgeConfigSpec.DoubleValue RAY_STEP;
    public static final ForgeConfigSpec.DoubleValue PUSH_LERP_SPEED;
    public static final ForgeConfigSpec.DoubleValue WALL_RAY_LENGTH;
    public static final ForgeConfigSpec.DoubleValue SAFE_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue DEFAULT_FIXED_YAW;

    // ---- 正交投影 & 缩放 ----
    public static final ForgeConfigSpec.DoubleValue ZOOM_DEFAULT;
    public static final ForgeConfigSpec.DoubleValue ZOOM_MIN;
    public static final ForgeConfigSpec.DoubleValue ZOOM_MAX;
    public static final ForgeConfigSpec.DoubleValue ZOOM_STEP;
    public static final ForgeConfigSpec.DoubleValue NEAR_PLANE_OUTDOOR;
    public static final ForgeConfigSpec.DoubleValue NEAR_PLANE_CAVE;
    public static final ForgeConfigSpec.DoubleValue FAR_PLANE;

    // ---- 剖视图 ----
    public static final ForgeConfigSpec.IntValue DEFAULT_ROOF_OFFSET;
    public static final ForgeConfigSpec.IntValue CEILING_SOLID_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue PLANE_BUFFER;
    public static final ForgeConfigSpec.IntValue DETECT_CEILING_RANGE;
    public static final ForgeConfigSpec.IntValue DETECT_WALL_RANGE;
    public static final ForgeConfigSpec.IntValue MIN_BLOCKED_DIRS;
    public static final ForgeConfigSpec.IntValue DETECT_INTERVAL;

    // ---- 点击标记 ----
    public static final ForgeConfigSpec.LongValue MARKER_DURATION_MS;

    // ---- 功能开关 ----
    public static final ForgeConfigSpec.BooleanValue BIRDVIEW_ENABLED;
    public static final ForgeConfigSpec.BooleanValue SECTION_VIEW_ENABLED;
    public static final ForgeConfigSpec.BooleanValue CUSTOM_MAIN_MENU_ENABLED;

    public static final ForgeConfigSpec CLIENT_SPEC;

    // =========================================================================
    //  服务端配置（SERVER）：战斗数值、AI 参数等需要同步的选项
    // =========================================================================

    // ---- 战斗系统 ----
    public static final ForgeConfigSpec.DoubleValue SEARCH_RANGE;
    public static final ForgeConfigSpec.DoubleValue MELEE_RANGE;
    public static final ForgeConfigSpec.IntValue MELEE_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue BOW_CHARGE_TICKS_NEEDED;
    public static final ForgeConfigSpec.IntValue RANGED_COOLDOWN_TICKS;

    // ---- 末影龙 AI ----
    public static final ForgeConfigSpec.DoubleValue DRAGON_PHASE_AB_Y;
    public static final ForgeConfigSpec.DoubleValue DRAGON_PHASE_C_Y;
    public static final ForgeConfigSpec.DoubleValue DRAGON_Y_TOLERANCE;
    public static final ForgeConfigSpec.DoubleValue DRAGON_CENTER_X;
    public static final ForgeConfigSpec.DoubleValue DRAGON_CENTER_Z;
    public static final ForgeConfigSpec.DoubleValue DRAGON_MAX_RANGE;
    public static final ForgeConfigSpec.DoubleValue DRAGON_PULL_BACK_SPEED;
    public static final ForgeConfigSpec.DoubleValue DRAGON_SPEED_PATROL;
    public static final ForgeConfigSpec.DoubleValue DRAGON_SPEED_CIRCLE;
    public static final ForgeConfigSpec.DoubleValue DRAGON_SPEED_CHARGE;
    public static final ForgeConfigSpec.DoubleValue DRAGON_PATROL_RADIUS;
    public static final ForgeConfigSpec.IntValue DRAGON_PATROL_SWITCH_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue DRAGON_PHASE_C_HP_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue DRAGON_A3_HEAL_HP_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue DRAGON_A3_HEAL_RATE;
    public static final ForgeConfigSpec.IntValue DRAGON_CD_FIREBALL_A;
    public static final ForgeConfigSpec.IntValue DRAGON_CD_FIREBALL_B;
    public static final ForgeConfigSpec.IntValue DRAGON_CD_CHARGE;
    public static final ForgeConfigSpec.IntValue DRAGON_CD_BREATH;
    public static final ForgeConfigSpec.IntValue DRAGON_CD_AOE;
    public static final ForgeConfigSpec.IntValue DRAGON_CD_METEOR;
    public static final ForgeConfigSpec.IntValue DRAGON_CD_SHOCKWAVE;
    public static final ForgeConfigSpec.IntValue DRAGON_ULTIMATE_TRIGGER_INTERVAL;
    public static final ForgeConfigSpec.IntValue DRAGON_C3_SKILL_DURATION;
    public static final ForgeConfigSpec.DoubleValue DRAGON_RING_AOE_MAX_RADIUS;
    public static final ForgeConfigSpec.DoubleValue DRAGON_SHOCKWAVE_MAX_RADIUS;
    public static final ForgeConfigSpec.IntValue DRAGON_CHARGE_DURATION;
    public static final ForgeConfigSpec.IntValue DRAGON_LANDING_DURATION;

    // ---- 飞行生物约束 ----
    public static final ForgeConfigSpec.IntValue DIVE_DURATION;
    public static final ForgeConfigSpec.DoubleValue BIRDVIEW_RANGE;
    public static final ForgeConfigSpec.IntValue FLYING_Y_MIN_OFFSET;
    public static final ForgeConfigSpec.IntValue FLYING_Y_MAX_OFFSET;

    public static final ForgeConfigSpec SERVER_SPEC;

    static {
        // =====================================================================
        //  客户端配置
        // =====================================================================
        ForgeConfigSpec.Builder client = new ForgeConfigSpec.Builder();

        client.push("birdview").comment("鸟瞰视角设置");
        BIRDSEYE_HEIGHT = client
                .comment("默认鸟瞰高度（格，玩家上方）")
                .defineInRange("defaultHeight", 10.0, 3.0, 50.0);
        BIRDSEYE_PITCH = client
                .comment("鸟瞰俯仰角（度，0=平视 90=垂直向下）")
                .defineInRange("pitch", 45.0, 10.0, 80.0);
        HEIGHT_LERP_SPEED = client
                .comment("高度平滑插值速度（越大越灵敏）")
                .defineInRange("heightLerpSpeed", 0.15, 0.01, 1.0);
        CEILING_CLEARANCE = client
                .comment("天花板下安全距离（格）")
                .defineInRange("ceilingClearance", 0.6, 0.1, 3.0);
        MIN_HORIZONTAL_OFFSET = client
                .comment("最小水平偏移（格，防室内贴脸）")
                .defineInRange("minHorizontalOffset", 5.0, 1.0, 20.0);
        SCAN_COLUMNS = client
                .comment("遮挡检测采样列数")
                .defineInRange("scanColumns", 7, 3, 21);
        RAY_STEP = client
                .comment("射线墙壁检测步长（格）")
                .defineInRange("rayStep", 0.5, 0.1, 2.0);
        PUSH_LERP_SPEED = client
                .comment("墙壁推离平滑速度")
                .defineInRange("pushLerpSpeed", 0.25, 0.01, 1.0);
        WALL_RAY_LENGTH = client
                .comment("墙壁检测射线长度（格）")
                .defineInRange("wallRayLength", 2.5, 1.0, 10.0);
        SAFE_DISTANCE = client
                .comment("相机距墙安全距离（格）")
                .defineInRange("safeDistance", 1.8, 0.5, 5.0);
        DEFAULT_FIXED_YAW = client
                .comment("默认相机朝向角度（度）")
                .defineInRange("defaultFixedYaw", 135.0, 0.0, 360.0);
        client.pop();

        client.push("ortho").comment("正交投影 & 缩放设置");
        ZOOM_DEFAULT = client
                .comment("默认缩放值")
                .defineInRange("zoomDefault", 30.0, 1.0, 90.0);
        ZOOM_MIN = client
                .comment("最小缩放")
                .defineInRange("zoomMin", 10.0, 1.0, 30.0);
        ZOOM_MAX = client
                .comment("最大缩放")
                .defineInRange("zoomMax", 60.0, 10.0, 200.0);
        ZOOM_STEP = client
                .comment("滚轮缩放步进量")
                .defineInRange("zoomStep", 3.0, 0.5, 10.0);
        NEAR_PLANE_OUTDOOR = client
                .comment("室外近裁剪面（格，-3000 推到相机后方）")
                .defineInRange("nearPlaneOutdoor", -3000.0, -5000.0, 1.0);
        NEAR_PLANE_CAVE = client
                .comment("洞穴近裁剪面（格，-3000 与室外一致）")
                .defineInRange("nearPlaneCave", -3000.0, -5000.0, 10.0);
        FAR_PLANE = client
                .comment("远裁剪面（格，3000 配合 near=-3000 形成 6000 格宽渲染范围）")
                .defineInRange("farPlane", 3000.0, 100.0, 5000.0);
        client.pop();

        client.push("sectionView").comment("剖视图设置");
        DEFAULT_ROOF_OFFSET = client
                .comment("默认屋顶偏移（格，回退值）")
                .defineInRange("defaultRoofOffset", 2, 1, 10);
        CEILING_SOLID_THRESHOLD = client
                .comment("天花板判定所需固体方块数（3×3 网格中）")
                .defineInRange("ceilingSolidThreshold", 3, 1, 9);
        PLANE_BUFFER = client
                .comment("裁剪平面缓冲（格，防闪烁）")
                .defineInRange("planeBuffer", 0.75, 0.0, 2.0);
        DETECT_CEILING_RANGE = client
                .comment("天花板检测最大距离（格）")
                .defineInRange("detectCeilingRange", 6, 2, 20);
        DETECT_WALL_RANGE = client
                .comment("墙壁检测最大距离（格）")
                .defineInRange("detectWallRange", 6, 2, 20);
        MIN_BLOCKED_DIRS = client
                .comment("封闭空间判定所需最少被阻挡方向数（上 + 4方向 + 4角）")
                .defineInRange("minBlockedDirs", 4, 1, 9);
        DETECT_INTERVAL = client
                .comment("封闭空间检测间隔（tick）")
                .defineInRange("detectInterval", 10, 1, 100);
        client.pop();

        client.push("marker").comment("点击标记设置");
        MARKER_DURATION_MS = client
                .comment("点击标记持续时间（毫秒）")
                .defineInRange("markerDurationMs", 1000L, 200L, 5000L);
        client.pop();

        client.push("features").comment("功能开关（客户端）");
        BIRDVIEW_ENABLED = client
                .comment("启用鸟瞰视角")
                .define("birdviewEnabled", true);
        SECTION_VIEW_ENABLED = client
                .comment("启用剖视图")
                .define("sectionViewEnabled", true);
        CUSTOM_MAIN_MENU_ENABLED = client
                .comment("启用自定义主菜单")
                .define("customMainMenuEnabled", true);
        client.pop();

        CLIENT_SPEC = client.build();

        // =====================================================================
        //  服务端配置
        // =====================================================================
        ForgeConfigSpec.Builder server = new ForgeConfigSpec.Builder();

        server.push("combat").comment("战斗系统设置");
        SEARCH_RANGE = server
                .comment("锁敌搜索范围（格）")
                .defineInRange("searchRange", 64.0, 16.0, 256.0);
        MELEE_RANGE = server
                .comment("近战攻击距离（格）")
                .defineInRange("meleeRange", 4.0, 1.0, 10.0);
        MELEE_COOLDOWN_TICKS = server
                .comment("近战攻击冷却（tick，20tick=1秒）")
                .defineInRange("meleeCooldownTicks", 10, 1, 40);
        BOW_CHARGE_TICKS_NEEDED = server
                .comment("弓蓄力所需 tick")
                .defineInRange("bowChargeTicksNeeded", 10, 5, 40);
        RANGED_COOLDOWN_TICKS = server
                .comment("远程攻击全局冷却（tick）")
                .defineInRange("rangedCooldownTicks", 5, 1, 20);
        server.pop();

        server.push("dragon").comment("末影龙 AI 战斗设置");
        DRAGON_PHASE_AB_Y = server
                .comment("A/B 阶段锁定 Y 高度")
                .defineInRange("phaseAB_Y", 74.0, 60.0, 100.0);
        DRAGON_PHASE_C_Y = server
                .comment("C 阶段锁定 Y 高度")
                .defineInRange("phaseC_Y", 68.0, 50.0, 90.0);
        DRAGON_Y_TOLERANCE = server
                .comment("Y 轴容差（格）")
                .defineInRange("yTolerance", 1.0, 0.1, 5.0);
        DRAGON_CENTER_X = server
                .comment("祭坛中心 X 坐标")
                .defineInRange("centerX", 0.0, -100.0, 100.0);
        DRAGON_CENTER_Z = server
                .comment("祭坛中心 Z 坐标")
                .defineInRange("centerZ", 0.0, -100.0, 100.0);
        DRAGON_MAX_RANGE = server
                .comment("龙最大活动范围半径（格）")
                .defineInRange("maxRange", 30.0, 10.0, 100.0);
        DRAGON_PULL_BACK_SPEED = server
                .comment("拉回速度")
                .defineInRange("pullBackSpeed", 2.0, 0.5, 10.0);
        DRAGON_SPEED_PATROL = server
                .comment("巡逻速度")
                .defineInRange("speedPatrol", 2.5, 0.5, 10.0);
        DRAGON_SPEED_CIRCLE = server
                .comment("环绕速度")
                .defineInRange("speedCircle", 3.5, 0.5, 10.0);
        DRAGON_SPEED_CHARGE = server
                .comment("冲锋速度")
                .defineInRange("speedCharge", 6.0, 1.0, 15.0);
        DRAGON_PATROL_RADIUS = server
                .comment("巡逻半径（格）")
                .defineInRange("patrolRadius", 25.0, 5.0, 50.0);
        DRAGON_PATROL_SWITCH_INTERVAL = server
                .comment("巡逻方向切换间隔（tick）")
                .defineInRange("patrolSwitchInterval", 200, 20, 600);
        DRAGON_PHASE_C_HP_THRESHOLD = server
                .comment("C 阶段触发血量阈值（比例 0~1）")
                .defineInRange("phaseC_hpThreshold", 0.5, 0.1, 0.9);
        DRAGON_A3_HEAL_HP_THRESHOLD = server
                .comment("A3 回血触发阈值（比例 0~1）")
                .defineInRange("a3_healHpThreshold", 0.8, 0.1, 1.0);
        DRAGON_A3_HEAL_RATE = server
                .comment("A3 回血速率（比例/tick）")
                .defineInRange("a3_healRate", 0.02, 0.001, 0.1);
        DRAGON_CD_FIREBALL_A = server
                .comment("A 阶段火球冷却（tick）")
                .defineInRange("cdFireballA", 80, 10, 300);
        DRAGON_CD_FIREBALL_B = server
                .comment("B 阶段火球冷却（tick）")
                .defineInRange("cdFireballB", 60, 10, 300);
        DRAGON_CD_CHARGE = server
                .comment("冲锋冷却（tick）")
                .defineInRange("cdCharge", 100, 20, 400);
        DRAGON_CD_BREATH = server
                .comment("龙息冷却（tick）")
                .defineInRange("cdBreath", 80, 20, 400);
        DRAGON_CD_AOE = server
                .comment("AOE 技能冷却（tick）")
                .defineInRange("cdAOE", 120, 20, 400);
        DRAGON_CD_METEOR = server
                .comment("流星雨冷却（tick）")
                .defineInRange("cdMeteor", 200, 20, 600);
        DRAGON_CD_SHOCKWAVE = server
                .comment("冲击波冷却（tick）")
                .defineInRange("cdShockwave", 160, 20, 600);
        DRAGON_ULTIMATE_TRIGGER_INTERVAL = server
                .comment("C2→C3 触发间隔（tick）")
                .defineInRange("ultimateTriggerInterval", 120, 20, 400);
        DRAGON_C3_SKILL_DURATION = server
                .comment("C3 技能持续（tick）")
                .defineInRange("c3SkillDuration", 100, 20, 400);
        DRAGON_RING_AOE_MAX_RADIUS = server
                .comment("环形 AOE 最大半径（格）")
                .defineInRange("ringAoeMaxRadius", 12.0, 3.0, 30.0);
        DRAGON_SHOCKWAVE_MAX_RADIUS = server
                .comment("冲击波最大半径（格）")
                .defineInRange("shockwaveMaxRadius", 15.0, 3.0, 30.0);
        DRAGON_CHARGE_DURATION = server
                .comment("冲锋持续（tick）")
                .defineInRange("chargeDuration", 30, 5, 100);
        DRAGON_LANDING_DURATION = server
                .comment("落地持续（tick）")
                .defineInRange("landingDuration", 40, 10, 100);
        server.pop();

        server.push("flyingMob").comment("飞行生物 Y 轴约束设置");
        DIVE_DURATION = server
                .comment("俯冲持续时间（tick）")
                .defineInRange("diveDuration", 40, 10, 200);
        BIRDVIEW_RANGE = server
                .comment("鸟瞰模式生效范围（格）")
                .defineInRange("birdviewRange", 64.0, 16.0, 256.0);
        FLYING_Y_MIN_OFFSET = server
                .comment("飞行生物最低 Y（玩家头上格数）")
                .defineInRange("yMinOffset", 1, 0, 10);
        FLYING_Y_MAX_OFFSET = server
                .comment("飞行生物最高 Y（玩家头上格数）")
                .defineInRange("yMaxOffset", 5, 1, 20);
        server.pop();

        SERVER_SPEC = server.build();
    }

    // =========================================================================
    //  配置加载/重载事件 → 烘焙到各个系统的静态字段
    // =========================================================================

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent.Loading event) {
        // ModConfigEvent.Loading 对 CLIENT 和 SERVER 配置分别触发。
        // 只对 SERVER 配置执行烘焙，因为大多数配置值在 SERVER 端。
        // CLIENT 配置（如缩放）在 ModConfigEvent.Reloading 时也会被处理。
        if (event.getConfig().getType() == ModConfig.Type.SERVER) {
            bakeConfig();
        }
    }

    @SubscribeEvent
    public static void onReload(final ModConfigEvent.Reloading event) {
        // 玩家修改配置文件后热重载 /reload
        bakeConfig();
    }

    /**
     * 将配置值烘焙到各个系统类的静态字段中。
     * 这样各处代码无需直接引用 Config 类，保持原有访问方式不变。
     */
    private static void bakeConfig() {
        // ===== 鸟瞰视角 =====
        com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent.BIRDSEYE_HEIGHT = BIRDSEYE_HEIGHT.get();
        com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent.BIRDSEYE_PITCH = BIRDSEYE_PITCH.get().floatValue();
        com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent.HEIGHT_LERP_SPEED = HEIGHT_LERP_SPEED.get();
        com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent.CEILING_CLEARANCE = CEILING_CLEARANCE.get();
        com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent.MIN_HORIZONTAL_OFFSET = MIN_HORIZONTAL_OFFSET.get();
        // MIN_HEIGHT 由 MIN_HORIZONTAL_OFFSET 和 BIRDSEYE_PITCH 计算得出
        com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent.MIN_HEIGHT =
                MIN_HORIZONTAL_OFFSET.get() * Math.tan(Math.toRadians(BIRDSEYE_PITCH.get()));
        com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent.SCAN_COLUMNS = SCAN_COLUMNS.get();
        com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent.RAY_STEP = RAY_STEP.get();
        com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent.PUSH_LERP_SPEED = PUSH_LERP_SPEED.get();
        com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent.WALL_RAY_LENGTH = WALL_RAY_LENGTH.get();
        com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent.SAFE_DISTANCE = SAFE_DISTANCE.get();
        com.Hen3579.Nujomod.Client.Events.BirdviewClientEvent.DEFAULT_FIXED_YAW = DEFAULT_FIXED_YAW.get();

        // ===== 正交投影 & 缩放 =====
        com.Hen3579.Nujomod.Client.Events.OrthoviewClientEvent.ZOOM_DEFAULT = ZOOM_DEFAULT.get();
        com.Hen3579.Nujomod.Client.Events.OrthoviewClientEvent.ZOOM_MIN = ZOOM_MIN.get();
        com.Hen3579.Nujomod.Client.Events.OrthoviewClientEvent.ZOOM_MAX = ZOOM_MAX.get();
        com.Hen3579.Nujomod.Client.Events.OrthoviewClientEvent.ZOOM_STEP = ZOOM_STEP.get();
        com.Hen3579.Nujomod.Client.Events.OrthoviewClientEvent.NEAR_PLANE_OUTDOOR = NEAR_PLANE_OUTDOOR.get().floatValue();
        com.Hen3579.Nujomod.Client.Events.OrthoviewClientEvent.NEAR_PLANE_CAVE = NEAR_PLANE_CAVE.get().floatValue();
        com.Hen3579.Nujomod.Client.Events.OrthoviewClientEvent.FAR_PLANE = FAR_PLANE.get().floatValue();
        com.Hen3579.Nujomod.Client.Events.OrthoviewClientEvent.MARKER_DURATION_MS = MARKER_DURATION_MS.get();

        // ===== 剖视图 =====
        com.Hen3579.Nujomod.Client.Events.SectionViewCuller.DEFAULT_ROOF_OFFSET = DEFAULT_ROOF_OFFSET.get();
        com.Hen3579.Nujomod.Client.Events.SectionViewCuller.CEILING_SOLID_THRESHOLD = CEILING_SOLID_THRESHOLD.get();
        com.Hen3579.Nujomod.Client.Events.SectionViewCuller.PLANE_BUFFER = PLANE_BUFFER.get();
        com.Hen3579.Nujomod.Client.Events.SectionViewCuller.DETECT_CEILING_RANGE = DETECT_CEILING_RANGE.get();
        com.Hen3579.Nujomod.Client.Events.SectionViewCuller.DETECT_WALL_RANGE = DETECT_WALL_RANGE.get();
        com.Hen3579.Nujomod.Client.Events.SectionViewCuller.MIN_BLOCKED_DIRS = MIN_BLOCKED_DIRS.get();
        com.Hen3579.Nujomod.Client.Events.SectionViewCuller.DETECT_INTERVAL = DETECT_INTERVAL.get();

        // ===== 战斗系统 =====
        com.Hen3579.Nujomod.Client.Events.LockTargetSystem.SEARCH_RANGE = SEARCH_RANGE.get();
        com.Hen3579.Nujomod.Client.Events.LockTargetSystem.MELEE_RANGE = MELEE_RANGE.get();
        com.Hen3579.Nujomod.Client.Events.LockTargetSystem.MELEE_COOLDOWN_TICKS = MELEE_COOLDOWN_TICKS.get();
        com.Hen3579.Nujomod.Client.Events.LockTargetSystem.BOW_CHARGE_TICKS_NEEDED = BOW_CHARGE_TICKS_NEEDED.get();
        com.Hen3579.Nujomod.Client.Events.LockTargetSystem.GLOBAL_RANGED_COOLDOWN_TICKS = RANGED_COOLDOWN_TICKS.get();

        // ===== 末影龙 AI =====
        com.Hen3579.Nujomod.Server.DragonServerState.PHASE_AB_Y = DRAGON_PHASE_AB_Y.get();
        com.Hen3579.Nujomod.Server.DragonServerState.PHASE_C_Y = DRAGON_PHASE_C_Y.get();
        com.Hen3579.Nujomod.Server.DragonServerState.Y_TOLERANCE = DRAGON_Y_TOLERANCE.get();
        com.Hen3579.Nujomod.Server.DragonServerState.CENTER_X = DRAGON_CENTER_X.get();
        com.Hen3579.Nujomod.Server.DragonServerState.CENTER_Z = DRAGON_CENTER_Z.get();
        com.Hen3579.Nujomod.Server.DragonServerState.MAX_RANGE = DRAGON_MAX_RANGE.get();
        com.Hen3579.Nujomod.Server.DragonServerState.MAX_RANGE_SQ = DRAGON_MAX_RANGE.get() * DRAGON_MAX_RANGE.get();
        com.Hen3579.Nujomod.Server.DragonServerState.PULL_BACK_SPEED = DRAGON_PULL_BACK_SPEED.get();
        com.Hen3579.Nujomod.Server.DragonServerState.SPEED_PATROL = DRAGON_SPEED_PATROL.get();
        com.Hen3579.Nujomod.Server.DragonServerState.SPEED_CIRCLE = DRAGON_SPEED_CIRCLE.get();
        com.Hen3579.Nujomod.Server.DragonServerState.SPEED_CHARGE = DRAGON_SPEED_CHARGE.get();
        com.Hen3579.Nujomod.Server.DragonServerState.PATROL_RADIUS = DRAGON_PATROL_RADIUS.get();
        com.Hen3579.Nujomod.Server.DragonServerState.PATROL_SWITCH_INTERVAL = DRAGON_PATROL_SWITCH_INTERVAL.get();
        com.Hen3579.Nujomod.Server.DragonServerState.PHASE_C_HP_THRESHOLD = DRAGON_PHASE_C_HP_THRESHOLD.get();
        com.Hen3579.Nujomod.Server.DragonServerState.A3_HEAL_HP_THRESHOLD = DRAGON_A3_HEAL_HP_THRESHOLD.get();
        com.Hen3579.Nujomod.Server.DragonServerState.A3_HEAL_RATE = DRAGON_A3_HEAL_RATE.get();
        com.Hen3579.Nujomod.Server.DragonServerState.CD_FIREBALL_A = DRAGON_CD_FIREBALL_A.get();
        com.Hen3579.Nujomod.Server.DragonServerState.CD_FIREBALL_B = DRAGON_CD_FIREBALL_B.get();
        com.Hen3579.Nujomod.Server.DragonServerState.CD_CHARGE = DRAGON_CD_CHARGE.get();
        com.Hen3579.Nujomod.Server.DragonServerState.CD_BREATH = DRAGON_CD_BREATH.get();
        com.Hen3579.Nujomod.Server.DragonServerState.CD_AOE = DRAGON_CD_AOE.get();
        com.Hen3579.Nujomod.Server.DragonServerState.CD_METEOR = DRAGON_CD_METEOR.get();
        com.Hen3579.Nujomod.Server.DragonServerState.CD_SHOCKWAVE = DRAGON_CD_SHOCKWAVE.get();
        com.Hen3579.Nujomod.Server.DragonServerState.CD_ULTIMATE_TRIGGER = DRAGON_ULTIMATE_TRIGGER_INTERVAL.get();
        com.Hen3579.Nujomod.Server.DragonServerState.C3_SKILL_DURATION = DRAGON_C3_SKILL_DURATION.get();
        com.Hen3579.Nujomod.Server.DragonServerState.RING_AOE_MAX_RADIUS = DRAGON_RING_AOE_MAX_RADIUS.get();
        com.Hen3579.Nujomod.Server.DragonServerState.SHOCKWAVE_MAX_RADIUS = DRAGON_SHOCKWAVE_MAX_RADIUS.get();
        com.Hen3579.Nujomod.Server.DragonServerState.CHARGE_DURATION = DRAGON_CHARGE_DURATION.get();
        com.Hen3579.Nujomod.Server.DragonServerState.LANDING_DURATION = DRAGON_LANDING_DURATION.get();

        // ===== 飞行生物约束 =====
        com.Hen3579.Nujomod.Server.BirdviewServerState.DIVE_DURATION = DIVE_DURATION.get();
        com.Hen3579.Nujomod.Server.BirdviewServerState.BIRDVIEW_RANGE = BIRDVIEW_RANGE.get();
        com.Hen3579.Nujomod.Server.BirdviewServerState.BIRDVIEW_RANGE_SQ = BIRDVIEW_RANGE.get() * BIRDVIEW_RANGE.get();

        // 飞行生物 Y 偏移
        com.Hen3579.Nujomod.Server.FlyingMobConfig.Y_MIN_OFFSET = FLYING_Y_MIN_OFFSET.get();
        com.Hen3579.Nujomod.Server.FlyingMobConfig.Y_MAX_OFFSET = FLYING_Y_MAX_OFFSET.get();
    }
}