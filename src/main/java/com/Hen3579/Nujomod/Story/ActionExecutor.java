package com.Hen3579.Nujomod.Story;

import com.Hen3579.Nujomod.Story.model.ActionDef;
import com.Hen3579.Nujomod.Story.model.DialogueDef;
import com.Hen3579.Nujomod.Story.model.NpcDef;
import com.Hen3579.Nujomod.Story.model.StageDef;
import com.Hen3579.Nujomod.Story.network.StoryNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.util.FakePlayer;
import org.slf4j.Logger;

import java.util.List;

/**
 * 动作执行器 — 在服务端执行 ActionDef 描述的动作。
 * <p>
 * 支持所有 ActionDef.type 类型：
 * teleport, spawn_npc, dialogue, give_item, change_stage,
 * add_mark, remove_mark, message, play_sound, delay,
 * set_flag, clear_flag, run_function
 */
public class ActionExecutor {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 执行单个动作
     */
    public static void execute(ActionDef action, ServerPlayer player) {
        if (action == null || action.type == null) return;

        switch (action.type) {
            case "teleport" -> executeTeleport(action, player);
            case "spawn_npc" -> executeSpawnNpc(action, player);
            case "dialogue" -> executeDialogue(action, player);
            case "give_item" -> executeGiveItem(action, player);
            case "change_stage" -> executeChangeStage(action, player);
            case "add_mark" -> executeAddMark(action, player);
            case "remove_mark" -> executeRemoveMark(action, player);
            case "message" -> executeMessage(action, player);
            case "play_sound" -> executePlaySound(action, player);
            case "delay" -> executeDelay(action, player);
            case "set_flag" -> executeSetFlag(action, player);
            case "clear_flag" -> executeClearFlag(action, player);
            case "run_function" -> executeRunFunction(action, player);
            default -> LOGGER.warn("[ActionExecutor] 未知动作类型: {}", action.type);
        }
    }

    /**
     * 执行动作列表
     */
    public static void executeAll(List<ActionDef> actions, ServerPlayer player) {
        if (actions == null || actions.isEmpty()) return;
        for (ActionDef action : actions) {
            execute(action, player);
        }
    }

    // ===== 具体动作实现 =====

    private static void executeTeleport(ActionDef action, ServerPlayer player) {
        if (action.dimension == null) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ServerLevel targetLevel = server.getLevel(
                net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        new ResourceLocation(action.dimension)
                )
        );
        if (targetLevel == null) {
            LOGGER.warn("[ActionExecutor] 维度不存在: {}", action.dimension);
            return;
        }

