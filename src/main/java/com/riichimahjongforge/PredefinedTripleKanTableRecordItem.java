package com.riichimahjongforge;

public class PredefinedTripleKanTableRecordItem extends MahjongTableRecordSimpleEastHandItem {

    private static final int[] EAST_TRIPLE_KAN_OPTIONS_TSUMO_PREP_SETUP = {0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 4};
    private static final int[] WALL_TRIPLE_KAN_RON_TILES = {3, 4};

    public PredefinedTripleKanTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected int[] handCodes() {
        return EAST_TRIPLE_KAN_OPTIONS_TSUMO_PREP_SETUP;
    }

    @Override
    protected int[] wallCodes() {
        return WALL_TRIPLE_KAN_RON_TILES;
    }
}
