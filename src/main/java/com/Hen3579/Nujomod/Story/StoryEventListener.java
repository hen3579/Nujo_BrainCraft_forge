package com.Hen3579.Nujomod.Story;

import com.Hen3579.Nujomod.Story.model.QuestDef;
import com.Hen3579.Nujomod.Story.model.TriggerDef;
import com.Hen3579.Nujomod.Story.network.StoryNetwork;
import com.Hen3579.Nujomod.NujoBraincraft;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.List;

/**
 * 剧情事件监听器 — 监听游戏事件，匹配 Quest 触发器，执行剧情。
 * <p>
 * 监听的事件类型：
 * <ul>
 *   <li>PlayerChangedDimensionEvent — 进维度</li>
 *   <li>PlayerInteractEvent.EntityInteract — 点 NPC / 交物品</li>
 *   <li>LivingDeathEvent — 杀 BOSS / 杀实体</li>
 *   <li>PlayerLoggedInEvent — 玩家登录（同步数据 + 初始化）</li>
 *   <li>PlayerEvent.StartTracking — 开始追踪实体（同步进度）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = NujoBraincraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StoryEventListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** NPC 实体上存储 npc_id 的 NBT key */
    public static final String NPC_ID_KEY = "StoryNpcId";

    // ===== 进维度 =====

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        String fromDim = event.getFrom().location().toString();
        String toDim = event.getTo().location().toString();

        List<QuestDef> quests = StoryManager.INSTANCE.getQuestsByTriggerType("dimension_change");
        for (QuestDef quest : quests) {
            TriggerDef trigger = quest.trigger;
            boolean matches = true;

            // 检查目标维度
            if (trigger.to != null && !trigger.to.equals(toDim)) {
                matches = false;
            }
            // 检查来源维度（可选）
            if (matches && trigger.from != null && !trigger.from.equals(fromDim)) {
                matches = false;
            }

            if (matches) {
                tryTriggerQuest(quest, player);
            }
        }
    }

    // ===== 点 NPC / 交物品 =====

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getLevel().isClientSide()) return;

        Entity target = event.getTarget();
        if (target == null) return;

        // 检查目标实体是否是剧情 NPC
        CompoundTag persistentData = target.getPersistentData();
        String npcId = persistentData.contains(NPC_ID_KEY) ? persistentData.getString(NPC_ID_KEY) : null;

        if (npcId != null) {
            // 检查 interact_npc 触发器
            List<QuestDef> quests = StoryManager.INSTANCE.getQuestsByTriggerType("interact_npc");
            for (QuestDef quest : quests) {
                if (npcId.equals(quest.trigger.npc)) {
                    tryTriggerQuest(quest, player);
                }
            }

            // 检查 deliver_item 触发器
            List<QuestDef> deliverQuests = StoryManager.INSTANCE.getQuestsByTriggerType("deliver_item");
            for (QuestDef quest : deliverQuests) {
                if (npcId.equals(quest.trigger.npc)) {
                    // 检查玩家手持物品是否匹配
                    var heldItem = player.getMainHandItem();
                    if (!heldItem.isEmpty()) {
                        String itemId = BuiltInRegistriesHelper.getItemId(heldItem.getItem());
                        if (itemId != null && itemId.equals(quest.trigger.item)) {
                            tryTriggerQuest(quest, player);
                        }
                    }
                }
            }
        }
    }

    // ===== 杀 BOSS / 杀实体 =====

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide()) return;

        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player)) return;

        String entityType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()) != null
                ? net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString() : null;

        if (entityType == null) return;

        // kill_entity 触发器
        List<QuestDef> quests = StoryManager.INSTANCE.getQuestsByTriggerType("kill_entity");
        for (QuestDef quest : quests) {
            if (entityType.equals(quest.trigger.entity_type)) {
                tryTriggerQuest(quest, player);
            }
        }

        // kill_boss 触发器（检查 BOSS 标记）
        CompoundTag persistentData = entity.getPersistentData();
        if (persistentData.contains("StoryBossId")) {
            String bossId = persistentData.getString("StoryBossId");
            List<QuestDef> bossQuests = StoryManager.INSTANCE.getQuestsByTriggerType("kill_boss");
            for (QuestDef quest : bossQuests) {
                if (bossId.equals(quest.trigger.boss)) {
                    tryTriggerQuest(quest, player);
                }
            }
        }
    }

    // ===== 玩家登录 =====

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // 同步剧情注册表到客户端
        syncStoryDataToClient(player);

        // 同步玩家进度到客户端
        StoryNetwork.syncCapabilityToClient(player);

        // 如果玩家还没有初始阶段，设置为 "prologue"
        StoryCapability cap = StoryProgress.getOrNull(player);
        if (cap != null && cap.getCurrentStage().equals(StoryCapability.DEFAULT_STAGE)) {
            cap.setCurrentStage("prologue");
            StoryNetwork.syncCapabilityToClient(player);
        }

        // 触发 player_login 自定义事件
        List<QuestDef> loginQuests = StoryManager.INSTANCE.getQuestsByTriggerType("custom");
        for (QuestDef quest : loginQuests) {
            if ("player_login".equals(quest.trigger.custom_id)) {
                tryTriggerQuest(quest, player);
            }
        }
    }

    // ===== 开始追踪（同步进度） =====

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof Player)) return;
        // 同步追踪者的进度
        StoryNetwork.syncCapabilityToClient(player);
    }

    // ===== 玩家退出 =====

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // 清理（无需操作，Capability 自动保存）
    }

    // ===== 核心逻辑 =====

    /**
     * 尝试触发一个 Quest：
     * 1. 检查是否已完成（如果 once=true）
     * 2. 检查所有前置条件
     * 3. 全部满足则执行动作
     */
    public static void tryTriggerQuest(QuestDef quest, ServerPlayer player) {
        StoryCapability cap = StoryProgress.getOrNull(player);
        if (cap == null) return;

        // 检查一次性任务
        if (quest.once && cap.isQuestCompleted(quest.id)) {
            return;
        }

        // 检查前置条件
        if (!ConditionEvaluator.evaluateAll(quest.conditions, player)) {
            return;
        }

        // 标记为已完成
        if (quest.once) {
            cap.markQuestCompleted(quest.id);
        }

        // 执行动作
        ActionExecutor.executeAll(quest.actions, player);

        LOGGER.info("[StoryEventListener] Quest 触发: {} (玩家: {})",
                quest.id, player.getName().getString());
    }

    /**
     * 手动触发自定义事件（供其他代码调用）
     */
    public static void triggerCustom(String customId, ServerPlayer player) {
        List<QuestDef> quests = StoryManager.INSTANCE.getQuestsByTriggerType("custom");
        for (QuestDef quest : quests) {
            if (customId.equals(quest.trigger.custom_id)) {
                tryTriggerQuest(quest, player);
            }
        }
    }

    /**
     * 同步 StoryManager 注册表到客户端
     */
    private static void syncStoryDataToClient(ServerPlayer player) {
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().create();
        String stagesJson = gson.toJson(StoryManager.INSTANCE.getAllStagesSorted());
        String dialoguesJson = gson.toJson(StoryManager.INSTANCE.dialoguesToJsonList());
        String questsJson = gson.toJson(StoryManager.INSTANCE.questsToJsonList());
        String npcsJson = gson.toJson(StoryManager.INSTANCE.npcsToJsonList());

        StoryNetwork.INSTANCE.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new com.Hen3579.Nujomod.Story.network.StoryDataSyncPacket(
                        stagesJson, dialoguesJson, questsJson, npcsJson
                )
        );
    }
}
