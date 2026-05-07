package com.riichimahjongforge.mahjongtable.record;

import com.themahjong.TheMahjongTile;
import java.util.List;

/**
 * Dealer holds the 13-orphan kokushi musou 13-wait. Drawn tile is M2 (waste);
 * any terminal/honor on the next draw is a win.
 */
public class PredefinedKokushimusouTableRecordItem extends MahjongTableRecordSimpleEastHandItem {

    public PredefinedKokushimusouTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected List<TheMahjongTile> handTiles() {
        return List.of(
                m(1), m(9),
                p(1), p(9),
                s(1), s(9),
                w(TheMahjongTile.Wind.EAST), w(TheMahjongTile.Wind.SOUTH),
                w(TheMahjongTile.Wind.WEST), w(TheMahjongTile.Wind.NORTH),
                d(TheMahjongTile.Dragon.HAKU), d(TheMahjongTile.Dragon.HATSU),
                d(TheMahjongTile.Dragon.CHUN),
                m(2));
    }

    @Override
    protected List<TheMahjongTile> wallTiles() {
        return List.of(d(TheMahjongTile.Dragon.CHUN));
    }
}
