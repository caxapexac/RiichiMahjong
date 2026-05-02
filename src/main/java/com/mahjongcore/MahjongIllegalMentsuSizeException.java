package com.mahjongcore;

import com.mahjongcore.hands.Mentsu;

import java.util.List;

/**
 * Thrown when the meld set does not form a valid winning pattern.
 */
public class MahjongIllegalMentsuSizeException extends MahjongException {
    private List<Mentsu> mentsuList;

    public MahjongIllegalMentsuSizeException(List<Mentsu> mentsuList) {
        super("Meld set does not form a valid winning pattern");
        this.mentsuList = mentsuList;
    }

    public String getAdvice() {
        return "Total mentsu must be 5 (or 7 for chitoitsu) but found " + mentsuList.size();
    }

    /**
     * @return the meld list that was rejected
     */
    public List<Mentsu> getMentsuList() {
        return mentsuList;
    }
}
