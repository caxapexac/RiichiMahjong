package com.riichimahjong.mahjongtable.record;

import com.themahjong.TheMahjongTile;
import java.util.List;

/**
 * Dealer holds 4 concealed triplets of M1/M2/M3/M4 plus a single M5 (12 + 1 = 13);
 * drawn tile is M6 (waste). Wall queues M5 then M6, so after a discard cycle the
 * dealer's next draw can pair their M5 → suuankou tsumo.
 */
public class PredefinedSuuankouTableRecordItem extends MahjongTableRecordSimpleEastHandItem {

    public PredefinedSuuankouTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected List<TheMahjongTile> handTiles() {
        return List.of(
                m(1), m(1), m(1),
                m(2), m(2), m(2),
                m(3), m(3), m(3),
                m(4), m(4), m(4),
                m(5),
                m(6));
    }

    @Override
    protected List<TheMahjongTile> wallTiles() {
        return List.of(m(5), m(6));
    }
}
