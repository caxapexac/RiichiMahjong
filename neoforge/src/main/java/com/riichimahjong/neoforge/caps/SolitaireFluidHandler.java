package com.riichimahjong.neoforge.caps;

import com.riichimahjong.mahjongsolitaire.MahjongSolitaireBlockEntity;
import com.riichimahjong.registry.ModFluids;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * NeoForge {@link IFluidHandler} adapter for the solitaire BE's Liquid XP output.
 * Output-only: pumps drain XP, fills are rejected. mB is the NeoForge unit (1000/bucket).
 */
public final class SolitaireFluidHandler implements IFluidHandler {

    private final MahjongSolitaireBlockEntity be;

    public SolitaireFluidHandler(MahjongSolitaireBlockEntity be) {
        this.be = be;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        int stored = be.getLiquidXpStored();
        if (stored <= 0) return FluidStack.EMPTY;
        return new FluidStack(ModFluids.LIQUID_EXPERIENCE.get(), stored);
    }

    @Override
    public int getTankCapacity(int tank) {
        return MahjongSolitaireBlockEntity.LIQUID_XP_CAPACITY_MB;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return stack.getFluid() == ModFluids.LIQUID_EXPERIENCE.get();
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !isFluidValid(0, resource)) return FluidStack.EMPTY;
        return drain(resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;
        Fluid liquidXp = ModFluids.LIQUID_EXPERIENCE.get();
        int drained = be.extractLiquidXp(maxDrain, action.simulate());
        if (drained <= 0) return FluidStack.EMPTY;
        return new FluidStack(liquidXp, drained);
    }
}
