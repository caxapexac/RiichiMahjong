package com.mahjongcore.yaku.normals;


import com.mahjongcore.hands.MentsuComp;

import static com.mahjongcore.yaku.normals.NormalYaku.SANKANTSU;

/**
 * hand contains exactly three quads (kantsu).
 */
public class SankantsuResolver implements NormalYakuResolver {
    private final NormalYaku yakuEnum = SANKANTSU;
    private final int kantsuCount;

    public SankantsuResolver(MentsuComp comp) {
        kantsuCount = comp.getKantsuCount();
    }

    public NormalYaku getNormalYaku() {
        return yakuEnum;
    }

    public boolean isMatch() {
        return kantsuCount == 3;
    }
}
