package com.riichimahjong.yakugenerator.neoforge;

import com.riichimahjong.yakugenerator.YakuGeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public final class EnergyPushPlatformImpl {
    private EnergyPushPlatformImpl() {}

    public static int tickPush(YakuGeneratorBlockEntity be) {
        if (!(be.getLevel() instanceof ServerLevel serverLevel)) return 0;
        BlockCapabilityCache<IEnergyStorage, Direction>[] caches = caches(be, serverLevel);
        int totalDrained = 0;
        for (BlockCapabilityCache<IEnergyStorage, Direction> cache : caches) {
            int available = be.getEnergyStored();
            if (available <= 0) break;
            IEnergyStorage receiver = cache.getCapability();
            if (receiver == null || !receiver.canReceive()) continue;
            int accepted = receiver.receiveEnergy(available, false);
            if (accepted > 0) {
                be.extractEnergy(accepted, false);
                totalDrained += accepted;
            }
        }
        return totalDrained;
    }

    @SuppressWarnings("unchecked")
    private static BlockCapabilityCache<IEnergyStorage, Direction>[] caches(
            YakuGeneratorBlockEntity be, ServerLevel level) {
        Object existing = be.loaderEnergyPushState;
        if (existing != null) {
            return (BlockCapabilityCache<IEnergyStorage, Direction>[]) existing;
        }
        BlockCapabilityCache<IEnergyStorage, Direction>[] fresh =
                (BlockCapabilityCache<IEnergyStorage, Direction>[]) new BlockCapabilityCache<?, ?>[6];
        BlockPos source = be.getBlockPos();
        for (Direction dir : Direction.values()) {
            fresh[dir.ordinal()] = BlockCapabilityCache.create(
                    Capabilities.EnergyStorage.BLOCK,
                    level,
                    source.relative(dir),
                    dir.getOpposite());
        }
        be.loaderEnergyPushState = fresh;
        return fresh;
    }
}
