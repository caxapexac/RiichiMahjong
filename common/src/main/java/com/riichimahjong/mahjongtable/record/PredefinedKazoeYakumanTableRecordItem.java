package com.riichimahjong.mahjongtable.record;

import com.themahjong.TheMahjongTile;
import java.util.List;

/**
 * Dealer holds chiitoitsu pairs M2..M8 (12 tiles + drawn M8 forming the M8 pair).
 * Dora indicator is M2 → dora is M3, then chained dora makes the count climb past
 * 13 han for kazoe yakuman scoring.
 */
public class PredefinedKazoeYakumanTableRecordItem extends MahjongTableRecordSimpleEastHandItem {

    public PredefinedKazoeYakumanTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected List<TheMahjongTile> handTiles() {
        return List.of(
                m(2), m(2),
                m(3), m(3),
                m(4), m(4),
                m(5), m(5),
                m(6), m(6),
                m(7), m(7),
                m(8),
                m(8));
    }

    @Override
    protected List<TheMahjongTile> wallTiles() {
        return List.of(m(7));
    }

    @Override
    protected TheMahjongTile doraIndicator() {
        return m(2);
    }
}
