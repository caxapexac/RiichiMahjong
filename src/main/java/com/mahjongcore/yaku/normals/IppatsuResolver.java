package com.mahjongcore.yaku.normals;

import com.mahjongcore.MahjongGeneralSituation;
import com.mahjongcore.MahjongPersonalSituation;

public class IppatsuResolver extends SituationResolver implements NormalYakuResolver {

    public IppatsuResolver(MahjongGeneralSituation generalSituation, MahjongPersonalSituation personalSituation) {
        super(generalSituation, personalSituation);
    }

    @Override
    public NormalYaku getNormalYaku() {
        return NormalYaku.IPPATSU;
    }

    @Override
    public boolean isMatch() {
        if (isSituationsNull()) {
            return false;
        }
        return personalSituation.isIppatsu();
    }
}
