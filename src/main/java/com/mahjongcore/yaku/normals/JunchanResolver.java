package com.mahjongcore.yaku.normals;

import com.mahjongcore.hands.Kotsu;
import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.hands.Shuntsu;
import com.mahjongcore.hands.Toitsu;

import java.util.List;

import static com.mahjongcore.yaku.normals.NormalYaku.JUNCHAN;

/**
 * (pure outside hand): all melds contain a terminal tile (no honors).
 */
public class JunchanResolver implements NormalYakuResolver {
    private final Toitsu janto;
    private final List<Shuntsu> shuntsuList;
    private final List<Kotsu> kotsuList;
    private NormalYaku yakuEnum = JUNCHAN;

    public JunchanResolver(MentsuComp comp) {
        janto = comp.getJanto();
        shuntsuList = comp.getShuntsuList();
        kotsuList = comp.getKotsuKantsu();
    }

    public NormalYaku getNormalYaku() {
        return yakuEnum;
    }

    public boolean isMatch() {
        if (janto == null) {
            return false;
        }
        for (Shuntsu shuntsu : shuntsuList) {
            int num = shuntsu.getTile().getNumber();
            if (num != 2 && num != 8) {
                return false;
            }
        }

        for (Kotsu kotsu : kotsuList) {
            int num = kotsu.getTile().getNumber();
            if (num != 1 && num != 9) {
                return false;
            }
        }

        return true;
    }
}
