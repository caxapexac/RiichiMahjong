package com.mahjongcore.yaku.normals;

import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.hands.Shuntsu;
import com.mahjongcore.tile.TileType;

import java.util.List;

import static com.mahjongcore.yaku.normals.NormalYaku.SANSHOKUDOHJUN;

/**
 * (three-color straight): same sequence in all three suits.
 */
public class SanshokudoujunResolver extends SanshokuResolver implements NormalYakuResolver {
    private final NormalYaku yakuEnum = SANSHOKUDOHJUN;
    private final int shuntsuCount;
    private final List<Shuntsu> shuntsuList;

    public SanshokudoujunResolver(MentsuComp comp) {
        shuntsuCount = comp.getShuntsuCount();
        shuntsuList = comp.getShuntsuList();
    }

    public NormalYaku getNormalYaku() {
        return yakuEnum;
    }

    public boolean isMatch() {
        if (shuntsuCount < 3) {
            return false;
        }

        Shuntsu candidate = null;

        for (Shuntsu shuntsu : shuntsuList) {
            TileType shuntsuType = shuntsu.getTile().getType();
            int shuntsuNum = shuntsu.getTile().getNumber();

            if (candidate == null) {
                candidate = shuntsu;
                continue;
            }

            if (candidate.getTile().getNumber() == shuntsuNum) {
                detectType(shuntsuType);
                detectType(candidate.getTile().getType());
            } else {
                candidate = shuntsu;
            }
        }
        return manzu && pinzu && sohzu;
    }
}
