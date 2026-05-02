package com.mahjongcore.yaku.yakuman;

import com.mahjongcore.hands.Kotsu;
import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.hands.Shuntsu;
import com.mahjongcore.hands.Toitsu;

import java.util.List;

import static com.mahjongcore.yaku.yakuman.Yakuman.TSUISOU;

/**
 * (all honors): all tiles are honor tiles (winds and dragons).
 */
public class TsuuiisouResolver implements YakumanResolver {
    private final Yakuman yakuman = TSUISOU;

    private final Toitsu janto;
    private final List<Shuntsu> shuntsuList;
    private final List<Toitsu> toitsuList;
    private final List<Kotsu> kotsuList;

    public TsuuiisouResolver(MentsuComp comp) {
        janto = comp.getJanto();
        shuntsuList = comp.getShuntsuList();
        toitsuList = comp.getToitsuList();
        kotsuList = comp.getKotsuKantsu();
    }

    public Yakuman getYakuman() {
        return yakuman;
    }

    public boolean isMatch() {
        if (shuntsuList.size() > 0) {
            return false;
        }
        if (janto == null) {
            for (Toitsu toitsu : toitsuList) {
                if (toitsu.getTile().getNumber() != 0) {
                    return false;
                }
            }
            return true;
        }

        if (janto.getTile().getNumber() != 0) {
            return false;
        }

        for (Kotsu kotsu : kotsuList) {
            if (kotsu.getTile().getNumber() != 0) {
                return false;
            }
        }

        return true;
    }
}
