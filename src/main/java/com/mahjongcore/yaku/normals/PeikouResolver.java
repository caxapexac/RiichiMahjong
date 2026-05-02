package com.mahjongcore.yaku.normals;

import com.mahjongcore.hands.Shuntsu;

import java.util.List;

public abstract class PeikouResolver implements NormalYakuResolver {
    protected int peiko(List<Shuntsu> shuntsuList) {
        if (shuntsuList.size() < 2) {
            return 0;
        }

        Shuntsu stockOne = null;
        Shuntsu stockTwo = null;

        int peiko = 0;
        for (Shuntsu shuntsu : shuntsuList) {
            if (shuntsu.isOpen()) {
                return 0;
            }

            if (stockOne == null) {
                stockOne = shuntsu;
                continue;
            }

            if (stockOne.equals(shuntsu) && peiko == 0) {
                peiko = 1;
                continue;
            }

            if (stockTwo == null) {
                stockTwo = shuntsu;
                continue;
            }

            if (stockTwo.equals(shuntsu)) {
                return 2;
            }
        }
        return peiko;
    }
}
