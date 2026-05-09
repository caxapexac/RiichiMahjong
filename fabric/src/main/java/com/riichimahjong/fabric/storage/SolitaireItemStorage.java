package com.riichimahjong.fabric.storage;

import com.riichimahjong.mahjongsolitaire.MahjongSolitaireBlockEntity;
import java.util.Collections;
import java.util.Iterator;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.InsertionOnlyStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/**
 * Fabric {@link net.fabricmc.fabric.api.transfer.v1.storage.Storage} adapter for the
 * solitaire BE's hopper-input surface. Insert-only; extraction is empty.
 *
 * <p><b>Non-transactional:</b> mutations are applied immediately when {@code insert}
 * is called, regardless of whether the surrounding transaction commits or rolls back.
 * Standard hoppers / fluid pipes commit transactions per-tick, so this is safe for
 * the intended use case. A full {@code SnapshotParticipant}-based impl would be
 * needed for correctness against speculative transactions; not worth the complexity
 * for a marker-fluid feature.
 */
public final class SolitaireItemStorage implements InsertionOnlyStorage<ItemVariant> {

    private final MahjongSolitaireBlockEntity be;

    public SolitaireItemStorage(MahjongSolitaireBlockEntity be) {
        this.be = be;
    }

    @Override
    public long insert(ItemVariant variant, long maxAmount, TransactionContext transaction) {
        if (variant.isBlank() || maxAmount <= 0) return 0;
        var stack = variant.toStack((int) Math.min(maxAmount, 64));
        if (!MahjongSolitaireBlockEntity.isHintItem(stack)) return 0;
        return be.tryInsertHintItem(stack, /*simulate=*/ false);
    }

    @Override
    public Iterator<StorageView<ItemVariant>> iterator() {
        return Collections.emptyIterator();
    }
}
