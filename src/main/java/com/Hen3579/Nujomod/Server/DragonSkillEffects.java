package com.Hen3579.Nujomod.Server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.core.particles.ParticleTypes;

import java.util.List;

/**
 * 末影龙视觉特效与技能效果系统
 *
 * 所有方法在服务器端执行，使用 sendParticles / playSound 广播到客户端。
 * 仅在鸟瞰模式下调用（由 DragonAIController 控制）。
 *
 * 特效分类：
 * - 阶段转换：爆炸+闪电+咆哮
 * - 僵直：愤怒粒子环绕
 * - 冲锋：火焰尾迹
 * - 龙息：扇形粒子+酸雾区域
 * - 终极技能：环形AOE / 流星雨 / 冲击波
 * - 水晶摧毁：增强爆炸
 */
public class DragonSkillEffects {

    // ===== 阶段转换特效 =====

    /**
     * 阶段转换视觉效果：爆炸粒子 + 闪电 + 咆哮音效
     */
    public static void playPhaseTransition(EnderDragon dragon, ServerLevel level) {
        Vec3 pos = dragon.position();

        // 大范围爆炸粒子
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                pos.x, pos.y + 2, pos.z, 5,
                0, 0, 0, 0.1);

        // 末影粒子爆散
        level.sendParticles(ParticleTypes.DRAGON_BREATH,
                pos.x, pos.y + 2, pos.z, 30,
                2.0, 1.0, 2.0, 0.1);

