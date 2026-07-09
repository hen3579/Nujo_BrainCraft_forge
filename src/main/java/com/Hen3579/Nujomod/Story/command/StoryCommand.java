package com.Hen3579.Nujomod.Story.command;

import com.Hen3579.Nujomod.Story.*;
import com.Hen3579.Nujomod.Story.model.QuestDef;
import com.Hen3579.Nujomod.Story.network.StoryNetwork;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /story 命令系统 — 管理和调试剧情进度。
 * <p>
 * 子命令：
 * <ul>
 *   <li>/story info [player] — 查看剧情进度</li>
 *   <li>/story setstage &lt;stage_id&gt; [player] — 设置当前阶段</li>
 *   <li>/story reset [player] — 重置所有进度</li>
 *   <li>/story reload — 重新加载剧情 JSON</li>
 *   <li>/story trigger &lt;quest_id&gt; [player] — 手动触发任务</li>
 *   <li>/story mark add &lt;mark_id&gt; [stars] [player] — 添加标记</li>
 *   <li>/story mark remove &lt;mark_id&gt; [player] — 移除标记</li>
 *   <li>/story character &lt;actor|actress&gt; [player] — 设置角色</li>
 * </ul>
 */
public class StoryCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("story")
                .requires(source -> source.hasPermission(2)) // 需要 OP 权限

                // info
                .then(Commands.literal("info")
                        .executes(ctx -> showInfo(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> showInfo(ctx, EntityArgument.getPlayer(ctx, "player")))))

                // setstage
                .then(Commands.literal("setstage")
                        .then(Commands.argument("stage_id", StringArgumentType.string())
                                .executes(ctx -> setStage(ctx, ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "stage_id")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> setStage(ctx, EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "stage_id"))))))

                // reset
                .then(Commands.literal("reset")
                        .executes(ctx -> reset(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reset(ctx, EntityArgument.getPlayer(ctx, "player")))))

                // reload
                .then(Commands.literal("reload")
                        .executes(StoryCommand::reload))

                // trigger
                .then(Commands.literal("trigger")
                        .then(Commands.argument("quest_id", StringArgumentType.string())
                                .executes(ctx -> triggerQuest(ctx, ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "quest_id")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> triggerQuest(ctx, EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "quest_id"))))))

                // mark
                .then(Commands.literal("mark")
                        .then(Commands.literal("add")
                                .then(Commands.argument("mark_id", StringArgumentType.string())
                                        .executes(ctx -> addMark(ctx, ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "mark_id"), 1))
                                        .then(Commands.argument("stars", IntegerArgumentType.integer(1, 10))
                                                .executes(ctx -> addMark(ctx, ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "mark_id"),
                                                        IntegerArgumentType.getInteger(ctx, "stars")))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> addMark(ctx, EntityArgument.getPlayer(ctx, "player"),
                                                                StringArgumentType.getString(ctx, "mark_id"),
                                                                IntegerArgumentType.getInteger(ctx, "stars")))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("mark_id", StringArgumentType.string())
                                        .executes(ctx -> removeMark(ctx, ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "mark_id")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> removeMark(ctx, EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "mark_id")))))))

                // character
                .then(Commands.literal("character")
                        .then(Commands.argument("character", StringArgumentType.string())
                                .executes(ctx -> setCharacter(ctx, ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "character")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> setCharacter(ctx, EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "character"))))))
        );
    }

    private static int showInfo(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        StoryCapability cap = StoryProgress.getOrNull(player);
        if (cap == null) {
            ctx.getSource().sendFailure(Component.literal("无法获取剧情进度数据"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6=== 剧情进度: " + player.getName().getString() + " ===\n" +
                "§e当前阶段: §f" + cap.getCurrentStage() + "\n" +
                "§e角色: §f" + cap.getCharacter() + "\n" +
                "§e标记: §f" + (cap.getMarks().isEmpty() ? "无" : cap.getMarks().toString()) + "\n" +
                "§e已完成任务: §f" + (cap.getCompletedQuests().isEmpty() ? "无" : cap.getCompletedQuests().toString()) + "\n" +
                "§e标志位: §f" + (cap.getFlags().isEmpty() ? "无" : cap.getFlags().toString())
        ), false);
        return 1;
    }

    private static int setStage(CommandContext<CommandSourceStack> ctx, ServerPlayer player, String stageId) {
        if (StoryManager.INSTANCE.getStage(stageId) == null) {
            ctx.getSource().sendFailure(Component.literal("阶段不存在: " + stageId));
            return 0;
        }

        StoryCapability cap = StoryProgress.getOrNull(player);
        if (cap == null) return 0;

        cap.setCurrentStage(stageId);
        StoryNetwork.syncCapabilityToClient(player);

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已设置 " + player.getName().getString() + " 的阶段为: " + stageId), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        StoryCapability cap = StoryProgress.getOrNull(player);
        if (cap == null) return 0;

        cap.reset();
        cap.setCurrentStage("prologue");
        StoryNetwork.syncCapabilityToClient(player);

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已重置 " + player.getName().getString() + " 的剧情进度"), true);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        // StoryLoader 已注册为 reload listener，执行 /reload 会自动触发
        // 这里提供快捷入口
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a请使用 §e/reload §a命令重新加载所有数据包（包括剧情数据）"), false);
        return 1;
    }

    private static int triggerQuest(CommandContext<CommandSourceStack> ctx, ServerPlayer player, String questId) {
        QuestDef quest = StoryManager.INSTANCE.getQuest(questId);
        if (quest == null) {
            ctx.getSource().sendFailure(Component.literal("任务不存在: " + questId));
            return 0;
        }

        StoryEventListener.tryTriggerQuest(quest, player);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已触发任务: " + questId + " (玩家: " + player.getName().getString() + ")"), true);
        return 1;
    }

    private static int addMark(CommandContext<CommandSourceStack> ctx, ServerPlayer player, String markId, int stars) {
        StoryCapability cap = StoryProgress.getOrNull(player);
        if (cap == null) return 0;

        cap.addMark(markId, stars);
        StoryNetwork.syncCapabilityToClient(player);

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已添加标记 " + markId + " (" + stars + "星) 给 " + player.getName().getString()), true);
        return 1;
    }

    private static int removeMark(CommandContext<CommandSourceStack> ctx, ServerPlayer player, String markId) {
        StoryCapability cap = StoryProgress.getOrNull(player);
        if (cap == null) return 0;

        cap.removeMark(markId);
        StoryNetwork.syncCapabilityToClient(player);

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已移除标记 " + markId + " 从 " + player.getName().getString()), true);
        return 1;
    }

    private static int setCharacter(CommandContext<CommandSourceStack> ctx, ServerPlayer player, String character) {
        StoryCapability cap = StoryProgress.getOrNull(player);
        if (cap == null) return 0;

        cap.setCharacter(character);
        StoryNetwork.syncCapabilityToClient(player);

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已设置 " + player.getName().getString() + " 的角色为: " + character), true);
        return 1;
    }
}
