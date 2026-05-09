package com.riichimahjong.mahjongtable.record;

import com.themahjong.TheMahjongTile;
import java.util.List;

/**
 * Dealer holds a 13-tile chuurenpoutou tenpai (1112345678999 in MANZU); drawn tile
 * is East wind. Wall starts with M1, M5 — letting the dealer test discards and
 * subsequent draws against a 9-wait shape.
 */
public class PredefinedChuurenpoutouTableRecordItem extends MahjongTableRecordSimpleEastHandItem {

    public PredefinedChuurenpoutouTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected List<TheMahjongTile> handTiles() {
        return List.of(
                m(1), m(1), m(1),
                m(2), m(3), m(4), m(5), m(6), m(7), m(8),
                m(9), m(9), m(9),
                w(TheMahjongTile.Wind.EAST));
    }

    @Override
    protected List<TheMahjongTile> wallTiles() {
        return List.of(m(1), m(5));
    }
}
