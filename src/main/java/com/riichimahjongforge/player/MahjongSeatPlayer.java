package com.riichimahjongforge.player;

import com.riichimahjongforge.MahjongTableBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

public interface MahjongSeatPlayer {

    UUID getUuid();

    /** Human-readable actor label for diagnostics/logging. */
    String actorDebugLabel(ServerLevel level, int seat);

    /** Online player entity for this seat when applicable, otherwise null. */
    @Nullable
    ServerPlayer onlinePlayer(ServerLevel level);

    /**
     * Attempts to place a freshly drawn tile outside table hand slots (for human inventory-backed turns).
     * Returns true when the stack was consumed by the seat-backed storage.
     */
    default boolean tryStoreDrawnTileOffTable(ServerLevel level, ItemStack drawn) {
        return false;
    }

    /** Returns true when off-table seat storage currently contains at least one tile with given code. */
    default boolean hasTileOffTable(ServerLevel level, int tileCode) {
        return false;
    }

    /** Removes one off-table tile stack by code for discard flow; null when unavailable. */
    @Nullable
    default ItemStack takeTileOffTableForDiscard(ServerLevel level, int tileCode) {
        return null;
    }

    /**
     * Called every server tick while the match is active.
     * During a claim window, called for each eligible claimant seat.
     * During a turn, called for the active turn seat.
     */
    void tick(ServerLevel level, MahjongTableBlockEntity table, int seat);

    /** Resets any pending delayed actions. Called at match start, abort, and table reset. */
    void resetDelay();

    /** Called when this player is removed from the seat (leave, kick, seat close). */
    void onSeatVacated();
}