        player.teleportTo(targetLevel, action.x, action.y, action.z,
                java.util.Set.of(), player.getYRot(), player.getXRot());
        LOGGER.info("[ActionExecutor] 传送玩家 {} 到 {} [{}, {}, {}]",
                player.getName().getString(), action.dimension, action.x, action.y, action.z);
    }

    private static void executeSpawnNpc(ActionDef action, ServerPlayer player) {
        NpcDef npcDef = StoryManager.INSTANCE.getNpc(action.npc);
        if (npcDef == null) {
            LOGGER.warn("[ActionExecutor] NPC 定义不存在: {}", action.npc);
            return;
        }

        ResourceLocation entityRL = new ResourceLocation(npcDef.entity_type);
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(entityRL);
        if (entityType == null || BuiltInRegistries.ENTITY_TYPE.getKey(entityType) == null) {
            LOGGER.warn("[ActionExecutor] 实体类型不存在: {}", npcDef.entity_type);
            return;
        }

        ServerLevel level = player.serverLevel();
        double spawnX = action.x != 0 ? action.x : player.getX();
        double spawnY = action.y != 0 ? action.y : player.getY();
        double spawnZ = action.z != 0 ? action.z : player.getZ();

        // 生成实体
        net.minecraft.world.entity.Entity spawned = entityType.spawn(level,
                new BlockPos((int) spawnX, (int) spawnY, (int) spawnZ),
                MobSpawnType.TRIGGERED);

        // 设置自定义名称
        if (spawned != null && npcDef.display_name != null) {
            spawned.setCustomName(Component.translatable(npcDef.display_name));
            spawned.setCustomNameVisible(true);
            // 在实体上存储 npc_id，供交互事件使用
            spawned.getPersistentData().putString(StoryEventListener.NPC_ID_KEY, npcDef.id);
        }

        LOGGER.info("[ActionExecutor] 生成 NPC {} 于 [{}, {}, {}]",
                action.npc, spawnX, spawnY, spawnZ);
    }

    private static void executeDialogue(ActionDef action, ServerPlayer player) {
        DialogueDef dialogue = StoryManager.INSTANCE.getDialogue(action.id);
        if (dialogue == null) {
            LOGGER.warn("[ActionExecutor] 对话定义不存在: {}", action.id);
            return;
        }
        // 通过网络包通知客户端打开对话 GUI
        StoryNetwork.sendDialogueToClient(player, dialogue);
        LOGGER.info("[ActionExecutor] 打开对话: {} (玩家: {})", action.id, player.getName().getString());
    }

    private static void executeGiveItem(ActionDef action, ServerPlayer player) {
        if (action.item == null) return;
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(action.item));
        if (item == null) {
            LOGGER.warn("[ActionExecutor] 物品不存在: {}", action.item);
            return;
        }
        ItemStack stack = new ItemStack(item, action.count);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        LOGGER.info("[ActionExecutor] 给予物品 {} x{} (玩家: {})",
                action.item, action.count, player.getName().getString());
    }

    private static void executeChangeStage(ActionDef action, ServerPlayer player) {
        StageDef newStage = StoryManager.INSTANCE.getStage(action.id);
        if (newStage == null) {
            LOGGER.warn("[ActionExecutor] 阶段定义不存在: {}", action.id);
            return;
        }

        StoryCapability cap = StoryProgress.getOrNull(player);
        if (cap == null) return;

        // 执行旧阶段的 on_exit
        StageDef oldStage = StoryManager.INSTANCE.getStage(cap.getCurrentStage());
        if (oldStage != null && oldStage.on_exit != null) {
            executeAll(oldStage.on_exit, player);
        }

        // 切换阶段
        cap.setCurrentStage(action.id);

        // 应用初始标记
        if (newStage.initial_marks != null) {
            for (var entry : newStage.initial_marks.entrySet()) {
                cap.addMark(entry.getKey(), entry.getValue());
            }
        }

        // 执行新阶段的 on_enter
        if (newStage.on_enter != null) {
            executeAll(newStage.on_enter, player);
        }

        // 同步到客户端
        StoryNetwork.syncCapabilityToClient(player);

        LOGGER.info("[ActionExecutor] 玩家 {} 阶段切换: {} → {}",
                player.getName().getString(), oldStage != null ? oldStage.id : "none", action.id);
    }

    private static void executeAddMark(ActionDef action, ServerPlayer player) {
        StoryCapability cap = StoryProgress.getOrNull(player);
        if (cap == null) return;
        cap.addMark(action.mark, action.stars);
        StoryNetwork.syncCapabilityToClient(player);
        LOGGER.info("[ActionExecutor] 玩家 {} 获得标记 {} ({}星)",
                player.getName().getString(), action.mark, action.stars);
    }

    private static void executeRemoveMark(ActionDef action, ServerPlayer player) {
        StoryCapability cap = StoryProgress.getOrNull(player);
        if (cap == null) return;
        cap.removeMark(action.mark);
        StoryNetwork.syncCapabilityToClient(player);
        LOGGER.info("[ActionExecutor] 玩家 {} 移除标记 {}",
                player.getName().getString(), action.mark);
    }

    private static void executeMessage(ActionDef action, ServerPlayer player) {
        Component message;
        if (action.text != null && action.text.contains(".")) {
            // 看起来像 lang key（包含 .），用翻译
            message = Component.translatable(action.text);
        } else {
            message = Component.literal(action.text != null ? action.text : "");
        }

        if (action.color != null) {
            try {
                ChatFormatting fmt = ChatFormatting.getByName(action.color);
                if (fmt != null) {
                    message = message.copy().withStyle(fmt);
                }
            } catch (Exception ignored) {}
        }

        player.sendSystemMessage(message);
    }

    private static void executePlaySound(ActionDef action, ServerPlayer player) {
        if (action.sound == null) return;
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(action.sound));
        if (sound == null) {
            LOGGER.warn("[ActionExecutor] 音效不存在: {}", action.sound);
            return;
        }
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void executeDelay(ActionDef action, ServerPlayer player) {
        if (action.then == null || action.then.isEmpty()) return;
        // 通过调度器延迟执行
        MinecraftServer server = player.getServer();
        if (server == null) return;

        server.tell(new net.minecraft.server.TickTask(
                server.getTickCount() + action.ticks,
                () -> executeAll(action.then, player)
        ));
    }

    private static void executeSetFlag(ActionDef action, ServerPlayer player) {
        StoryCapability cap = StoryProgress.getOrNull(player);
        if (cap == null) return;
        cap.setFlag(action.flag, true);
        StoryNetwork.syncCapabilityToClient(player);
    }

    private static void executeClearFlag(ActionDef action, ServerPlayer player) {
        StoryCapability cap = StoryProgress.getOrNull(player);
        if (cap == null) return;
        cap.clearFlag(action.flag);
        StoryNetwork.syncCapabilityToClient(player);
    }

    private static void executeRunFunction(ActionDef action, ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        CommandSourceStack source = server.createCommandSourceStack()
                .withEntity(player)
                .withPermission(2); // OP 权限执行函数

        try {
            // 执行 /function <function_path>
            server.getCommands().performPrefixedCommand(
                    source, "/function " + action.function
            );
            LOGGER.info("[ActionExecutor] 执行函数: {}", action.function);
        } catch (Exception e) {
            LOGGER.error("[ActionExecutor] 函数执行失败: {}", action.function, e);
        }
    }
}
