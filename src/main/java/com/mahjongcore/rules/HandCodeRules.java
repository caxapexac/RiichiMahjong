package com.mahjongcore.rules;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class HandCodeRules {

    private HandCodeRules() {}

    public static int[] sortedHandCodes(ArrayDeque<Integer> hand) {
        int[] out = new int[hand.size()];
        int i = 0;
        for (int code : hand) {
            out[i++] = code;
        }
        Arrays.sort(out);
        return out;
    }

    public static void sortInPlace(List<Integer> tileCodes) {
        Collections.sort(tileCodes);
    }
}
