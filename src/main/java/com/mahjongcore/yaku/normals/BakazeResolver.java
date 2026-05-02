package com.mahjongcore.yaku.normals;

import com.mahjongcore.MahjongGeneralSituation;
import com.mahjongcore.MahjongPersonalSituation;
import com.mahjongcore.hands.Kotsu;
import com.mahjongcore.hands.MentsuComp;

public class BakazeResolver extends SituationResolver implements NormalYakuResolver {
    private final MentsuComp comp;

    public BakazeResolver(MentsuComp comp, MahjongGeneralSituation generalSituation, MahjongPersonalSituation personalSituation) {
        super(generalSituation, personalSituation);
        this.comp = comp;
    }

    @Override
    public NormalYaku getNormalYaku() {
        return NormalYaku.BAKAZE;
    }

    @Override
    public boolean isMatch() {
        if ((isSituationsNull())) {
            return false;
        }
        for (Kotsu kotsu : comp.getKotsuKantsu()) {
            if (kotsu.getTile() == generalSituation.getBakaze()) {
                return true;
            }
        }
        return false;
    }
}
