package com.mahjongcore.yaku.normals;

import com.mahjongcore.MahjongGeneralSituation;
import com.mahjongcore.MahjongPersonalSituation;

import static com.mahjongcore.yaku.normals.NormalYaku.CHANKAN;

public class ChankanResolver extends SituationResolver implements NormalYakuResolver {

    public ChankanResolver(MahjongGeneralSituation generalSituation, MahjongPersonalSituation personalSituation) {
        super(generalSituation, personalSituation);
    }

    @Override
    public NormalYaku getNormalYaku() {
        return CHANKAN;
    }

    @Override
    public boolean isMatch() {
        if (isSituationsNull()) {
            return false;
        }
        return personalSituation.isChankan();
    }
}
