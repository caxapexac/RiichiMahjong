package com.riichimahjong.themahjongcompat;

import com.themahjong.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MatchNbtTest {

    // -------------------------------------------------------------------------
    // Match-level round trips
    // -------------------------------------------------------------------------

    @Test
    void notStartedMatchRoundTrips() {
        TheMahjongMatch match = TheMahjongMatch.defaults()
                .withPlayerCount(3)
                .withStartingPoints(30_000)
                .withTargetPoints(40_000)
                .withRoundCount(1);

        TheMahjongMatch result = roundTrip(match);

        assertEquals(3, result.playerCount());
        assertEquals(30_000, result.startingPoints());
        assertEquals(40_000, result.targetPoints());
        assertEquals(1, result.roundCount());
        assertEquals(TheMahjongMatch.State.NOT_STARTED, result.state());
        assertTrue(result.currentRound().isEmpty());
        assertTrue(result.completedRounds().isEmpty());
    }

    @Test
    void startedMatchRoundTrips() {
        TheMahjongMatch match = TheMahjongMatch.defaults().validate().startRound(new Random(42));

        TheMahjongMatch result = roundTrip(match);

        assertEquals(TheMahjongMatch.State.IN_ROUND, result.state());
        assertTrue(result.currentRound().isPresent());
        assertEquals(4, result.currentRound().orElseThrow().players().size());
    }

    @Test
    void versionMismatchThrows() {
        CompoundTag tag = MatchNbt.writeMatch(TheMahjongMatch.defaults());
        tag.putInt("v", 99);

        assertThrows(IllegalArgumentException.class, () -> MatchNbt.readMatch(tag));
    }

    // -------------------------------------------------------------------------
    // Round fields
    // -------------------------------------------------------------------------

    @Test
    void roundFieldsRoundTrip() {
        TheMahjongMatch match = TheMahjongMatch.defaults().validate().startRound(new Random(7));
        TheMahjongRound original = match.currentRound().orElseThrow();

        TheMahjongRound result = roundTrip(match).currentRound().orElseThrow();

        assertEquals(original.roundWind(), result.roundWind());
        assertEquals(original.handNumber(), result.handNumber());
        assertEquals(original.honba(), result.honba());
        assertEquals(original.riichiSticks(), result.riichiSticks());
        assertEquals(original.dealerSeat(), result.dealerSeat());
        assertEquals(original.state(), result.state());
        assertEquals(original.currentTurnSeat(), result.currentTurnSeat());
        assertEquals(original.claimSourceSeat(), result.claimSourceSeat());
        assertEquals(original.revealedDoraCount(), result.revealedDoraCount());
        assertEquals(original.liveWall(), result.liveWall());
        assertEquals(original.rinshanTiles(), result.rinshanTiles());
        assertEquals(original.doraIndicators(), result.doraIndicators());
        assertEquals(original.uraDoraIndicators(), result.uraDoraIndicators());
    }

    // -------------------------------------------------------------------------
    // ActiveTile variants
    // -------------------------------------------------------------------------

    @Test
    void activeTileNoneRoundTrips() {
        TheMahjongRound.ActiveTile tile = TheMahjongRound.ActiveTile.none();
        assertInstanceOf(TheMahjongRound.ActiveTile.None.class, activeTileRoundTrip(tile));
    }

    @Test
    void activeTileDrawnRoundTrips() {
        TheMahjongTile t = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 5, true);
        TheMahjongRound.ActiveTile result = activeTileRoundTrip(TheMahjongRound.ActiveTile.drawn(t));

        assertInstanceOf(TheMahjongRound.ActiveTile.Drawn.class, result);
        assertEquals(t, ((TheMahjongRound.ActiveTile.Drawn) result).tile());
    }

    @Test
    void activeTileHeldDiscardRoundTrips() {
        TheMahjongTile t = new TheMahjongTile(TheMahjongTile.Suit.WIND, 2, false);
        TheMahjongRound.ActiveTile result = activeTileRoundTrip(TheMahjongRound.ActiveTile.heldDiscard(t));

        assertInstanceOf(TheMahjongRound.ActiveTile.HeldDiscard.class, result);
        assertEquals(t, ((TheMahjongRound.ActiveTile.HeldDiscard) result).tile());
    }

    // -------------------------------------------------------------------------
    // Tile serialization
    // -------------------------------------------------------------------------

    @Test
    void tileRoundTripsWithRedDora() {
        TheMahjongTile tile = new TheMahjongTile(TheMahjongTile.Suit.PINZU, 5, true);
        TheMahjongMatch match = matchWithTileInHand(0, tile);
        TheMahjongTile result = roundTrip(match).currentRound().orElseThrow().players().get(0).currentHand().get(0);
        assertEquals(tile, result);
    }

    @Test
    void honorTileRoundTrips() {
        TheMahjongTile tile = new TheMahjongTile(TheMahjongTile.Suit.DRAGON, 3, false);
        TheMahjongMatch match = matchWithTileInHand(0, tile);
        TheMahjongTile result = roundTrip(match).currentRound().orElseThrow().players().get(0).currentHand().get(0);
        assertEquals(tile, result);
    }

    // -------------------------------------------------------------------------
    // Meld variants
    // -------------------------------------------------------------------------

    @Test
    void chiMeldRoundTrips() {
        TheMahjongMeld.Chi chi = new TheMahjongMeld.Chi(
                List.of(
                        new TheMahjongTile(TheMahjongTile.Suit.MANZU, 3, false),
                        new TheMahjongTile(TheMahjongTile.Suit.MANZU, 4, false),
                        new TheMahjongTile(TheMahjongTile.Suit.MANZU, 5, false)),
                2, 3, 0);

        TheMahjongMeld result = meldRoundTrip(chi);

        assertInstanceOf(TheMahjongMeld.Chi.class, result);
        TheMahjongMeld.Chi rChi = (TheMahjongMeld.Chi) result;
        assertEquals(chi.tiles(), rChi.tiles());
        assertEquals(chi.claimedTileIndex(), rChi.claimedTileIndex());
        assertEquals(chi.sourceSeat(), rChi.sourceSeat());
        assertEquals(chi.sourceDiscardIndex(), rChi.sourceDiscardIndex());
    }

    @Test
    void ponMeldRoundTrips() {
        TheMahjongTile t = new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 7, false);
        TheMahjongMeld.Pon pon = new TheMahjongMeld.Pon(List.of(t, t, t), 1, 2, 5);

        TheMahjongMeld result = meldRoundTrip(pon);

        assertInstanceOf(TheMahjongMeld.Pon.class, result);
        TheMahjongMeld.Pon rPon = (TheMahjongMeld.Pon) result;
        assertEquals(pon.tiles(), rPon.tiles());
        assertEquals(pon.claimedTileIndex(), rPon.claimedTileIndex());
        assertEquals(pon.sourceSeat(), rPon.sourceSeat());
        assertEquals(pon.sourceDiscardIndex(), rPon.sourceDiscardIndex());
    }

    @Test
    void daiminkanMeldRoundTrips() {
        TheMahjongTile t = new TheMahjongTile(TheMahjongTile.Suit.PINZU, 9, false);
        TheMahjongMeld.Daiminkan kan = new TheMahjongMeld.Daiminkan(List.of(t, t, t, t), 0, 1, 3);

        TheMahjongMeld result = meldRoundTrip(kan);

        assertInstanceOf(TheMahjongMeld.Daiminkan.class, result);
        assertEquals(kan.tiles(), ((TheMahjongMeld.Daiminkan) result).tiles());
    }

    @Test
    void ankanMeldRoundTrips() {
        TheMahjongTile t = new TheMahjongTile(TheMahjongTile.Suit.DRAGON, 1, false);
        TheMahjongMeld.Ankan ankan = new TheMahjongMeld.Ankan(List.of(t, t, t, t));

        TheMahjongMeld result = meldRoundTrip(ankan);

        assertInstanceOf(TheMahjongMeld.Ankan.class, result);
        assertEquals(ankan.tiles(), ((TheMahjongMeld.Ankan) result).tiles());
    }

    @Test
    void kakanMeldRoundTrips() {
        TheMahjongTile t = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);
        TheMahjongMeld.Pon pon = new TheMahjongMeld.Pon(List.of(t, t, t), 0, 2, 1);
        TheMahjongMeld.Kakan kakan = new TheMahjongMeld.Kakan(pon, t);

        TheMahjongMeld result = meldRoundTrip(kakan);

        assertInstanceOf(TheMahjongMeld.Kakan.class, result);
        TheMahjongMeld.Kakan rKakan = (TheMahjongMeld.Kakan) result;
        assertEquals(kakan.tiles(), rKakan.tiles());
        assertEquals(kakan.addedTile(), rKakan.addedTile());
        assertEquals(pon.sourceSeat(), rKakan.upgradedFrom().sourceSeat());
    }

    // -------------------------------------------------------------------------
    // Discard
    // -------------------------------------------------------------------------

    @Test
    void discardWithRiichiRoundTrips() {
        TheMahjongDiscard discard = new TheMahjongDiscard(
                new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 1, false), true);

        TheMahjongMatch match = matchWithDiscardForSeat(0, discard);
        TheMahjongDiscard result = roundTrip(match).currentRound().orElseThrow().players().get(0).discards().get(0);

        assertEquals(discard.tile(), result.tile());
        assertTrue(result.riichiDeclared());
    }

    // -------------------------------------------------------------------------
    // Player flags
    // -------------------------------------------------------------------------

    @Test
    void playerFlagsRoundTrip() {
        TheMahjongMatch base = TheMahjongMatch.defaults().validate().startRound(new Random(1));
        TheMahjongRound round = base.currentRound().orElseThrow();
        TheMahjongPlayer original = round.players().get(0);
        TheMahjongPlayer modified = new TheMahjongPlayer(
                original.points() + 1000,
                TheMahjongPlayer.RiichiState.DOUBLE_RIICHI, true,
                original.currentHand(), original.melds(),
                original.discards(), original.temporaryFuritenTiles());

        List<TheMahjongPlayer> players = new java.util.ArrayList<>(round.players());
        players.set(0, modified);
        TheMahjongRound modifiedRound = new TheMahjongRound(
                round.roundWind(), round.handNumber(), round.honba(), round.riichiSticks(),
                round.dealerSeat(), round.state(), round.currentTurnSeat(),
                round.claimSourceSeat().orElse(-1), round.activeTile(),
                round.liveWall(), round.rinshanTiles(), round.doraIndicators(),
                round.uraDoraIndicators(), round.revealedDoraCount(), players, List.of());
        TheMahjongMatch match = new TheMahjongMatch(base.playerCount(), base.startingPoints(), base.targetPoints(), base.roundCount(), base.state(), base.tileSet(), base.ruleSet(), base.completedRounds(),modifiedRound);

        TheMahjongPlayer result = roundTrip(match).currentRound().orElseThrow().players().get(0);

        assertTrue(result.riichi());
        assertTrue(result.doubleRiichi());
        assertTrue(result.ippatsuEligible());
        assertEquals(original.points() + 1000, result.points());
    }

    // -------------------------------------------------------------------------
    // TileSet round trip
    // -------------------------------------------------------------------------

    @Test
    void customTileSetRoundTrips() {
        TheMahjongTileSet tileSet = TheMahjongTileSet.standardRiichi(true);
        TheMahjongMatch match = TheMahjongMatch.defaults().withTileSet(tileSet);

        TheMahjongTileSet result = roundTrip(match).tileSet();

        assertEquals(tileSet.tiles(), result.tiles());
        assertEquals(tileSet.copiesPerTile(), result.copiesPerTile());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static TheMahjongMatch roundTrip(TheMahjongMatch match) {
        CompoundTag tag = MatchNbt.writeMatch(match);
        return MatchNbt.readMatch(tag);
    }

    private static TheMahjongRound.ActiveTile activeTileRoundTrip(TheMahjongRound.ActiveTile activeTile) {
        TheMahjongMatch base = TheMahjongMatch.defaults().validate().startRound(new Random(1));
        TheMahjongRound r = base.currentRound().orElseThrow();
        TheMahjongRound modified = new TheMahjongRound(
                r.roundWind(), r.handNumber(), r.honba(), r.riichiSticks(),
                r.dealerSeat(), r.state(), r.currentTurnSeat(),
                r.claimSourceSeat().orElse(-1), activeTile,
                r.liveWall(), r.rinshanTiles(), r.doraIndicators(),
                r.uraDoraIndicators(), r.revealedDoraCount(), r.players(), List.of());
        return roundTrip(new TheMahjongMatch(base.playerCount(), base.startingPoints(), base.targetPoints(), base.roundCount(), base.state(), base.tileSet(), base.ruleSet(), base.completedRounds(),modified)).currentRound().orElseThrow().activeTile();
    }

    private static TheMahjongMeld meldRoundTrip(TheMahjongMeld meld) {
        TheMahjongMatch base = TheMahjongMatch.defaults().validate().startRound(new Random(1));
        TheMahjongRound round = base.currentRound().orElseThrow();
        TheMahjongPlayer p = round.players().get(0);
        TheMahjongPlayer withMeld = new TheMahjongPlayer(
                p.points(), p.riichiState(), p.ippatsuEligible(),
                p.currentHand(), List.of(meld), p.discards(), p.temporaryFuritenTiles());
        List<TheMahjongPlayer> players = new java.util.ArrayList<>(round.players());
        players.set(0, withMeld);
        TheMahjongRound modifiedRound = new TheMahjongRound(
                round.roundWind(), round.handNumber(), round.honba(), round.riichiSticks(),
                round.dealerSeat(), round.state(), round.currentTurnSeat(),
                round.claimSourceSeat().orElse(-1), round.activeTile(),
                round.liveWall(), round.rinshanTiles(), round.doraIndicators(),
                round.uraDoraIndicators(), round.revealedDoraCount(), players, List.of());
        return roundTrip(new TheMahjongMatch(base.playerCount(), base.startingPoints(), base.targetPoints(), base.roundCount(), base.state(), base.tileSet(), base.ruleSet(), base.completedRounds(),modifiedRound))
                .currentRound().orElseThrow().players().get(0).melds().get(0);
    }

    private static TheMahjongMatch matchWithTileInHand(int seat, TheMahjongTile tile) {
        TheMahjongMatch base = TheMahjongMatch.defaults().validate().startRound(new Random(1));
        TheMahjongRound round = base.currentRound().orElseThrow();
        TheMahjongPlayer p = round.players().get(seat);
        TheMahjongPlayer modified = new TheMahjongPlayer(
                p.points(), p.riichiState(), p.ippatsuEligible(),
                List.of(tile), p.melds(), p.discards(), p.temporaryFuritenTiles());
        List<TheMahjongPlayer> players = new java.util.ArrayList<>(round.players());
        players.set(seat, modified);
        TheMahjongRound modifiedRound = new TheMahjongRound(
                round.roundWind(), round.handNumber(), round.honba(), round.riichiSticks(),
                round.dealerSeat(), round.state(), round.currentTurnSeat(),
                round.claimSourceSeat().orElse(-1), round.activeTile(),
                round.liveWall(), round.rinshanTiles(), round.doraIndicators(),
                round.uraDoraIndicators(), round.revealedDoraCount(), players, List.of());
        return new TheMahjongMatch(base.playerCount(), base.startingPoints(), base.targetPoints(), base.roundCount(), base.state(), base.tileSet(), base.ruleSet(), base.completedRounds(),modifiedRound);
    }

    private static TheMahjongMatch matchWithDiscardForSeat(int seat, TheMahjongDiscard discard) {
        TheMahjongMatch base = TheMahjongMatch.defaults().validate().startRound(new Random(1));
        TheMahjongRound round = base.currentRound().orElseThrow();
        TheMahjongPlayer p = round.players().get(seat);
        TheMahjongPlayer modified = new TheMahjongPlayer(
                p.points(), p.riichiState(), p.ippatsuEligible(),
                p.currentHand(), p.melds(), List.of(discard), p.temporaryFuritenTiles());
        List<TheMahjongPlayer> players = new java.util.ArrayList<>(round.players());
        players.set(seat, modified);
        TheMahjongRound modifiedRound = new TheMahjongRound(
                round.roundWind(), round.handNumber(), round.honba(), round.riichiSticks(),
                round.dealerSeat(), round.state(), round.currentTurnSeat(),
                round.claimSourceSeat().orElse(-1), round.activeTile(),
                round.liveWall(), round.rinshanTiles(), round.doraIndicators(),
                round.uraDoraIndicators(), round.revealedDoraCount(), players, List.of());
        return new TheMahjongMatch(base.playerCount(), base.startingPoints(), base.targetPoints(), base.roundCount(), base.state(), base.tileSet(), base.ruleSet(), base.completedRounds(),modifiedRound);
    }
}
