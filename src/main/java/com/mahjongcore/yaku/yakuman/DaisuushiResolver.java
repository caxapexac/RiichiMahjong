package com.mahjongcore.yaku.yakuman;

import com.mahjongcore.hands.Kotsu;
import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.tile.TileType;

import java.util.List;

import static com.mahjongcore.yaku.yakuman.Yakuman.DAISUUSHI;

/**
 * (big four winds): triplets or quads of all four wind tiles.
 *
 * @author knn
 */
public class DaisuushiResolver implements YakumanResolver {
    private final Yakuman yakuman = DAISUUSHI;

    private final List<Kotsu> kotsuList;

    public DaisuushiResolver(MentsuComp comp) {
        kotsuList = comp.getKotsuKantsu();
    }

    public Yakuman getYakuman() {
        return yakuman;
    }

    public boolean isMatch() {
        int fonpaiCount = 0;
        for (Kotsu kotsu : kotsuList) {
            if (kotsu.getTile().getType() == TileType.FONPAI) {
                fonpaiCount++;
            }
        }

        return fonpaiCount == 4;
    }
}
