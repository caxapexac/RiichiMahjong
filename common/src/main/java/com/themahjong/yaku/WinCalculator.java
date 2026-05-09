package com.themahjong.yaku;

import com.themahjong.TheMahjongRuleSet;
import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongTile;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Computes a {@link WinResult} from a set of hand decompositions and round state.
 *
 * When multiple decompositions are possible (e.g. ambiguous shanpon vs tanki), the
 * highest-scoring one is chosen. The winner always collects all riichi sticks on the
 * table.
 *
 * Payment formulas (Tenhou / EMA rules):
 *   basic = min(fu × 2^(han+2), mangan_basic)   (capped by limit hand thresholds)
 *   Dealer ron:         basic × 6, rounded up to 100
 *   Non-dealer ron:     basic × 4, rounded up to 100
 *   Dealer tsumo each:  basic × 2 per non-dealer, rounded up to 100
 *   Non-dealer tsumo:   basic × 2 from dealer, basic × 1 from each other non-dealer
 *   Honba bonus (ron):  +300 per honba from the single payer
 *   Honba bonus (tsumo): +100 per honba per payer
 */
public final class WinCalculator {

    private static final int MANGAN_BASIC    = 2000;
    private static final int HANEMAN_BASIC   = 3000;
    private static final int BAIMAN_BASIC    = 4000;
    private static final int SANBAIMAN_BASIC = 6000;
    private static final int YAKUMAN_BASIC   = 8000;

    private WinCalculator() {}

    /**
     * Picks the best-scoring decomposition and returns the full win result.
     *
     * @param decompositions  all valid hand decompositions; must be non-empty
     * @param ctx             win context (tsumo/ron, riichi state, winds, etc.)
     * @param allWinnerTiles  all tiles belonging to the winner including the winning tile
     *                        (concealed hand + all meld tiles)
     * @param doraIndicators  face-up dora indicators (already subList'd to revealedCount)
     * @param uraDoraIndicators ura-dora indicators; pass empty list if player is not in riichi
     * @param playerCount     number of players in the round
     * @param winnerSeat      seat index of the winner
     * @param loserSeat       seat index of the discarder (== winnerSeat for tsumo)
     * @param dealerSeat      current dealer seat
     * @param honba           current honba count
     * @param riichiSticks    riichi sticks on the table (winner collects all)
     * @param rules           scoring rule variant (WRC, Tenhou, etc.)
     */
    public static WinResult calculateBest(
            List<HandShape> decompositions,
            WinContext ctx,
            List<TheMahjongTile> allWinnerTiles,
            List<TheMahjongTile> doraIndicators,
            List<TheMahjongTile> uraDoraIndicators,
            int playerCount,
            int winnerSeat,
            int loserSeat,
            int dealerSeat,
            int honba,
            int riichiSticks,
            TheMahjongRuleSet rules) {
        return calculateBest(decompositions, ctx, allWinnerTiles, doraIndicators, uraDoraIndicators,
                playerCount, winnerSeat, loserSeat, dealerSeat, honba, riichiSticks, rules, 0);
    }

