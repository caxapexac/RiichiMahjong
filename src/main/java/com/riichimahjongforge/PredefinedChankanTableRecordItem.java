package com.riichimahjongforge;

public class PredefinedChankanTableRecordItem extends MahjongTableRecordSimpleEastHandItem {

    // Player hand: 123p+456p+89p+1s+3s+456s, tenpai waiting on 2s (kanchan 13s) or 7p (kanchan 89p).
    // Draw tile is 1p (code 9) — player must discard it. Uses 0 copies of 2s.
    private static final int[] PLAYER_HAND = {3, 3, 3, 4, 4, 4, 5, 5, 5, 6, 7, 15, 15, 21};
    // 2s (code 19) placed first in wall — north bot draws it and can declare kakan.
    private static final int[] WALL_CODES = {22, 8, 22, 22, 22, 8};
    // North bot concealed hand: 3×2s + S5+S6+S7+S8+S9+East (9 tiles).
    // Bot already has 3×2s; when it draws the wall 2s it can kakan, triggering chankan window.
    private static final int[] SECOND_HAND_CODES = {8, 8};

    public PredefinedChankanTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected int[] handCodes() {
        return PLAYER_HAND;
    }

    @Override
    protected int[] wallCodes() {
        return WALL_CODES;
    }

    @Override
    protected int[] secondHandCodes() {
        return SECOND_HAND_CODES;
    }
}
