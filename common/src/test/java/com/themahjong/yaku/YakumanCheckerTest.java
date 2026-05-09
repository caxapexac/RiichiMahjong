package com.themahjong.yaku;

import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongRuleSet;
import com.themahjong.TheMahjongTile;
import com.themahjong.TheMahjongTile.Suit;
import com.themahjong.yaku.HandShape.Chitoitsu;
import com.themahjong.yaku.HandShape.ConcealedGroup;
import com.themahjong.yaku.HandShape.Kokushimusou;
import com.themahjong.yaku.HandShape.Standard;
import org.junit.jupiter.api.Test;

import java.util.List;

import com.themahjong.TheMahjongPlayer.RiichiState;

import static com.themahjong.TheMahjongTile.Dragon.*;
import static com.themahjong.TheMahjongTile.Wind.*;
import static com.themahjong.yaku.Yakuman.*;
import static org.junit.jupiter.api.Assertions.*;

class YakumanCheckerTest {

    // -------------------------------------------------------------------------
    // Tile factories
    // -------------------------------------------------------------------------

    private static TheMahjongTile m(int rank) { return new TheMahjongTile(Suit.MANZU, rank, false); }
    private static TheMahjongTile p(int rank) { return new TheMahjongTile(Suit.PINZU, rank, false); }
    private static TheMahjongTile s(int rank) { return new TheMahjongTile(Suit.SOUZU, rank, false); }
    private static TheMahjongTile wind(TheMahjongTile.Wind w) { return new TheMahjongTile(Suit.WIND, w.tileRank(), false); }
    private static TheMahjongTile dragon(TheMahjongTile.Dragon d) { return new TheMahjongTile(Suit.DRAGON, d.tileRank(), false); }

    // -------------------------------------------------------------------------
    // ConcealedGroup helpers (always closed — concealed hand decomposition)
    // -------------------------------------------------------------------------

    private static ConcealedGroup.Triplet concealedTriplet(TheMahjongTile tile) {
        return new ConcealedGroup.Triplet(List.of(tile, tile, tile));
    }

    private static ConcealedGroup.Sequence concealedSeq(TheMahjongTile t1, TheMahjongTile t2, TheMahjongTile t3) {
        return new ConcealedGroup.Sequence(List.of(t1, t2, t3));
    }

    // -------------------------------------------------------------------------
    // TheMahjongMeld helpers (declared melds — open or closed)
    // -------------------------------------------------------------------------

    /** Open triplet claimed from seat 1, discard 0. */
    private static TheMahjongMeld.Pon openPon(TheMahjongTile tile) {
        return new TheMahjongMeld.Pon(List.of(tile, tile, tile), 0, 1, 0);
    }

    /** Closed quad (self-drawn). */
    private static TheMahjongMeld.Ankan closedAnkan(TheMahjongTile tile) {
        return new TheMahjongMeld.Ankan(List.of(tile, tile, tile, tile));
    }

    /** Open quad claimed from seat 1, discard 0. */
    private static TheMahjongMeld.Daiminkan openDaiminkan(TheMahjongTile tile) {
        return new TheMahjongMeld.Daiminkan(List.of(tile, tile, tile, tile), 0, 1, 0);
    }

    /** Open chi, claimed tile is t1, claimed from seat 1, discard 0. */
    private static TheMahjongMeld.Chi openChi(TheMahjongTile t1, TheMahjongTile t2, TheMahjongTile t3) {
        return new TheMahjongMeld.Chi(List.of(t1, t2, t3), 0, 1, 0);
    }

    // -------------------------------------------------------------------------
    // Standard hand builder shortcut
    // -------------------------------------------------------------------------

    /** Fully concealed Standard hand with no declared melds. */
    private static Standard closed(List<ConcealedGroup> groups, TheMahjongTile pair) {
        return new Standard(List.of(), groups, pair);
    }

    private static WinContext tsumoCtx(TheMahjongTile winTile) {
        return WinContext.tsumo(false, false, RiichiState.NONE, false,
                winTile, TheMahjongTile.Wind.EAST, TheMahjongTile.Wind.EAST, false, false);
    }

