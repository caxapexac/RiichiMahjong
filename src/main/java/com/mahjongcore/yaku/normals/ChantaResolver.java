package com.mahjongcore.yaku.normals;


import com.mahjongcore.hands.Kotsu;
import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.hands.Shuntsu;

import static com.mahjongcore.yaku.normals.NormalYaku.CHANTA;

/**
 * All melds contain a terminal or honor tile.
 */
public class ChantaResolver implements NormalYakuResolver {
    private final NormalYaku yakuEnum = CHANTA;

    private MentsuComp comp;

    public ChantaResolver(MentsuComp comp) {
        this.comp = comp;
    }

    public NormalYaku getNormalYaku() {
        return yakuEnum;
    }

    public boolean isMatch() {
        if (comp.getJanto() == null) {
            return false;
        }
        int jantoNum = comp.getJanto().getTile().getNumber();
        if (jantoNum != 1 && jantoNum != 9 && jantoNum != 0) {
            return false;
        }

        if (comp.getShuntsuCount() == 0) {
            return false;
        }

        for (Shuntsu shuntsu : comp.getShuntsuList()) {
            int shuntsuNum = shuntsu.getTile().getNumber();
            if (shuntsuNum != 2 && shuntsuNum != 8) {
                return false;
            }
        }

        for (Kotsu kotsu : comp.getKotsuKantsu()) {
            int kotsuNum = kotsu.getTile().getNumber();
            if (kotsuNum != 1 && kotsuNum != 9 && kotsuNum != 0) {
                return false;
            }
        }

        return true;
    }
}