    /** Sanma-aware overload. {@code winnerKitaCount} is added to the dora total when
     *  {@code rules.kitaCountsAsDora()} is true. Pass 0 in 4-player games. */
    public static WinResult calculateBest(
            List<HandShape> decompositions,
            WinContext ctx,
            List<TheMahjongTile> allWinnerTiles,
            List<TheMahjongTile> doraIndicators,
            List<TheMahjongTile> uraDoraIndicators,
            int playerCount,
            int winnerSeat,
            int loserSeat,
            int dealerSeat,
            int honba,
            int riichiSticks,
            TheMahjongRuleSet rules,
            int winnerKitaCount) {

        int doraCount = countDora(allWinnerTiles, doraIndicators, uraDoraIndicators, playerCount);
        if (rules.kitaCountsAsDora()) doraCount += winnerKitaCount;
        boolean handClosed = isHandClosed(decompositions);

        WinResult best = null;
        for (HandShape shape : decompositions) {
            List<Yakuman> yakumanList = Yakuman.check(shape, ctx, rules);
            List<NonYakuman> yakuList;
            int han;
            int fu = FuCalculator.calculate(shape, ctx, rules);

            if (!yakumanList.isEmpty()) {
                yakuList = List.of();
                // WRC: renhou scores as mangan (5 han), not yakuman level
                if (yakumanList.size() == 1 && yakumanList.get(0) == Yakuman.RENHOU) {
                    han = 5;
                } else {
                    han = yakumanList.size() * 13;
                }
            } else {
                yakuList = NonYakuman.check(shape, ctx);
                if (yakuList.isEmpty()) continue;
                han = 0;
                for (NonYakuman y : yakuList) {
                    han += handClosed ? y.closedHan() : y.openHan().orElse(0);
                }
                han += doraCount;
            }

            // Multi-yakuman scales the basic points (2 yakuman = 16000, etc.). RENHOU
            // is exempt because it scores as mangan, not as yakuman.
            int yakumanMultiplier = (yakumanList.isEmpty()
                    || (yakumanList.size() == 1 && yakumanList.get(0) == Yakuman.RENHOU))
                    ? 1
                    : yakumanList.size();

            OptionalInt paoSeat = findPaoSeat(shape, yakumanList, rules);
            List<Integer> deltas = computeDeltas(
                    han, fu, ctx.tsumo(), ctx.dealer(),
                    playerCount, winnerSeat, loserSeat, dealerSeat, honba, riichiSticks,
                    rules.kiriageMangan(), paoSeat, yakumanMultiplier, rules.tsumoLoss());
            WinResult candidate = new WinResult(yakuList, yakumanList, han, fu, doraCount, deltas);
            if (best == null || winnerGain(candidate, winnerSeat) > winnerGain(best, winnerSeat)) {
                best = candidate;
            }
        }

        if (best == null) {
            // No valid decomposition with yaku — zero result (caller should handle this)
            return new WinResult(List.of(), List.of(), 0, 0, doraCount, zeroDeltas(playerCount));
        }
        return best;
    }

    // -------------------------------------------------------------------------
    // Payment calculation
    // -------------------------------------------------------------------------

    private static List<Integer> computeDeltas(
            int han, int fu, boolean isTsumo, boolean isDealer,
            int playerCount, int winnerSeat, int loserSeat, int dealerSeat,
            int honba, int riichiSticks, boolean kiriageMangan, OptionalInt paoSeat,
            int yakumanMultiplier, boolean tsumoLoss) {

        int[] deltas = new int[playerCount];
        int basic = basicPoints(han, fu, kiriageMangan) * yakumanMultiplier;
        // Ron honba scales with the number of would-be tsumo payers: 4P = 300/stick,
        // 3P sanma = 200/stick, derived as (playerCount-1) × 100 (each tsumo payer
        // contributes 100/stick).
        int ronHonbaPerStick = (playerCount - 1) * 100;

        if (paoSeat.isPresent()) {
            int pao = paoSeat.getAsInt();
            if (isTsumo) {
                // Pao player pays the full combined tsumo amount; other players pay nothing.
                int total = 0;
                if (isDealer) {
                    total = (roundUp100(basic * 2) + honba * 100) * (playerCount - 1);
                } else {
                    int dealerPays    = roundUp100(basic * 2) + honba * 100;
                    int nonDealerPays = roundUp100(basic)     + honba * 100;
                    for (int i = 0; i < playerCount; i++) {
                        if (i == winnerSeat) continue;
                        total += (i == dealerSeat) ? dealerPays : nonDealerPays;
                    }
                }
                deltas[pao] -= total;
                deltas[winnerSeat] += total;
            } else {
                // Pao on ron: pao seat and the actual discarder split the payment 50/50.
                // (Tenhou rule. Some rulesets put 100% on pao — model that via TheMahjongRuleSet
                // if needed.) Honba pass-through goes to the discarder, not the pao seat.
                int total = roundUp100(basic * (isDealer ? 6 : 4));
                int paoShare = roundUp100(total / 2);
                int discarderShare = total - paoShare + honba * ronHonbaPerStick;
                if (pao == loserSeat) {
                    deltas[loserSeat] -= total + honba * ronHonbaPerStick;
                } else {
                    deltas[pao]       -= paoShare;
                    deltas[loserSeat] -= discarderShare;
                }
                deltas[winnerSeat] += total + honba * ronHonbaPerStick;
            }
        } else if (isTsumo) {
            // Sanma north-bisection: imagined NORTH share is split among remaining payers
            // (rounded up to 100 each). When playerCount==3 and !tsumoLoss, this redistributes
            // the missing 4th seat's share. Tsumo-loss simply omits it.
            int sanmaShareEach = 0;
            if (playerCount == 3 && !tsumoLoss) {
                int northWouldPay = isDealer ? (basic * 2) : basic; // pre-rounding
                sanmaShareEach = roundUp100(northWouldPay / 2);
            }
            if (isDealer) {
                int each = roundUp100(basic * 2) + honba * 100 + sanmaShareEach;
                for (int i = 0; i < playerCount; i++) {
                    if (i != winnerSeat) {
                        deltas[i] -= each;
                        deltas[winnerSeat] += each;
                    }
                }
            } else {
                int dealerPays    = roundUp100(basic * 2) + honba * 100 + sanmaShareEach;
                int nonDealerPays = roundUp100(basic)     + honba * 100 + sanmaShareEach;
                for (int i = 0; i < playerCount; i++) {
                    if (i == winnerSeat) continue;
                    int pays = (i == dealerSeat) ? dealerPays : nonDealerPays;
                    deltas[i] -= pays;
                    deltas[winnerSeat] += pays;
                }
            }
        } else {
            int pays = roundUp100(basic * (isDealer ? 6 : 4)) + honba * ronHonbaPerStick;
            deltas[loserSeat] -= pays;
            deltas[winnerSeat] += pays;
        }

        deltas[winnerSeat] += riichiSticks * 1000;

        List<Integer> result = new ArrayList<>(playerCount);
        for (int d : deltas) result.add(d);
        return List.copyOf(result);
    }