    // -------------------------------------------------------------------------
    // KOKUSHIMUSOU
    // -------------------------------------------------------------------------

    @Test
    void kokushimusou_matchesKokushiShape() {
        assertTrue(checkKokushimusou(new Kokushimusou(m(1))));
    }

    @Test
    void kokushimusou_rejectsStandardShape() {
        Standard s = closed(List.of(
                concealedTriplet(m(1)), concealedTriplet(m(9)),
                concealedTriplet(p(1)), concealedTriplet(p(9))), m(1));
        assertFalse(checkKokushimusou(s));
    }

    // -------------------------------------------------------------------------
    // SUUANKOU
    // -------------------------------------------------------------------------

    @Test
    void suuankou_fourClosedTriplets() {
        Standard s = closed(List.of(
                concealedTriplet(m(2)), concealedTriplet(m(5)),
                concealedTriplet(p(3)), concealedTriplet(s(7))), m(9));
        assertTrue(checkSuuankou(s, tsumoCtx(m(9))));
    }

    @Test
    void suuankou_fourClosedAnkans() {
        Standard s = new Standard(
                List.of(closedAnkan(m(2)), closedAnkan(m(5)), closedAnkan(p(3)), closedAnkan(s(7))),
                List.of(), m(9));
        assertTrue(checkSuuankou(s, tsumoCtx(m(9))));
    }

    @Test
    void suuankou_rejectsOneOpenPon() {
        Standard s = new Standard(
                List.of(openPon(s(7))),
                List.of(concealedTriplet(m(2)), concealedTriplet(m(5)), concealedTriplet(p(3))),
                m(9));
        assertFalse(checkSuuankou(s, tsumoCtx(m(9))));
    }

    @Test
    void suuankou_rejectsConcealedSequence() {
        Standard s = closed(List.of(
                concealedTriplet(m(2)), concealedTriplet(m(5)),
                concealedTriplet(p(3)), concealedSeq(s(1), s(2), s(3))), m(9));
        assertFalse(checkSuuankou(s, tsumoCtx(m(9))));
    }

    // -------------------------------------------------------------------------
    // CHUURENPOUTOU
    // -------------------------------------------------------------------------

    @Test
    void chuurenpoutou_incorrectDecompositionReturnsFalse() {
        // kotsu(1) + seq(2-3-4) + seq(5-6-7) + kotsu(9) + pair(5)
        // counts: 1→3, 5→3, 8→0  — pattern requires 8→1, so fails
        Standard s = closed(List.of(
                concealedTriplet(m(1)),
                concealedSeq(m(2), m(3), m(4)),
                concealedSeq(m(5), m(6), m(7)),
                concealedTriplet(m(9))), m(5));
        assertFalse(checkChuurenpoutou(s));
    }

    @Test
    void chuurenpoutou_validDecomposition() {
        // kotsu(1) + seq(1-2-3) + seq(4-5-6) + seq(7-8-9) + pair(9)
        // counts: 1→4(diff+1), 2–8→1 each, 9→3  →  extras=1 ✓
        Standard s = closed(List.of(
                concealedTriplet(m(1)),
                concealedSeq(m(1), m(2), m(3)),
                concealedSeq(m(4), m(5), m(6)),
                concealedSeq(m(7), m(8), m(9))), m(9));
        assertTrue(checkChuurenpoutou(s));
    }

    @Test
    void chuurenpoutou_rejectsMixedSuit() {
        Standard s = closed(List.of(
                concealedTriplet(m(1)),
                concealedSeq(m(1), m(2), m(3)),
                concealedSeq(m(4), m(5), m(6)),
                concealedSeq(p(7), p(8), p(9))), m(9));  // pinzu — wrong suit
        assertFalse(checkChuurenpoutou(s));
    }

    @Test
    void chuurenpoutou_rejectsOpenHand() {
        Standard s = new Standard(
                List.of(openChi(m(7), m(8), m(9))),
                List.of(concealedTriplet(m(1)), concealedSeq(m(1), m(2), m(3)), concealedSeq(m(4), m(5), m(6))),
                m(9));
        assertFalse(checkChuurenpoutou(s));
    }

