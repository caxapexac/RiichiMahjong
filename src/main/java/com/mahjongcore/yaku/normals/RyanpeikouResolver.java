package com.mahjongcore.yaku.normals;

import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.hands.Shuntsu;

import java.util.List;

import static com.mahjongcore.yaku.normals.NormalYaku.RYANPEIKOU;

/**
 * (two double sequences): two pairs of identical sequences. Exclusive with ipeiko.
 */
public class RyanpeikouResolver extends PeikouResolver implements NormalYakuResolver {
    private final NormalYaku yakuEnum = RYANPEIKOU;

    private final List<Shuntsu> shuntsuList;

    public RyanpeikouResolver(MentsuComp comp) {
        shuntsuList = comp.getShuntsuList();
    }

    public NormalYaku getNormalYaku() {
        return yakuEnum;
    }

    public boolean isMatch() {
        return peiko(shuntsuList) == 2;
    }
}
