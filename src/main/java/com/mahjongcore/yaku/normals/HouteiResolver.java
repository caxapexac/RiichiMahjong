package com.mahjongcore.yaku.normals;

import com.mahjongcore.MahjongGeneralSituation;
import com.mahjongcore.MahjongPersonalSituation;

/**
 * winning with ron on the last discarded tile.
 */
public class HouteiResolver extends SituationResolver implements NormalYakuResolver {

    public HouteiResolver(MahjongGeneralSituation generalSituation, MahjongPersonalSituation personalSituation) {
        super(generalSituation, personalSituation);
    }

    @Override
    public NormalYaku getNormalYaku() {
        return NormalYaku.HOUTEI;
    }

    @Override
    public boolean isMatch() {
        if (isSituationsNull()) {
            return false;
        }
        return generalSituation.isHoutei() && !personalSituation.isTsumo();
    }
}
