package com.riichimahjongforge.client;

import com.riichimahjongforge.MahjongTableBlockEntity;

/** Action-bar lines after the table block entity receives synced NBT (including {@code MtGame}). */
public final class MahjongTableClientSide {

    private MahjongTableClientSide() {}

    public static void afterTableBeData(MahjongTableBlockEntity table) {
        // Intentionally no player-facing action bar turn hints.
    }
}
