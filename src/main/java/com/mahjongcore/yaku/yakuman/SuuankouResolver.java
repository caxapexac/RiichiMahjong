package com.mahjongcore.yaku.yakuman;

import com.mahjongcore.hands.Kotsu;
import com.mahjongcore.hands.MentsuComp;

import java.util.List;

import static com.mahjongcore.yaku.yakuman.Yakuman.SUUANKOU;

/**
 * (four concealed triplets): four closed triplets or quads.
 */
public class SuuankouResolver implements YakumanResolver {
    private final int count;
    private final List<Kotsu> kotsuList;
    private final Yakuman yakuman = SUUANKOU;

    public SuuankouResolver(MentsuComp comp) {
        kotsuList = comp.getKotsuKantsu();
        count = comp.getKotsuCount() + comp.getKantsuCount();
    }

    public Yakuman getYakuman() {
        return yakuman;
    }

    public boolean isMatch() {
        if (count < 4) {
            return false;
        }
        for (Kotsu kotsu : kotsuList) {
            if (kotsu.isOpen()) {
                return false;
            }
        }

        return true;
    }
}
