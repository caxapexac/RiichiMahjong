package com.mahjongcore.yaku.normals;

import com.mahjongcore.MahjongPersonalSituation;
import com.mahjongcore.hands.MentsuComp;

public class RinshankaihouResolver implements NormalYakuResolver {
    private final MentsuComp comp;
    private final MahjongPersonalSituation personalSituation;

    public RinshankaihouResolver(MentsuComp comp, MahjongPersonalSituation personalSituation) {
        this.comp = comp;
        this.personalSituation = personalSituation;
    }

    @Override
    public NormalYaku getNormalYaku() {
        return NormalYaku.RINSHANKAIHOU;
    }

    @Override
    public boolean isMatch() {
        if (personalSituation == null) {
            return false;
        }

        if (comp.getKantsuCount() == 0) {
            return false;
        }

        return personalSituation.isRinshankaihou();
    }
}
