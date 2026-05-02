package com.mahjongcore.yaku.yakuman;

import com.mahjongcore.MahjongPlayer;
import com.mahjongcore.hands.Hands;

/**
 * (thirteen orphans) check. Does not implement YakumanResolver because the hand shape
 * is irregular; Hands.findMentsu() calls this directly and stores the result.
 *
 * @see Hands
 * @see MahjongPlayer
 */
public class KokushimusouResolver {
    private static final int[] kokushi = {
        1, 0, 0, 0, 0, 0, 0, 0, 1,
        1, 0, 0, 0, 0, 0, 0, 0, 1,
        1, 0, 0, 0, 0, 0, 0, 0, 1,
        1, 1, 1, 1,
        1, 1, 1
    };
    private final int[] hands;

    public KokushimusouResolver(int[] hands) {
        this.hands = hands;
    }

    public boolean isMatch() {
        // subtract the required kokushi terminal/honor pattern
        int count = 0;
        for (int i = 0; i < hands.length; i++) {
            hands[i] -= kokushi[i];

            if (hands[i] == -1) {
                return false;
            }

            if (kokushi[i] == 0 && hands[i] > 0) {
                return false;
            }
            if (hands[i] == 1) {
                count++;
            }
        }
        if (count == 1) {
            if (hands[0] == 1 ||
                hands[8] == 1 ||
                hands[9] == 1 ||
                hands[17] == 1 ||
                hands[18] == 1 ||
                hands[26] == 1 ||
                hands[27] == 1 ||
                hands[28] == 1 ||
                hands[29] == 1 ||
                hands[30] == 1 ||
                hands[31] == 1 ||
                hands[32] == 1 ||
                hands[33] == 1) {
                return true;
            }
        }
        return false;
    }
}
