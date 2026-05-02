package com.mahjongcore.yaku.yakuman;

import com.mahjongcore.MahjongGeneralSituation;
import com.mahjongcore.MahjongPersonalSituation;

public class TenhouResolver implements YakumanResolver {
    private final MahjongGeneralSituation generalSituation;
    private final MahjongPersonalSituation personalSituation;

    public TenhouResolver(MahjongGeneralSituation generalSituation, MahjongPersonalSituation personalSituation) {
        this.generalSituation = generalSituation;
        this.personalSituation = personalSituation;
    }

    @Override
    public Yakuman getYakuman() {
        return Yakuman.TENHOU;
    }

    @Override
    public boolean isMatch() {
        // avoid NullPointerException
        if (generalSituation == null || personalSituation == null) return false;

        return generalSituation.isFirstRound() && personalSituation.isParent() && personalSituation.isTsumo();
    }
}
