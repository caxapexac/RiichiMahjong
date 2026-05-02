package com.mahjongcore.yaku.normals;


import com.mahjongcore.hands.Kotsu;
import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.tile.Tile;

import java.util.List;

import static com.mahjongcore.yaku.normals.NormalYaku.HATSU;

/**
 * (green dragon): hand contains a triplet or quad of the green dragon (HAT).
 */
public class HatsuResolver implements NormalYakuResolver {
    private final NormalYaku yakuEnum = HATSU;
    private final List<Kotsu> kotsuList;

    public HatsuResolver(MentsuComp comp) {
        kotsuList = comp.getKotsuKantsu();
    }

    public NormalYaku getNormalYaku() {
        return yakuEnum;
    }

    public boolean isMatch() {
        for (Kotsu kotsu : kotsuList) {
            if (kotsu.getTile() == Tile.HAT) {
                return true;
            }
        }

        return false;
    }
}
