package com.mahjongcore.yaku.yakuman;

import com.mahjongcore.hands.MentsuComp;

import static com.mahjongcore.yaku.yakuman.Yakuman.SUUKANTSU;

/**
 * (four quads): all four melds are kantsu.
 */
public class SuukantsuResolver implements YakumanResolver {
    private final int kantsuCount;
    private final Yakuman yakuman = SUUKANTSU;

    public SuukantsuResolver(MentsuComp comp) {
        kantsuCount = comp.getKantsuCount();
    }

    public Yakuman getYakuman() {
        return yakuman;
    }

    public boolean isMatch() {
        return kantsuCount == 4;
    }
}
