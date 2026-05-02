package com.mahjongcore.rules;

import com.mahjongcore.tile.Tile;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class MeldCandidateRules {

    private MeldCandidateRules() {}

    public enum MeldKind {
        CHI,
        PON,
        DAIMIN_KAN,
        ANKAN
    }

    public record MeldView(MeldKind kind, int[] tileCodes) {}

    public static List<Integer> closedKanCodes(ArrayDeque<Integer> concealed) {
        int[] counts = concealedCounts(concealed);
        ArrayList<Integer> out = new ArrayList<>(3);
        for (int code = 0; code < counts.length; code++) {
            if (counts[code] >= 4) {
                out.add(code);
            }
        }
        return out;
    }

    public static List<Integer> addedKanCodes(ArrayDeque<Integer> concealed, List<Integer> openPonTileCodes) {
        int[] counts = concealedCounts(concealed);
        ArrayList<Integer> out = new ArrayList<>(2);
        for (int code : openPonTileCodes) {
            if (code < 0 || code >= counts.length) {
                continue;
            }
            if (counts[code] <= 0 || out.contains(code)) {
                continue;
            }
            out.add(code);
        }
        return out;
    }

    public static List<Integer> kanCodes(ArrayDeque<Integer> concealed, List<Integer> openPonTileCodes) {
        ArrayList<Integer> out = new ArrayList<>(4);
        out.addAll(closedKanCodes(concealed));
        for (int code : addedKanCodes(concealed, openPonTileCodes)) {
            if (!out.contains(code)) {
                out.add(code);
            }
        }
        return out;
    }

    public static List<Integer> extractOpenPonTileCodes(List<MeldView> melds) {
        ArrayList<Integer> out = new ArrayList<>(2);
        for (MeldView meld : melds) {
            if (meld.kind() != MeldKind.PON) {
                continue;
            }
            int[] tiles = meld.tileCodes();
            if (tiles == null || tiles.length < 3) {
                continue;
            }
            out.add(tiles[0]);
        }
        return out;
    }

    public static int findUpgradeableOpenPonIndex(List<MeldView> melds, int tileCode) {
        for (int i = 0; i < melds.size(); i++) {
            MeldView meld = melds.get(i);
            if (meld.kind() != MeldKind.PON) {
                continue;
            }
            int[] tiles = meld.tileCodes();
            if (tiles == null || tiles.length < 3 || tiles[0] != tileCode) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private static int[] concealedCounts(ArrayDeque<Integer> concealed) {
        int[] counts = new int[Tile.values().length];
        for (int code : concealed) {
            if (code >= 0 && code < counts.length) {
                counts[code]++;
            }
        }
        return counts;
    }
}
