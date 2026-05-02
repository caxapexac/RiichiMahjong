package com.mahjongcore.yaku.normals;


import com.mahjongcore.hands.Kotsu;
import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.tile.Tile;

import java.util.List;

/**
 * (red dragon): hand contains a triplet or quad of the red dragon (CHN).
 */
public class ChunResolver implements NormalYakuResolver {
    private final NormalYaku yakuEnum = NormalYaku.CHUN;
    private final List<Kotsu> kotsuList;

    public ChunResolver(MentsuComp comp) {
        kotsuList = comp.getKotsuKantsu();
    }

    public NormalYaku getNormalYaku() {
        return yakuEnum;
    }

    public boolean isMatch() {
        for (Kotsu kotsu : kotsuList) {
            if (kotsu.getTile() == Tile.CHN) {
                return true;
            }
        }
        return false;
    }
}
