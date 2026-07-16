package com.Hen3579.Nujomod.Server;

/**
 * 飞行生物 Y 轴约束配置。
 * 独立于 Mixin 类，避免 Mixin 限制（不允许非私有静态字段/方法）。
 * 由 Config.java 在加载时赋值，由 FlyingMobMoveMixin 读取。
 */
public class FlyingMobConfig {
    public static int Y_MIN_OFFSET = 1;
    public static int Y_MAX_OFFSET = 5;
}