        // 反向传送门粒子（紫色漩涡感）
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                pos.x, pos.y + 2, pos.z, 20,
                1.5, 0.8, 1.5, 0.2);

        // 闪电效果（在龙头顶劈一道闪电）
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                pos.x, pos.y + 3, pos.z, 15,
                0.5, 1.0, 0.5, 0.3);

        // 咆哮音效（全维度可听）
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.0f, 0.8f);

        // 爆炸音效
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5f, 0.6f);
    }

    // ===== 僵直特效 =====

    /**
     * 僵直视觉效果：愤怒村民粒子环绕龙 + 烟雾
     * 每 10 tick 调用一次（避免粒子过多）
     */
    public static void playStunEffect(EnderDragon dragon, ServerLevel level) {
        Vec3 pos = dragon.position();

        // 愤怒粒子环绕龙头顶
        double angle = (dragon.tickCount * 0.3) % (Math.PI * 2);
        double px = pos.x + Math.cos(angle) * 3.0;
        double py = pos.y + 4.0;
        double pz = pos.z + Math.sin(angle) * 3.0;

        level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                px, py, pz, 3,
                0.3, 0.3, 0.3, 0.0);

        // 第二组粒子（反方向）
        double angle2 = angle + Math.PI;
        px = pos.x + Math.cos(angle2) * 3.0;
        pz = pos.z + Math.sin(angle2) * 3.0;

        level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                px, py, pz, 3,
                0.3, 0.3, 0.3, 0.0);

        // 烟雾从龙身上冒出
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                pos.x, pos.y + 3, pos.z, 4,
                1.0, 0.5, 1.0, 0.05);
    }

    // ===== 冲锋尾迹 =====

    /**
     * 冲锋尾迹特效：火焰+烟雾粒子从龙身后散出
     * 每 tick 调用
     */
    public static void playChargeTrail(EnderDragon dragon, ServerLevel level) {
        Vec3 pos = dragon.position();
        Vec3 motion = dragon.getDeltaMovement();

        // 在龙后方生成粒子
        double trailX = pos.x - motion.x * 2.0;
        double trailZ = pos.z - motion.z * 2.0;

        // 火焰
        level.sendParticles(ParticleTypes.FLAME,
                trailX, pos.y + 1.5, trailZ, 5,
                0.5, 0.3, 0.5, 0.02);

        // 大烟雾
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                trailX, pos.y + 1.0, trailZ, 3,
                0.4, 0.2, 0.4, 0.01);
    }

    // ===== 扇形龙息 =====

    /**
     * 扇形龙息特效 + 酸雾区域伤害
     *
     * 在龙前方扇形区域内：
     * 1. 生成龙息粒子（紫色喷射效果）
     * 2. 创建 AreaEffectCloud（酸雾区域，持续伤害+减速）
     * 3. 对扇形范围内玩家造成伤害
     *
     * @param dragon 龙
     * @param level 服务器世界
     * @param target 目标玩家
     * @param tick 当前技能 tick（用于控制粒子密度和伤害频率）
     */
    public static void playFanBreath(EnderDragon dragon, ServerLevel level,
                                      ServerPlayer target, int tick) {
        Vec3 dragonPos = dragon.position();

        // 龙的朝向（Yaw → 弧度）
        float yawRad = (float) Math.toRadians(dragon.getYRot());

        // 扇形范围参数
        double breathRange = 10.0;        // 射程 10 格
        double fanHalfAngle = Math.PI / 4; // 45° 半角 = 90° 扇形

        // 生成扇形粒子（3 排扇形喷射）
        for (int row = 0; row < 3; row++) {
            double rowDist = 2.0 + row * 3.0; // 第 1 排 2 格，第 2 排 5 格，第 3 排 8 格
            int particleCount = 6 + row * 4;   // 越远粒子越多

            for (int i = 0; i < particleCount; i++) {
                double angleOffset = (i / (double) (particleCount - 1) - 0.5) * 2 * fanHalfAngle;
                double angle = yawRad + angleOffset;

                double px = dragonPos.x + Math.sin(angle) * rowDist;
                double py = dragonPos.y + 1.5 + level.getRandom().nextDouble() * 0.5;
                double pz = dragonPos.z + Math.cos(angle) * rowDist;

                // 龙息粒子
                level.sendParticles(ParticleTypes.DRAGON_BREATH,
                        px, py, pz, 1,
                        0.1, 0.05, 0.1, 0.02);
            }
        }

        // 龙息源头粒子（龙嘴部）
        level.sendParticles(ParticleTypes.DRAGON_BREATH,
                dragonPos.x, dragonPos.y + 2.5, dragonPos.z, 8,
                0.8, 0.2, 0.8, 0.1);

        // 每 20 tick（1 秒）创建一个酸雾区域
        if (tick % 20 == 0) {
            spawnBreathCloud(dragon, level, yawRad, breathRange, fanHalfAngle);
        }

        // 每 10 tick 对扇形内玩家造成伤害
        if (tick % 10 == 0 && target != null) {
            double dx = target.getX() - dragonPos.x;
            double dz = target.getZ() - dragonPos.z;
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (dist < breathRange) {
                // 检查是否在扇形角度内
                double angleToTarget = Math.atan2(dx, dz);
                double angleDiff = normalizeAngle(angleToTarget - yawRad);

                if (Math.abs(angleDiff) < fanHalfAngle) {
                    target.hurt(dragon.damageSources().mobAttack(dragon), 6.0f);
                    // 减速效果
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                }
            }
        }

        // 龙息音效（低频播放）
        if (tick % 15 == 0) {
            level.playSound(null, dragonPos.x, dragonPos.y, dragonPos.z,
                    SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE, 0.8f, 0.5f);
        }
    }

    /** 在龙前方创建酸雾区域（AreaEffectCloud） */
    private static void spawnBreathCloud(EnderDragon dragon, ServerLevel level,
                                          float yawRad, double range, double fanHalfAngle) {
        // 在扇形中心位置生成酸雾
        double cloudX = dragon.getX() + Math.sin(yawRad) * range * 0.6;
        double cloudZ = dragon.getZ() + Math.cos(yawRad) * range * 0.6;
        double cloudY = dragon.getY();

        AreaEffectCloud cloud = new AreaEffectCloud(level, cloudX, cloudY, cloudZ);
        cloud.setRadius(3.0f);
        cloud.setRadiusOnUse(-0.5f);
        cloud.setWaitTime(10);
        cloud.setDuration(100); // 5 秒
        cloud.setRadiusPerTick(-0.01f);

        // 龙息伤害药水效果
        cloud.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 0));
        cloud.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));

        // 设置云的粒子类型
        cloud.setParticle(ParticleTypes.DRAGON_BREATH);

        level.addFreshEntity(cloud);
    }

    /** 角度归一化到 [-PI, PI] */
    private static double normalizeAngle(double angle) {
        while (angle > Math.PI) angle -= Math.PI * 2;
        while (angle < -Math.PI) angle += Math.PI * 2;
        return angle;
    }

    // ===== 终极技能：环形 AOE =====

    /**
     * 环形 AOE：扩散粒子环 + 范围伤害
     *
     * @param dragon 龙
     * @param level 服务器世界
     * @param tick 技能 tick
     * @param radius 当前扩散半径
     */
    public static void playRingAOE(EnderDragon dragon, ServerLevel level, int tick, double radius) {
        Vec3 pos = dragon.position();

        // 扩散粒子环（在 Y=64 平面上画一个圈）
        int particleCount = 32;
        for (int i = 0; i < particleCount; i++) {
            double angle = (i / (double) particleCount) * Math.PI * 2;
            double px = pos.x + Math.cos(angle) * radius;
            double py = pos.y;
            double pz = pos.z + Math.sin(angle) * radius;

            // 龙息粒子
            level.sendParticles(ParticleTypes.DRAGON_BREATH,
                    px, py, pz, 1,
                    0, 0.2, 0, 0.02);

            // 火焰粒子（外环）
            level.sendParticles(ParticleTypes.FLAME,
                    px, py + 0.5, pz, 1,
                    0, 0.05, 0, 0.01);
        }

        // 中心爆炸粒子
        if (tick == 10) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    pos.x, pos.y + 1, pos.z, 1,
                    0, 0, 0, 0);

            level.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5f, 0.7f);
        }
    }

    /**
     * 执行环形 AOE 伤害判定
     */
    public static void applyRingAOEDamage(EnderDragon dragon, ServerLevel level, double radius) {
        AABB aoeBox = AABB.ofSize(dragon.position(), radius * 2, 10, radius * 2);
        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, aoeBox);

        for (ServerPlayer p : players) {
            double distSq = p.distanceToSqr(dragon.getX(), dragon.getY(), dragon.getZ());
            if (distSq < radius * radius) {
                p.hurt(dragon.damageSources().mobAttack(dragon), 12.0f);
                // 击退
                double dx = p.getX() - dragon.getX();
                double dz = p.getZ() - dragon.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 0.1) {
                    p.push(dx / dist * 1.5, 0.5, dz / dist * 1.5);
                }
            }
        }
    }

    // ===== 终极技能：流星雨 =====

    /**
     * 流星雨：在祭坛周围天降火球
     *
     * @param dragon 龙
     * @param level 服务器世界
     * @param tick 技能 tick
     */
    public static void playMeteorShower(EnderDragon dragon, ServerLevel level, int tick) {
        Vec3 pos = dragon.position();

        // 每 10 tick 降下 2 颗火球，持续 100 tick（共 ~20 颗）
        if (tick % 10 == 0 && tick <= 100) {
            for (int i = 0; i < 2; i++) {
                // 随机位置（半径 10~25 格）
                double angle = level.getRandom().nextDouble() * Math.PI * 2;
                double dist = 10.0 + level.getRandom().nextDouble() * 15.0;
                double targetX = pos.x + Math.cos(angle) * dist;
                double targetZ = pos.z + Math.sin(angle) * dist;

                // 火球从 Y=80 降下
                double startX = targetX + (level.getRandom().nextDouble() - 0.5) * 3;
                double startZ = targetZ + (level.getRandom().nextDouble() - 0.5) * 3;

                DragonFireball fireball = new DragonFireball(level, dragon,
                        0, -1.5, 0); // 向下飞
                fireball.setPos(startX, 80.0, startZ);
                level.addFreshEntity(fireball);

                // 流星尾迹粒子
                level.sendParticles(ParticleTypes.FLAME,
                        startX, 78.0, startZ, 8,
                        0.3, 0, 0.3, 0.05);

                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        startX, 75.0, startZ, 4,
                        0.2, 0, 0.2, 0.02);
            }
        }

        // 龙抬头蓄力粒子（流星雨期间）
        if (tick <= 100 && tick % 5 == 0) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    pos.x, pos.y + 4, pos.z, 5,
                    0.5, 0.5, 0.5, 0.1);
        }

        // 开始音效
        if (tick == 0) {
            level.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE, 2.0f, 0.4f);
        }
    }

    // ===== 终极技能：冲击波 =====

    /**
     * 冲击波：扩散冲击粒子 + 击退所有玩家
     *
     * @param dragon 龙
     * @param level 服务器世界
     * @param tick 技能 tick
     * @param radius 当前冲击波半径
     */
    public static void playShockwave(EnderDragon dragon, ServerLevel level, int tick, double radius) {
        Vec3 pos = dragon.position();

        // 扩散冲击波粒子（地面冲击效果）
        int particleCount = 24;
        for (int i = 0; i < particleCount; i++) {
            double angle = (i / (double) particleCount) * Math.PI * 2;
            double px = pos.x + Math.cos(angle) * radius;
            double py = pos.y;
            double pz = pos.z + Math.sin(angle) * radius;

            // 冲击波粒子（向上喷射）
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    px, py, pz, 1,
                    0, 0.3, 0, 0.05);

            // 火花
            level.sendParticles(ParticleTypes.CRIT,
                    px, py + 0.5, pz, 1,
                    0, 0.1, 0, 0.1);
        }

        // 起始音效
        if (tick == 0) {
            level.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0f, 0.5f);

            level.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.5f, 0.6f);
        }
    }

    /**
     * 执行冲击波伤害 + 击退判定
     */
    public static void applyShockwaveDamage(EnderDragon dragon, ServerLevel level, double radius) {
        AABB box = AABB.ofSize(dragon.position(), radius * 2, 10, radius * 2);
        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, box);

        for (ServerPlayer p : players) {
            double dx = p.getX() - dragon.getX();
            double dz = p.getZ() - dragon.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (dist < radius && dist > 0.1) {
                p.hurt(dragon.damageSources().mobAttack(dragon), 8.0f);

                // 强力击退
                double knockback = 2.0;
                p.push(dx / dist * knockback, 0.8, dz / dist * knockback);
            }
        }
    }

    // ===== 水晶摧毁增强特效 =====

    /**
     * 水晶摧毁增强特效：大范围龙息粒子 + 爆炸
     *
     * @param crystal 被摧毁的水晶
     * @param level 服务器世界
     */
    public static void playCrystalDestroy(EndCrystal crystal, ServerLevel level) {
        Vec3 pos = crystal.position();

        // 龙息粒子爆发
        level.sendParticles(ParticleTypes.DRAGON_BREATH,
                pos.x, pos.y, pos.z, 40,
                2.0, 1.0, 2.0, 0.1);

        // 爆炸粒子
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                pos.x, pos.y, pos.z, 3,
                0.5, 0.3, 0.5, 0.1);

        // 末影粒子（紫色螺旋上升）
        for (int i = 0; i < 5; i++) {
            double angle = (i / 5.0) * Math.PI * 2;
            double r = 1.5;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    pos.x + Math.cos(angle) * r, pos.y + i * 0.5, pos.z + Math.sin(angle) * r,
                    5, 0.1, 0.2, 0.1, 0.05);
        }

        // 爆炸音效
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.0f, 0.8f);

        // 末影龙死亡音效变调（用于水晶被摧毁的紧张感）
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 0.8f, 1.2f);
    }

    // ===== 辅助：环绕龙身粒子 =====

    /**
     * 在龙身周围生成环绕粒子（用于冲锋/狂暴状态下的能量光环）
     */
    public static void playEnergyAura(EnderDragon dragon, ServerLevel level) {
        Vec3 pos = dragon.position();
        double t = dragon.tickCount * 0.2;

        for (int i = 0; i < 4; i++) {
            double angle = t + i * Math.PI / 2;
            double px = pos.x + Math.cos(angle) * 4.0;
            double py = pos.y + 1.0 + Math.sin(t * 2 + i) * 0.5;
            double pz = pos.z + Math.sin(angle) * 4.0;

            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    px, py, pz, 1,
                    0, 0.1, 0, 0.02);
        }
    }

    /**
     * 回血特效：绿色粒子 + 心形粒子环绕龙
     */
    public static void playHealEffect(EnderDragon dragon, ServerLevel level) {
        Vec3 pos = dragon.position();

        // 治疗粒子（从水晶方向飞向龙）
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                pos.x, pos.y + 2, pos.z, 3,
                1.0, 0.5, 1.0, 0.05);

        // 治疗魔法粒子
        level.sendParticles(ParticleTypes.COMPOSTER,
                pos.x, pos.y + 1.5, pos.z, 4,
                1.0, 0.5, 1.0, 0.03);

        // 心形粒子
        level.sendParticles(ParticleTypes.HEART,
                pos.x, pos.y + 3, pos.z, 1,
                0.3, 0.2, 0.3, 0.0);
    }
}
