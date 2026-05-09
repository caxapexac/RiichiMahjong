package com.riichimahjong.neoforge.caps;

import com.riichimahjong.yakugenerator.YakuGeneratorBlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * NeoForge {@link IEnergyStorage} adapter for the yaku generator's RF output.
 * Extract-only — yaku gen produces energy from yaku results, doesn't accept input.
 */
public final class YakuGenEnergyHandler implements IEnergyStorage {

    private final YakuGeneratorBlockEntity be;

    public YakuGenEnergyHandler(YakuGeneratorBlockEntity be) {
        this.be = be;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return 0;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return be.extractEnergy(maxExtract, simulate);
    }

    @Override
    public int getEnergyStored() {
        return be.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored() {
        return be.getMaxEnergyStored();
    }

    @Override
    public boolean canExtract() {
        return true;
    }

    @Override
    public boolean canReceive() {
        return false;
    }
}
