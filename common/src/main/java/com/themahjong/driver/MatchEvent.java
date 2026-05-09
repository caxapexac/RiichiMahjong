package com.themahjong.driver;

import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongTile;
import com.themahjong.yaku.WinResult;

import java.util.List;

/**
 * Notable state changes the driver broadcasts to all {@link MahjongPlayerInterface}
 * implementations via {@link MahjongPlayerInterface#onEvent}. Pure observation channel —
 * players cannot react with an action here; reactions flow through
 * {@link MahjongPlayerInterface#chooseAction}.
 *
 * <p>The set is intentionally minimal; richer events can be added as needed.
 */
public sealed interface MatchEvent
        permits MatchEvent.RoundStarted, MatchEvent.Discarded, MatchEvent.MeldDeclared,
                MatchEvent.RiichiDeclared, MatchEvent.KitaDeclared, MatchEvent.RoundEnded,
                MatchEvent.MatchEnded {

    record RoundStarted(int dealerSeat, com.themahjong.TheMahjongTile.Wind roundWind, int handNumber, int honba) implements MatchEvent {}

    record Discarded(int seat, TheMahjongTile tile, boolean riichi) implements MatchEvent {}

    record MeldDeclared(int seat, TheMahjongMeld meld) implements MatchEvent {}

    record RiichiDeclared(int seat) implements MatchEvent {}

    record KitaDeclared(int seat, TheMahjongTile tile) implements MatchEvent {}

    record RoundEnded(List<WinResult> winResults) implements MatchEvent {}

    record MatchEnded() implements MatchEvent {}
}
