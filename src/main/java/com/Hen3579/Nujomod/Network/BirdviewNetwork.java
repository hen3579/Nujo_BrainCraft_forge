package com.Hen3579.Nujomod.Network;

import com.Hen3579.Nujomod.NujoBraincraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 模组网络通道注册
 *
 * 用于客户端 → 服务器同步鸟瞰模式开关状态，
 * 使服务器端能据此对飞行生物执行 Y 轴约束。
 */
public class BirdviewNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(NujoBraincraft.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    /** 注册所有网络包（在 FMLCommonSetupEvent 中调用） */
    public static void register() {
        INSTANCE.registerMessage(
                packetId++,
                BirdviewStatePacket.class,
                BirdviewStatePacket::encode,
                BirdviewStatePacket::decode,
                BirdviewStatePacket::handle
        );
    }
}
