package com.mahjongcore.yaku.yakuman;

import com.mahjongcore.MahjongGeneralSituation;
import com.mahjongcore.MahjongPersonalSituation;

public class ChihouResolver implements YakumanResolver {
    private final MahjongGeneralSituation generalSituation;
    private final MahjongPersonalSituation personalSituation;

    public ChihouResolver(MahjongGeneralSituation generalSituation, MahjongPersonalSituation personalSituation) {

        this.generalSituation = generalSituation;
        this.personalSituation = personalSituation;
    }

    @Override
    public Yakuman getYakuman() {
        return Yakuman.CHIHOU;
    }

    @Override
    public boolean isMatch() {
        // avoid NullPointerException
        if (generalSituation == null || personalSituation == null) return false;
        return generalSituation.isFirstRound() && personalSituation.isTsumo() && !personalSituation.isParent();
    }
}
