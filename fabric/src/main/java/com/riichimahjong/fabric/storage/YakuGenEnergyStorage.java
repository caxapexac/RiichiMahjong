package com.riichimahjong.fabric.storage;

import com.riichimahjong.yakugenerator.YakuGeneratorBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import team.reborn.energy.api.EnergyStorage;

/**
 * Team Reborn Energy storage adapter for the yaku generator's RF output. Extract-only,
 * mirrors the NeoForge {@code IEnergyStorage} adapter's semantics. Energy uses RF
 * units (long, but conceptually compatible with NeoForge's int RF — values stay below
 * Integer.MAX_VALUE in practice).
 *
 * <p>Non-transactional same as the solitaire storage adapters — Energy transactions
 * commit per tick when used by tech-mod pipes.
 */
public final class YakuGenEnergyStorage implements EnergyStorage {

    private final YakuGeneratorBlockEntity be;

    public YakuGenEnergyStorage(YakuGeneratorBlockEntity be) {
        this.be = be;
    }

    @Override
    public boolean supportsInsertion() {
        return false;
    }

    @Override
    public boolean supportsExtraction() {
        return true;
    }

    @Override
    public long insert(long maxAmount, TransactionContext transaction) {
        return 0;
    }

    @Override
    public long extract(long maxAmount, TransactionContext transaction) {
        if (maxAmount <= 0) return 0;
        int request = (int) Math.min(maxAmount, Integer.MAX_VALUE);
        return be.extractEnergy(request, /*simulate=*/ false);
    }

    @Override
    public long getAmount() {
        return be.getEnergyStored();
    }

    @Override
    public long getCapacity() {
        return be.getMaxEnergyStored();
    }
}
