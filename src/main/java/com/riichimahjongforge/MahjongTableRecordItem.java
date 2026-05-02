package com.riichimahjongforge;

import com.mahjongcore.MahjongMatchDefinition;
import com.mahjongcore.MahjongGameState;
import com.mahjongcore.rules.RoundSetupRules;
import com.riichimahjongforge.nbt.MahjongGameStateNbt;
import com.riichimahjongforge.nbt.MahjongMatchDefinitionNbt;
import com.riichimahjongforge.player.BotSeatPlayer;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public class MahjongTableRecordItem extends Item {

    public static final String TABLE_STATE_TAG = "MahjongTableState";
    private static final String EMPTY_NAME_TRANSLATION_KEY = "item.riichi_mahjong_forge.mahjong_table_record_empty";

    public MahjongTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return hasRecordedTableState(stack) || super.isFoil(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        if (!hasRecordedTableState(stack)) {
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
        if (tag == null) {
            return;
        }
        tag.remove(TABLE_STATE_TAG);
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }

    public static CompoundTag recordedTableStateOrNull(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TABLE_STATE_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return null;
        }
        return tag.getCompound(TABLE_STATE_TAG).copy();
    }

    public boolean tryLoadIntoTable(ServerPlayer player, MahjongTableBlockEntity table, ItemStack stack) {
        CompoundTag tableState = recordedTableStateOrNull(stack);
        if (tableState == null) {
            return false;
        }
        if (recordHasOnlyBots(tableState)) {
            var rulesFromRecord = MahjongMatchDefinitionNbt.load(tableState, com.mahjongcore.MahjongMatchDefinition.DEFAULT);
            var seats = new com.mahjongcore.MahjongMatchDefinition.SeatDefinition[MahjongTableBlockEntity.SEAT_COUNT];
            seats[0] = new com.mahjongcore.MahjongMatchDefinition.SeatDefinition(true, player.getUUID());
            for (int seat = 1; seat < MahjongTableBlockEntity.SEAT_COUNT; seat++) {
                seats[seat] = new com.mahjongcore.MahjongMatchDefinition.SeatDefinition(true, BotSeatPlayer.uuidForSeat(seat));
            }
            MahjongMatchDefinitionNbt.save(tableState, rulesFromRecord.withSeats(seats));
        }
        return table.readStateFromRecord(player, stack, tableState);
    }

    public boolean tryWriteFromTable(ServerPlayer player, MahjongTableBlockEntity table, ItemStack stack) {
        if (hasRecordedTableState(stack)) {
            return false;
        }
        return table.writeStateIntoRecord(player, stack);
    }

    private static boolean tableHasOnlyBots(MahjongTableBlockEntity table) {
        for (int seat = 0; seat < MahjongTableBlockEntity.SEAT_COUNT; seat++) {
            if (!table.isSeatEnabled(seat)) {
                continue;
            }
            UUID occupant = table.occupantAt(seat);
            if (occupant != null && !BotSeatPlayer.isBotUuid(occupant)) {
                return false;
            }
        }
        return true;
    }

    private static boolean recordHasOnlyBots(CompoundTag tableState) {
        MahjongMatchDefinition rulesFromRecord =
                MahjongMatchDefinitionNbt.load(tableState, MahjongMatchDefinition.DEFAULT);
        MahjongMatchDefinition.SeatDefinition[] seats = rulesFromRecord.seats();
        for (int seat = 0; seat < seats.length; seat++) {
            MahjongMatchDefinition.SeatDefinition seatDef = seats[seat];
            if (seatDef == null || !seatDef.enabled()) {
                continue;
            }
            UUID occupant = seatDef.occupant();
            if (occupant != null && !BotSeatPlayer.isBotUuid(occupant)) {
                return false;
            }
        }
        return true;
    }


    protected static void requireCodeInPool(List<Integer> pool, int code, String label) {
        if (!pool.contains(code)) {
            throw new IllegalStateException(
                    "Predefined record definition requires tile code " + code + " (" + label + ") but it is not available in the pool");
        }
    }

    /**
     * Fills 13 hand slots for {@code seat}. {@code reservedCodes} are already-removed tile codes to place
     * at the first slots in order; remaining slots are drawn randomly from {@code pool}.
     */
    protected static void addSeatHandDefault(
            ListTag items, int seat, List<Integer> pool, int[] reservedCodes, Random random) {
        int base = MahjongTableBlockEntity.playerZoneBase(seat);
        int slotCount = 13;
        int i = 0;
        if (reservedCodes != null) {
            int fixedCount = Math.min(reservedCodes.length, slotCount);
            for (; i < fixedCount; i++) {
                items.add(tileEntryDefault(base + i, reservedCodes[i]));
            }
        }

        for (; i < slotCount; i++) {
            items.add(tileEntryDefault(base + i, RoundSetupRules.drawRandomCode(pool, random)));
        }
    }

    protected static void addWallDefault(ListTag items, List<Integer> pool, Random random) {
        addWallDefault(items, pool, null, random);
    }

    /**
     * Fills live wall slots. {@code reservedCodes} are already-removed tile codes to place at the first
     * wall slots in order; remaining slots are drawn randomly from {@code pool}.
     */
    protected static void addWallDefault(ListTag items, List<Integer> pool, int[] reservedCodes, Random random) {
        int i = 0;
        if (reservedCodes != null) {
            int fixedCount = Math.min(reservedCodes.length, MahjongTableBlockEntity.WALL_SLOTS);
            for (; i < fixedCount; i++) {
                items.add(tileEntryDefault(
                        MahjongTableBlockEntity.INV_WALL_START + i,
                        reservedCodes[i]));
            }
        }

        int available = Math.max(0, pool.size() - MahjongTableBlockEntity.DEAD_WALL_SLOTS);
        int max = Math.min(available, MahjongTableBlockEntity.WALL_SLOTS);
        for (; i < max; i++) {
            items.add(tileEntryDefault(MahjongTableBlockEntity.INV_WALL_START + i, RoundSetupRules.drawRandomCode(pool, random)));
        }
    }

    protected static void addDeadWallDefault(ListTag items, List<Integer> pool, Random random) {
        addDeadWallDefault(items, pool, -1, random);
    }

    /**
     * Fills the dead wall. {@code reservedDoraCode} is an already-removed tile code to place at
     * {@link RoundSetupRules#DORA_INDICATOR_DEAD_WALL_INDEX}, or {@code -1} to draw randomly for that slot too.
     */
    protected static void addDeadWallDefault(ListTag items, List<Integer> pool, int reservedDoraCode, Random random) {
        addDeadWallDefault(items, pool, new int[0], reservedDoraCode, random);
    }

    /**
     * Fills the dead wall. {@code reservedHaiteihai} are already-removed tile codes to place at dead wall
     * slots 0..n-1 (rinshan draw order); {@code reservedDoraCode} is placed at
     * {@link RoundSetupRules#DORA_INDICATOR_DEAD_WALL_INDEX}, or {@code -1} to draw randomly for that slot.
     */
    protected static void addDeadWallDefault(ListTag items, List<Integer> pool, int[] reservedHaiteihai, int reservedDoraCode, Random random) {
        int extraReserved = reservedHaiteihai.length + (reservedDoraCode >= 0 ? 1 : 0);
        int max = Math.min(pool.size() + extraReserved, MahjongTableBlockEntity.DEAD_WALL_SLOTS);
        for (int i = 0; i < max; i++) {
            int code;
            if (i < reservedHaiteihai.length) {
                code = reservedHaiteihai[i];
            } else if (reservedDoraCode >= 0 && i == RoundSetupRules.DORA_INDICATOR_DEAD_WALL_INDEX) {
                code = reservedDoraCode;
            } else {
                code = RoundSetupRules.drawRandomCode(pool, random);
            }
            items.add(tileEntryDefault(MahjongTableBlockEntity.INV_DEAD_WALL_START + i, code));
        }
    }

    protected static CompoundTag tileEntryDefault(int slot, int tileCode) {
        CompoundTag entry = new CompoundTag();
        entry.putInt("Slot", slot);
        entry.putString("id", tileItemIdDefault(tileCode));
        entry.putByte("Count", (byte) 1);
        return entry;
    }

    protected static String tileItemIdDefault(int tileCode) {
        Item item = MahjongTileItems.itemForCode(tileCode);
        if (item == null) {
            item = MahjongTileItems.itemForCode(0);
        }
        return ForgeRegistries.ITEMS.getKey(item).toString();
    }

    protected CompoundTag createRecordedTableState(
            MahjongMatchDefinition matchDefinition, CompoundTag gameStateTag, ListTag inventoryEntries) {
        CompoundTag tableState = new CompoundTag();
        MahjongMatchDefinitionNbt.save(tableState, matchDefinition);
        tableState.put("MtGame", gameStateTag.copy());
        tableState.put("MtItemsInt", inventoryEntries.copy());
        return tableState;
    }

    protected static CompoundTag createDefaultGameState(int lastDrawnCode) {
        MahjongGameState gameState = new MahjongGameState();
        gameState.beginHandFromPrepared(0);
        gameState.handNumber = 4;
        gameState.currentTurnSeat = 0;
        gameState.phase = MahjongGameState.TurnPhase.MUST_DISCARD;
        gameState.lastDrawnCode = lastDrawnCode;
        CompoundTag gameTag = new CompoundTag();
        MahjongGameStateNbt.save(gameState, gameTag);
        return gameTag;
    }
}
