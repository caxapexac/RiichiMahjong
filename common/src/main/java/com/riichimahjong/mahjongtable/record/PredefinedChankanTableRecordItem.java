package com.riichimahjong.mahjongtable.record;

import com.themahjong.TheMahjongTile;
import java.util.List;

/**
 * Dealer hand: 444m 555m 666m 78m 77p, tenpai with kanchan waits, drawn S4 (waste).
 * Wall queues S5, M9, S5, S5, S5, M9 to feed bot draws. Note: the legacy fixture
 * pre-seeded a north bot with two M9s so a kakan opportunity would arise — that
 * pre-existing pon isn't expressible via {@link com.themahjong.TheMahjongFixedDeal}
 * alone, so the chankan trigger requires further library work. The fixture
 * preserves the dealer setup for now.
 */
public class PredefinedChankanTableRecordItem extends MahjongTableRecordSimpleEastHandItem {

    public PredefinedChankanTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected List<TheMahjongTile> handTiles() {
        return List.of(
                m(4), m(4), m(4),
                m(5), m(5), m(5),
                m(6), m(6), m(6),
                m(7), m(8),
                p(7), p(7),
                s(4));
    }

    @Override
    protected List<TheMahjongTile> wallTiles() {
        return List.of(s(5), m(9), s(5), s(5), s(5), m(9));
    }
}