    @Test
    void chuurenpoutou_rejectsTwoExtraTiles() {
        // Two triplets of rank 1 → counts[0]=8, diff=+5 → rejected
        Standard s = closed(List.of(
                concealedTriplet(m(1)),
                concealedTriplet(m(1)),
                concealedSeq(m(4), m(5), m(6)),
                concealedSeq(m(7), m(8), m(9))), m(9));
        assertFalse(checkChuurenpoutou(s));
    }

    // -------------------------------------------------------------------------
    // DAISANGEN
    // -------------------------------------------------------------------------

    @Test
    void daisangen_threeClosedDragonTriplets() {
        Standard s = closed(List.of(
                concealedTriplet(dragon(HAKU)),
                concealedTriplet(dragon(HATSU)),
                concealedTriplet(dragon(CHUN)),
                concealedSeq(m(3), m(4), m(5))), m(7));
        assertTrue(checkDaisangen(s));
    }

    @Test
    void daisangen_threeOpenDragonPons() {
        Standard s = new Standard(
                List.of(openPon(dragon(HAKU)), openPon(dragon(HATSU)), openPon(dragon(CHUN))),
                List.of(concealedSeq(m(3), m(4), m(5))),
                m(7));
        assertTrue(checkDaisangen(s));
    }

    @Test
    void daisangen_rejectsTwoDragons() {
        Standard s = closed(List.of(
                concealedTriplet(dragon(HAKU)),
                concealedTriplet(dragon(HATSU)),
                concealedSeq(m(3), m(4), m(5)),
                concealedSeq(m(6), m(7), m(8))), m(9));
        assertFalse(checkDaisangen(s));
    }

    // -------------------------------------------------------------------------
    // TSUISOU
    // -------------------------------------------------------------------------

    @Test
    void tsuisou_standardFormAllHonors() {
        Standard s = closed(List.of(
                concealedTriplet(wind(EAST)),
                concealedTriplet(wind(SOUTH)),
                concealedTriplet(wind(WEST)),
                concealedTriplet(dragon(HAKU))), wind(NORTH));
        assertTrue(checkTsuisou(s));
    }

    @Test
    void tsuisou_chitoitsuFormAllHonors() {
        Chitoitsu c = new Chitoitsu(List.of(
                wind(EAST), wind(SOUTH), wind(WEST), wind(NORTH),
                dragon(HAKU), dragon(HATSU), dragon(CHUN)));
        assertTrue(checkTsuisou(c));
    }

    @Test
    void tsuisou_rejectsNumberedTile() {
        Standard s = closed(List.of(
                concealedTriplet(wind(EAST)),
                concealedTriplet(wind(SOUTH)),
                concealedTriplet(wind(WEST)),
                concealedTriplet(m(1))), wind(NORTH));  // not an honor
        assertFalse(checkTsuisou(s));
    }

    @Test
    void tsuisou_rejectsChi() {
        Standard s = new Standard(
                List.of(openChi(m(1), m(2), m(3))),
                List.of(concealedTriplet(wind(EAST)), concealedTriplet(wind(SOUTH)), concealedTriplet(wind(WEST))),
                dragon(HAKU));
        assertFalse(checkTsuisou(s));
    }

    // -------------------------------------------------------------------------
    // SHOUSUUSHI
    // -------------------------------------------------------------------------

    @Test
    void shousuushi_threeWindTripletsWindPair() {
        Standard s = closed(List.of(
                concealedTriplet(wind(EAST)),
                concealedTriplet(wind(SOUTH)),
                concealedTriplet(wind(WEST)),
                concealedSeq(m(3), m(4), m(5))), wind(NORTH));
        assertTrue(checkShousuushi(s));
    }

    @Test
    void shousuushi_rejectsFourWindTriplets() {
        // 4 wind triplets → daisuushi, not shousuushi
        Standard s = closed(List.of(
                concealedTriplet(wind(EAST)),
                concealedTriplet(wind(SOUTH)),
                concealedTriplet(wind(WEST)),
                concealedTriplet(wind(NORTH))), dragon(HAKU));
        assertFalse(checkShousuushi(s));
    }

