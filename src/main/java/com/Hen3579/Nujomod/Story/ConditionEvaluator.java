package com.Hen3579.Nujomod.Story;

import com.Hen3579.Nujomod.Story.model.ConditionDef;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * 条件判断器 — 检查玩家当前状态是否满足 ConditionDef 定义的条件。
 * <p>
 * 支持所有 ConditionDef.type 类型：
 * stage_equals, stage_not, has_mark, not_has_mark,
 * character_is, has_item, has_flag, not_has_flag,
 * and, or, not
 */
public class ConditionEvaluator {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 检查单个条件是否满足
     */
    public static boolean evaluate(ConditionDef condition, Player player) {
        if (condition == null || condition.type == null) {
            return true; // 无条件 = 总是满足
        }

        StoryCapability cap = StoryProgress.getOrNull(player);
        if (cap == null) {
            return false;
        }

        return switch (condition.type) {
            case "stage_equals" -> cap.getCurrentStage().equals(condition.stage);
            case "stage_not" -> !cap.getCurrentStage().equals(condition.stage);
            case "has_mark" -> cap.getMarkStars(condition.mark) >= condition.min_stars;
            case "not_has_mark" -> !cap.hasMark(condition.mark);
            case "character_is" -> cap.getCharacter().equals(condition.character);
            case "has_item" -> checkHasItem(player, condition.item, condition.count);
            case "has_flag" -> cap.hasFlag(condition.flag);
            case "not_has_flag" -> !cap.hasFlag(condition.flag);
            case "and" -> condition.conditions != null &&
                    condition.conditions.stream().allMatch(c -> evaluate(c, player));
            case "or" -> condition.conditions != null &&
                    condition.conditions.stream().anyMatch(c -> evaluate(c, player));
            case "not" -> condition.condition != null && !evaluate(condition.condition, player);
            default -> {
                LOGGER.warn("[ConditionEvaluator] 未知条件类型: {}", condition.type);
                yield false;
            }
        };
    }

    /**
     * 检查列表中的所有条件是否全部满足
     */
    public static boolean evaluateAll(java.util.List<ConditionDef> conditions, Player player) {
        if (conditions == null || conditions.isEmpty()) return true;
        for (ConditionDef cond : conditions) {
            if (!evaluate(cond, player)) return false;
        }
        return true;
    }

    /**
     * 检查玩家背包中是否有足够数量的指定物品
     */
    private static boolean checkHasItem(Player player, String itemId, int count) {
        if (itemId == null) return false;
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
        if (item == null) return false;

        int found = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == item) {
                found += stack.getCount();
                if (found >= count) return true;
            }
        }
        return false;
    }
}
