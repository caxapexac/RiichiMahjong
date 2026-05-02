package com.mahjongcore.yaku.normals;

import com.mahjongcore.hands.Kotsu;
import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.tile.TileType;

import java.util.List;

import static com.mahjongcore.yaku.normals.NormalYaku.SANSHOKUDOUHKOU;

/**
 * (three-color triplets): same-numbered triplet or quad in all three suits.
 */
public class SanshokudouhkouResolver extends SanshokuResolver implements NormalYakuResolver {
    private final NormalYaku yakuEnum = SANSHOKUDOUHKOU;
    private final int kotsuCount;
    private final List<Kotsu> kotsuList;

    public SanshokudouhkouResolver(MentsuComp comp) {
        kotsuCount = comp.getKotsuCount() + comp.getKantsuCount();
        kotsuList = comp.getKotsuKantsu();
    }

    public NormalYaku getNormalYaku() {
        return yakuEnum;
    }

    public boolean isMatch() {
        if (kotsuCount < 3) {
            return false;
        }

        Kotsu candidate = null;
        for (Kotsu kotsu : kotsuList) {
            TileType shuntsuType = kotsu.getTile().getType();
            int shuntsuNum = kotsu.getTile().getNumber();

            if (candidate == null) {
                candidate = kotsu;
                continue;
            }

            if (candidate.getTile().getNumber() == shuntsuNum) {
                detectType(shuntsuType);
                detectType(candidate.getTile().getType());
            } else {
                candidate = kotsu;
            }
        }
        return manzu && pinzu && sohzu;
    }
}
