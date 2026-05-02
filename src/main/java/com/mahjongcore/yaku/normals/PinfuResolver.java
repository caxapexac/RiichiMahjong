package com.mahjongcore.yaku.normals;


import com.mahjongcore.MahjongGeneralSituation;
import com.mahjongcore.MahjongPersonalSituation;
import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.hands.Shuntsu;
import com.mahjongcore.hands.Toitsu;
import com.mahjongcore.tile.Tile;

import java.util.List;

import static com.mahjongcore.tile.TileType.SANGEN;
import static com.mahjongcore.yaku.normals.NormalYaku.PINFU;

/**
 * all melds are sequences, jantou is not a yakuhai tile, and the wait is two-sided (ryanmen).
 */
public class PinfuResolver extends SituationResolver implements NormalYakuResolver {
    private final NormalYaku yakuEnum = PINFU;

    private final Toitsu janto;
    private final int shuntsuCount;
    private final List<Shuntsu> shuntsuList;
    private final Tile last;


    public PinfuResolver(MentsuComp comp, MahjongGeneralSituation generalSituation, MahjongPersonalSituation personalSituation) {
        super(generalSituation, personalSituation);
        janto = comp.getJanto();
        shuntsuCount = comp.getShuntsuCount();
        shuntsuList = comp.getShuntsuList();
        last = comp.getLast();
    }

    public NormalYaku getNormalYaku() {
        return yakuEnum;
    }

    public boolean isMatch() {
        if (shuntsuCount < 4) {
            return false;
        }
        Tile janto = this.janto.getTile();
        if (janto.getType() == SANGEN) {
            return false;
        }

        if (!isSituationsNull()) {
            if (janto == generalSituation.getBakaze()) {
                return false;
            }
            if (janto == personalSituation.getJikaze()) {
                return false;
            }
        }

        boolean isRyanmen = false;
        for (Shuntsu shuntsu : shuntsuList) {
            if (shuntsu.isOpen()) {
                return false;
            }

            if (isRyanmen(shuntsu, last)) {
                isRyanmen = true;
            }
        }

        return isRyanmen;
    }

    /**
     * @param shuntsu sequence to test
     * @param last    winning tile
     * @return true if shuntsu completes a two-sided wait (ryanmen)
     */
    private boolean isRyanmen(Shuntsu shuntsu, Tile last) {
        if (shuntsu.getTile().getType() != last.getType()) {
            return false;
        }

        int shuntsuNum = shuntsu.getTile().getNumber();
        int lastNum = last.getNumber();
        if (shuntsuNum == 2 && lastNum == 1) {
            return true;
        }

        if (shuntsuNum == 8 && lastNum == 9) {
            return true;
        }

        int i = shuntsuNum - lastNum;
        if (i == 1 || i == -1) {
            return true;
        }

        return false;
    }
}
