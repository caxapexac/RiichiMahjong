package com.mahjongcore;

import com.mahjongcore.tile.Tile;

public class MahjongIllegalShuntsuIdentifierException extends MahjongException {
    private Tile tile;

    public MahjongIllegalShuntsuIdentifierException(Tile tile) {
        super("Invalid shuntsu identifier tile detected");
        this.tile = tile;
    }

    public String getAdvice() {
        String entry = "Attempted to save " + tile.name() + " as the identifier tile\n";
        if (tile.getNumber() == 0) {
            return entry + "Honor tiles cannot form a sequence";
        }
        return entry + "The identifier is the middle tile of a sequence, so 1 and 9 cannot be used";
    }
}