    // -------------------------------------------------------------------------
    // Limit hand thresholds and basic points
    // -------------------------------------------------------------------------

    static int basicPoints(int han, int fu, boolean kiriageMangan) {
        if (han >= 13) return YAKUMAN_BASIC;
        if (han >= 11) return SANBAIMAN_BASIC;
        if (han >= 8)  return BAIMAN_BASIC;
        if (han >= 6)  return HANEMAN_BASIC;
        if (kiriageMangan && ((han == 4 && fu == 30) || (han == 3 && fu == 60))) return MANGAN_BASIC;
        return Math.min(fu * (1 << (han + 2)), MANGAN_BASIC);
    }

    private static int roundUp100(int x) {
        return ((x + 99) / 100) * 100;
    }

    // -------------------------------------------------------------------------
    // Pao (liability) detection
    // -------------------------------------------------------------------------

    /**
     * Returns the pao (liability) seat for the winning hand, if any.
     *
     * Pao always applies for Daisangen and Daisuushi.
     * For Suukantsu it applies only when {@code rules.paoOnSuukantsu()} is true.
     *
     * The pao seat is the player who discarded the tile that completed the qualifying yakuman set
     * (3rd dragon group for Daisangen, 4th wind group for Daisuushi, 4th kan for Suukantsu).
     * A group completed via Ankan carries no pao liability.
     */
    static OptionalInt findPaoSeat(HandShape shape, List<Yakuman> yakumanList, TheMahjongRuleSet rules) {
        if (!(shape instanceof HandShape.Standard s)) return OptionalInt.empty();

        boolean hasDaisangen = yakumanList.contains(Yakuman.DAISANGEN);
        boolean hasDaisuushi = yakumanList.contains(Yakuman.DAISUUSHI);
        boolean hasSuukantsu = rules.paoOnSuukantsu() && yakumanList.contains(Yakuman.SUUKANTSU);

        if (!hasDaisangen && !hasDaisuushi && !hasSuukantsu) return OptionalInt.empty();

        OptionalInt result = OptionalInt.empty();

        // Checked in increasing-dominance order; last match wins when multiple apply.
        if (hasDaisangen) result = lastHonorGroupPaoSeat(s.melds(), TheMahjongTile.Suit.DRAGON);
        if (hasDaisuushi) result = lastHonorGroupPaoSeat(s.melds(), TheMahjongTile.Suit.WIND);
        if (hasSuukantsu) result = lastKanPaoSeat(s.melds());

        return result;
    }

