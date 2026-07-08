package com.Hen3579.Nujomod.Server;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;

import java.util.Set;

/**
 * 飞行生物识别注册表
 *
 * 区分受限飞行生物（会被 Y 轴约束系统钳制）和豁免生物（Boss 级，不受限制）。
 */
public class FlyingMobRegistry {

    /** 受 Y 轴约束的飞行生物类型 */
    private static final Set<EntityType<?>> FLYING_TYPES = Set.of(
            EntityType.BAT,
            EntityType.PHANTOM,
            EntityType.GHAST,
            EntityType.ALLAY,
            EntityType.PARROT,
            EntityType.BEE,
            EntityType.VEX,
            EntityType.BLAZE
    );

    /** 豁免生物（Boss / 特殊生物，不受 Y 轴约束） */
    private static final Set<EntityType<?>> EXEMPT_TYPES = Set.of(
            EntityType.ENDER_DRAGON,
            EntityType.WITHER
    );

    /**
     * 判断实体是否为受约束的飞行生物。
     * 排除 Boss 级生物和被骑乘的生物。
     */
    public static boolean isFlyingMob(Entity entity) {
        if (entity == null) return false;
        if (!(entity instanceof Mob)) return false;

        EntityType<?> type = entity.getType();

        // Boss 豁免
        if (EXEMPT_TYPES.contains(type)) return false;
        // 被骑乘的实体豁免（避免把玩家也弹飞）
        if (entity.isVehicle()) return false;

        // 已知飞行类型
        if (FLYING_TYPES.contains(type)) return true;

        // 兜底：无重力且非水生生物
        Mob mob = (Mob) entity;
        if (mob.isNoGravity()) return true;

        return false;
    }

    /** 是否为豁免生物 */
    public static boolean isExempt(Entity entity) {
        if (entity == null) return false;
        return EXEMPT_TYPES.contains(entity.getType());
    }
}
