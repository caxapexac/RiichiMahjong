package com.themahjong.yaku;

import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongPlayer.RiichiState;
import com.themahjong.TheMahjongTile;
import com.themahjong.TheMahjongTile.Suit;

import static com.themahjong.TheMahjongTile.Wind.*;
import com.themahjong.yaku.HandShape.Chitoitsu;
import com.themahjong.yaku.HandShape.ConcealedGroup;
import com.themahjong.yaku.HandShape.Kokushimusou;
import com.themahjong.yaku.HandShape.Standard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.themahjong.yaku.NonYakuman.*;
import static org.junit.jupiter.api.Assertions.*;

class NonYakumanCheckerTest {

    private static TheMahjongTile m(int rank) { return new TheMahjongTile(Suit.MANZU, rank, false); }
    private static TheMahjongTile p(int rank) { return new TheMahjongTile(Suit.PINZU, rank, false); }
    private static TheMahjongTile wind(TheMahjongTile.Wind w)     { return new TheMahjongTile(Suit.WIND,   w.tileRank(), false); }
    private static TheMahjongTile s(int rank)  { return new TheMahjongTile(Suit.SOUZU, rank, false); }
    private static TheMahjongTile dragon(TheMahjongTile.Dragon d) { return new TheMahjongTile(Suit.DRAGON, d.tileRank(), false); }

    private static ConcealedGroup.Triplet triplet(TheMahjongTile t) {
        return new ConcealedGroup.Triplet(List.of(t, t, t));
    }

    private static ConcealedGroup.Sequence seq(TheMahjongTile t1, TheMahjongTile t2, TheMahjongTile t3) {
        return new ConcealedGroup.Sequence(List.of(t1, t2, t3));
    }