    @Test
    void shousuushi_rejectsNonWindPair() {
        Standard s = closed(List.of(
                concealedTriplet(wind(EAST)),
                concealedTriplet(wind(SOUTH)),
                concealedTriplet(wind(WEST)),
                concealedSeq(m(3), m(4), m(5))), m(7));  // non-wind pair
        assertFalse(checkShousuushi(s));
    }

    // -------------------------------------------------------------------------
    // DAISUUSHI
    // -------------------------------------------------------------------------

    @Test
    void daisuushi_fourWindTriplets() {
        Standard s = closed(List.of(
                concealedTriplet(wind(EAST)),
                concealedTriplet(wind(SOUTH)),
                concealedTriplet(wind(WEST)),
                concealedTriplet(wind(NORTH))), dragon(HAKU));
        assertTrue(checkDaisuushi(s));
    }

    @Test
    void daisuushi_rejectsThreeWinds() {
        Standard s = closed(List.of(
                concealedTriplet(wind(EAST)),
                concealedTriplet(wind(SOUTH)),
                concealedTriplet(wind(WEST)),
                concealedSeq(m(3), m(4), m(5))), wind(NORTH));
        assertFalse(checkDaisuushi(s));
    }

    // -------------------------------------------------------------------------
    // RYUUIISOU
    // -------------------------------------------------------------------------

    @Test
    void ryuuiisou_allGreenTriplets() {
        Standard s = closed(List.of(
                concealedTriplet(s(2)),
                concealedTriplet(s(3)),
                concealedTriplet(s(6)),
                concealedTriplet(dragon(HATSU))), s(8));
        assertTrue(checkRyuuiisou(s));
    }

    @Test
    void ryuuiisou_validGreenSequence234() {
        // S2-S3-S4 sequence — all three tiles are green
        Standard s = closed(List.of(
                concealedSeq(s(2), s(3), s(4)),
                concealedTriplet(s(6)),
                concealedTriplet(s(8)),
                concealedTriplet(dragon(HATSU))), s(2));
        assertTrue(checkRyuuiisou(s));
    }

    @Test
    void ryuuiisou_rejectsSequence345BecauseSouzu5NotGreen() {
        // Bug in legacy: S3 shuntsu (3-4-5) was incorrectly allowed; S5 is not green
        Standard s = closed(List.of(
                concealedSeq(s(3), s(4), s(5)),    // s(5) is not green
                concealedTriplet(s(6)),
                concealedTriplet(s(8)),
                concealedTriplet(dragon(HATSU))), s(2));
        assertFalse(checkRyuuiisou(s));
    }

    @Test
    void ryuuiisou_rejectsNonGreenTile() {
        Standard s = closed(List.of(
                concealedTriplet(s(2)),
                concealedTriplet(s(3)),
                concealedTriplet(s(5)),             // s(5) not green
                concealedTriplet(dragon(HATSU))), s(8));
        assertFalse(checkRyuuiisou(s));
    }

    @Test
    void ryuuiisou_chitoitsuForm() {
        Chitoitsu c = new Chitoitsu(List.of(s(2), s(3), s(4), s(6), s(8), dragon(HATSU), s(2)));
        assertTrue(checkRyuuiisou(c));
    }

    // -------------------------------------------------------------------------
    // CHINROUTOU
    // -------------------------------------------------------------------------

    @Test
    void chinroutou_allTerminalTriplets() {
        Standard s = closed(List.of(
                concealedTriplet(m(1)),
                concealedTriplet(m(9)),
                concealedTriplet(p(1)),
                concealedTriplet(s(9))), p(9));
        assertTrue(checkChinroutou(s));
    }

    @Test
    void chinroutou_rejectsChi() {
        Standard s = new Standard(
                List.of(openChi(m(1), m(2), m(3))),
                List.of(concealedTriplet(m(1)), concealedTriplet(m(9)), concealedTriplet(p(1))),
                p(9));
        assertFalse(checkChinroutou(s));
    }

