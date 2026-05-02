package com.mahjongcore.yaku.normals;


import com.mahjongcore.hands.Kotsu;
import com.mahjongcore.hands.MentsuComp;

import java.util.List;

import static com.mahjongcore.yaku.normals.NormalYaku.SANANKO;

/**
 * hand contains exactly three concealed triplets.
 */
public class SanankouResolver implements NormalYakuResolver {
    private final NormalYaku yakuEnum = SANANKO;
    private final List<Kotsu> kotsuList;
    private final int kotsuCount;

    public SanankouResolver(MentsuComp comp) {
        kotsuList = comp.getKotsuKantsu();
        kotsuCount = comp.getKotsuCount() + comp.getKantsuCount();
    }

    public NormalYaku getNormalYaku() {
        return yakuEnum;
    }

    public boolean isMatch() {
        if (kotsuCount < 3) {
            return false;
        }

        int ankoCount = 0;
        for (Kotsu kotsu : kotsuList) {
            if (!kotsu.isOpen()) {
                ankoCount++;
            }
        }
        return ankoCount == 3;
    }
}
