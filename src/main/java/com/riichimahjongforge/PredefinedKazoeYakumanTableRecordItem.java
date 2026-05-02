package com.riichimahjongforge;

public class PredefinedKazoeYakumanTableRecordItem extends MahjongTableRecordSimpleEastHandItem {

    private static final int[] EAST_KAZOE_STYLE_TSUMO_PREP_SETUP = {1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7};
    private static final int[] WALL_KAZOE_RON_TILES = {6};

    public PredefinedKazoeYakumanTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected int[] handCodes() {
        return EAST_KAZOE_STYLE_TSUMO_PREP_SETUP;
    }

    @Override
    protected int[] wallCodes() {
        return WALL_KAZOE_RON_TILES;
    }

    @Override
    protected int doraIndicatorCode() {
        return 1;
    }
}
