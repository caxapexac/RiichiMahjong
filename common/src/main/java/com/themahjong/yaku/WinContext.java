package com.themahjong.yaku;

import com.themahjong.TheMahjongPlayer.RiichiState;
import com.themahjong.TheMahjongTile;

/**
 * Situational context at the moment a winning hand is evaluated.
 * Derived by the caller from {@code TheMahjongRound} state.
 *
 * @param winType                type of win: self-draw, ron, or chankan (robbing a kan)
 * @param dealer                 true = winner is the current dealer
 * @param uninterruptedFirstRound true = no tile has been drawn or discarded before this win
 * @param riichiState            riichi declaration state of the winning player
 * @param ippatsuEligible        true = player is eligible for ippatsu
 * @param winningTile            the tile that completed the winning hand
 * @param seatWind               seat wind of the winning player
 * @param roundWind              current round wind
 * @param lastTile               true = winning tile was the last drawable tile (enables Haitei/Houtei)
 * @param rinshanDraw            true = winning tile was drawn from the rinshan wall after a kan
 */
public record WinContext(WinType winType, boolean dealer, boolean uninterruptedFirstRound,
                         RiichiState riichiState, boolean ippatsuEligible,
                         TheMahjongTile winningTile,
                         TheMahjongTile.Wind seatWind, TheMahjongTile.Wind roundWind,
                         boolean lastTile, boolean rinshanDraw) {

    public enum WinType { TSUMO, RON, CHANKAN }

    /** True if this is a self-draw win (tsumo). */
    public boolean tsumo() { return winType == WinType.TSUMO; }

    public static WinContext tsumo(boolean dealer, boolean uninterruptedFirstRound,
                                   RiichiState riichiState, boolean ippatsuEligible,
                                   TheMahjongTile winningTile,
                                   TheMahjongTile.Wind seatWind, TheMahjongTile.Wind roundWind,
                                   boolean lastTile, boolean rinshanDraw) {
        return new WinContext(WinType.TSUMO, dealer, uninterruptedFirstRound, riichiState, ippatsuEligible,
                winningTile, seatWind, roundWind, lastTile, rinshanDraw);
    }

    public static WinContext ron(boolean dealer, boolean uninterruptedFirstRound,
                                 RiichiState riichiState, boolean ippatsuEligible,
                                 TheMahjongTile winningTile,
                                 TheMahjongTile.Wind seatWind, TheMahjongTile.Wind roundWind,
                                 boolean lastTile, boolean rinshanDraw) {
        return new WinContext(WinType.RON, dealer, uninterruptedFirstRound, riichiState, ippatsuEligible,
                winningTile, seatWind, roundWind, lastTile, rinshanDraw);
    }

    public static WinContext chankan(boolean dealer, boolean uninterruptedFirstRound,
                                     RiichiState riichiState, boolean ippatsuEligible,
                                     TheMahjongTile winningTile,
                                     TheMahjongTile.Wind seatWind, TheMahjongTile.Wind roundWind,
                                     boolean lastTile) {
        return new WinContext(WinType.CHANKAN, dealer, uninterruptedFirstRound, riichiState, ippatsuEligible,
                winningTile, seatWind, roundWind, lastTile, false);
    }
}
