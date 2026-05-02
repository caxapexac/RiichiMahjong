package com.mahjongcore.rules;

import com.mahjongcore.tile.Tile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

public final class RoundSetupRules {

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 4;
    public static final int TILE_COPIES_PER_TYPE = 4;
    public static final int HAND_TILES_PER_PLAYER = 13;
    public static final int DEALER_EXTRA_DRAW = 1;
    public static final int FULL_TILE_SET_SIZE = 136;
    public static final int DEAD_WALL_SIZE = 14;
    public static final int DORA_INDICATOR_DEAD_WALL_INDEX = 4;

    private RoundSetupRules() {}

    public static int requiredDealtTilesForPlayers(int playerCount) {
        if (playerCount < MIN_PLAYERS || playerCount > MAX_PLAYERS) {
            return Integer.MAX_VALUE;
        }
        return HAND_TILES_PER_PLAYER * playerCount + DEALER_EXTRA_DRAW;
    }

    public static boolean isDealNeededValid(int dealNeeded) {
        return dealNeeded > 0 && dealNeeded <= FULL_TILE_SET_SIZE;
    }

    /** Four copies of each tile code, ordered by {@link Tile#values()}. */
    public static ArrayList<Integer> sortedWallCodes() {
        ArrayList<Integer> out = new ArrayList<>(FULL_TILE_SET_SIZE);
        for (Tile t : Tile.values()) {
            int code = t.getCode();
            for (int i = 0; i < TILE_COPIES_PER_TYPE; i++) {
                out.add(code);
            }
        }
        return out;
    }

    public static int takeCodeFromPool(List<Integer> pool, int code, Random random) {
        int index = pool.indexOf(code);
        if (index < 0) {
            return drawRandomCode(pool, random);
        }
        return pool.remove(index);
    }

    public static int drawRandomCode(List<Integer> pool, Random random) {
        if (pool.isEmpty()) {
            return 0;
        }
        int index = random.nextInt(pool.size());
        return pool.remove(index);
    }

    public static WallPartitionPlan wallPartitionPlan(int totalTiles, int playerCount, int deadWallSize) {
        int dealNeeded = requiredDealtTilesForPlayers(playerCount);
        int tailLen = Math.max(0, totalTiles - dealNeeded);
        int fullLiveWallTarget = Math.max(0, totalTiles - dealNeeded - deadWallSize);
        int liveTailCount = Math.min(tailLen, fullLiveWallTarget);
        int deadWallCount = Math.min(deadWallSize, Math.max(0, tailLen - liveTailCount));
        return new WallPartitionPlan(dealNeeded, tailLen, fullLiveWallTarget, liveTailCount, deadWallCount);
    }

    public record WallPartitionPlan(
            int dealNeeded, int tailLen, int fullLiveWallTarget, int liveTailCount, int deadWallCount) {}

    /**
     * Returns seat order for the initial hand-deal stream (without dealer extra draw):
     * 3 rounds of 4 tiles per player, then 1 tile per player.
     */
    public static List<Integer> initialDealSeatSequence(List<Integer> playOrder) {
        int n = playOrder.size();
        if (n < MIN_PLAYERS || n > MAX_PLAYERS) {
            return List.of();
        }
        ArrayList<Integer> sequence = new ArrayList<>(n * HAND_TILES_PER_PLAYER);
        for (int round = 0; round < 3; round++) {
            for (int seat : playOrder) {
                for (int k = 0; k < 4; k++) {
                    sequence.add(seat);
                }
            }
        }
        for (int seat : playOrder) {
            sequence.add(seat);
        }
        return sequence;
    }

    public static boolean isValidPlayOrder(List<Integer> playOrder, int seatCount) {
        int n = playOrder.size();
        if (n < MIN_PLAYERS || n > MAX_PLAYERS) {
            return false;
        }
        HashSet<Integer> seen = new HashSet<>(n);
        for (int seat : playOrder) {
            if (seat < 0 || seat >= seatCount) {
                return false;
            }
            if (!seen.add(seat)) {
                return false;
            }
        }
        return true;
    }
}
