package com.riichimahjongforge;

public class PredefinedRinshanTableRecordItem extends MahjongTableRecordSimpleEastHandItem {

    // Hand: ankan on 1-man (code 0), concealed tiles 234p 567p 89p east-east, draw tile east (27).
    // After declaring ankan on 1-man, rinshan draw is 7p (code 15) completing 789p.
    private static final int[] EAST_HAND = {0, 0, 0, 0, 10, 11, 12, 13, 14, 15, 16, 17, 27, 27};
    private static final int[] WALL_CODES = {};
    // Rinshan draw tile (7p, code 15) placed at dead wall slot 0 (first kan draw).
    private static final int[] HAITEIHAI_CODES = {15};

    public PredefinedRinshanTableRecordItem(Properties properties) {
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

    @Override
    protected int[] haiteihaiCodes() {
        return HAITEIHAI_CODES;
    }
}
