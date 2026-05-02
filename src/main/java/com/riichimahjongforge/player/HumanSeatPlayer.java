package com.riichimahjongforge.player;

import com.riichimahjongforge.MahjongTableBlockEntity;
import com.riichimahjongforge.MahjongTileItems;
import com.mahjongcore.MahjongGameState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;
import javax.annotation.Nullable;

public class HumanSeatPlayer implements MahjongSeatPlayer {

    private static final int AUTO_DRAW_DELAY_TICKS = 7;
    private static final int AUTO_DISCARD_DELAY_TICKS = 7;

    private final UUID uuid;
    private long nextAutoDrawTime = Long.MIN_VALUE;
    private long nextAutoDiscardTime = Long.MIN_VALUE;

    public HumanSeatPlayer(UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    @Override
    public String actorDebugLabel(ServerLevel level, int seat) {
        ServerPlayer player = onlinePlayer(level);
        if (player != null) {
            return "human:" + player.getGameProfile().getName();
        }
        return "human:offline";
    }

    @Override
    @Nullable
    public ServerPlayer onlinePlayer(ServerLevel level) {
        return level.getServer().getPlayerList().getPlayer(uuid);
    }

    @Override
    public boolean tryStoreDrawnTileOffTable(ServerLevel level, ItemStack drawn) {
        ServerPlayer player = onlinePlayer(level);
        if (player == null) {
            return false;
        }
        if (!canInsertIntoInventory(player)) {
            return false;
        }
        player.getInventory().add(drawn);
        return drawn.isEmpty();
    }

    @Override
    public boolean hasTileOffTable(ServerLevel level, int tileCode) {
        ServerPlayer player = onlinePlayer(level);
        return playerInventoryHasTileCode(player, tileCode);
    }

    @Override
    @Nullable
    public ItemStack takeTileOffTableForDiscard(ServerLevel level, int tileCode) {
        ServerPlayer player = onlinePlayer(level);
        if (player == null || tileCode < 0 || tileCode > 33) {
            return null;
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) {
                continue;
            }
            Integer code = MahjongTileItems.codeForItem(st.getItem());
            if (code == null || code != tileCode) {
                continue;
            }
            ItemStack out = st.split(1);
            if (st.isEmpty()) {
                inv.setItem(i, ItemStack.EMPTY);
            }
            inv.setChanged();
            return out;
        }
        return null;
    }

    private static boolean canInsertIntoInventory(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean playerInventoryHasTileCode(@Nullable ServerPlayer player, int tileCode) {
        if (player == null || tileCode < 0 || tileCode > 33) {
            return false;
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) {
                continue;
            }
            Integer code = MahjongTileItems.codeForItem(st.getItem());
            if (code != null && code == tileCode) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick(ServerLevel level, MahjongTableBlockEntity table, int seat) {
        MahjongGameState gs = table.activeGameState();
        if (gs == null) return;
        ServerPlayer player = onlinePlayer(level);
        if (gs.isClaimWindowActive()) {
            nextAutoDrawTime = Long.MIN_VALUE;
            nextAutoDiscardTime = Long.MIN_VALUE;
            table.passIfNoLegalClaim(seat);
            return;
        }
        if (gs.phase == MahjongGameState.TurnPhase.MUST_DRAW && gs.currentTurnSeat == seat) {
            // Only draw when the player is online; bots use performSeatDraw unconditionally.
            if (player != null) {
                if (nextAutoDrawTime == Long.MIN_VALUE) {
                    nextAutoDrawTime = level.getGameTime() + AUTO_DRAW_DELAY_TICKS;
                    return;
                }
                if (level.getGameTime() < nextAutoDrawTime) {
                    return;
                }
                table.performSeatDraw(level, seat);
                nextAutoDrawTime = Long.MIN_VALUE;
            }
        } else if (gs.phase == MahjongGameState.TurnPhase.MUST_DISCARD && gs.currentTurnSeat == seat) {
            if (player != null) {
                if (nextAutoDiscardTime == Long.MIN_VALUE) {
                    nextAutoDiscardTime = level.getGameTime() + AUTO_DISCARD_DELAY_TICKS;
                    return;
                }
                if (level.getGameTime() < nextAutoDiscardTime) {
                    return;
                }
                table.tryAutoDiscardForRiichi(level, seat);
                nextAutoDiscardTime = Long.MIN_VALUE;
            }
        } else {
            nextAutoDrawTime = Long.MIN_VALUE;
            nextAutoDiscardTime = Long.MIN_VALUE;
        }
    }

    @Override
    public void resetDelay() {
        nextAutoDrawTime = Long.MIN_VALUE;
        nextAutoDiscardTime = Long.MIN_VALUE;
    }

    @Override
    public void onSeatVacated() {}
}
