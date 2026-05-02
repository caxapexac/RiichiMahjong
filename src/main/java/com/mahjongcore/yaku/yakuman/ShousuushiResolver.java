package com.mahjongcore.yaku.yakuman;

import com.mahjongcore.hands.Kotsu;
import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.hands.Toitsu;

import java.util.List;

import static com.mahjongcore.tile.TileType.FONPAI;
import static com.mahjongcore.yaku.yakuman.Yakuman.SHOUSUUSHI;

/**
 * (little four winds): three wind triplets plus one wind pair.
 */
public class ShousuushiResolver implements YakumanResolver {
    private final Yakuman yakuman = SHOUSUUSHI;
    private final Toitsu janto;
    private final List<Kotsu> kotsuList;
    private final int kotsuCount;

    public ShousuushiResolver(MentsuComp comp) {
        janto = comp.getJanto();
        kotsuList = comp.getKotsuKantsu();
        kotsuCount = comp.getKotsuCount() + comp.getKantsuCount();
    }

    public Yakuman getYakuman() {
        return yakuman;
    }

    public boolean isMatch() {
        if (janto == null) {
            return false;
        }

        if (janto.getTile().getType() != FONPAI) {
            return false;
        }
        if (kotsuCount < 3) {
            return false;
        }

        int shosushiCount = 0;
        for (Kotsu kotsu : kotsuList) {
            if (kotsu.getTile().getType() == FONPAI) {
                shosushiCount++;
            }
        }
        return shosushiCount == 3;
    }
}
