package com.mahjongcore.yaku.normals;

import com.mahjongcore.MahjongGeneralSituation;
import com.mahjongcore.MahjongPersonalSituation;

public abstract class SituationResolver {
    protected final MahjongGeneralSituation generalSituation;
    protected final MahjongPersonalSituation personalSituation;

    protected SituationResolver(MahjongGeneralSituation generalSituation, MahjongPersonalSituation personalSituation) {
        this.generalSituation = generalSituation;
        this.personalSituation = personalSituation;
    }

    protected boolean isSituationsNull() {
        return personalSituation == null || generalSituation == null;
    }
}
