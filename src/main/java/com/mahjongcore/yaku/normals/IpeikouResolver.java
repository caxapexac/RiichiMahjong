package com.mahjongcore.yaku.normals;

import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.hands.Shuntsu;

import java.util.List;

import static com.mahjongcore.yaku.normals.NormalYaku.IIPEIKOU;

/**
 * (one double sequence): two identical sequences. Excluded when ryanpeiko applies.
 */
public class IpeikouResolver extends PeikouResolver implements NormalYakuResolver {
    private final NormalYaku yakuEnum = IIPEIKOU;
    private final List<Shuntsu> shuntsuList;

    public IpeikouResolver(MentsuComp comp) {
        shuntsuList = comp.getShuntsuList();
    }

    public NormalYaku getNormalYaku() {
        return yakuEnum;
    }

    public boolean isMatch() {
        return peiko(shuntsuList) == 1;
    }
}
