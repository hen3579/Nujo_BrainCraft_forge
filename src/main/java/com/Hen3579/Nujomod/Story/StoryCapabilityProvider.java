package com.Hen3579.Nujomod.Story;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;

/**
 * Capability Provider — 将 StoryCapability 挂载到玩家实体上。
 * <p>
 * 负责实例化、序列化/反序列化（存档持久化）、以及 LazyOptional 懒加载。
 */
public class StoryCapabilityProvider implements ICapabilitySerializable<CompoundTag> {

    public static final Capability<StoryCapability> STORY_CAP =
            CapabilityManager.get(new CapabilityToken<>() {});

    private StoryCapability capability = null;
    private final LazyOptional<StoryCapability> lazyOptional = LazyOptional.of(this::getOrCreate);

    @Nonnull
    private StoryCapability getOrCreate() {
        if (capability == null) {
            capability = new StoryCapability();
        }
        return capability;
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == STORY_CAP) {
            return lazyOptional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return getOrCreate().serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        getOrCreate().deserializeNBT(nbt);
    }

    /**
     * 使 LazyOptional 失效（玩家退出时调用，防止内存泄漏）
     */
    public void invalidate() {
        lazyOptional.invalidate();
    }
}
