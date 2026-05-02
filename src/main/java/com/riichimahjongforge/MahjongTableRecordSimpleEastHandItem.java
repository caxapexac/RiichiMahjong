package com.riichimahjongforge;

import com.mahjongcore.MahjongMatchDefinition;
import com.mahjongcore.rules.RoundSetupRules;
import java.util.List;
import java.util.Random;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

public abstract class MahjongTableRecordSimpleEastHandItem extends MahjongTableRecordItem {

    protected MahjongTableRecordSimpleEastHandItem(Properties properties) {
        super(properties);
    }

    protected abstract int[] handCodes();

    protected abstract int[] wallCodes();

    /** Returns the tile code to place as the dora indicator, or {@code -1} to use a random tile. */
    protected int doraIndicatorCode() {
        return -1;
    }

    /**
     * Returns up to 4 tile codes to place as haiteihai (rinshan draw tiles) in dead wall slots 0–3,
     * in draw order (first kan draw at index 0, second at index 1, etc.).
     * Return an empty array (default) to fill those slots randomly.
     */
    protected int[] haiteihaiCodes() {
        return new int[0];
    }

    /**
     * Returns tile codes to hardcode the hand of seat 3 (the first bot after the player, counter-clockwise).
     * Return an empty array (default) to fill that seat's hand randomly.
     */
    protected int[] secondHandCodes() {
        return new int[0];
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        int[] handCodes = handCodes();
        int[] wallCodes = wallCodes();
        int drawCode = handCodes[handCodes.length - 1];
        writeRecordedTableState(
                stack,
                createRecordedTableState(
                        MahjongMatchDefinition.DEFAULT,
                        createDefaultGameState(drawCode),
                        createInventoryEntries(handCodes, drawCode, wallCodes, doraIndicatorCode(), haiteihaiCodes(), secondHandCodes())));
        return stack;
    }

    private static ListTag createInventoryEntries(int[] handCodes, int drawCode, int[] wallCodes, int doraIndicatorCode) {
        return createInventoryEntries(handCodes, drawCode, wallCodes, doraIndicatorCode, new int[0], new int[0]);
    }

    private static ListTag createInventoryEntries(int[] handCodes, int drawCode, int[] wallCodes, int doraIndicatorCode, int[] haiteihaiCodes) {
        return createInventoryEntries(handCodes, drawCode, wallCodes, doraIndicatorCode, haiteihaiCodes, new int[0]);
    }

    private static ListTag createInventoryEntries(int[] handCodes, int drawCode, int[] wallCodes, int doraIndicatorCode, int[] haiteihaiCodes, int[] secondHandCodes) {
        ListTag items = new ListTag();
        Random random = new Random(0xC6E4D31L);
        List<Integer> pool = RoundSetupRules.sortedWallCodes();

        // --- Phase 1: reserve all fixed (non-random) tiles from the pool upfront ---
        // Fail fast if the pool cannot satisfy the definition.
        int[] reservedHand = new int[handCodes.length];
        for (int i = 0; i < handCodes.length; i++) {
            requireCodeInPool(pool, handCodes[i], "handCodes[" + i + "]");
            reservedHand[i] = RoundSetupRules.takeCodeFromPool(pool, handCodes[i], random);
        }
        requireCodeInPool(pool, drawCode, "drawCode");
        int reservedDraw = RoundSetupRules.takeCodeFromPool(pool, drawCode, random);
        int[] reservedWall = new int[wallCodes.length];
        for (int i = 0; i < wallCodes.length; i++) {
            requireCodeInPool(pool, wallCodes[i], "wallCodes[" + i + "]");
            reservedWall[i] = RoundSetupRules.takeCodeFromPool(pool, wallCodes[i], random);
        }
        if (doraIndicatorCode >= 0) {
            requireCodeInPool(pool, doraIndicatorCode, "doraIndicatorCode");
        }
        int reservedDora = doraIndicatorCode >= 0 ? RoundSetupRules.takeCodeFromPool(pool, doraIndicatorCode, random) : -1;
        int[] reservedHaiteihai = new int[Math.min(haiteihaiCodes.length, 4)];
        for (int i = 0; i < reservedHaiteihai.length; i++) {
            requireCodeInPool(pool, haiteihaiCodes[i], "haiteihaiCodes[" + i + "]");
            reservedHaiteihai[i] = RoundSetupRules.takeCodeFromPool(pool, haiteihaiCodes[i], random);
        }

        int[] reservedSecondHand = new int[0];
        if (secondHandCodes.length > 0) {
            reservedSecondHand = new int[secondHandCodes.length];
            for (int i = 0; i < secondHandCodes.length; i++) {
                requireCodeInPool(pool, secondHandCodes[i], "secondHandCodes[" + i + "]");
                reservedSecondHand[i] = RoundSetupRules.takeCodeFromPool(pool, secondHandCodes[i], random);
            }
        }

        // --- Phase 2: place reserved tiles and fill remaining slots from pool ---
        addSeatHandDefault(items, 0, pool, reservedHand, random);
        items.add(tileEntryDefault(
                MahjongTableBlockEntity.playerZoneBase(0) + 13,
                reservedDraw));

        for (int seat = 1; seat < MahjongTableBlockEntity.SEAT_COUNT - 1; seat++) {
            addSeatHandDefault(items, seat, pool, null, random);
        }
        addSeatHandDefault(items, MahjongTableBlockEntity.SEAT_COUNT - 1, pool, reservedSecondHand.length > 0 ? reservedSecondHand : null, random);

        addWallDefault(items, pool, reservedWall, random);
        addDeadWallDefault(items, pool, reservedHaiteihai, reservedDora, random);
        return items;
    }
}