    @Test
    void chinroutou_rejectsConcealedSequence() {
        Standard s = closed(List.of(
                concealedTriplet(m(1)),
                concealedTriplet(m(9)),
                concealedTriplet(p(1)),
                concealedSeq(m(1), m(2), m(3))), p(9));
        assertFalse(checkChinroutou(s));
    }

    @Test
    void chinroutou_rejectsMiddleTile() {
        Standard s = closed(List.of(
                concealedTriplet(m(1)),
                concealedTriplet(m(9)),
                concealedTriplet(p(1)),
                concealedTriplet(m(5))), p(9));    // not a terminal
        assertFalse(checkChinroutou(s));
    }

    // -------------------------------------------------------------------------
    // SUUKANTSU
    // -------------------------------------------------------------------------

    @Test
    void suukantsu_fourQuads() {
        Standard s = new Standard(
                List.of(closedAnkan(m(1)), openDaiminkan(m(9)), closedAnkan(p(1)), openDaiminkan(s(9))),
                List.of(), dragon(HAKU));
        assertTrue(checkSuukantsu(s));
    }

    @Test
    void suukantsu_rejectsThreeQuadsOneTriplet() {
        Standard s = new Standard(
                List.of(closedAnkan(m(1)), openDaiminkan(m(9)), closedAnkan(p(1))),
                List.of(concealedTriplet(s(9))),
                dragon(HAKU));
        assertFalse(checkSuukantsu(s));
    }

    // -------------------------------------------------------------------------
    // TENHOU
    // -------------------------------------------------------------------------

    @Test
    void tenhou_dealerTsumoFirstRound() {
        assertTrue(checkTenhou(WinContext.tsumo(true, true, RiichiState.NONE, false, m(1), EAST, EAST, false, false)));
    }

    @Test
    void tenhou_rejectsNonDealer() {
        assertFalse(checkTenhou(WinContext.tsumo(false, true, RiichiState.NONE, false, m(1), EAST, EAST, false, false)));
    }

    @Test
    void tenhou_rejectsRon() {
        assertFalse(checkTenhou(WinContext.ron(true, true, RiichiState.NONE, false, m(1), EAST, EAST, false, false)));
    }

    @Test
    void tenhou_rejectsInterruptedRound() {
        assertFalse(checkTenhou(WinContext.tsumo(true, false, RiichiState.NONE, false, m(1), EAST, EAST, false, false)));
    }

    // -------------------------------------------------------------------------
    // CHIHOU
    // -------------------------------------------------------------------------

    @Test
    void chihou_nonDealerTsumoFirstRound() {
        assertTrue(checkChihou(WinContext.tsumo(false, true, RiichiState.NONE, false, m(1), EAST, EAST, false, false)));
    }

    @Test
    void chihou_rejectsDealer() {
        assertFalse(checkChihou(WinContext.tsumo(true, true, RiichiState.NONE, false, m(1), EAST, EAST, false, false)));
    }

    @Test
    void chihou_rejectsRon() {
        assertFalse(checkChihou(WinContext.ron(false, true, RiichiState.NONE, false, m(1), EAST, EAST, false, false)));
    }

    @Test
    void chihou_rejectsInterruptedRound() {
        assertFalse(checkChihou(WinContext.tsumo(false, false, RiichiState.NONE, false, m(1), EAST, EAST, false, false)));
    }

    // -------------------------------------------------------------------------
    // RENHOU
    // -------------------------------------------------------------------------

    @Test
    void renhou_nonDealerRonClosedFirstRound() {
        Standard s = closed(List.of(
                concealedTriplet(m(1)), concealedTriplet(m(2)),
                concealedTriplet(m(3)), concealedTriplet(m(4))), m(5));
        assertTrue(checkRenhou(s, WinContext.ron(false, true, RiichiState.NONE, false, m(1), EAST, EAST, false, false)));
    }

