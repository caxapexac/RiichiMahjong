package com.mahjongcore.yaku.yakuman;

import com.mahjongcore.MahjongGeneralSituation;
import com.mahjongcore.MahjongPersonalSituation;

public class RenhouResolver implements YakumanResolver {
    private final MahjongGeneralSituation generalSituation;
    private final MahjongPersonalSituation personalSituation;

    public RenhouResolver(MahjongGeneralSituation generalSituation, MahjongPersonalSituation personalSituation) {
        this.generalSituation = generalSituation;
        this.personalSituation = personalSituation;
    }

    @Override
    public Yakuman getYakuman() {
        return Yakuman.RENHOU;
    }

    @Override
    public boolean isMatch() {
        // avoid NullPointerException
        if (generalSituation == null || personalSituation == null) return false;
        return generalSituation.isFirstRound() && !personalSituation.isTsumo() && !personalSituation.isParent();
    }
}
