package com.mahjongcore.yaku.yakuman;

import com.mahjongcore.hands.Kotsu;
import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.hands.Shuntsu;
import com.mahjongcore.hands.Toitsu;
import com.mahjongcore.tile.Tile;

import java.util.List;

import static com.mahjongcore.tile.Tile.*;
import static com.mahjongcore.yaku.yakuman.Yakuman.RYUUIISOU;

/**
 * (all green): all tiles are green — S2, S3, S4, S6, S8, and HAT (green dragon).
 */
public class RyuuiisouResolver implements YakumanResolver {
    private final Yakuman yakuman = RYUUIISOU;
    private final List<Toitsu> toitsuList;
    private final List<Shuntsu> shuntsuList;
    private final List<Kotsu> kotsuList;

    public RyuuiisouResolver(MentsuComp hands) {
        toitsuList = hands.getToitsuList();
        shuntsuList = hands.getShuntsuList();
        kotsuList = hands.getKotsuKantsu();
    }

    public Yakuman getYakuman() {
        return yakuman;
    }

    public boolean isMatch() {
        for (Toitsu toitsu : toitsuList) {
            if (!isGreen(toitsu.getTile())) {
                return false;
            }
        }
        for (Kotsu kotsu : kotsuList) {
            if (!isGreen(kotsu.getTile())) {
                return false;
            }
        }

        for (Shuntsu shuntsu : shuntsuList) {
            if (shuntsu.getTile() != S3) {
                return false;
            }
        }

        return true;
    }

    /**
     * @param tile the tile to check
     * @return true if the tile is green
     */
    private boolean isGreen(Tile tile) {
        return tile == HAT
            || tile == S2
            || tile == S3
            || tile == S4
            || tile == S6
            || tile == S8;
    }
}
