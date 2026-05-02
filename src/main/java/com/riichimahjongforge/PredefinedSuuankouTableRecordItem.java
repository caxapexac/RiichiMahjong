package com.riichimahjongforge;

public class PredefinedSuuankouTableRecordItem extends MahjongTableRecordSimpleEastHandItem {

    private static final int[] EAST_SUUANKOU_TSUMO_PREP_SETUP = {0, 0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 5};
    private static final int[] WALL_SUUANKOU_RON_TILES = {4, 5};

    public PredefinedSuuankouTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected int[] handCodes() {
        return EAST_SUUANKOU_TSUMO_PREP_SETUP;
    }

    @Override
    protected int[] wallCodes() {
        return WALL_SUUANKOU_RON_TILES;
    }
}
