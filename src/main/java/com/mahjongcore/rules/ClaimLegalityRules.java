package com.mahjongcore.rules;

import com.mahjongcore.tile.Tile;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class ClaimLegalityRules {

    private ClaimLegalityRules() {}

    public record ChiPair(int tileA, int tileB) {}

    public static int[] concealedCounts(ArrayDeque<Integer> hand) {
        int[] c = new int[34];
        for (int code : hand) {
            if (code >= 0 && code < 34) {
                c[code]++;
            }
        }
        return c;
    }

    public static List<ChiPair> findChiPairs(ArrayDeque<Integer> hand, int discardCode) {
        ArrayList<ChiPair> out = new ArrayList<>(2);
        if (discardCode < 0 || discardCode > 26) {
            return out;
        }
        Tile t = Tile.valueOf(discardCode);
        int suitBase = discardCode - t.getNumber() + 1;
        if (t.getNumber() <= 0) {
            return out;
        }
        int[][] pairs = new int[][] {{discardCode - 2, discardCode - 1}, {discardCode - 1, discardCode + 1}, {discardCode + 1, discardCode + 2}};
        int[] hc = concealedCounts(hand);
        for (int[] pair : pairs) {
            int a = pair[0];
            int b = pair[1];
            if (a < suitBase || a > suitBase + 8 || b < suitBase || b > suitBase + 8) {
                continue;
            }
            if (hc[a] > 0 && hc[b] > 0) {
                out.add(new ChiPair(a, b));
            }
        }
        return out;
    }

    public static boolean canPon(ArrayDeque<Integer> hand, int discardCode) {
        if (discardCode < 0 || discardCode > 33) {
            return false;
        }
        int[] c = concealedCounts(hand);
        return c[discardCode] >= 2;
    }

    public static boolean canDaiminKan(ArrayDeque<Integer> hand, int discardCode) {
        if (discardCode < 0 || discardCode > 33) {
            return false;
        }
        int[] c = concealedCounts(hand);
        return c[discardCode] >= 3;
    }
}
