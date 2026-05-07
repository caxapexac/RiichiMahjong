package com.riichimahjongforge.mahjongtable.record;

import com.riichimahjongforge.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjongforge.mahjongtable.MahjongTableBlockEntity.StartMatchResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Save/load tool for the mahjong table — a "table state record". Empty stacks save
 * the current BE state on RMB; filled stacks restore it on RMB (table must be IDLE).
 * Loading does not consume the recorded state — the same record can be re-applied any
 * number of times to spin up a fresh test fixture.
 *
 * <p>Subclasses (the predefined-situation items) override {@link #applyToTable} to
 * inject a hardcoded match state from {@link com.themahjong.TheMahjongFixedDeal}
 * instead of stack NBT.
 */
public class MahjongTableRecordItem extends Item {

    public static final String TABLE_STATE_TAG = "MahjongTableState";
    private static final String EMPTY_NAME_TRANSLATION_KEY = "item.riichi_mahjong_forge.mahjong_table_record_empty";

    public MahjongTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isPredefinedFixture() || hasRecordedTableState(stack) || super.isFoil(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        if (!isPredefinedFixture() && !hasRecordedTableState(stack)) {
            return Component.translatable(EMPTY_NAME_TRANSLATION_KEY);
        }
        return super.getName(stack);
    }

    public static boolean hasRecordedTableState(ItemStack stack) {
        return recordedTableStateOrNull(stack) != null;
    }

    public static void writeRecordedTableState(ItemStack stack, CompoundTag tableState) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.put(TABLE_STATE_TAG, tableState.copy());
    }

    public static void clearRecordedTableState(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(TABLE_STATE_TAG);
        if (tag.isEmpty()) stack.setTag(null);
    }

    public static CompoundTag recordedTableStateOrNull(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TABLE_STATE_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return null;
        }
        return tag.getCompound(TABLE_STATE_TAG).copy();
    }

    /**
     * Apply this record to the given table. Default implementation pours the stack's
     * recorded state back into the BE; subclasses can override to inject a
     * programmatically-built fixture instead.
     *
     * @return {@code true} when the table was modified.
     */
    public boolean applyToTable(ServerPlayer player, MahjongTableBlockEntity table, ItemStack stack) {
        CompoundTag snapshot = recordedTableStateOrNull(stack);
        if (snapshot == null) return false;
        StartMatchResult result = table.tryApplyRecordSnapshot(snapshot, player.getUUID());
        if (result == StartMatchResult.NOT_IDLE) {
            player.displayClientMessage(
                    Component.literal("Cannot load record: end the current match first."), true);
            return false;
        }
        return true;
    }

    /**
     * Save the table's current state into this stack. Returns {@code false} if the
     * stack already holds a recorded state (don't overwrite by accident — empty it
     * first if you want to re-record).
     */
    public boolean recordFromTable(ServerPlayer player, MahjongTableBlockEntity table, ItemStack stack) {
        if (hasRecordedTableState(stack)) {
            player.displayClientMessage(
                    Component.literal("Record is already filled — drop the snapshot first."), true);
            return false;
        }
        writeRecordedTableState(stack, table.exportRecordSnapshot());
        return true;
    }

    /**
     * Subclasses that build their state from a {@link com.themahjong.TheMahjongFixedDeal}
     * (predefined situations) override {@link #applyToTable} and should also report
     * "filled" here so the item displays correctly without carrying any NBT.
     */
    protected boolean isPredefinedFixture() {
        return false;
    }

    /** Public form for use by the table block's RMB dispatcher. */
    public boolean isPredefinedFixtureForRouting() {
        return isPredefinedFixture();
    }
}
