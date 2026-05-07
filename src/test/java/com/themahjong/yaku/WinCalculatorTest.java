package com.themahjong.yaku;

import com.themahjong.TheMahjongRuleSet;
import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongTile;
import com.themahjong.TheMahjongTile.Suit;
import com.themahjong.TheMahjongPlayer.RiichiState;
import com.themahjong.yaku.HandShape.ConcealedGroup.Sequence;
import com.themahjong.yaku.HandShape.Standard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class WinCalculatorTest {

    private static TheMahjongTile m(int rank) { return new TheMahjongTile(Suit.MANZU, rank, false); }
    private static TheMahjongTile p(int rank) { return new TheMahjongTile(TheMahjongTile.Suit.PINZU, rank, false); }
    private static TheMahjongTile s(int rank) { return new TheMahjongTile(TheMahjongTile.Suit.SOUZU, rank, false); }

    private static Sequence seq(TheMahjongTile a, TheMahjongTile b, TheMahjongTile c) {
        return new Sequence(List.of(a, b, c));
    }

    // Four distinct sequences + terminal pair: no yaku except renhou.
    // No iipeiko (all different), no tanyao (has terminals), no pinfu (terminal pair), no yakuhai.
    private static Standard renhouHand() {
        return new Standard(List.of(), List.of(
                seq(m(1), m(2), m(3)),
                seq(m(4), m(5), m(6)),
                seq(s(7), s(8), s(9)),
                seq(p(1), p(2), p(3))), p(9));
    }

    private static List<TheMahjongTile> renhouTiles() {
        return List.of(m(1),m(2),m(3), m(4),m(5),m(6), s(7),s(8),s(9), p(1),p(2),p(3), p(9),p(9));
    }

    // -------------------------------------------------------------------------
    // Renhou scoring (WRC: mangan-capped, not yakuman)
    // -------------------------------------------------------------------------

    @Test
    void renhou_wrc_nonDealer_ron_scoresMangan() {
        // Non-dealer ron renhou: basic=2000, payment = roundUp100(2000*4) = 8000
        WinContext ctx = WinContext.ron(
                false, true, RiichiState.NONE, false,
                p(9), TheMahjongTile.Wind.SOUTH, TheMahjongTile.Wind.EAST,
                false, false);

        WinResult result = WinCalculator.calculateBest(
                List.of(renhouHand()), ctx, renhouTiles(),
                List.of(), List.of(),
                4, 1, 0, 0, 0, 0, TheMahjongRuleSet.wrc());

        assertEquals(List.of(Yakuman.RENHOU), result.yakuman());
        assertEquals(5, result.han());
        assertEquals(8000, result.pointDeltas().get(1));
        assertEquals(-8000, result.pointDeltas().get(0));
    }

    @Test
    void renhou_wrc_notYakumanLevel() {
        // WRC renhou must not score at yakuman level (16000 for non-dealer)
        WinContext ctx = WinContext.ron(
                false, true, RiichiState.NONE, false,
                p(9), TheMahjongTile.Wind.SOUTH, TheMahjongTile.Wind.EAST,
                false, false);

        WinResult result = WinCalculator.calculateBest(
                List.of(renhouHand()), ctx, renhouTiles(),
                List.of(), List.of(),
                4, 1, 0, 0, 0, 0, TheMahjongRuleSet.wrc());

        assertNotEquals(16000, result.pointDeltas().get(1));
    }

    // -------------------------------------------------------------------------
    // Pao (liability) — findPaoSeat unit tests
    // -------------------------------------------------------------------------

    private static TheMahjongTile haku()  { return new TheMahjongTile(Suit.DRAGON, 1, false); }
    private static TheMahjongTile hatsu() { return new TheMahjongTile(Suit.DRAGON, 2, false); }
    private static TheMahjongTile chun()  { return new TheMahjongTile(Suit.DRAGON, 3, false); }
    private static TheMahjongTile east()  { return new TheMahjongTile(Suit.WIND,   1, false); }
    private static TheMahjongTile south() { return new TheMahjongTile(Suit.WIND,   2, false); }
    private static TheMahjongTile west()  { return new TheMahjongTile(Suit.WIND,   3, false); }
    private static TheMahjongTile north() { return new TheMahjongTile(Suit.WIND,   4, false); }

    private static TheMahjongMeld.Pon pon(TheMahjongTile t, int fromSeat) {
        return new TheMahjongMeld.Pon(List.of(t, t, t), 0, fromSeat, 0);
    }
    private static TheMahjongMeld.Daiminkan daiminkan(TheMahjongTile t, int fromSeat) {
        return new TheMahjongMeld.Daiminkan(List.of(t, t, t, t), 0, fromSeat, 0);
    }
    private static TheMahjongMeld.Ankan ankan(TheMahjongTile t) {
        return new TheMahjongMeld.Ankan(List.of(t, t, t, t));
    }
    private static TheMahjongMeld.Kakan kakan(TheMahjongMeld.Pon upgradedFrom) {
        return new TheMahjongMeld.Kakan(upgradedFrom, upgradedFrom.tiles().get(0));
    }

    private static HandShape.ConcealedGroup.Sequence seq123m() {
        return new Sequence(List.of(m(1), m(2), m(3)));
    }

    @Test
    void findPaoSeat_daisangen_lastDragonPonFromSeat2() {
        Standard hand = new Standard(
                List.of(pon(haku(), 1), pon(hatsu(), 3), pon(chun(), 2)),
                List.of(seq123m()), m(5));
        assertEquals(OptionalInt.of(2),
                WinCalculator.findPaoSeat(hand, List.of(Yakuman.DAISANGEN), TheMahjongRuleSet.wrc()));
    }

    @Test
    void findPaoSeat_daisangen_lastDragonAnkan_noPao() {
        // 3rd dragon completed via Ankan (self-drawn 4th tile) — no liability
        Standard hand = new Standard(
                List.of(pon(haku(), 1), pon(hatsu(), 3), ankan(chun())),
                List.of(seq123m()), m(5));
        assertEquals(OptionalInt.empty(),
                WinCalculator.findPaoSeat(hand, List.of(Yakuman.DAISANGEN), TheMahjongRuleSet.wrc()));
    }

    @Test
    void findPaoSeat_daisangen_lastDragonKakan_usesOriginalPonSeat() {
        TheMahjongMeld.Pon chunPon = pon(chun(), 3);
        Standard hand = new Standard(
                List.of(pon(haku(), 1), pon(hatsu(), 2), kakan(chunPon)),
                List.of(seq123m()), m(5));
        assertEquals(OptionalInt.of(3),
                WinCalculator.findPaoSeat(hand, List.of(Yakuman.DAISANGEN), TheMahjongRuleSet.wrc()));
    }

    @Test
    void findPaoSeat_daisuushi_lastWindPonFromSeat1() {
        Standard hand = new Standard(
                List.of(pon(east(), 2), pon(south(), 3), pon(west(), 1), pon(north(), 1)),
                List.of(), m(5));
        assertEquals(OptionalInt.of(1),
                WinCalculator.findPaoSeat(hand, List.of(Yakuman.DAISUUSHI), TheMahjongRuleSet.wrc()));
    }

    @Test
    void findPaoSeat_suukantsu_lastKanDaiminkan_wrcPaoEnabled() {
        Standard hand = new Standard(
                List.of(ankan(m(1)), ankan(m(2)), ankan(m(3)), daiminkan(m(4), 2)),
                List.of(), m(5));
        assertEquals(OptionalInt.of(2),
                WinCalculator.findPaoSeat(hand, List.of(Yakuman.SUUKANTSU), TheMahjongRuleSet.wrc()));
    }

    @Test
    void findPaoSeat_suukantsu_lastKanAnkan_noPao() {
        Standard hand = new Standard(
                List.of(daiminkan(m(1), 2), daiminkan(m(2), 3), daiminkan(m(3), 1), ankan(m(4))),
                List.of(), m(5));
        assertEquals(OptionalInt.empty(),
                WinCalculator.findPaoSeat(hand, List.of(Yakuman.SUUKANTSU), TheMahjongRuleSet.wrc()));
    }

    @Test
    void findPaoSeat_suukantsu_tenhouRules_noPao() {
        // Tenhou: paoOnSuukantsu = false — no liability even with Daiminkan 4th kan
        Standard hand = new Standard(
                List.of(ankan(m(1)), ankan(m(2)), ankan(m(3)), daiminkan(m(4), 2)),
                List.of(), m(5));
        assertEquals(OptionalInt.empty(),
                WinCalculator.findPaoSeat(hand, List.of(Yakuman.SUUKANTSU), TheMahjongRuleSet.tenhou()));
    }

    @Test
    void findPaoSeat_noYakumanEligible_empty() {
        Standard hand = new Standard(List.of(), List.of(
                new Sequence(List.of(m(1), m(2), m(3))),
                new Sequence(List.of(m(4), m(5), m(6))),
                new Sequence(List.of(m(7), m(8), m(9))),
                new Sequence(List.of(p(1), p(2), p(3)))), p(9));
        assertEquals(OptionalInt.empty(),
                WinCalculator.findPaoSeat(hand, List.of(), TheMahjongRuleSet.wrc()));
    }

    // -------------------------------------------------------------------------
    // Pao payment — full calculateBest integration tests
    // -------------------------------------------------------------------------

    // Suukantsu: yakuman basic = 8000
    // Non-dealer ron: roundUp100(8000 * 4) = 32000
    // Non-dealer tsumo total (4-player, dealer at 0, winner at 1):
    //   dealer pays roundUp100(8000*2)=16000, non-dealers pay roundUp100(8000*1)=8000 each × 2 = 32000

    private static List<TheMahjongTile> suukantsuTiles() {
        return List.of(m(1),m(1),m(1),m(1), m(2),m(2),m(2),m(2),
                m(3),m(3),m(3),m(3), m(4),m(4),m(4),m(4), m(5),m(5));
    }

    private static Standard suukantsuWithDaiminkan4th(int fromSeat) {
        return new Standard(
                List.of(ankan(m(1)), ankan(m(2)), ankan(m(3)), daiminkan(m(4), fromSeat)),
                List.of(), m(5));
    }

    @Test
    void pao_suukantsu_ron_paoPayerPaysNotLoser() {
        // Winner seat 1, loser seat 3, pao seat 2
        WinContext ctx = WinContext.ron(
                false, false, RiichiState.NONE, false,
                m(5), TheMahjongTile.Wind.SOUTH, TheMahjongTile.Wind.EAST,
                false, false);

        WinResult result = WinCalculator.calculateBest(
                List.of(suukantsuWithDaiminkan4th(2)), ctx, suukantsuTiles(),
                List.of(), List.of(), 4, 1, 3, 0, 0, 0, TheMahjongRuleSet.wrc());

        assertEquals(List.of(Yakuman.SUUKANTSU), result.yakuman());
        assertEquals( 32000, result.pointDeltas().get(1));  // winner gains
        // Standard ron pao split: pao seat and the actual discarder each pay half.
        assertEquals(-16000, result.pointDeltas().get(2));  // pao pays half
        assertEquals(-16000, result.pointDeltas().get(3));  // discarder pays half
        assertEquals(     0, result.pointDeltas().get(0));
    }

    @Test
    void pao_suukantsu_tsumo_paoPayerPaysAll_othersPay0() {
        // Winner seat 1 (non-dealer), dealer seat 0, pao seat 2
        WinContext ctx = WinContext.tsumo(
                false, false, RiichiState.NONE, false,
                m(5), TheMahjongTile.Wind.SOUTH, TheMahjongTile.Wind.EAST,
                false, true);

        WinResult result = WinCalculator.calculateBest(
                List.of(suukantsuWithDaiminkan4th(2)), ctx, suukantsuTiles(),
                List.of(), List.of(), 4, 1, 1, 0, 0, 0, TheMahjongRuleSet.wrc());

        assertEquals( 32000, result.pointDeltas().get(1));  // winner gains
        assertEquals(-32000, result.pointDeltas().get(2));  // pao pays all
        assertEquals(     0, result.pointDeltas().get(0));  // dealer pays nothing
        assertEquals(     0, result.pointDeltas().get(3));  // non-dealer pays nothing
    }

    @Test
    void pao_suukantsu_ankan4th_normalPaymentApplies() {
        // 4th kan is Ankan — no pao; normal ron payment from loser seat 3
        Standard hand = new Standard(
                List.of(daiminkan(m(1), 2), daiminkan(m(2), 3), daiminkan(m(3), 1), ankan(m(4))),
                List.of(), m(5));
        WinContext ctx = WinContext.ron(
                false, false, RiichiState.NONE, false,
                m(5), TheMahjongTile.Wind.SOUTH, TheMahjongTile.Wind.EAST,
                false, false);

        WinResult result = WinCalculator.calculateBest(
                List.of(hand), ctx, suukantsuTiles(),
                List.of(), List.of(), 4, 1, 3, 0, 0, 0, TheMahjongRuleSet.wrc());

        assertEquals(List.of(Yakuman.SUUKANTSU), result.yakuman());
        assertEquals( 32000, result.pointDeltas().get(1));  // winner gains
        assertEquals(-32000, result.pointDeltas().get(3));  // normal loser pays
        assertEquals(     0, result.pointDeltas().get(2));  // seat 2 pays nothing
    }

    @Test
    void pao_suukantsu_tenhouRules_normalPaymentApplies() {
        // paoOnSuukantsu=false on Tenhou — no pao even with Daiminkan 4th kan
        WinContext ctx = WinContext.ron(
                false, false, RiichiState.NONE, false,
                m(5), TheMahjongTile.Wind.SOUTH, TheMahjongTile.Wind.EAST,
                false, false);

        WinResult result = WinCalculator.calculateBest(
                List.of(suukantsuWithDaiminkan4th(2)), ctx, suukantsuTiles(),
                List.of(), List.of(), 4, 1, 3, 0, 0, 0, TheMahjongRuleSet.tenhou());

        assertEquals(-32000, result.pointDeltas().get(3));  // loser pays (no pao)
        assertEquals(     0, result.pointDeltas().get(2));  // seat 2 unaffected
    }

    @Test
    void renhou_tenhou_notAllowed_returnsZeroResult() {
        // Tenhou renhouAllowed=false — hand has no other yaku → zero result
        WinContext ctx = WinContext.ron(
                false, true, RiichiState.NONE, false,
                p(9), TheMahjongTile.Wind.SOUTH, TheMahjongTile.Wind.EAST,
                false, false);

        WinResult result = WinCalculator.calculateBest(
                List.of(renhouHand()), ctx, renhouTiles(),
                List.of(), List.of(),
                4, 1, 0, 0, 0, 0, TheMahjongRuleSet.tenhou());

        assertTrue(result.yakuman().isEmpty());
        assertEquals(0, result.han());
        assertEquals(0, result.pointDeltas().get(1));
    }

    // -------------------------------------------------------------------------
    // Sanma manzu dora wrap (3-player deck has only m1 and m9)
    // -------------------------------------------------------------------------

    @Test
    void doraFromIndicator_3p_wrapsManzu1ToManzu9AndViceVersa() {
        assertEquals(m(9), WinCalculator.doraFromIndicator(m(1), 3));
        assertEquals(m(1), WinCalculator.doraFromIndicator(m(9), 3));
        // Non-manzu suits behave normally in 3p.
        assertEquals(p(2), WinCalculator.doraFromIndicator(p(1), 3));
    }

    @Test
    void doraFromIndicator_4p_doesNotSpecialCaseManzu() {
        assertEquals(m(2), WinCalculator.doraFromIndicator(m(1), 4));
        assertEquals(m(1), WinCalculator.doraFromIndicator(m(9), 4));
    }
}
