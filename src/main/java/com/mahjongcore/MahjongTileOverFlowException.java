package com.mahjongcore;

import com.mahjongcore.tile.Tile;

public class MahjongTileOverFlowException extends MahjongException {
    private int code;
    private int num;

    public MahjongTileOverFlowException(int code, int num) {
        super("A tile can appear at most 4 times in a mahjong hand");
        this.code = code;
        this.num = num;
    }

    public String getAdvice() {
        return Tile.valueOf(code).name() + " (code=" + code + ") appears " + num + " times";
    }
}
