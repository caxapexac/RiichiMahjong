package com.mahjongcore.rules;

import com.mahjongcore.MahjongGeneralSituation;
import com.mahjongcore.MahjongIllegalMentsuSizeException;
import com.mahjongcore.MahjongTileOverFlowException;
import com.mahjongcore.MahjongPersonalSituation;
import com.mahjongcore.MahjongPlayer;
import com.mahjongcore.MahjongScore;
import com.mahjongcore.hands.Hands;
import com.mahjongcore.hands.Mentsu;
import com.mahjongcore.tile.Tile;
import com.mahjongcore.yaku.normals.NormalYaku;
import com.mahjongcore.yaku.yakuman.Yakuman;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class WinRules {

    private WinRules() {}

    public enum FuritenReason {
        NONE,
        TEMPORARY,
        RIICHI_PERMANENT,
        DISCARD_WAIT_MATCH
    }

    public enum VerdictTier {
        HAN,
        MANGAN,
        HANEMAN,
        BAIMAN,
        SANBAIMAN,
        KAZOE_YAKUMAN,
        YAKUMAN
    }

    public record FuritenEvaluation(boolean furitenForRon, FuritenReason reason) {}

    public record TsumoSplit(boolean winnerIsParent, int dealerPay, int childPay) {
        public int winnerGain(int playerCount) {
            int opponents = Math.max(0, playerCount - 1);
            if (winnerIsParent) {
                return childPay * opponents;
            }
            return dealerPay + childPay * Math.max(0, opponents - 1);
        }
    }

    public record RonEvaluation(
            int han,
            boolean yakuman,
            List<Yakuman> yakumanNames,
            List<NormalYaku> yakuLines,
            MahjongScore score,
            boolean winnerIsParent) {
        private static RonEvaluation noWin() {
            return new RonEvaluation(0, false, List.of(), List.of(), MahjongScore.SCORE0, false);
        }
    }

    public static RonEvaluation noWinRonEvaluation() {
        return RonEvaluation.noWin();
    }

    public static FuritenEvaluation evaluateFuritenForRon(
            boolean temporaryFuriten, boolean riichiPermanentFuriten, boolean[] seenDiscardsByTile, boolean[] winningShapeWaitByTile) {
        if (riichiPermanentFuriten) {
            return new FuritenEvaluation(true, FuritenReason.RIICHI_PERMANENT);
        }
        if (temporaryFuriten) {
            return new FuritenEvaluation(true, FuritenReason.TEMPORARY);
        }
        if (seenDiscardsByTile != null && winningShapeWaitByTile != null) {
            int n = Math.min(seenDiscardsByTile.length, winningShapeWaitByTile.length);
            for (int code = 0; code < n; code++) {
                if (seenDiscardsByTile[code] && winningShapeWaitByTile[code]) {
                    return new FuritenEvaluation(true, FuritenReason.DISCARD_WAIT_MATCH);
                }
            }
        }
        return new FuritenEvaluation(false, FuritenReason.NONE);
    }

    public static boolean isTenpai(ArrayDeque<Integer> concealedHand, List<Mentsu> openAsMentsu) {
        if (concealedHand == null) {
            return false;
        }
        int concealedCount = concealedHand.size();
        if (concealedCount <= 0) {
            return false;
        }
        if (concealedCount % 3 == 1) {
            return isTenpaiFromThirteenLikeHand(ClaimLegalityRules.concealedCounts(concealedHand), openAsMentsu);
        }
        if (concealedCount % 3 == 2) {
            int[] counts = ClaimLegalityRules.concealedCounts(concealedHand);
            for (int discard = 0; discard < 34; discard++) {
                if (counts[discard] <= 0) {
                    continue;
                }
                counts[discard]--;
                if (isTenpaiFromThirteenLikeHand(counts, openAsMentsu)) {
                    counts[discard]++;
                    return true;
                }
                counts[discard]++;
            }
        }
        return false;
    }

    public static boolean isRiichiActionAvailable(
            boolean mustDiscardPhase,
            boolean isCurrentTurnSeat,
            int lastDrawnCode,
            ArrayDeque<Integer> concealedHand,
            List<Mentsu> openAsMentsu) {
        if (concealedHand == null || openAsMentsu == null) {
            return false;
        }
        if (!mustDiscardPhase) {
            return false;
        }
        if (!isCurrentTurnSeat || lastDrawnCode < 0) {
            return false;
        }
        for (Mentsu meld : openAsMentsu) {
            if (meld != null && meld.isOpen()) {
                return false;
            }
        }
        return isTenpai(concealedHand, openAsMentsu);
    }

    public static boolean isTsumoActionAvailable(
            boolean mustDiscardPhase,
            boolean isCurrentTurnSeat,
            int lastDrawnCode,
            ArrayDeque<Integer> concealedHand,
            List<Mentsu> openAsMentsu) {
        if (concealedHand == null || openAsMentsu == null) {
            return false;
        }
        if (!mustDiscardPhase) {
            return false;
        }
        if (!isCurrentTurnSeat || lastDrawnCode < 0 || lastDrawnCode > 33) {
            return false;
        }
        int[] counts = ClaimLegalityRules.concealedCounts(concealedHand);
        if (counts[lastDrawnCode] <= 0) {
            return false;
        }
        return canWinWithLastTile(counts, lastDrawnCode, openAsMentsu);
    }

    /** Winning-shape waits (0..33) for current concealed counts + open melds, yaku-agnostic. */
    public static List<Integer> winningShapeWaitCodes(int[] concealedCounts, List<Mentsu> openAsMentsu) {
        ArrayList<Integer> waits = new ArrayList<>(13);
        if (concealedCounts == null || openAsMentsu == null) {
            return waits;
        }
        for (int waitTile = 0; waitTile < 34; waitTile++) {
            if (concealedCounts[waitTile] >= 4) {
                continue;
            }
            if (canCompleteWinningShape(concealedCounts, openAsMentsu, waitTile)) {
                waits.add(waitTile);
            }
        }
        return waits;
    }

    public static boolean canCompleteWinningShape(int[] concealedCounts, List<Mentsu> openAsMentsu, int winCode) {
        if (concealedCounts == null || openAsMentsu == null || winCode < 0 || winCode > 33) {
            return false;
        }
        Tile last = Tile.valueOf(winCode);
        try {
            Hands hands = new Hands(concealedCounts.clone(), last, openAsMentsu);
            if (hands.getCanWin()) {
                return true;
            }
        } catch (MahjongTileOverFlowException | MahjongIllegalMentsuSizeException | RuntimeException ignored) {
            // Fall through to compatibility variant.
        }
        int[] concealedWithLast = concealedCounts.clone();
        if (concealedWithLast[winCode] >= 4) {
            return false;
        }
        concealedWithLast[winCode]++;
        try {
            Hands hands = new Hands(concealedWithLast, last, openAsMentsu);
            return hands.getCanWin();
        } catch (MahjongTileOverFlowException | MahjongIllegalMentsuSizeException | RuntimeException ignored) {
            return false;
        }
    }

    public static boolean canWinWithLastTile(int[] fullConcealedCounts, int lastTile, List<Mentsu> openAsMentsu) {
        try {
            Hands hands = new Hands(fullConcealedCounts.clone(), Tile.valueOf(lastTile), openAsMentsu);
            if (hands.getCanWin()) {
                return true;
            }
        } catch (MahjongTileOverFlowException | MahjongIllegalMentsuSizeException | RuntimeException ignored) {
            // Fall through to compatibility variant.
        }
        int[] withoutLast = fullConcealedCounts.clone();
        if (withoutLast[lastTile] <= 0) {
            return false;
        }
        withoutLast[lastTile]--;
        try {
            Hands hands = new Hands(withoutLast, Tile.valueOf(lastTile), openAsMentsu);
            return hands.getCanWin();
        } catch (MahjongTileOverFlowException | MahjongIllegalMentsuSizeException | RuntimeException ignored) {
            return false;
        }
    }

    public static Tile doraFromIndicator(Tile indicator) {
        int code = indicator.getCode();
        if (code >= 0 && code <= 26) {
            int suitBase = (code / 9) * 9;
            return Tile.valueOf(suitBase + ((code - suitBase + 1) % 9));
        }
        if (code >= 27 && code <= 30) {
            return Tile.valueOf(27 + ((code - 27 + 1) % 4));
        }
        if (code >= 31 && code <= 33) {
            return Tile.valueOf(31 + ((code - 31 + 1) % 3));
        }
        return indicator;
    }

    public static MahjongGeneralSituation generalSituation(
            boolean firstRound, boolean houtei, Tile bakaze, List<Integer> doraIndicatorCodes) {
        MahjongGeneralSituation g = new MahjongGeneralSituation();
        g.setFirstRound(firstRound);
        g.setHoutei(houtei);
        g.setBakaze(bakaze);
        ArrayList<Tile> dora = new ArrayList<>();
        if (doraIndicatorCodes != null) {
            for (int code : doraIndicatorCodes) {
                if (code >= 0 && code <= 33) {
                    dora.add(doraFromIndicator(Tile.valueOf(code)));
                }
            }
        }
        g.setDora(dora);
        g.setUradora(List.of());
        return g;
    }

    public static MahjongPersonalSituation personalSituationForRon(
            int claimantSeat,
            List<Integer> playOrder,
            boolean[] riichiDeclaredBySeat,
            boolean[] ippatsuEligibleBySeat,
            boolean claimIsChankanWindow) {
        boolean riichi = riichiDeclaredBySeat != null
                && claimantSeat >= 0
                && claimantSeat < riichiDeclaredBySeat.length
                && riichiDeclaredBySeat[claimantSeat];
        boolean ippatsu = ippatsuEligibleBySeat != null
                && claimantSeat >= 0
                && claimantSeat < ippatsuEligibleBySeat.length
                && ippatsuEligibleBySeat[claimantSeat];
        return new MahjongPersonalSituation(
                false,
                ippatsu,
                riichi,
                false,
                claimIsChankanWindow,
                false,
                TurnOrderRules.jikazeForSeat(playOrder, claimantSeat));
    }

    public static boolean canRon(
            boolean claimWindowActive,
            int claimantSeat,
            int discarderSeat,
            List<Integer> playOrder,
            boolean[] riichiDeclaredBySeat,
            boolean[] ippatsuEligibleBySeat,
            boolean claimIsChankanWindow,
            int discardCode,
            ArrayDeque<Integer> concealedHand,
            List<Mentsu> openAsMentsu,
            int handNumber,
            boolean scoreAsNotFirstRound,
            int liveWallRemaining,
            List<Integer> doraIndicatorCodes) {
        if (concealedHand == null || openAsMentsu == null) {
            return false;
        }
        if (!claimWindowActive) {
            return false;
        }
        if (claimantSeat == discarderSeat || playOrder == null || !playOrder.contains(claimantSeat)) {
            return false;
        }
        if (discardCode < 0 || discardCode > 33) {
            return false;
        }
        RonEvaluation result = evaluateRonWinningHand(
                claimantSeat,
                discardCode,
                concealedHand,
                openAsMentsu,
                handNumber,
                scoreAsNotFirstRound,
                playOrder,
                liveWallRemaining,
                doraIndicatorCodes,
                personalSituationForRon(
                        claimantSeat,
                        playOrder,
                        riichiDeclaredBySeat,
                        ippatsuEligibleBySeat,
                        claimIsChankanWindow));
        return result.yakuman() || result.han() > 0;
    }

    public static RonEvaluation evaluateRonWinningHand(
            int winnerSeat,
            int winCode,
            ArrayDeque<Integer> concealedHand,
            List<Mentsu> openAsMentsu,
            int handNumber,
            boolean scoreAsNotFirstRound,
            List<Integer> playOrder,
            int liveWallRemaining,
            List<Integer> doraIndicatorCodes,
            MahjongPersonalSituation personalSituation) {
        if (concealedHand == null || openAsMentsu == null || personalSituation == null) {
            return noWinRonEvaluation();
        }
        int[] concealed = ClaimLegalityRules.concealedCounts(concealedHand);
        MahjongGeneralSituation gen = generalSituation(
                handNumber == 0 && !scoreAsNotFirstRound,
                liveWallRemaining <= 0,
                Tile.TON,
                doraIndicatorCodes);
        boolean winnerIsParent = playOrder != null && !playOrder.isEmpty() && playOrder.get(0) == winnerSeat;
        return evaluateWinningHand(winCode, concealed, openAsMentsu, gen, personalSituation, winnerIsParent);
    }

    public static RonEvaluation evaluateWinningHand(
            int winCode,
            int[] concealedCounts,
            List<Mentsu> openAsMentsu,
            MahjongGeneralSituation generalSituation,
            MahjongPersonalSituation personalSituation,
            boolean winnerIsParent) {
        if (winCode < 0 || winCode > 33) {
            return RonEvaluation.noWin();
        }
        if (concealedCounts == null || openAsMentsu == null || generalSituation == null || personalSituation == null) {
            return RonEvaluation.noWin();
        }
        Tile last = Tile.valueOf(winCode);
        List<Mentsu> melds = new ArrayList<>(openAsMentsu);
        try {
            Hands hands = new Hands(concealedCounts, last, melds);
            if (!hands.getCanWin()) {
                int[] concealedWithLast = concealedCounts.clone();
                concealedWithLast[winCode]++;
                Hands handsWithLast = new Hands(concealedWithLast, last, melds);
                if (!handsWithLast.getCanWin()) {
                    return RonEvaluation.noWin();
                }
                hands = handsWithLast;
            }
            MahjongPlayer player = new MahjongPlayer(hands, generalSituation, personalSituation);
            player.calculate();
            boolean yakuman = !player.getYakumanList().isEmpty();
            if (yakuman) {
                List<Yakuman> yakumanNames = List.copyOf(player.getYakumanList());
                return new RonEvaluation(
                        13,
                        true,
                        yakumanNames,
                        List.of(),
                        player.getScore(),
                        winnerIsParent);
            }
            int han = Math.max(0, player.getHan());
            if (han <= 0) {
                return RonEvaluation.noWin();
            }
            ArrayList<NormalYaku> yakuLines = new ArrayList<>();
            boolean openHand = hands.isOpen();
            for (NormalYaku y : player.getNormalYakuList()) {
                int value = openHand ? y.getKuisagari() : y.getHan();
                for (int i = 0; i < value; i++) {
                    yakuLines.add(y);
                }
            }
            return new RonEvaluation(
                    han,
                    false,
                    List.of(),
                    yakuLines,
                    player.getScore(),
                    winnerIsParent);
        } catch (MahjongTileOverFlowException | MahjongIllegalMentsuSizeException | RuntimeException e) {
            return RonEvaluation.noWin();
        }
    }

    public static boolean isEpicVerdictNotification(int han, boolean yakuman) {
        return yakuman || han >= 5;
    }

    public static VerdictTier verdictTier(int han, boolean yakuman) {
        if (yakuman) {
            return VerdictTier.YAKUMAN;
        }
        if (han >= 13) {
            return VerdictTier.KAZOE_YAKUMAN;
        }
        if (han >= 11) {
            return VerdictTier.SANBAIMAN;
        }
        if (han >= 8) {
            return VerdictTier.BAIMAN;
        }
        if (han >= 6) {
            return VerdictTier.HANEMAN;
        }
        if (han >= 5) {
            return VerdictTier.MANGAN;
        }
        return VerdictTier.HAN;
    }

    public static TsumoSplit resolveCompactTsumoSplit(
            boolean winnerIsParent, int parentTsumoEach, int parentPay, int childPay) {
        if (winnerIsParent) {
            int eachPay = Math.max(0, parentTsumoEach);
            return new TsumoSplit(true, eachPay, eachPay);
        }
        return new TsumoSplit(false, Math.max(0, parentPay), Math.max(0, childPay));
    }

    private static boolean isTenpaiFromThirteenLikeHand(int[] concealedCounts, List<Mentsu> openAsMentsu) {
        return !winningShapeWaitCodes(concealedCounts, openAsMentsu).isEmpty();
    }
}
