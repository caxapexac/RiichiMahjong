package com.themahjong.replay;

import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;

import java.util.ArrayList;
import java.util.List;

/**
 * Reconstructs the ordered wall array that {@link TheMahjongRound#start} expects from a
 * parsed {@link TenhouRound}.
 *
 * <p>Wall layout: {@code [13×pc initial tiles] → [4 rinshan] → [5 dora indicators]
 * → [5 ura dora indicators] → [live wall draws]}.
 *
 * <p>Rinshan draws are identified as draws that immediately follow a kan claim in the action
 * sequence. Unknown rinshan/dora/ura slots are padded with the first dora indicator tile so the
 * wall always satisfies the minimum-size requirement.
 */
public final class TenhouWallReconstructor {

    private TenhouWallReconstructor() {}

    public static List<TheMahjongTile> reconstruct(TenhouRound round) {
        int playerCount = round.playerCount();
        int rinshanCount = TheMahjongRound.rinshanTileCountFor(playerCount);
        List<TheMahjongTile> wall = new ArrayList<>();

        for (int seat = 0; seat < playerCount; seat++) {
            wall.addAll(round.initialHands().get(seat));
        }

        List<TheMahjongTile> rinshanDraws = new ArrayList<>();
        List<TheMahjongTile> liveDraws = new ArrayList<>();
        boolean expectRinshan = false;
        for (TenhouAction action : round.actions()) {
            if (action instanceof TenhouAction.Claim c
                    && (isKan(c.type()) || c.type() == TenhouAction.ClaimType.KITA)) {
                expectRinshan = true;
            } else if (action instanceof TenhouAction.Draw d) {
                if (expectRinshan) {
                    rinshanDraws.add(d.tile());
                    expectRinshan = false;
                } else {
                    liveDraws.add(d.tile());
                }
            }
        }

        TheMahjongTile filler = round.doraIndicator();

        List<TheMahjongTile> rinshan = new ArrayList<>(rinshanDraws);
        while (rinshan.size() < rinshanCount) rinshan.add(filler);
        wall.addAll(rinshan.subList(0, rinshanCount));

        List<TheMahjongTile> doraList = new ArrayList<>();
        doraList.add(round.doraIndicator());
        for (TenhouAction a : round.actions()) {
            if (a instanceof TenhouAction.DoraReveal dr) doraList.add(dr.indicator());
        }
        while (doraList.size() < TheMahjongRound.MAX_DORA_INDICATORS) doraList.add(filler);
        wall.addAll(doraList.subList(0, TheMahjongRound.MAX_DORA_INDICATORS));

        List<TheMahjongTile> uraList = new ArrayList<>();
        for (TenhouAction a : round.actions()) {
            if (a instanceof TenhouAction.Win w) {
                uraList.addAll(w.uraDoraIndicators());
                break;
            }
        }
        while (uraList.size() < TheMahjongRound.MAX_DORA_INDICATORS) uraList.add(filler);
        wall.addAll(uraList.subList(0, TheMahjongRound.MAX_DORA_INDICATORS));

        // Pad live draws to the full expected live-wall size so that liveWall.isEmpty()
        // is only true when the actual game exhausted all tiles (HAITEI/HOUTEI).
        // Sanma deck = 108 tiles (M2-M8 removed); 4-player deck = 136.
        int totalTiles = (playerCount == 3) ? 108 : 136;
        long kanCount = round.actions().stream()
                .filter(a -> a instanceof TenhouAction.Claim c
                        && (isKan(c.type()) || c.type() == TenhouAction.ClaimType.KITA))
                .count();
        // Each kan/kita moves the last live-wall tile into the dead wall (dead wall stays
        // at the original size). Tenhou logs reflect this: a round with N replacement-draw
        // events has at most (baseMax - N) live draws.
        int maxLiveWall = totalTiles
                - playerCount * TheMahjongRound.INITIAL_HAND_SIZE
                - rinshanCount
                - TheMahjongRound.MAX_DORA_INDICATORS * 2
                - (int) kanCount;
        while (liveDraws.size() < maxLiveWall) liveDraws.add(filler);
        wall.addAll(liveDraws);

        return List.copyOf(wall);
    }

    static boolean isKan(TenhouAction.ClaimType type) {
        return type == TenhouAction.ClaimType.DAIMINKAN
                || type == TenhouAction.ClaimType.KAKAN
                || type == TenhouAction.ClaimType.ANKAN;
        // KITA is not a kan — it does not yield a rinshan draw
    }
}
