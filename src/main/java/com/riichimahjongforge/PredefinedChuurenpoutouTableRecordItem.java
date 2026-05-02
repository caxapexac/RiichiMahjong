package com.riichimahjongforge;

public class PredefinedChuurenpoutouTableRecordItem extends MahjongTableRecordSimpleEastHandItem {

    private static final int[] EAST_CHUUREN_RON_SETUP = {0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 8, 8, 27};
    private static final int[] WALL_CHUUREN_RON_TILES = {0, 4};

    public PredefinedChuurenpoutouTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected int[] handCodes() {
        return EAST_CHUUREN_RON_SETUP;
    }

    @Override
    protected int[] wallCodes() {
        return WALL_CHUUREN_RON_TILES;
    }
}
