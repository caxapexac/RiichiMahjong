package com.themahjong;

public record TheMahjongDiscard(TheMahjongTile tile, boolean riichiDeclared) {

    public TheMahjongDiscard {
        if (tile == null) {
            throw new IllegalArgumentException("tile cannot be null");
        }
    }
}
