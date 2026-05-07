package com.riichimahjongforge.mahjongtable.record;

import com.themahjong.TheMahjongTile;
import java.util.List;

/**
 * Dealer starts with three concealed kan candidates (4×M1, 4×M2, 4×M3) plus M4 +
 * drawn M5. Sets up sankantsu/suukantsu options with successive ankan declarations
 * and rinshan draws. Note: pre-existing kan declarations aren't expressible via
 * {@link com.themahjong.TheMahjongFixedDeal} alone, so this fixture surfaces the
 * raw concealed quads — the player declares each ankan in-game.
 */
public class PredefinedTripleKanTableRecordItem extends MahjongTableRecordSimpleEastHandItem {

    public PredefinedTripleKanTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected List<TheMahjongTile> handTiles() {
        return List.of(
                m(1), m(1), m(1), m(1),
                m(2), m(2), m(2), m(2),
                m(3), m(3), m(3), m(3),
                m(4),
                m(5));
    }

    @Override
    protected List<TheMahjongTile> wallTiles() {
        return List.of(m(4), m(5));
    }
}
