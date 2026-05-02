package com.mahjongcore.hands;

import com.mahjongcore.MahjongHandsOverFlowException;
import com.mahjongcore.MahjongIllegalMentsuSizeException;
import com.mahjongcore.MahjongTileOverFlowException;
import com.mahjongcore.tile.Tile;
import com.mahjongcore.yaku.yakuman.KokushimusouResolver;

import java.util.*;

/**
 * Handles all hand tile operations and win detection.
 * TODO: draw-and-discard operation method
 */
public class Hands {
    private Set<MentsuComp> mentsuCompSet = new HashSet<>();
    private int[] handsComp = new int[34];
    private Tile last;
    private boolean canWin = false;
    private boolean isOpen = false;

    private List<Mentsu> inputtedMentsuList = new ArrayList<>();
    private int[] handStocks = new int[34];
    private int[] inputtedTiles;
    private boolean isKokushimuso = false;

    /**
     * @param otherTiles
     * @param last
     * @param mentsuList
     * @throws MahjongTileOverFlowException
     */
    public Hands(int[] otherTiles, Tile last, List<Mentsu> mentsuList) throws MahjongTileOverFlowException, MahjongIllegalMentsuSizeException {
        inputtedTiles = otherTiles;
        this.last = last;
        inputtedMentsuList = mentsuList;
        setHandsComp(otherTiles, mentsuList);
        findMentsu();
    }

    /**
     * @param otherTiles
     * @param last
     * @param mentsu
     * @throws MahjongTileOverFlowException
     */
    public Hands(int[] otherTiles, Tile last, Mentsu... mentsu) throws MahjongTileOverFlowException, MahjongIllegalMentsuSizeException {
        inputtedTiles = otherTiles;
        setHandsComp(otherTiles, Arrays.asList(mentsu));
        this.last = last;
        Collections.addAll(inputtedMentsuList, mentsu);
        findMentsu();
    }

    /**
     * @param allTiles all tiles including last; total must be 14
     * @param last     this tile must also be included in allTiles
     */
    public Hands(int[] allTiles, Tile last) throws MahjongHandsOverFlowException, MahjongTileOverFlowException, MahjongIllegalMentsuSizeException {
        inputtedTiles = allTiles;
        this.last = last;
        checkTiles(allTiles);
        handsComp = allTiles;
        findMentsu();
    }

    /**
     * Converts an input mentsu list into tile counts and adds them to handsComp.
     *
     * @param otherTiles tile count array
     * @param mentsuList list of mentsu
     */
    private void setHandsComp(int[] otherTiles, List<Mentsu> mentsuList) {
        System.arraycopy(otherTiles, 0, handsComp, 0, otherTiles.length);
        for (Mentsu mentsu : mentsuList) {
            int code = mentsu.getTile().getCode();

            if (mentsu.isOpen()) {
                isOpen = true;
            }

            if (mentsu instanceof Shuntsu) {
                handsComp[code - 1] += 1;
                handsComp[code] += 1;
                handsComp[code + 1] += 1;
            } else if (mentsu instanceof Kotsu) {
                handsComp[code] += 3;
            } else if (mentsu instanceof Kantsu) {
                handsComp[code] += 4;
            } else if (mentsu instanceof Toitsu) {
                handsComp[code] += 2;
            }
        }
    }

    public Set<MentsuComp> getMentsuCompSet() {
        return mentsuCompSet;
    }

    public boolean getCanWin() {
        return canWin;
    }

    public Tile getLast() {
        return last;
    }

    public int[] getHandsComp() {
        return handsComp;
    }

    public boolean getIsKokushimuso() {
        return isKokushimuso;
    }

    public boolean isOpen() {
        return isOpen;
    }

    private void checkTiles(int[] allTiles) throws MahjongHandsOverFlowException {
        int num = 0;
        for (int tileNum : allTiles) {
            num += tileNum;
            if (num > 14) {
                throw new MahjongHandsOverFlowException();
            }
        }
    }

    public void initStock() {
        System.arraycopy(inputtedTiles, 0, handStocks, 0, inputtedTiles.length);
    }

