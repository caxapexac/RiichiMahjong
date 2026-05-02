package com.mahjongcore.yaku.normals;

import com.mahjongcore.hands.MentsuComp;

import static com.mahjongcore.yaku.normals.NormalYaku.CHITOITSU;

/**
 * (seven pairs): hand consists entirely of seven distinct pairs.
 */
public class ChitoitsuResolver implements NormalYakuResolver {
    private final NormalYaku yakuEnum = CHITOITSU;
    private final int toitsuCount;

    public ChitoitsuResolver(MentsuComp comp) {
        toitsuCount = comp.getToitsuCount();
    }

    public NormalYaku getNormalYaku() {
        return yakuEnum;
    }

    public boolean isMatch() {
        return toitsuCount == 7;
    }
}
