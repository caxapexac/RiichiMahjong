package com.riichimahjongforge;

public class PredefinedKokushimusouTableRecordItem extends MahjongTableRecordSimpleEastHandItem {

    private static final int[] EAST_KOKUSHI_13WAIT_PREP_SETUP = {0, 8, 9, 17, 18, 26, 27, 28, 29, 30, 31, 32, 33, 1};
    private static final int[] WALL_KOKUSHI_RON_TILES = {33};

    public PredefinedKokushimusouTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected int[] handCodes() {
        return EAST_KOKUSHI_13WAIT_PREP_SETUP;
    }

    @Override
    protected int[] wallCodes() {
        return WALL_KOKUSHI_RON_TILES;
    }
}