    private static Standard closedStandard() {
        return new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(4), m(5), m(6)),
                        seq(m(7), m(8), m(9)), triplet(m(5))),
                m(1));
    }

    private static Standard openStandard() {
        TheMahjongMeld.Pon pon = new TheMahjongMeld.Pon(List.of(m(3), m(3), m(3)), 0, 1, 0);
        return new Standard(List.of(pon),
                List.of(seq(m(1), m(2), m(3)), seq(m(4), m(5), m(6)), triplet(m(7))),
                m(9));
    }

    private static WinContext tsumo() { return WinContext.tsumo(false, false, RiichiState.NONE, false, m(1), EAST, EAST, false, false); }
    private static WinContext ron()   { return WinContext.ron(false, false, RiichiState.NONE, false, m(1), EAST, EAST, false, false); }

    // -------------------------------------------------------------------------
    // RIICHI
    // -------------------------------------------------------------------------

    @Test
    void riichi_riichiState_returnsTrue() {
        assertTrue(checkRiichi(WinContext.ron(false, false, RiichiState.RIICHI, false, m(1), EAST, EAST, false, false)));
    }

    @Test
    void riichi_noneState_returnsFalse() {
        assertFalse(checkRiichi(WinContext.ron(false, false, RiichiState.NONE, false, m(1), EAST, EAST, false, false)));
    }

    @Test
    void riichi_doubleRiichiState_returnsFalse() {
        // DOUBLE_RIICHI is its own yaku; plain RIICHI must not fire
        assertFalse(checkRiichi(WinContext.ron(false, false, RiichiState.DOUBLE_RIICHI, false, m(1), EAST, EAST, false, false)));
    }

    @Test
    void riichi_tsumoWin_returnsTrue() {
        assertTrue(checkRiichi(WinContext.tsumo(false, false, RiichiState.RIICHI, false, m(1), EAST, EAST, false, false)));
    }

    // -------------------------------------------------------------------------
    // DOUBLE_RIICHI
    // -------------------------------------------------------------------------

    @Test
    void doubleRiichi_doubleRiichiState_returnsTrue() {
        assertTrue(checkDoubleRiichi(WinContext.ron(false, false, RiichiState.DOUBLE_RIICHI, false, m(1), EAST, EAST, false, false)));
    }

    @Test
    void doubleRiichi_noneState_returnsFalse() {
        assertFalse(checkDoubleRiichi(WinContext.ron(false, false, RiichiState.NONE, false, m(1), EAST, EAST, false, false)));
    }

    @Test
    void doubleRiichi_riichiState_returnsFalse() {
        // Plain RIICHI must not trigger DOUBLE_RIICHI
        assertFalse(checkDoubleRiichi(WinContext.ron(false, false, RiichiState.RIICHI, false, m(1), EAST, EAST, false, false)));
    }

    @Test
    void doubleRiichi_tsumoWin_returnsTrue() {
        assertTrue(checkDoubleRiichi(WinContext.tsumo(false, false, RiichiState.DOUBLE_RIICHI, false, m(1), EAST, EAST, false, false)));
    }

    // -------------------------------------------------------------------------
    // IPPATSU
    // -------------------------------------------------------------------------

    @Test
    void ippatsu_eligible_returnsTrue() {
        assertTrue(checkIppatsu(WinContext.ron(false, false, RiichiState.RIICHI, true, m(1), EAST, EAST, false, false)));
    }

    @Test
    void ippatsu_notEligible_returnsFalse() {
        assertFalse(checkIppatsu(WinContext.ron(false, false, RiichiState.RIICHI, false, m(1), EAST, EAST, false, false)));
    }

    @Test
    void ippatsu_tsumoWin_returnsTrue() {
        assertTrue(checkIppatsu(WinContext.tsumo(false, false, RiichiState.RIICHI, true, m(1), EAST, EAST, false, false)));
    }

    @Test
    void ippatsu_eligibleWithDoubleRiichi_returnsTrue() {
        assertTrue(checkIppatsu(WinContext.ron(false, false, RiichiState.DOUBLE_RIICHI, true, m(1), EAST, EAST, false, false)));
    }

    // -------------------------------------------------------------------------
    // MENZEN_TSUMO
    // -------------------------------------------------------------------------

    @Test
    void menzenTsumo_tsumoClosedStandard_returnsTrue() {
        assertTrue(checkMenzenTsumo(closedStandard(), tsumo()));
    }

    @Test
    void menzenTsumo_tsumoOpenStandard_returnsFalse() {
        assertFalse(checkMenzenTsumo(openStandard(), tsumo()));
    }

    @Test
    void menzenTsumo_ronClosedStandard_returnsFalse() {
        assertFalse(checkMenzenTsumo(closedStandard(), ron()));
    }

    @Test
    void menzenTsumo_tsumoChitoitsu_returnsTrue() {
        Chitoitsu c = new Chitoitsu(List.of(m(1), m(2), m(3), m(4), m(5), m(6), m(7)));
        assertTrue(checkMenzenTsumo(c, tsumo()));
    }

    @Test
    void menzenTsumo_tsumoKokushimusou_returnsTrue() {
        assertTrue(checkMenzenTsumo(new Kokushimusou(m(1)), tsumo()));
    }

    @Test
    void menzenTsumo_ronChitoitsu_returnsFalse() {
        Chitoitsu c = new Chitoitsu(List.of(m(1), m(2), m(3), m(4), m(5), m(6), m(7)));
        assertFalse(checkMenzenTsumo(c, ron()));
    }

    // -------------------------------------------------------------------------
    // PINFU
    // -------------------------------------------------------------------------

    // seatWind=EAST, roundWind=EAST; pair=WEST (not yakuhai)
    // sequences: M2-M3-M4, M5-M6-M7, P1-P2-P3, S3-S4-S5; win=M4 (rightmost of M2-M3-M4, left.rank=2>1 → ryanmen)
    private static WinContext pinfuCtx(TheMahjongTile winTile, TheMahjongTile.Wind seat, TheMahjongTile.Wind round) {
        return WinContext.ron(false, false, RiichiState.NONE, false, winTile, seat, round, false, false);
    }

    private static Standard pinfuHand(TheMahjongTile pair) {
        return new Standard(List.of(),
                List.of(seq(m(2), m(3), m(4)), seq(m(5), m(6), m(7)),
                        seq(p(1), p(2), p(3)), seq(new TheMahjongTile(Suit.SOUZU, 3, false),
                                new TheMahjongTile(Suit.SOUZU, 4, false),
                                new TheMahjongTile(Suit.SOUZU, 5, false))),
                pair);
    }

    @Test
    void pinfu_allSeqNonYakuhaiPairRyanmen_returnsTrue() {
        assertTrue(checkPinfu(pinfuHand(wind(WEST)), pinfuCtx(m(4), EAST, EAST)));
    }

    @Test
    void pinfu_hasTriplet_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(2), m(3), m(4)), seq(m(5), m(6), m(7)),
                        seq(p(1), p(2), p(3)), triplet(m(9))),
                wind(WEST));
        assertFalse(checkPinfu(s, pinfuCtx(m(4), EAST, EAST)));
    }

    @Test
    void pinfu_openHand_returnsFalse() {
        TheMahjongMeld.Pon pon = new TheMahjongMeld.Pon(List.of(m(9), m(9), m(9)), 0, 1, 0);
        Standard s = new Standard(List.of(pon),
                List.of(seq(m(2), m(3), m(4)), seq(m(5), m(6), m(7)), seq(p(1), p(2), p(3))),
                wind(WEST));
        assertFalse(checkPinfu(s, pinfuCtx(m(4), EAST, EAST)));
    }

    @Test
    void pinfu_dragonPair_returnsFalse() {
        TheMahjongTile haku = new TheMahjongTile(Suit.DRAGON, TheMahjongTile.Dragon.HAKU.tileRank(), false);
        assertFalse(checkPinfu(pinfuHand(haku), pinfuCtx(m(4), EAST, EAST)));
    }

    @Test
    void pinfu_seatWindPair_returnsFalse() {
        assertFalse(checkPinfu(pinfuHand(wind(EAST)), pinfuCtx(m(4), EAST, SOUTH)));
    }

    @Test
    void pinfu_roundWindPair_returnsFalse() {
        assertFalse(checkPinfu(pinfuHand(wind(SOUTH)), pinfuCtx(m(4), EAST, SOUTH)));
    }

    @Test
    void pinfu_nonSeatNonRoundWindPair_returnsTrue() {
        // WEST is neither seat (EAST) nor round (SOUTH)
        assertTrue(checkPinfu(pinfuHand(wind(WEST)), pinfuCtx(m(4), EAST, SOUTH)));
    }

    @Test
    void pinfu_kanchanWait_returnsFalse() {
        // win tile M3 is the middle of M2-M3-M4 → kanchan
        assertFalse(checkPinfu(pinfuHand(wind(WEST)), pinfuCtx(m(3), EAST, EAST)));
    }

    @Test
    void pinfu_penchanLeft_returnsFalse() {
        // seq M7-M8-M9, win=M7 (leftmost), right.rank=9==maxRank → penchan
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(4), m(5), m(6)),
                        seq(p(1), p(2), p(3)), seq(m(7), m(8), m(9))),
                wind(WEST));
        assertFalse(checkPinfu(s, pinfuCtx(m(7), EAST, EAST)));
    }

    @Test
    void pinfu_penchanRight_returnsFalse() {
        // seq M1-M2-M3, win=M3 (rightmost), left.rank=1 not > 1 → penchan
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(4), m(5), m(6)),
                        seq(p(2), p(3), p(4)), seq(p(5), p(6), p(7))),
                wind(WEST));
        assertFalse(checkPinfu(s, pinfuCtx(m(3), EAST, EAST)));
    }

    @Test
    void pinfu_chitoitsu_returnsFalse() {
        assertFalse(checkPinfu(new Chitoitsu(List.of(m(1), m(2), m(3), m(4), m(5), m(6), m(7))),
                pinfuCtx(m(1), EAST, EAST)));
    }

    // -------------------------------------------------------------------------
    // IIPEIKO
    // -------------------------------------------------------------------------

    @Test
    void iipeiko_twoIdenticalSequences_returnsTrue() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(1), m(2), m(3)),
                        seq(m(4), m(5), m(6)), triplet(m(9))),
                m(7));
        assertTrue(checkIipeiko(s));
    }

    @Test
    void iipeiko_noDuplicate_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(4), m(5), m(6)),
                        seq(m(7), m(8), m(9)), triplet(m(1))),
                m(5));
        assertFalse(checkIipeiko(s));
    }

    @Test
    void iipeiko_sameRankDifferentSuit_returnsFalse() {
        TheMahjongTile p1 = new TheMahjongTile(Suit.PINZU, 1, false);
        TheMahjongTile p2 = new TheMahjongTile(Suit.PINZU, 2, false);
        TheMahjongTile p3 = new TheMahjongTile(Suit.PINZU, 3, false);
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(p1, p2, p3),
                        seq(m(4), m(5), m(6)), triplet(m(9))),
                m(7));
        assertFalse(checkIipeiko(s));
    }

    @Test
    void iipeiko_openHand_returnsFalse() {
        TheMahjongMeld.Pon pon = new TheMahjongMeld.Pon(List.of(m(9), m(9), m(9)), 0, 1, 0);
        Standard s = new Standard(List.of(pon),
                List.of(seq(m(1), m(2), m(3)), seq(m(1), m(2), m(3)), triplet(m(5))),
                m(7));
        assertFalse(checkIipeiko(s));
    }

    @Test
    void iipeiko_chitoitsu_returnsFalse() {
        assertFalse(checkIipeiko(new Chitoitsu(List.of(m(1), m(2), m(3), m(4), m(5), m(6), m(7)))));
    }

    @Test
    void iipeiko_ryanpeikouHand_returnsTrue() {
        // Two pairs of identical sequences — iipeiko fires (subsumed by ryanpeikou in aggregate)
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(1), m(2), m(3)),
                        seq(m(4), m(5), m(6)), seq(m(4), m(5), m(6))),
                m(9));
        assertTrue(checkIipeiko(s));
    }

    // -------------------------------------------------------------------------
    // TANYAO
    // -------------------------------------------------------------------------

    @Test
    void tanyao_allSimplesClosed_returnsTrue() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(2), m(3), m(4)), seq(m(5), m(6), m(7)),
                        seq(p(2), p(3), p(4)), triplet(m(5))),
                m(3));
        assertTrue(checkTanyao(s));
    }

    @Test
    void tanyao_allSimplesOpen_returnsTrue() {
        TheMahjongMeld.Pon pon = new TheMahjongMeld.Pon(List.of(m(3), m(3), m(3)), 0, 1, 0);
        Standard s = new Standard(List.of(pon),
                List.of(seq(m(5), m(6), m(7)), seq(p(2), p(3), p(4)), triplet(m(6))),
                m(4));
        assertTrue(checkTanyao(s));
    }

    @Test
    void tanyao_hasTerminal_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(4), m(5), m(6)),
                        seq(p(2), p(3), p(4)), triplet(m(5))),
                m(3));
        assertFalse(checkTanyao(s));
    }

    @Test
    void tanyao_hasHonor_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(2), m(3), m(4)), seq(m(5), m(6), m(7)),
                        seq(p(2), p(3), p(4)), triplet(m(5))),
                wind(EAST));
        assertFalse(checkTanyao(s));
    }

    @Test
    void tanyao_chitoitsuAllSimples_returnsTrue() {
        Chitoitsu c = new Chitoitsu(List.of(m(2), m(3), m(4), m(5), m(6), m(7), p(2)));
        assertTrue(checkTanyao(c));
    }

    @Test
    void tanyao_chitoitsuHasHonor_returnsFalse() {
        Chitoitsu c = new Chitoitsu(List.of(m(2), m(3), m(4), m(5), m(6), m(7), wind(EAST)));
        assertFalse(checkTanyao(c));
    }

    @Test
    void tanyao_kokushimusou_returnsFalse() {
        assertFalse(checkTanyao(new Kokushimusou(m(1))));
    }

    // -------------------------------------------------------------------------
    // HAKU / HATSU / CHUN
    // -------------------------------------------------------------------------

    private static Standard withTriplet(TheMahjongTile tile) {
        return new Standard(List.of(),
                List.of(triplet(tile), seq(m(2), m(3), m(4)), seq(m(5), m(6), m(7)), seq(p(2), p(3), p(4))),
                m(5));
    }

    private static Standard withOpenPon(TheMahjongTile tile) {
        TheMahjongMeld.Pon pon = new TheMahjongMeld.Pon(List.of(tile, tile, tile), 0, 1, 0);
        return new Standard(List.of(pon),
                List.of(seq(m(2), m(3), m(4)), seq(m(5), m(6), m(7)), seq(p(2), p(3), p(4))),
                m(5));
    }

    @Test void haku_closedTriplet_returnsTrue()  { assertTrue(checkHaku(withTriplet(dragon(TheMahjongTile.Dragon.HAKU)))); }
    @Test void haku_openPon_returnsTrue()         { assertTrue(checkHaku(withOpenPon(dragon(TheMahjongTile.Dragon.HAKU)))); }
    @Test void haku_wrongDragon_returnsFalse()    { assertFalse(checkHaku(withTriplet(dragon(TheMahjongTile.Dragon.HATSU)))); }
    @Test void haku_chitoitsu_returnsFalse()      { assertFalse(checkHaku(new Chitoitsu(List.of(dragon(TheMahjongTile.Dragon.HAKU), m(2), m(3), m(4), m(5), m(6), m(7))))); }

    @Test void hatsu_closedTriplet_returnsTrue()  { assertTrue(checkHatsu(withTriplet(dragon(TheMahjongTile.Dragon.HATSU)))); }
    @Test void hatsu_openPon_returnsTrue()         { assertTrue(checkHatsu(withOpenPon(dragon(TheMahjongTile.Dragon.HATSU)))); }
    @Test void hatsu_wrongDragon_returnsFalse()    { assertFalse(checkHatsu(withTriplet(dragon(TheMahjongTile.Dragon.HAKU)))); }

    @Test void chun_closedTriplet_returnsTrue()   { assertTrue(checkChun(withTriplet(dragon(TheMahjongTile.Dragon.CHUN)))); }
    @Test void chun_openPon_returnsTrue()          { assertTrue(checkChun(withOpenPon(dragon(TheMahjongTile.Dragon.CHUN)))); }
    @Test void chun_wrongDragon_returnsFalse()     { assertFalse(checkChun(withTriplet(dragon(TheMahjongTile.Dragon.HATSU)))); }

    // -------------------------------------------------------------------------
    // SEAT_WIND / ROUND_WIND
    // -------------------------------------------------------------------------

    private static WinContext windCtx(TheMahjongTile.Wind seat, TheMahjongTile.Wind round) {
        return WinContext.ron(false, false, RiichiState.NONE, false, m(1), seat, round, false, false);
    }

    @Test
    void seatWind_matchesSeatTriplet_returnsTrue() {
        assertTrue(checkSeatWind(withTriplet(wind(SOUTH)), windCtx(SOUTH, EAST)));
    }

    @Test
    void seatWind_roundWindTriplet_returnsFalse() {
        assertFalse(checkSeatWind(withTriplet(wind(EAST)), windCtx(SOUTH, EAST)));
    }

    @Test
    void seatWind_openPon_returnsTrue() {
        assertTrue(checkSeatWind(withOpenPon(wind(WEST)), windCtx(WEST, EAST)));
    }

    @Test
    void roundWind_matchesRoundTriplet_returnsTrue() {
        assertTrue(checkRoundWind(withTriplet(wind(EAST)), windCtx(SOUTH, EAST)));
    }

    @Test
    void roundWind_seatWindTriplet_returnsFalse() {
        assertFalse(checkRoundWind(withTriplet(wind(SOUTH)), windCtx(SOUTH, EAST)));
    }

    @Test
    void seatAndRound_doubleWind_bothReturnTrue() {
        // EAST triplet when seat=EAST, round=EAST → both yaku fire
        WinContext ctx = windCtx(EAST, EAST);
        Standard s = withTriplet(wind(EAST));
        assertTrue(checkSeatWind(s, ctx));
        assertTrue(checkRoundWind(s, ctx));
    }

    // -------------------------------------------------------------------------
    // HAITEI / HOUTEI
    // -------------------------------------------------------------------------

    private static WinContext lastTileTsumo() {
        return WinContext.tsumo(false, false, RiichiState.NONE, false, m(1), EAST, EAST, true, false);
    }

    private static WinContext lastTileRon() {
        return WinContext.ron(false, false, RiichiState.NONE, false, m(1), EAST, EAST, true, false);
    }

    @Test void haitei_tsumoLastTile_returnsTrue()    { assertTrue(checkHaitei(lastTileTsumo())); }
    @Test void haitei_ronLastTile_returnsFalse()     { assertFalse(checkHaitei(lastTileRon())); }
    @Test void haitei_tsumoNotLastTile_returnsFalse(){ assertFalse(checkHaitei(tsumo())); }

    @Test void houtei_ronLastTile_returnsTrue()          { assertTrue(checkHoutei(lastTileRon())); }
    @Test void houtei_tsumoLastTile_returnsFalse()       { assertFalse(checkHoutei(lastTileTsumo())); }
    @Test void houtei_ronNotLastTile_returnsFalse()      { assertFalse(checkHoutei(ron())); }
    @Test void houtei_chankanLastTile_returnsFalse() {
        // Chankan is not a ron — houtei must not fire
        WinContext ctx = WinContext.chankan(false, false, RiichiState.NONE, false, m(1), EAST, EAST, true);
        assertFalse(checkHoutei(ctx));
    }

    // -------------------------------------------------------------------------
    // CHANKAN
    // -------------------------------------------------------------------------

    @Test void chankan_chankanWin_returnsTrue() {
        WinContext ctx = WinContext.chankan(false, false, RiichiState.NONE, false, m(1), EAST, EAST, false);
        assertTrue(checkChankan(ctx));
    }

    @Test void chankan_ronWin_returnsFalse()   { assertFalse(checkChankan(ron())); }
    @Test void chankan_tsumoWin_returnsFalse() { assertFalse(checkChankan(tsumo())); }

    // -------------------------------------------------------------------------
    // RINSHAN_KAIHOU
    // -------------------------------------------------------------------------

    private static WinContext rinshanCtx() {
        return WinContext.tsumo(false, false, RiichiState.NONE, false, m(1), EAST, EAST, false, true);
    }

    @Test void rinshanKaihou_rinshanTsumo_returnsTrue()    { assertTrue(checkRinshanKaihou(rinshanCtx())); }
    @Test void rinshanKaihou_normalTsumo_returnsFalse()    { assertFalse(checkRinshanKaihou(tsumo())); }
    @Test void rinshanKaihou_rinshanFlagRon_returnsFalse() {
        // rinshanDraw=true but tsumo=false — impossible in practice, checker must still reject
        WinContext ctx = WinContext.ron(false, false, RiichiState.NONE, false, m(1), EAST, EAST, false, true);
        assertFalse(checkRinshanKaihou(ctx));
    }

    // -------------------------------------------------------------------------
    // Subsumption filtering in aggregate check()
    // -------------------------------------------------------------------------

    @Test
    void check_ryanpeikou_removesIipeiko() {
        // Stub both checkers by injecting a hand shape that would cause both to fire.
        // Since checkers are still stubs, we test the filter directly via a hand-built list.
        // Real coverage will follow once checkIipeiko / checkRyanpeikou are implemented.
        List<NonYakuman> raw = new ArrayList<>(List.of(IIPEIKO, RYANPEIKOU));
        NonYakuman.removeSubsumed(raw);
        assertFalse(raw.contains(IIPEIKO));
        assertTrue(raw.contains(RYANPEIKOU));
    }

    @Test
    void check_junchan_removesChanta() {
        List<NonYakuman> raw = new ArrayList<>(List.of(CHANTA, JUNCHAN));
        NonYakuman.removeSubsumed(raw);
        assertFalse(raw.contains(CHANTA));
        assertTrue(raw.contains(JUNCHAN));
    }

    @Test
    void check_chinitsu_removesHonitsu() {
        List<NonYakuman> raw = new ArrayList<>(List.of(HONITSU, CHINITSU));
        NonYakuman.removeSubsumed(raw);
        assertFalse(raw.contains(HONITSU));
        assertTrue(raw.contains(CHINITSU));
    }

    // -------------------------------------------------------------------------
    // Mutual exclusion via aggregate check()
    // -------------------------------------------------------------------------

    @Test
    void check_riichiState_containsOnlyRiichi() {
        List<NonYakuman> result = NonYakuman.check(null, WinContext.ron(false, false, RiichiState.RIICHI, false, m(1), EAST, EAST, false, false));
        assertTrue(result.contains(RIICHI));
        assertFalse(result.contains(DOUBLE_RIICHI));
    }

    @Test
    void check_doubleRiichiState_containsOnlyDoubleRiichi() {
        List<NonYakuman> result = NonYakuman.check(null, WinContext.ron(false, false, RiichiState.DOUBLE_RIICHI, false, m(1), EAST, EAST, false, false));
        assertFalse(result.contains(RIICHI));
        assertTrue(result.contains(DOUBLE_RIICHI));
    }

    @Test
    void check_noneState_containsNeither() {
        List<NonYakuman> result = NonYakuman.check(null, WinContext.ron(false, false, RiichiState.NONE, false, m(1), EAST, EAST, false, false));
        assertFalse(result.contains(RIICHI));
        assertFalse(result.contains(DOUBLE_RIICHI));
    }

    // -------------------------------------------------------------------------
    // CHIITOI
    // -------------------------------------------------------------------------

    @Test void chiitoi_chitoitsuShape_returnsTrue() {
        assertTrue(checkChiitoi(new Chitoitsu(List.of(m(1), m(3), m(5), m(7), p(2), p(4), p(6)))));
    }

    @Test void chiitoi_standardShape_returnsFalse() { assertFalse(checkChiitoi(closedStandard())); }
    @Test void chiitoi_kokushimusou_returnsFalse()  { assertFalse(checkChiitoi(new Kokushimusou(m(1)))); }

    // -------------------------------------------------------------------------
    // TOITOI
    // -------------------------------------------------------------------------

    @Test
    void toitoi_allClosedTriplets_returnsTrue() {
        Standard s = new Standard(List.of(),
                List.of(triplet(m(2)), triplet(m(4)), triplet(m(6)), triplet(m(8))), m(9));
        assertTrue(checkToitoi(s));
    }

    @Test
    void toitoi_mixedOpenAndClosedTriplets_returnsTrue() {
        TheMahjongMeld.Pon pon = new TheMahjongMeld.Pon(List.of(m(3), m(3), m(3)), 0, 1, 0);
        Standard s = new Standard(List.of(pon),
                List.of(triplet(m(5)), triplet(m(7)), triplet(m(9))), m(1));
        assertTrue(checkToitoi(s));
    }

    @Test
    void toitoi_hasSequence_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(triplet(m(2)), triplet(m(4)), triplet(m(6)), seq(m(7), m(8), m(9))), m(1));
        assertFalse(checkToitoi(s));
    }

    @Test
    void toitoi_openChi_returnsFalse() {
        TheMahjongMeld.Chi chi = new TheMahjongMeld.Chi(List.of(m(1), m(2), m(3)), 0, 1, 0);
        Standard s = new Standard(List.of(chi),
                List.of(triplet(m(5)), triplet(m(7)), triplet(m(9))), m(4));
        assertFalse(checkToitoi(s));
    }

    @Test void toitoi_chitoitsu_returnsFalse() {
        assertFalse(checkToitoi(new Chitoitsu(List.of(m(1), m(2), m(3), m(4), m(5), m(6), m(7)))));
    }

    // -------------------------------------------------------------------------
    // HONROUTOU
    // -------------------------------------------------------------------------

    @Test
    void honroutou_allTerminalAndHonorTriplets_returnsTrue() {
        Standard s = new Standard(List.of(),
                List.of(triplet(m(1)), triplet(m(9)), triplet(wind(EAST)), triplet(dragon(TheMahjongTile.Dragon.HAKU))),
                p(1));
        assertTrue(checkHonroutou(s));
    }

    @Test
    void honroutou_hasSimple_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(triplet(m(1)), triplet(m(9)), triplet(m(5)), triplet(dragon(TheMahjongTile.Dragon.HAKU))),
                p(1));
        assertFalse(checkHonroutou(s));
    }

    @Test
    void honroutou_chitoitsuAllTerminalHonor_returnsTrue() {
        Chitoitsu c = new Chitoitsu(List.of(m(1), m(9), p(1), p(9), s(1), wind(EAST), dragon(TheMahjongTile.Dragon.HAKU)));
        assertTrue(checkHonroutou(c));
    }

    @Test
    void honroutou_chitoitsuHasSimple_returnsFalse() {
        Chitoitsu c = new Chitoitsu(List.of(m(1), m(9), p(1), p(9), s(1), wind(EAST), m(5)));
        assertFalse(checkHonroutou(c));
    }

    // -------------------------------------------------------------------------
    // CHANTA
    // -------------------------------------------------------------------------

    @Test
    void chanta_allGroupsHaveTerminalOrHonorWithSequence_returnsTrue() {
        // M1-M2-M3, M7-M8-M9, P1-P2-P3, S7-S8-S9, pair=East
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(7), m(8), m(9)),
                        seq(p(1), p(2), p(3)), seq(s(7), s(8), s(9))),
                wind(EAST));
        assertTrue(checkChanta(s));
    }

    @Test
    void chanta_groupWithoutTerminalOrHonor_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(4), m(5), m(6)),
                        seq(p(1), p(2), p(3)), seq(s(7), s(8), s(9))),
                wind(EAST));
        assertFalse(checkChanta(s));
    }

    @Test
    void chanta_pairIsSimple_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(7), m(8), m(9)),
                        seq(p(1), p(2), p(3)), seq(s(7), s(8), s(9))),
                m(5));
        assertFalse(checkChanta(s));
    }

    @Test
    void chanta_allTriplets_returnsFalse() {
        // All terminal/honor triplets — that's honroutou, not chanta (no sequences)
        Standard s = new Standard(List.of(),
                List.of(triplet(m(1)), triplet(m(9)), triplet(p(1)), triplet(s(9))),
                wind(EAST));
        assertFalse(checkChanta(s));
    }

    @Test
    void chanta_chitoitsu_returnsFalse() {
        assertFalse(checkChanta(new Chitoitsu(List.of(m(1), m(9), p(1), p(9), s(1), s(9), wind(EAST)))));
    }

    // -------------------------------------------------------------------------
    // JUNCHAN
    // -------------------------------------------------------------------------

    @Test
    void junchan_allGroupsHaveTerminalWithSequence_returnsTrue() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(7), m(8), m(9)),
                        seq(p(1), p(2), p(3)), seq(s(7), s(8), s(9))),
                m(9));
        assertTrue(checkJunchan(s));
    }

    @Test
    void junchan_honorInGroup_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(7), m(8), m(9)),
                        seq(p(1), p(2), p(3)), triplet(wind(EAST))),
                m(9));
        assertFalse(checkJunchan(s));
    }

    @Test
    void junchan_honorPair_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(7), m(8), m(9)),
                        seq(p(1), p(2), p(3)), seq(s(7), s(8), s(9))),
                wind(EAST));
        assertFalse(checkJunchan(s));
    }

    @Test
    void junchan_allTriplets_returnsFalse() {
        // honroutou hand — no sequences
        Standard s = new Standard(List.of(),
                List.of(triplet(m(1)), triplet(m(9)), triplet(p(1)), triplet(s(9))),
                s(1));
        assertFalse(checkJunchan(s));
    }

    @Test
    void junchan_chitoitsu_returnsFalse() {
        assertFalse(checkJunchan(new Chitoitsu(List.of(m(1), m(9), p(1), p(9), s(1), s(9), m(9)))));
    }

    // -------------------------------------------------------------------------
    // SANSHOKU_DOUJUN
    // -------------------------------------------------------------------------

    @Test
    void sanshokuDoujun_sameSequenceAllThreeSuits_returnsTrue() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(2), m(3), m(4)), seq(p(2), p(3), p(4)),
                        seq(s(2), s(3), s(4)), triplet(m(7))),
                m(1));
        assertTrue(checkSanshokuDoujun(s));
    }

    @Test
    void sanshokuDoujun_missingOneSuit_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(2), m(3), m(4)), seq(p(2), p(3), p(4)),
                        seq(m(5), m(6), m(7)), triplet(m(9))),
                m(1));
        assertFalse(checkSanshokuDoujun(s));
    }

    @Test
    void sanshokuDoujun_openChi_returnsTrue() {
        TheMahjongMeld.Chi chi = new TheMahjongMeld.Chi(List.of(m(1), m(2), m(3)), 0, 1, 0);
        Standard s = new Standard(List.of(chi),
                List.of(seq(p(1), p(2), p(3)), seq(s(1), s(2), s(3)), triplet(m(5))),
                m(7));
        assertTrue(checkSanshokuDoujun(s));
    }

    // -------------------------------------------------------------------------
    // ITTSU
    // -------------------------------------------------------------------------

    @Test
    void ittsu_fullStraightManzu_returnsTrue() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(4), m(5), m(6)),
                        seq(m(7), m(8), m(9)), triplet(p(5))),
                p(1));
        assertTrue(checkIttsu(s));
    }

    @Test
    void ittsu_fullStraightPinzu_returnsTrue() {
        Standard s = new Standard(List.of(),
                List.of(seq(p(1), p(2), p(3)), seq(p(4), p(5), p(6)),
                        seq(p(7), p(8), p(9)), triplet(m(5))),
                m(1));
        assertTrue(checkIttsu(s));
    }

    @Test
    void ittsu_missesOneSegment_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(4), m(5), m(6)),
                        seq(p(7), p(8), p(9)), triplet(m(9))),
                p(1));
        assertFalse(checkIttsu(s));
    }

    @Test
    void ittsu_openChi_returnsTrue() {
        TheMahjongMeld.Chi chi = new TheMahjongMeld.Chi(List.of(m(1), m(2), m(3)), 0, 1, 0);
        Standard s = new Standard(List.of(chi),
                List.of(seq(m(4), m(5), m(6)), seq(m(7), m(8), m(9)), triplet(p(5))),
                p(1));
        assertTrue(checkIttsu(s));
    }

    // -------------------------------------------------------------------------
    // SANSHOKU_DOUKOU
    // -------------------------------------------------------------------------

    @Test
    void sanshokuDoukou_sameTripletAllThreeSuits_returnsTrue() {
        Standard s = new Standard(List.of(),
                List.of(triplet(m(5)), triplet(p(5)), triplet(s(5)), seq(m(1), m(2), m(3))),
                m(7));
        assertTrue(checkSanshokuDoukou(s));
    }

    @Test
    void sanshokuDoukou_missingOneSuit_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(triplet(m(5)), triplet(p(5)), seq(m(1), m(2), m(3)), seq(m(4), m(5), m(6))),
                m(7));
        assertFalse(checkSanshokuDoukou(s));
    }

    @Test
    void sanshokuDoukou_differentRanks_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(triplet(m(5)), triplet(p(6)), triplet(s(5)), seq(m(1), m(2), m(3))),
                m(7));
        assertFalse(checkSanshokuDoukou(s));
    }

    // -------------------------------------------------------------------------
    // SANANKOU
    // -------------------------------------------------------------------------

    private static WinContext tsumoWin(TheMahjongTile winTile) {
        return WinContext.tsumo(false, false, RiichiState.NONE, false, winTile, EAST, EAST, false, false);
    }

    private static WinContext ronWin(TheMahjongTile winTile) {
        return WinContext.ron(false, false, RiichiState.NONE, false, winTile, EAST, EAST, false, false);
    }

    @Test
    void sanankou_threeClosedTripletsTsumo_returnsTrue() {
        Standard s = new Standard(List.of(),
                List.of(triplet(m(2)), triplet(m(4)), triplet(m(6)), seq(m(7), m(8), m(9))),
                m(1));
        assertTrue(checkSanankou(s, tsumoWin(m(1))));
    }

    @Test
    void sanankou_threeClosedTripletsRonPairWait_returnsTrue() {
        // winning tile = pair tile → triplets pre-formed
        Standard s = new Standard(List.of(),
                List.of(triplet(m(2)), triplet(m(4)), triplet(m(6)), seq(m(7), m(8), m(9))),
                m(1));
        assertTrue(checkSanankou(s, ronWin(m(1))));
    }

    @Test
    void sanankou_threeClosedTripletsRonTripletWait_returnsFalse() {
        // winning tile matches a triplet tile → that triplet was completed by ron → open
        Standard s = new Standard(List.of(),
                List.of(triplet(m(2)), triplet(m(4)), triplet(m(6)), seq(m(7), m(8), m(9))),
                m(5));
        assertFalse(checkSanankou(s, ronWin(m(2))));
    }

    @Test
    void sanankou_twoTripletsOnlyInsufficient_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(triplet(m(2)), triplet(m(4)), seq(m(5), m(6), m(7)), seq(m(1), m(2), m(3))),
                m(9));
        assertFalse(checkSanankou(s, tsumoWin(m(9))));
    }

    @Test
    void sanankou_twoTripletsAndAnkan_returnsTrue() {
        TheMahjongMeld.Ankan ankan = new TheMahjongMeld.Ankan(List.of(m(8), m(8), m(8), m(8)));
        Standard s = new Standard(List.of(ankan),
                List.of(triplet(m(2)), triplet(m(4)), seq(m(5), m(6), m(7))),
                m(9));
        assertTrue(checkSanankou(s, tsumoWin(m(9))));
    }

    @Test void sanankou_chitoitsu_returnsFalse() {
        assertFalse(checkSanankou(
                new Chitoitsu(List.of(m(1), m(2), m(3), m(4), m(5), m(6), m(7))),
                tsumoWin(m(1))));
    }

    // -------------------------------------------------------------------------
    // SANKANTSU
    // -------------------------------------------------------------------------

    @Test
    void sankantsu_threeAnkans_returnsTrue() {
        TheMahjongMeld.Ankan a1 = new TheMahjongMeld.Ankan(List.of(m(1), m(1), m(1), m(1)));
        TheMahjongMeld.Ankan a2 = new TheMahjongMeld.Ankan(List.of(m(2), m(2), m(2), m(2)));
        TheMahjongMeld.Ankan a3 = new TheMahjongMeld.Ankan(List.of(m(3), m(3), m(3), m(3)));
        Standard s = new Standard(List.of(a1, a2, a3), List.of(seq(m(5), m(6), m(7))), m(9));
        assertTrue(checkSankantsu(s));
    }

    @Test
    void sankantsu_ankanDaiminkanKakan_returnsTrue() {
        TheMahjongMeld.Ankan ankan = new TheMahjongMeld.Ankan(List.of(m(1), m(1), m(1), m(1)));
        TheMahjongMeld.Daiminkan dmk = new TheMahjongMeld.Daiminkan(List.of(m(2), m(2), m(2), m(2)), 0, 1, 0);
        TheMahjongMeld.Pon pon = new TheMahjongMeld.Pon(List.of(m(3), m(3), m(3)), 0, 1, 0);
        TheMahjongMeld.Kakan kakan = new TheMahjongMeld.Kakan(pon, m(3));
        Standard s = new Standard(List.of(ankan, dmk, kakan), List.of(seq(m(5), m(6), m(7))), m(9));
        assertTrue(checkSankantsu(s));
    }

    @Test
    void sankantsu_twoKansOnly_returnsFalse() {
        TheMahjongMeld.Ankan a1 = new TheMahjongMeld.Ankan(List.of(m(1), m(1), m(1), m(1)));
        TheMahjongMeld.Ankan a2 = new TheMahjongMeld.Ankan(List.of(m(2), m(2), m(2), m(2)));
        TheMahjongMeld.Pon pon = new TheMahjongMeld.Pon(List.of(m(3), m(3), m(3)), 0, 1, 0);
        Standard s = new Standard(List.of(a1, a2, pon), List.of(seq(m(5), m(6), m(7))), m(9));
        assertFalse(checkSankantsu(s));
    }

    // -------------------------------------------------------------------------
    // SHOUSANGEN
    // -------------------------------------------------------------------------

    @Test
    void shousangen_twoDragonTripletsAndDragonPair_returnsTrue() {
        Standard s = new Standard(List.of(),
                List.of(triplet(dragon(TheMahjongTile.Dragon.HAKU)), triplet(dragon(TheMahjongTile.Dragon.HATSU)),
                        seq(m(2), m(3), m(4)), seq(m(5), m(6), m(7))),
                dragon(TheMahjongTile.Dragon.CHUN));
        assertTrue(checkShousangen(s));
    }

    @Test
    void shousangen_threeDragonTriplets_returnsFalse() {
        // DAISANGEN territory — shousangen requires exactly 2 triplets + dragon pair
        Standard s = new Standard(List.of(),
                List.of(triplet(dragon(TheMahjongTile.Dragon.HAKU)), triplet(dragon(TheMahjongTile.Dragon.HATSU)), triplet(dragon(TheMahjongTile.Dragon.CHUN)),
                        seq(m(2), m(3), m(4))),
                m(5));
        assertFalse(checkShousangen(s));
    }

    @Test
    void shousangen_oneDragonTripletDragonPair_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(triplet(dragon(TheMahjongTile.Dragon.HAKU)), seq(m(2), m(3), m(4)),
                        seq(m(5), m(6), m(7)), seq(p(2), p(3), p(4))),
                dragon(TheMahjongTile.Dragon.CHUN));
        assertFalse(checkShousangen(s));
    }

    @Test
    void shousangen_twoDragonTripletsNonDragonPair_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(triplet(dragon(TheMahjongTile.Dragon.HAKU)), triplet(dragon(TheMahjongTile.Dragon.HATSU)),
                        seq(m(2), m(3), m(4)), seq(m(5), m(6), m(7))),
                m(1));
        assertFalse(checkShousangen(s));
    }

    // -------------------------------------------------------------------------
    // RYANPEIKOU
    // -------------------------------------------------------------------------

    @Test
    void ryanpeikou_twoPairsOfIdenticalSequences_returnsTrue() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(1), m(2), m(3)),
                        seq(m(4), m(5), m(6)), seq(m(4), m(5), m(6))),
                m(9));
        assertTrue(checkRyanpeikou(s));
    }

    @Test
    void ryanpeikou_onlyOneMatchingPair_returnsFalse() {
        // IIPEIKO hand — only one pair of identical sequences
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(1), m(2), m(3)),
                        seq(m(4), m(5), m(6)), seq(m(7), m(8), m(9))),
                m(9));
        assertFalse(checkRyanpeikou(s));
    }

    @Test
    void ryanpeikou_openHand_returnsFalse() {
        TheMahjongMeld.Chi chi = new TheMahjongMeld.Chi(List.of(m(1), m(2), m(3)), 0, 1, 0);
        Standard s = new Standard(List.of(chi),
                List.of(seq(m(1), m(2), m(3)), seq(m(4), m(5), m(6)), seq(m(4), m(5), m(6))),
                m(9));
        assertFalse(checkRyanpeikou(s));
    }

    @Test
    void ryanpeikou_hasTriplet_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(1), m(2), m(3)),
                        seq(m(4), m(5), m(6)), triplet(m(9))),
                m(7));
        assertFalse(checkRyanpeikou(s));
    }

    // -------------------------------------------------------------------------
    // HONITSU
    // -------------------------------------------------------------------------

    @Test
    void honitsu_allManzuPlusHonors_returnsTrue() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(4), m(5), m(6)),
                        triplet(dragon(TheMahjongTile.Dragon.HAKU)), triplet(wind(EAST))),
                m(9));
        assertTrue(checkHonitsu(s));
    }

    @Test
    void honitsu_twoNumberSuits_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(p(1), p(2), p(3)),
                        triplet(dragon(TheMahjongTile.Dragon.HAKU)), seq(m(4), m(5), m(6))),
                m(9));
        assertFalse(checkHonitsu(s));
    }

    @Test
    void honitsu_allHonors_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(triplet(dragon(TheMahjongTile.Dragon.HAKU)), triplet(dragon(TheMahjongTile.Dragon.HATSU)),
                        triplet(dragon(TheMahjongTile.Dragon.CHUN)), triplet(wind(EAST))),
                wind(SOUTH));
        assertFalse(checkHonitsu(s));
    }

    @Test
    void honitsu_chitoitsuOneSuitPlusHonors_returnsTrue() {
        Chitoitsu c = new Chitoitsu(List.of(m(1), m(3), m(5), m(7), m(9), dragon(TheMahjongTile.Dragon.HAKU), wind(EAST)));
        assertTrue(checkHonitsu(c));
    }

    @Test
    void honitsu_chitoitsuTwoSuits_returnsFalse() {
        Chitoitsu c = new Chitoitsu(List.of(m(1), m(3), p(5), m(7), m(9), dragon(TheMahjongTile.Dragon.HAKU), wind(EAST)));
        assertFalse(checkHonitsu(c));
    }

    // -------------------------------------------------------------------------
    // CHINITSU
    // -------------------------------------------------------------------------

    @Test
    void chinitsu_allManzu_returnsTrue() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(4), m(5), m(6)),
                        seq(m(7), m(8), m(9)), triplet(m(5))),
                m(2));
        assertTrue(checkChinitsu(s));
    }

    @Test
    void chinitsu_oneManzuAndOneHonor_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(4), m(5), m(6)),
                        seq(m(7), m(8), m(9)), triplet(dragon(TheMahjongTile.Dragon.HAKU))),
                m(2));
        assertFalse(checkChinitsu(s));
    }

    @Test
    void chinitsu_twoNumberSuits_returnsFalse() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(p(4), p(5), p(6)),
                        seq(m(7), m(8), m(9)), triplet(m(5))),
                m(2));
        assertFalse(checkChinitsu(s));
    }

    @Test
    void chinitsu_chitoitsuOneSuit_returnsTrue() {
        Chitoitsu c = new Chitoitsu(List.of(m(1), m(2), m(3), m(4), m(5), m(6), m(7)));
        assertTrue(checkChinitsu(c));
    }

    @Test
    void chinitsu_chitoitsuHasHonor_returnsFalse() {
        Chitoitsu c = new Chitoitsu(List.of(m(1), m(2), m(3), m(4), m(5), m(6), wind(EAST)));
        assertFalse(checkChinitsu(c));
    }

    // -------------------------------------------------------------------------
    // Subsumption via aggregate: RYANPEIKOU removes IIPEIKO
    // -------------------------------------------------------------------------

    @Test
    void check_ryanpeikouHand_iipeikoCleaned() {
        Standard s = new Standard(List.of(),
                List.of(seq(m(1), m(2), m(3)), seq(m(1), m(2), m(3)),
                        seq(m(4), m(5), m(6)), seq(m(4), m(5), m(6))),
                m(9));
        List<NonYakuman> result = NonYakuman.check(s, tsumo());
        assertTrue(result.contains(RYANPEIKOU));
        assertFalse(result.contains(IIPEIKO));
    }
}
