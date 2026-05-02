package com.mahjongcore;

import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.yaku.normals.*;
import com.mahjongcore.yaku.yakuman.*;

import java.util.HashSet;
import java.util.Set;

/**
 * TODO: to be able to customize Yaku for use
 */
public class MahjongYakuConfig {
    public static Set<YakumanResolver> getYakumanResolverSet(MentsuComp comp, MahjongGeneralSituation generalSituation, MahjongPersonalSituation personalSituation) {
        // KOKUSHIMUSO is not
        Set<YakumanResolver> yakumanResolverSet = new HashSet<>(9);
        yakumanResolverSet.add(new ChinroutouResolver(comp));
        yakumanResolverSet.add(new ChuurenpoutouResolver(comp));
        yakumanResolverSet.add(new DaisangenResolver(comp));
        yakumanResolverSet.add(new DaisuushiResolver(comp));
        yakumanResolverSet.add(new RyuuiisouResolver(comp));
        yakumanResolverSet.add(new ShousuushiResolver(comp));
        yakumanResolverSet.add(new SuuankouResolver(comp));
        yakumanResolverSet.add(new SuukantsuResolver(comp));
        yakumanResolverSet.add(new TsuuiisouResolver(comp));

        yakumanResolverSet.add(new RenhouResolver(generalSituation, personalSituation));
        yakumanResolverSet.add(new ChihouResolver(generalSituation, personalSituation));
        yakumanResolverSet.add(new TenhouResolver(generalSituation, personalSituation));

        return yakumanResolverSet;
    }

    public static Set<NormalYakuResolver> getNormalYakuResolverSet(MentsuComp comp, MahjongGeneralSituation generalSituation, MahjongPersonalSituation personalSituation) {
        Set<NormalYakuResolver> normalYakuResolverSet = new HashSet<>(20);
        normalYakuResolverSet.add(new ChantaResolver(comp));
        normalYakuResolverSet.add(new ChunResolver(comp));
        normalYakuResolverSet.add(new ChinitsuResolver(comp));
        normalYakuResolverSet.add(new ChitoitsuResolver(comp));
        normalYakuResolverSet.add(new HakuResolver(comp));
        normalYakuResolverSet.add(new HatsuResolver(comp));
        normalYakuResolverSet.add(new HonitsuResolver(comp));
        normalYakuResolverSet.add(new HonroutouResolver(comp));
        normalYakuResolverSet.add(new IkkitsukanResolver(comp));
        normalYakuResolverSet.add(new IpeikouResolver(comp));
        normalYakuResolverSet.add(new JunchanResolver(comp));
        normalYakuResolverSet.add(new RyanpeikouResolver(comp));
        normalYakuResolverSet.add(new SanankouResolver(comp));
        normalYakuResolverSet.add(new SankantsuResolver(comp));
        normalYakuResolverSet.add(new SanshokudoujunResolver(comp));
        normalYakuResolverSet.add(new SanshokudouhkouResolver(comp));
        normalYakuResolverSet.add(new ShosangenResolver(comp));
        normalYakuResolverSet.add(new TanyaoResolver(comp));
        normalYakuResolverSet.add(new ToitoihouResolver(comp));

        normalYakuResolverSet.add(new PinfuResolver(comp, generalSituation, personalSituation));
        normalYakuResolverSet.add(new TsumoResolver(comp, generalSituation, personalSituation));
        normalYakuResolverSet.add(new JikazeResolver(comp, generalSituation, personalSituation));
        normalYakuResolverSet.add(new BakazeResolver(comp, generalSituation, personalSituation));
        normalYakuResolverSet.add(new IppatsuResolver(generalSituation, personalSituation));
        normalYakuResolverSet.add(new HouteiResolver(generalSituation, personalSituation));
        normalYakuResolverSet.add(new HaiteiResolver(generalSituation, personalSituation));
        normalYakuResolverSet.add(new RiichiResolver(generalSituation, personalSituation));
        normalYakuResolverSet.add(new RinshankaihouResolver(comp, personalSituation));
        normalYakuResolverSet.add(new ChankanResolver(generalSituation, personalSituation));
        normalYakuResolverSet.add(new DoubleRiichiResolver(generalSituation, personalSituation));

        return normalYakuResolverSet;
    }
}