    /** Kantsu (kans) are not searched; they must be explicitly provided as input mentsu. */
    public void findMentsu() throws MahjongTileOverFlowException, MahjongIllegalMentsuSizeException {
        checkTileOverFlow();

        // kokushi musou check
        initStock();
        KokushimusouResolver kokushimuso = new KokushimusouResolver(handStocks);
        if (kokushimuso.isMatch()) {
            isKokushimuso = true;
            canWin = true;
            return;
        }

        // find jantou candidates
        initStock();
        List<Toitsu> toitsuList = Toitsu.findJantoCandidate(handStocks);

        if (toitsuList.size() == 0) {
            canWin = false;
            return;
        }

        // chitoitsu (seven pairs)
        if (toitsuList.size() == 7) {
            canWin = true;
            List<Mentsu> mentsuList = new ArrayList<>(7);
            mentsuList.addAll(toitsuList);
            MentsuComp comp = new MentsuComp(mentsuList, last);
            mentsuCompSet.add(comp);
        }

        List<Mentsu> winCandidate = new ArrayList<>(4);
        for (Toitsu toitsu : toitsuList) {
            init(winCandidate, toitsu);

            // triplet-first search
            winCandidate.addAll(findKotsuCandidate());
            winCandidate.addAll(findShuntsuCandidate());
            convertToMentsuComp(winCandidate);

            init(winCandidate, toitsu);
            // sequence-first search
            winCandidate.addAll(findShuntsuCandidate());
            winCandidate.addAll(findKotsuCandidate());
            convertToMentsuComp(winCandidate);
        }

    }

    /**
     * @throws MahjongTileOverFlowException if any tile count exceeds 4
     */
    private void checkTileOverFlow() throws MahjongTileOverFlowException {
        for (int i = 0; i < handsComp.length; i++) {
            int hand = handsComp[i];
            if (hand > 4) {
                canWin = false;
                throw new MahjongTileOverFlowException(i, hand);
            }
        }
    }

    /**
     * Resets working state and removes the jantou from the tile stock.
     *
     * @param winCandidate candidate meld list
     * @param toitsu       the jantou candidate for this search cycle
     */
    private void init(List<Mentsu> winCandidate, Toitsu toitsu) {
        initStock();
        winCandidate.clear();
        handStocks[toitsu.getTile().getCode()] -= 2;
        winCandidate.add(toitsu);
    }

    /**
     * If handStocks is all zeros, winCandidate forms a complete hand and is saved.
     *
     * @param winCandidate candidate that may form a complete hand
     */
    private void convertToMentsuComp(List<Mentsu> winCandidate) throws MahjongIllegalMentsuSizeException {
        if (isAllZero(handStocks)) {
            canWin = true;
            winCandidate.addAll(inputtedMentsuList);
            MentsuComp mentsuComp = new MentsuComp(winCandidate, last);
            if (!mentsuCompSet.contains(mentsuComp)) {
                mentsuCompSet.add(mentsuComp);
            }
        }
    }

    /**
     * @param stocks array to check
     * @return true if all zeros, false if any element is nonzero
     */
    private boolean isAllZero(int[] stocks) {
        for (int i : stocks) {
            if (i != 0) {
                return false;
            }
        }
        return true;
    }

    private List<Mentsu> findShuntsuCandidate() {
        List<Mentsu> resultList = new ArrayList<>(4);
        // Numeric suits only: manzu [0..8], pinzu [9..17], sozu [18..26].
        int[] suitStartCodes = {0, 9, 18};
        for (int suitStart : suitStartCodes) {
            // Sequence start positions inside one suit: 1-2-3 through 7-8-9.
            for (int offset = 0; offset <= 6; offset++) {
                int first = suitStart + offset;
                int second = first + 1;
                int third = first + 2;
                // while loop needed to handle iipeiko (two identical sequences)
                while (handStocks[first] > 0 && handStocks[second] > 0 && handStocks[third] > 0) {
                    Shuntsu shuntsu = new Shuntsu(
                            false,
                            Tile.valueOf(first),
                            Tile.valueOf(second),
                            Tile.valueOf(third));

                    // Defensive: avoid infinite loops if evaluator rejects an otherwise consecutive candidate.
                    if (!shuntsu.isMentsu()) {
                        break;
                    }
                    resultList.add(shuntsu);
                    handStocks[first]--;
                    handStocks[second]--;
                    handStocks[third]--;
                }
            }
        }
        return resultList;
    }

    private List<Mentsu> findKotsuCandidate() {
        List<Mentsu> resultList = new ArrayList<>(4);
        for (int i = 0; i < handStocks.length; i++) {
            if (handStocks[i] >= 3) {
                resultList.add(new Kotsu(false, Tile.valueOf(i)));
                handStocks[i] -= 3;
            }
        }
        return resultList;
    }
}
