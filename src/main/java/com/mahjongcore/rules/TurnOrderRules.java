package com.mahjongcore.rules;

import com.mahjongcore.tile.Tile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class TurnOrderRules {

    private TurnOrderRules() {}

    public static List<Integer> counterClockwisePlayOrder(int seatCount, int dealerSeat, List<Integer> occupiedSeats) {
        ArrayList<Integer> order = new ArrayList<>(occupiedSeats.size());
        HashSet<Integer> remaining = new HashSet<>(occupiedSeats);
        int current = dealerSeat;
        while (!remaining.isEmpty()) {
            if (!remaining.remove(current)) {
                return new ArrayList<>(occupiedSeats);
            }
            order.add(current);
            if (remaining.isEmpty()) {
                break;
            }
            int probe = current;
            for (int step = 0; step < seatCount; step++) {
                probe = (probe + seatCount - 1) % seatCount;
                if (remaining.contains(probe)) {
                    current = probe;
                    break;
                }
            }
        }
        return order;
    }

    public static Tile jikazeForSeat(List<Integer> playOrder, int seat) {
        int dealerSeat = playOrder.isEmpty() ? 0 : playOrder.get(0);
        int seatIndex = playOrder.indexOf(seat);
        int dealerIndex = playOrder.indexOf(dealerSeat);
        if (seatIndex < 0 || dealerIndex < 0) {
            return Tile.TON;
        }
        int rel = (seatIndex - dealerIndex + playOrder.size()) % playOrder.size();
        int code = 27 + Math.min(rel, 3);
        return Tile.valueOf(code);
    }
}
