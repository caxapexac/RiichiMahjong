package com.mahjongcore.yaku.normals;

import com.mahjongcore.MahjongGeneralSituation;
import com.mahjongcore.MahjongPersonalSituation;

/**
 * winning with the last drawn tile (tsumo on the last tile).
 */
public class HaiteiResolver extends SituationResolver implements NormalYakuResolver {

    public HaiteiResolver(MahjongGeneralSituation generalSituation, MahjongPersonalSituation personalSituation) {
        super(generalSituation, personalSituation);
    }

    @Override
    public NormalYaku getNormalYaku() {
        return NormalYaku.HAITEI;
    }

    @Override
    public boolean isMatch() {
        if (isSituationsNull()) {
            return false;
        }

        return generalSituation.isHoutei() && personalSituation.isTsumo();
    }
}
