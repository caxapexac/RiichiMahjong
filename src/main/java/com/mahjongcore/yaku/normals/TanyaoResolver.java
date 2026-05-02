package com.mahjongcore.yaku.normals;

import com.mahjongcore.hands.Mentsu;
import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.hands.Shuntsu;

import java.util.List;

import static com.mahjongcore.yaku.normals.NormalYaku.TANYAO;

/**
 * (all simples): no terminals or honors; all tiles are 2-8.
 */
public class TanyaoResolver implements NormalYakuResolver {
    private final NormalYaku yakuEnum = TANYAO;
    private final List<Mentsu> allMentsu;

    public TanyaoResolver(MentsuComp comp) {
        allMentsu = comp.getAllMentsu();
    }

    public NormalYaku getNormalYaku() {
        return yakuEnum;
    }

    public boolean isMatch() {
        for (Mentsu mentsu : allMentsu) {
            int number = mentsu.getTile().getNumber();
            if (number == 0 || number == 1 || number == 9) {
                return false;
            }

            int shuntsuNum = mentsu.getTile().getNumber();
            boolean isEdgeShuntsu = (shuntsuNum == 2 || shuntsuNum == 8);
            if (mentsu instanceof Shuntsu && isEdgeShuntsu) {
                return false;
            }
        }

        return true;
    }
}
