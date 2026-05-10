package com.riichimahjong.yakugenerator.fabric;

import com.riichimahjong.yakugenerator.YakuGeneratorBlockEntity;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiCache;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import team.reborn.energy.api.EnergyStorage;

public final class EnergyPushPlatformImpl {
    private EnergyPushPlatformImpl() {}

    public static int tickPush(YakuGeneratorBlockEntity be) {
        if (!(be.getLevel() instanceof ServerLevel serverLevel)) return 0;
        BlockApiCache<EnergyStorage, Direction>[] caches = caches(be, serverLevel);
        int totalDrained = 0;
        try (Transaction txn = Transaction.openOuter()) {
            for (Direction dir : Direction.values()) {
                int available = be.getEnergyStored();
                if (available <= 0) break;
                EnergyStorage receiver = caches[dir.ordinal()].find(dir.getOpposite());
                if (receiver == null || !receiver.supportsInsertion()) continue;
                long accepted = receiver.insert(available, txn);
                if (accepted > 0) {
                    be.extractEnergy((int) accepted, false);
                    totalDrained += (int) accepted;
                }
            }
            txn.commit();
        }
        return totalDrained;
    }

    @SuppressWarnings("unchecked")
    private static BlockApiCache<EnergyStorage, Direction>[] caches(
            YakuGeneratorBlockEntity be, ServerLevel level) {
        Object existing = be.loaderEnergyPushState;
        if (existing != null) {
            return (BlockApiCache<EnergyStorage, Direction>[]) existing;
        }
        BlockApiCache<EnergyStorage, Direction>[] fresh =
                (BlockApiCache<EnergyStorage, Direction>[]) new BlockApiCache<?, ?>[6];
        BlockPos source = be.getBlockPos();
        for (Direction dir : Direction.values()) {
            fresh[dir.ordinal()] = BlockApiCache.create(
                    EnergyStorage.SIDED, level, source.relative(dir));
        }
        be.loaderEnergyPushState = fresh;
        return fresh;
    }
}
