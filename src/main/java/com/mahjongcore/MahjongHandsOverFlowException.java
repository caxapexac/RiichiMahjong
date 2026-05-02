package com.mahjongcore;

public class MahjongHandsOverFlowException extends MahjongException {
    public MahjongHandsOverFlowException() {
        super("Too many tiles");
    }
}
