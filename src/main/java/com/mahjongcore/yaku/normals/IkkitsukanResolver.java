package com.mahjongcore.yaku.normals;

import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.hands.Shuntsu;
import com.mahjongcore.tile.TileType;

import java.util.ArrayList;
import java.util.List;

import static com.mahjongcore.yaku.normals.NormalYaku.IKKITSUKAN;

/**
 * (pure straight): sequences 1-2-3, 4-5-6, and 7-8-9 of the same suit.
 */
public class IkkitsukanResolver implements NormalYakuResolver {
    private final NormalYaku yakuEnum = IKKITSUKAN;

    private List<Shuntsu> shuntsuList;
    private int shuntsuCount;

    public IkkitsukanResolver(MentsuComp comp) {
        shuntsuList = comp.getShuntsuList();
        shuntsuCount = comp.getShuntsuCount();
    }

    public NormalYaku getNormalYaku() {
        return yakuEnum;
    }

    public boolean isMatch() {
        if (shuntsuCount < 3) {
            return false;
        }

        List<Shuntsu> manzu = new ArrayList<>(4);
        List<Shuntsu> sohzu = new ArrayList<>(4);
        List<Shuntsu> pinzu = new ArrayList<>(4);

        for (Shuntsu shuntsu : shuntsuList) {
            TileType type = shuntsu.getTile().getType();
            if (type == TileType.MANZU) {
                manzu.add(shuntsu);
            } else if (type == TileType.SOHZU) {
                sohzu.add(shuntsu);
            } else if (type == TileType.PINZU) {
                pinzu.add(shuntsu);
            }
        }

        if (manzu.size() >= 3) {
            return isIkkitsukan(manzu);
        }
        if (sohzu.size() >= 3) {
            return isIkkitsukan(sohzu);
        }
        if (pinzu.size() >= 3) {
            return isIkkitsukan(pinzu);
        }
        return false;
    }

    /**
     * Checks whether the list contains sequences 1-2-3, 4-5-6, and 7-8-9.
     * Note: caller must pre-filter to a single suit; passing mixed suits may give false positives.
     *
     * @param oneTypeShuntsuList sequences from a single suit
     * @return true if all three sequences are present
     */
    private boolean isIkkitsukan(List<Shuntsu> oneTypeShuntsuList) {
        boolean number2 = false;
        boolean number5 = false;
        boolean number8 = false;

        for (Shuntsu shuntsu : oneTypeShuntsuList) {
            int num = shuntsu.getTile().getNumber();
            if (num == 2) {
                number2 = true;
            } else if (num == 5) {
                number5 = true;
            } else if (num == 8) {
                number8 = true;
            }
        }
        return number2 && number5 && number8;
    }
}
