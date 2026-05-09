package com.riichimahjong.fabric.storage;

import com.riichimahjong.mahjongsolitaire.MahjongSolitaireBlockEntity;
import com.riichimahjong.registry.ModFluids;
import java.util.Iterator;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.ExtractionOnlyStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/**
 * Fabric {@link net.fabricmc.fabric.api.transfer.v1.storage.Storage} adapter for the
 * solitaire BE's Liquid XP output. Extract-only. Fabric uses droplets (1 mB = 81
 * droplets via {@link FluidConstants}); we convert at the boundary so common-side
 * code stays in mB.
 *
 * <p>Non-transactional same as {@code SolitaireItemStorage} — see comment there.
 */
public final class SolitaireFluidStorage
        implements ExtractionOnlyStorage<FluidVariant>, SingleSlotStorage<FluidVariant> {

    /** Fabric droplets per NeoForge / common mB. (1 mB = 1/1000 bucket; 1 bucket = 81000 droplets.) */
    private static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000L;

    private final MahjongSolitaireBlockEntity be;

    public SolitaireFluidStorage(MahjongSolitaireBlockEntity be) {
        this.be = be;
    }

    @Override
    public long extract(FluidVariant variant, long maxAmount, TransactionContext transaction) {
        if (variant.isBlank() || maxAmount <= 0) return 0;
        if (!variant.isOf(ModFluids.LIQUID_EXPERIENCE.get())) return 0;
        int requestMb = (int) Math.min(maxAmount / DROPLETS_PER_MB, MahjongSolitaireBlockEntity.LIQUID_XP_CAPACITY_MB);
        if (requestMb <= 0) return 0;
        int drainedMb = be.extractLiquidXp(requestMb, /*simulate=*/ false);
        return (long) drainedMb * DROPLETS_PER_MB;
    }

    @Override
    public boolean isResourceBlank() {
        return be.getLiquidXpStored() <= 0;
    }

    @Override
    public FluidVariant getResource() {
        return FluidVariant.of(ModFluids.LIQUID_EXPERIENCE.get());
    }

    @Override
    public long getAmount() {
        return (long) be.getLiquidXpStored() * DROPLETS_PER_MB;
    }

    @Override
    public long getCapacity() {
        return (long) MahjongSolitaireBlockEntity.LIQUID_XP_CAPACITY_MB * DROPLETS_PER_MB;
    }

    @Override
    public Iterator<StorageView<FluidVariant>> iterator() {
        return java.util.Collections.<StorageView<FluidVariant>>singletonList(this).iterator();
    }
}
