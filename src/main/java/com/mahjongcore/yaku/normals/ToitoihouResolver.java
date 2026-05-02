package com.mahjongcore.yaku.normals;

import com.mahjongcore.hands.MentsuComp;

import static com.mahjongcore.yaku.normals.NormalYaku.TOITOIHOU;

/**
 * (all triplets): four triplets or quads.
 */
public class ToitoihouResolver implements NormalYakuResolver {
    private final NormalYaku yakuEnum = TOITOIHOU;
    private final int kotsuCount;

    public ToitoihouResolver(MentsuComp comp) {
        kotsuCount = comp.getKantsuCount() + comp.getKotsuCount();
    }

    public NormalYaku getNormalYaku() {
        return yakuEnum;
    }

    public boolean isMatch() {
        return kotsuCount == 4;
    }
}
