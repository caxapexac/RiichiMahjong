package com.mahjongcore.yaku.normals;


import com.mahjongcore.hands.Kotsu;
import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.hands.Shuntsu;
import com.mahjongcore.hands.Toitsu;

import java.util.List;

import static com.mahjongcore.yaku.normals.NormalYaku.HONROUTOU;

/**
 * (all terminals and honors): all tiles are terminals or honors.
 */
public class HonroutouResolver implements NormalYakuResolver {
    private final NormalYaku yakuEnum = HONROUTOU;

    private List<Shuntsu> shuntsuList;
    private List<Toitsu> toitsuList;
    private List<Kotsu> kotsuList;

    public HonroutouResolver(MentsuComp comp) {
        shuntsuList = comp.getShuntsuList();
        toitsuList = comp.getToitsuList();
        kotsuList = comp.getKotsuKantsu();
    }

    public NormalYaku getNormalYaku() {
        return yakuEnum;
    }

    /**
     * Returns false as soon as any non-terminal/non-honor tile is found.
     *
     * @return true if honrohtou
     */
    public boolean isMatch() {
        if (shuntsuList.size() > 0) {
            return false;
        }
        for (Toitsu toitsu : toitsuList) {
            int num = toitsu.getTile().getNumber();
            if (1 < num && num < 9) {
                return false;
            }
        }

        for (Kotsu kotsu : kotsuList) {
            int num = kotsu.getTile().getNumber();
            if (1 < num && num < 9) {
                return false;
            }
        }
        return true;
    }
}
