package com.riichimahjong.mahjongtable.record;

import com.themahjong.TheMahjongTile;
import java.util.List;

/**
 * Dealer hand pre-stages multiple potential kakan upgrades on M1/M2/M3 etc.
 * (pairs of M1-M6 + M7 + drawn P8). Note: a real kakan needs a pre-existing pon —
 * {@link com.themahjong.TheMahjongFixedDeal} can't yet construct open melds, so
 * this fixture is best-effort: the raw tile distribution is preserved for
 * library extension later.
 */
public class PredefinedKakanOptionsTableRecordItem extends MahjongTableRecordSimpleEastHandItem {

    public PredefinedKakanOptionsTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected List<TheMahjongTile> handTiles() {
        return List.of(
                m(1), m(1),
                m(2), m(2),
                m(3), m(3),
                m(4), m(4),
                m(5), m(5),
                m(6), m(6),
                m(7),
                p(8));
    }

    @Override
    protected List<TheMahjongTile> wallTiles() {
        return List.of(m(1), m(7), m(7), m(1), m(6));
    }
}
