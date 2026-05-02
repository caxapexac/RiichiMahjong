package com.mahjongcore.yaku.yakuman;

import com.mahjongcore.hands.Kotsu;
import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.hands.Toitsu;

import java.util.List;

import static com.mahjongcore.yaku.yakuman.Yakuman.CHINROUTOU;

/**
 * (all terminals): all tiles are terminals (1 and 9 only).
 */
public class ChinroutouResolver implements YakumanResolver {
    private final Yakuman yakuman = CHINROUTOU;
    private final int totalKotsuKantsu;
    private final List<Kotsu> kotsuList;
    private final Toitsu janto;

    public ChinroutouResolver(MentsuComp comp) {
        totalKotsuKantsu = comp.getKotsuCount() + comp.getKantsuCount();
        kotsuList = comp.getKotsuKantsu();
        janto = comp.getJanto();
    }

    public Yakuman getYakuman() {
        return yakuman;
    }

    /**
     * Returns false as soon as a non-terminal tile is found.
     *
     * @return true if chinrohtou
     */
    public boolean isMatch() {
        if (totalKotsuKantsu != 4) {
            return false;
        }

        int tileNum = janto.getTile().getNumber();
        if (tileNum != 1 && tileNum != 9) {
            return false;
        }

        for (Kotsu kotsu : kotsuList) {
            tileNum = kotsu.getTile().getNumber();
            if (tileNum != 1 && tileNum != 9) {
                return false;
            }
        }

        return true;
    }
}
