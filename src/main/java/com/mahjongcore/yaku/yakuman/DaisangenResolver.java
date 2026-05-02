package com.mahjongcore.yaku.yakuman;

import com.mahjongcore.hands.Kotsu;
import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.tile.TileType;

import java.util.List;

import static com.mahjongcore.yaku.yakuman.Yakuman.DAISANGEN;

/**
 * (big three dragons): triplets or quads of all three dragon tiles.
 */
public class DaisangenResolver implements YakumanResolver {
    private final Yakuman yakuman = DAISANGEN;

    private final List<Kotsu> kotsuList;

    public DaisangenResolver(MentsuComp comp) {
        kotsuList = comp.getKotsuKantsu();
    }

    public Yakuman getYakuman() {
        return yakuman;
    }

    public boolean isMatch() {
        int sangenCount = 0;
        for (Kotsu kotsu : kotsuList) {
            if (kotsu.getTile().getType() == TileType.SANGEN) {
                sangenCount++;
            }
        }

        return sangenCount == 3;
    }
}
