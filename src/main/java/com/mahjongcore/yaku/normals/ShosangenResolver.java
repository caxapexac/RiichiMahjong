package com.mahjongcore.yaku.normals;


import com.mahjongcore.hands.Kotsu;
import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.hands.Toitsu;
import com.mahjongcore.tile.TileType;

import java.util.List;

import static com.mahjongcore.yaku.normals.NormalYaku.SHOUSANGEN;

/**
 * (little three dragons): one dragon as jantou, the other two as triplets or quads.
 */
public class ShosangenResolver implements NormalYakuResolver {
    private final NormalYaku yakuEnum = SHOUSANGEN;

    private final Toitsu janto;
    private final List<Kotsu> kotsuList;

    public ShosangenResolver(MentsuComp comp) {
        janto = comp.getJanto();
        kotsuList = comp.getKotsuKantsu();
    }

    public NormalYaku getNormalYaku() {
        return yakuEnum;
    }

    public boolean isMatch() {
        if (janto == null) {
            return false;
        }

        if (janto.getTile().getType() != TileType.SANGEN) {
            return false;
        }
        int count = 0;
        for (Kotsu kotsu : kotsuList) {
            if (kotsu.getTile().getType() == TileType.SANGEN) {
                count++;
            }
            if (count == 2) {
                return true;
            }
        }

        return false;
    }
}