    @Test
    void renhou_rejectsDealer() {
        Standard s = closed(List.of(
                concealedTriplet(m(1)), concealedTriplet(m(2)),
                concealedTriplet(m(3)), concealedTriplet(m(4))), m(5));
        assertFalse(checkRenhou(s, WinContext.ron(true, true, RiichiState.NONE, false, m(1), EAST, EAST, false, false)));
    }

    @Test
    void renhou_rejectsTsumo() {
        Standard s = closed(List.of(
                concealedTriplet(m(1)), concealedTriplet(m(2)),
                concealedTriplet(m(3)), concealedTriplet(m(4))), m(5));
        assertFalse(checkRenhou(s, WinContext.tsumo(false, true, RiichiState.NONE, false, m(1), EAST, EAST, false, false)));
    }

    @Test
    void renhou_rejectsOpenHand() {
        // Fix vs legacy: open meld disqualifies renhou
        Standard s = new Standard(
                List.of(openPon(m(1))),
                List.of(concealedTriplet(m(2)), concealedTriplet(m(3)), concealedTriplet(m(4))),
                m(5));
        assertFalse(checkRenhou(s, WinContext.ron(false, true, RiichiState.NONE, false, m(1), EAST, EAST, false, false)));
    }

    @Test
    void renhou_rejectsInterruptedRound() {
        Standard s = closed(List.of(
                concealedTriplet(m(1)), concealedTriplet(m(2)),
                concealedTriplet(m(3)), concealedTriplet(m(4))), m(5));
        assertFalse(checkRenhou(s, WinContext.ron(false, false, RiichiState.NONE, false, m(1), EAST, EAST, false, false)));
    }

    // -------------------------------------------------------------------------
    // aggregate check()
    // -------------------------------------------------------------------------

    @Test
    void checkReturnsMultipleWhenApplicable() {
        // Daisuushi + Tsuisou: 4 wind triplets, all tiles are honor
        Standard s = closed(List.of(
                concealedTriplet(wind(EAST)),
                concealedTriplet(wind(SOUTH)),
                concealedTriplet(wind(WEST)),
                concealedTriplet(wind(NORTH))), dragon(HAKU));
        List<Yakuman> results = Yakuman.check(s, WinContext.ron(false, false, RiichiState.NONE, false, m(1), EAST, EAST, false, false), TheMahjongRuleSet.wrc());
        assertTrue(results.contains(DAISUUSHI));
        assertTrue(results.contains(TSUISOU));
    }

    @Test
    void checkReturnsEmptyForOrdinaryHand() {
        Standard s = closed(List.of(
                concealedSeq(m(1), m(2), m(3)),
                concealedSeq(m(4), m(5), m(6)),
                concealedSeq(m(7), m(8), m(9)),
                concealedTriplet(p(5))), s(3));
        assertTrue(Yakuman.check(s, WinContext.ron(false, false, RiichiState.NONE, false, m(1), EAST, EAST, false, false), TheMahjongRuleSet.wrc()).isEmpty());
    }

    // -------------------------------------------------------------------------
    // renhouAllowed gate in check()
    // -------------------------------------------------------------------------

    @Test
    void check_renhou_presentWhenRenhouAllowed() {
        Standard s = closed(List.of(
                concealedTriplet(m(1)), concealedTriplet(m(2)),
                concealedTriplet(m(3)), concealedTriplet(m(4))), m(5));
        WinContext ctx = WinContext.ron(false, true, RiichiState.NONE, false, m(1), EAST, EAST, false, false);
        assertTrue(Yakuman.check(s, ctx, TheMahjongRuleSet.wrc()).contains(RENHOU));
    }

    @Test
    void check_renhou_suppressedWhenRenhouNotAllowed() {
        Standard s = closed(List.of(
                concealedTriplet(m(1)), concealedTriplet(m(2)),
                concealedTriplet(m(3)), concealedTriplet(m(4))), m(5));
        WinContext ctx = WinContext.ron(false, true, RiichiState.NONE, false, m(1), EAST, EAST, false, false);
        assertFalse(Yakuman.check(s, ctx, TheMahjongRuleSet.tenhou()).contains(RENHOU));
    }
}
