package com.Hen3579.Nujomod.fogofwar;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;

/**
 * 战争迷雾客户端事件处理器。
 *
 * 职责：
 * 1. 每 10 tick 更新玩家视野 → 揭示新区块 + 自动存档
 * 2. 阻止非明亮区块内的实体渲染
 * 3. 进入/退出世界时加载/保存迷雾数据
 */
@Mod.EventBusSubscriber(modid = "nujobraincraft", value = Dist.CLIENT)
public class FogOfWarEvents {

    private static int tickCounter = 0;
    private static boolean savePathReady = false;

    // ======== Tick 更新 ========

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // 延迟初始化：等世界/服务端完全就绪后再设置存档路径
        if (!savePathReady) {
            initSavePath(mc);
        }

        tickCounter++;
        if (tickCounter % FogOfWarData.UPDATE_INTERVAL_TICKS != 0) return;

        ChunkPos playerChunk = mc.player.chunkPosition();
        FogOfWarData.update(playerChunk);

        // 每 10 tick 自动存档
        FogOfWarData.saveToDisk();
    }

    // ======== 实体隐藏 ========

    /**
     * 阻止非揭示区块内的实体渲染。
     * 优先级设为 HIGHEST 以便在其他 mod 之前拦截。
     * 玩家始终可见（自身 + 其他玩家）。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) return;

        if (!FogOfWarData.isRevealed(entity.blockPosition())) {
            event.setCanceled(true);
        }
    }

    // ======== 世界生命周期 ========

    /** 世界加载时：设置存档路径 + 加载迷雾数据 */
    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        // LevelEvent.Load 在 client 端也触发；只处理一次
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (savePathReady) return;

        initSavePath(mc);
        FogOfWarData.loadFromDisk();
    }

    /** 世界卸载时：保存迷雾数据 */
    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        FogOfWarData.saveToDisk();
        FogOfWarData.reset();
        savePathReady = false;
    }

    // ======== 内部方法 ========

    /** 估算并设置当前世界的存档路径 */
    private static void initSavePath(Minecraft mc) {
        try {
            Path path = resolveSavePath(mc);
            FogOfWarData.setSavePath(path);
            savePathReady = true;
        } catch (Exception e) {
            System.err.println("[FogOfWar] Failed to resolve save path: " + e.getMessage());
        }
    }

    /**
     * 推测存档路径：
     * 单人 → saves/<世界名>/nujobraincraft_fog.nbt
     * 多人 → nujobraincraft_fog_<服务器IP>.nbt（放在游戏根目录）
     */
    private static Path resolveSavePath(Minecraft mc) {
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            String worldName = mc.getSingleplayerServer().getWorldData().getLevelName();
            return mc.gameDirectory.toPath()
                    .resolve("saves")
                    .resolve(sanitize(worldName))
                    .resolve("nujobraincraft_fog.nbt");
        }
        if (mc.getCurrentServer() != null) {
            String ip = mc.getCurrentServer().ip;
            String name = "server_" + (ip != null ? sanitize(ip) : "localhost");
            return mc.gameDirectory.toPath().resolve("nujobraincraft_fog_" + name + ".nbt");
        }
        // 降级：使用默认名
        return mc.gameDirectory.toPath().resolve("nujobraincraft_fog_unknown.nbt");
    }

    /** 清理文件名中的非法字符 */
    private static String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    // ======== 公共查询接口 ========

    /** 获取指定方块位置的迷雾亮度倍率 [0, 1]。供 ModelBlockRendererMixin 使用。 */
    public static float getPosBrightness(int x, int y, int z) {
        return FogOfWarData.getBrightness(x, y, z);
    }
}
