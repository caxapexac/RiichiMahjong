package com.mahjongcore.yaku.normals;


import com.mahjongcore.hands.Kotsu;
import com.mahjongcore.hands.MentsuComp;

import java.util.List;

import static com.mahjongcore.tile.Tile.HAK;
import static com.mahjongcore.yaku.normals.NormalYaku.HAKU;

/**
 * (white dragon): hand contains a triplet or quad of the white dragon (HAK).
 */
public class HakuResolver implements NormalYakuResolver {
    private final NormalYaku yakuEnum = HAKU;
    private final List<Kotsu> kotsuList;

    public HakuResolver(MentsuComp comp) {
        kotsuList = comp.getKotsuKantsu();
    }

    public NormalYaku getNormalYaku() {
        return yakuEnum;
    }

    public boolean isMatch() {
        for (Kotsu kotsu : kotsuList) {
            if (kotsu.getTile() == HAK) {
                return true;
            }
        }
        return false;
    }

}