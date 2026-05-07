package com.riichimahjongforge.mahjongtable.record;

import com.themahjong.TheMahjongTile;
import java.util.List;

/**
 * Dealer starts the very first hand already in tenpai and wins immediately on the
 * drawn tile (P5) — a textbook Tenhou (heavenly hand) yakuman.
 */
public class PredefinedTenhouTableRecordItem extends MahjongTableRecordSimpleEastHandItem {

    public PredefinedTenhouTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected List<TheMahjongTile> handTiles() {
        return List.of(
                m(1), m(2), m(3),
                p(1), p(2), p(3),
                s(1), s(2), s(3),
                s(7), s(8), s(9),
                p(5),
                p(5));
    }

    @Override
    protected List<TheMahjongTile> wallTiles() {
        return List.of();
    }

    /** Tenhou requires the dealer's first uninterrupted draw — keep the
     *  fresh first-round state instead of seeding a phantom prior discard. */
    @Override
    protected boolean breakFirstRoundUninterrupted() {
        return false;
    }
}
