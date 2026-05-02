package com.mahjongcore.yaku.normals;

import com.mahjongcore.MahjongGeneralSituation;
import com.mahjongcore.MahjongPersonalSituation;
import com.mahjongcore.hands.Mentsu;
import com.mahjongcore.hands.MentsuComp;

public class TsumoResolver implements NormalYakuResolver {
    private final MentsuComp comp;
    private final MahjongGeneralSituation generalSituation;
    private final MahjongPersonalSituation personalSituation;

    public TsumoResolver(MentsuComp comp, MahjongGeneralSituation generalSituation, MahjongPersonalSituation personalSituation) {
        this.comp = comp;
        this.generalSituation = generalSituation;
        this.personalSituation = personalSituation;
    }

    @Override
    public NormalYaku getNormalYaku() {
        return NormalYaku.TSUMO;
    }

    @Override
    public boolean isMatch() {
        if (generalSituation == null || personalSituation == null) {
            return false;
        }
        boolean isOpen = false;
        for (Mentsu mentsu : comp.getAllMentsu()) {
            if (mentsu.isOpen()) {
                isOpen = true;
            }
        }
        return personalSituation.isTsumo() && !isOpen;
    }
}
