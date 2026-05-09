package com.riichimahjong.neoforge.caps;

import com.riichimahjong.mahjongsolitaire.MahjongSolitaireBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * NeoForge {@link IItemHandler} adapter for the solitaire BE's hopper-input surface.
 * Delegates to {@link MahjongSolitaireBlockEntity#tryInsertHintItem}. Extract is a
 * no-op — this is an input-only port (iron/gold/diamond → hint pairs).
 */
public final class SolitaireItemHandler implements IItemHandler {

    private final MahjongSolitaireBlockEntity be;

    public SolitaireItemHandler(MahjongSolitaireBlockEntity be) {
        this.be = be;
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (!MahjongSolitaireBlockEntity.isHintItem(stack)) return stack;
        int consumed = be.tryInsertHintItem(stack, simulate);
        if (consumed <= 0) return stack;
        ItemStack remainder = stack.copy();
        remainder.shrink(consumed);
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return MahjongSolitaireBlockEntity.isHintItem(stack);
    }
}