    /**
     * Pao seat for Daisangen/Daisuushi: the discarder of the LAST meld whose first
     * tile matches {@code suit}, but only if the qualifying set is fully assembled
     * via open calls. Specifically, all distinct ranks of {@code suit} that appear
     * in the yakuman set (3 dragons / 4 winds) must each be present in {@code melds}.
     * If any are concealed (self-drawn or ron-completed), no pao applies.
     */
    private static OptionalInt lastHonorGroupPaoSeat(List<TheMahjongMeld> melds, TheMahjongTile.Suit suit) {
        int requiredRanks = (suit == TheMahjongTile.Suit.DRAGON) ? 3 : 4;
        java.util.Set<Integer> seenRanks = new java.util.HashSet<>();
        int lastSeat = -1;
        for (TheMahjongMeld m : melds) {
            if (m instanceof TheMahjongMeld.Chi) continue;
            if (m.tiles().isEmpty() || m.tiles().get(0).suit() != suit) continue;
            seenRanks.add(m.tiles().get(0).rank());
            lastSeat = meldPaoSeat(m);
        }
        if (seenRanks.size() < requiredRanks) return OptionalInt.empty();
        return lastSeat >= 0 ? OptionalInt.of(lastSeat) : OptionalInt.empty();
    }

    /** Seat of the discarder of the last kan meld; empty if the last kan was Ankan. */
    private static OptionalInt lastKanPaoSeat(List<TheMahjongMeld> melds) {
        int seat = -1;
        for (TheMahjongMeld m : melds) {
            if (m instanceof TheMahjongMeld.Daiminkan || m instanceof TheMahjongMeld.Kakan || m instanceof TheMahjongMeld.Ankan) {
                seat = meldPaoSeat(m);
            }
        }
        return seat >= 0 ? OptionalInt.of(seat) : OptionalInt.empty();
    }

    /**
     * Returns the seat that discarded the tile used in this meld declaration, or -1 for Ankan
     * (self-drawn — no pao liability).
     * For Kakan, the liability falls on whoever discarded the original Pon tile.
     */
    private static int meldPaoSeat(TheMahjongMeld m) {
        if (m instanceof TheMahjongMeld.Pon p)       return p.sourceSeat();
        if (m instanceof TheMahjongMeld.Daiminkan d) return d.sourceSeat();
        if (m instanceof TheMahjongMeld.Kakan k)     return k.upgradedFrom().sourceSeat();
        return -1; // Ankan — no pao
    }

    // -------------------------------------------------------------------------
    // Dora counting
    // -------------------------------------------------------------------------

    private static int countDora(
            List<TheMahjongTile> tiles,
            List<TheMahjongTile> doraIndicators,
            List<TheMahjongTile> uraDoraIndicators,
            int playerCount) {
        int count = 0;
        for (TheMahjongTile indicator : doraIndicators) {
            TheMahjongTile dora = doraFromIndicator(indicator, playerCount);
            for (TheMahjongTile t : tiles) {
                if (t.matchesSuitRank(dora)) count++;
            }
        }
        for (TheMahjongTile t : tiles) {
            if (t.redDora()) count++;
        }
        for (TheMahjongTile indicator : uraDoraIndicators) {
            TheMahjongTile ura = doraFromIndicator(indicator, playerCount);
            for (TheMahjongTile t : tiles) {
                if (t.matchesSuitRank(ura)) count++;
            }
        }
        return count;
    }

    /** Backward-compat: assumes 4-player deck. */
    static TheMahjongTile doraFromIndicator(TheMahjongTile indicator) {
        return doraFromIndicator(indicator, 4);
    }

    /** Next tile after the indicator (wraps at suit boundary). In sanma (3-player) the
     *  manzu suit only has 1m and 9m, so 1m → 9m and 9m → 1m. */
    static TheMahjongTile doraFromIndicator(TheMahjongTile indicator, int playerCount) {
        if (playerCount == 3 && indicator.suit() == TheMahjongTile.Suit.MANZU) {
            int wrapped = (indicator.rank() == 1) ? 9 : 1;
            return new TheMahjongTile(TheMahjongTile.Suit.MANZU, wrapped, false);
        }
        int next = (indicator.rank() % indicator.suit().maxRank()) + 1;
        return new TheMahjongTile(indicator.suit(), next, false);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** True when every decomposition has a closed hand (no open melds). */
    private static boolean isHandClosed(List<HandShape> decompositions) {
        for (HandShape shape : decompositions) {
            if (shape instanceof HandShape.Standard s && !s.closed()) return false;
        }
        return true;
    }

    private static int winnerGain(WinResult r, int winnerSeat) {
        return r.pointDeltas().get(winnerSeat);
    }

    private static List<Integer> zeroDeltas(int playerCount) {
        List<Integer> z = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++) z.add(0);
        return List.copyOf(z);
    }
}
