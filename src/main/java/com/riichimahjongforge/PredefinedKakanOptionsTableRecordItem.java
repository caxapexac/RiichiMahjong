package com.riichimahjongforge;

public class PredefinedKakanOptionsTableRecordItem extends MahjongTableRecordSimpleEastHandItem {

    private static final int[] EAST_HAND = {0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 16};

    private static final int[] WALL_CODES = {0, 6, 6, 0, 5};

    public PredefinedKakanOptionsTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected int[] handCodes() {
        return EAST_HAND;
    }

    @Override
    protected int[] wallCodes() {
        return WALL_CODES;
    }
}
