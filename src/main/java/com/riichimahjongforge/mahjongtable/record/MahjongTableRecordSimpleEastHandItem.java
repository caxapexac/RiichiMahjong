package com.riichimahjongforge.mahjongtable.record;

import com.riichimahjongforge.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjongforge.mahjongtable.MahjongTableBlockEntity.StartMatchResult;
import com.riichimahjongforge.mahjongtable.RuleSetPreset;
import com.themahjong.TheMahjongDiscard;
import com.themahjong.TheMahjongFixedDeal;
import com.themahjong.TheMahjongMatch;
import com.themahjong.TheMahjongPlayer;
import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongRuleSet;
import com.themahjong.TheMahjongTile;
import com.themahjong.TheMahjongTileSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Base for predefined-situation table records: drops the table directly into a
 * 4-player WRC match with the dealer (East / seat 0) holding 13 hand tiles plus
 * a drawn tile, and a custom top-of-wall + dora indicator. Built on
 * {@link com.themahjong.TheMahjongFixedDeal} — no random tile placement for the
 * subset the subclass declares; remaining slots filled deterministically.
 *
 * <p>Subclasses declare the 14-tile dealer hand ({@link #handTiles()} — last entry
 * is the drawn tile), the top-of-wall sequence ({@link #wallTiles()} — first draws
 * for non-dealer seats and any subsequent dealer draws), and optionally the dora
 * indicator, rinshan order, and a hardcoded second-seat hand for testing claim flows.
 */
public abstract class MahjongTableRecordSimpleEastHandItem extends MahjongTableRecordItem {

    protected MahjongTableRecordSimpleEastHandItem(Properties properties) {
        super(properties);
    }

    /** 14 tiles: indices 0..12 = concealed hand, index 13 = drawn tile. */
    protected abstract List<TheMahjongTile> handTiles();

    /** Top-of-wall sequence in draw order. Non-dealer seats draw first; first
     *  entry goes to seat 1, second to seat 2, etc. May be empty. */
    protected abstract List<TheMahjongTile> wallTiles();

    /** Override to pin the round's dora indicator. Default null = random fill. */
    protected TheMahjongTile doraIndicator() {
        return null;
    }

    /** Override to pin rinshan-pile tiles (kan-replacement draws). Default empty = random. */
    protected List<TheMahjongTile> rinshanTiles() {
        return List.of();
    }

    /** Override to pin a hand for the second-from-East seat (the seat that draws first
     *  after the dealer's discard). Useful for forcing claim opportunities. */
    protected List<TheMahjongTile> secondHandTiles() {
        return List.of();
    }

    /**
     * Whether this fixture should fake a few prior turns so a tsumo on the
     * current draw is NOT mis-detected as Tenhou yakuman. Defaults to
     * {@code true}; the Tenhou-specific fixture overrides to {@code false}.
     */
    protected boolean breakFirstRoundUninterrupted() {
        return true;
    }

    /** Hand number for the produced round. Default 1 (East-1). Override e.g. 4 if
     *  the situation must reflect "fourth hand of East". */
    protected int startingHandNumber() {
        return 1;
    }

    /** Round wind. Default EAST. */
    protected TheMahjongTile.Wind roundWind() {
        return TheMahjongTile.Wind.EAST;
    }

    /** Honba count. Default 0. */
    protected int honba() {
        return 0;
    }

    /** Rule set used by the produced match. Default WRC (4-player, no aka). */
    protected TheMahjongRuleSet ruleSet() {
        return TheMahjongRuleSet.wrc();
    }

    /** Tile set used by the produced match. Default standard riichi without red fives. */
    protected TheMahjongTileSet tileSet() {
        return TheMahjongTileSet.standardRiichi(false);
    }

    /** Preset whose canonical seat layout the table should adopt. Default 4-player WRC. */
    protected RuleSetPreset preset() {
        return RuleSetPreset.WRC_4P;
    }

    @Override
    protected boolean isPredefinedFixture() {
        return true;
    }

    // ---- tile factories (concise authoring for subclasses) ----------------

    protected static TheMahjongTile m(int rank) {
        return new TheMahjongTile(TheMahjongTile.Suit.MANZU, rank, false);
    }

    protected static TheMahjongTile p(int rank) {
        return new TheMahjongTile(TheMahjongTile.Suit.PINZU, rank, false);
    }

    protected static TheMahjongTile s(int rank) {
        return new TheMahjongTile(TheMahjongTile.Suit.SOUZU, rank, false);
    }

    protected static TheMahjongTile w(TheMahjongTile.Wind wind) {
        return new TheMahjongTile(TheMahjongTile.Suit.WIND, wind.tileRank(), false);
    }

    protected static TheMahjongTile d(TheMahjongTile.Dragon dragon) {
        return new TheMahjongTile(TheMahjongTile.Suit.DRAGON, dragon.tileRank(), false);
    }

    @Override
    public boolean applyToTable(ServerPlayer player, MahjongTableBlockEntity table, ItemStack stack) {
        TheMahjongMatch match;
        try {
            match = buildMatch();
        } catch (RuntimeException ex) {
            player.displayClientMessage(
                    Component.literal("Predefined record failed to build: " + ex.getMessage()), true);
            return false;
        }
        StartMatchResult result = table.tryApplyPredefined(
                /* seed */ 0xC6E4D31L, preset(), match, player.getUUID());
        if (result == StartMatchResult.NOT_IDLE) {
            player.displayClientMessage(
                    Component.literal("Cannot load record: end the current match first."), true);
            return false;
        }
        return true;
    }

    @Override
    public boolean recordFromTable(ServerPlayer player, MahjongTableBlockEntity table, ItemStack stack) {
        // Predefined records are write-once at registration. Don't let RMB clobber the
        // baked situation with a snapshot of whatever's currently on the table.
        player.displayClientMessage(
                Component.literal("Predefined records cannot be re-recorded."), true);
        return false;
    }

    private TheMahjongMatch buildMatch() {
        List<TheMahjongTile> hand = handTiles();
        if (hand.size() != 14) {
            throw new IllegalStateException(
                    "handTiles() must return exactly 14 tiles (13 concealed + 1 drawn), got " + hand.size());
        }
        List<TheMahjongTile> concealed = List.copyOf(hand.subList(0, 13));
        TheMahjongTile drawn = hand.get(13);

        // topOfWall layout: [drawn tile for the dealer's first turn,
        //                    then user-supplied wallTiles in order].
        // FixedDeal.drawForDealer(true) draws the FIRST topOfWall entry into the
        // dealer's hand, advancing state to TURN. Subsequent entries are the next
        // tiles drawn by other seats / the dealer's later draws.
        List<TheMahjongTile> wallProvided = wallTiles();
        List<TheMahjongTile> top = new ArrayList<>(1 + wallProvided.size());
        top.add(drawn);
        top.addAll(wallProvided);

        TheMahjongFixedDeal.Builder b = TheMahjongFixedDeal.builder()
                .playerCount(preset().playerCount())
                .ruleSet(ruleSet())
                .tileSet(tileSet())
                .roundWind(roundWind())
                .handNumber(startingHandNumber())
                .honba(honba())
                .handForSeat(0, concealed)
                .topOfWall(top)
                .drawForDealer(true);

        List<TheMahjongTile> second = secondHandTiles();
        if (!second.isEmpty()) {
            if (second.size() != 13) {
                throw new IllegalStateException(
                        "secondHandTiles(), if set, must be 13 tiles; got " + second.size());
            }
            b.handForSeat(preset().playerCount() - 1, second);
        }

        TheMahjongTile dora = doraIndicator();
        if (dora != null) {
            b.doraIndicators(List.of(dora));
        }

        List<TheMahjongTile> rinshan = rinshanTiles();
        if (!rinshan.isEmpty()) {
            b.rinshanTiles(rinshan);
        }

        TheMahjongMatch match = b.buildMatch();
        // Push the round past its uninterrupted-first-round state so a tsumo
        // on the current draw doesn't accidentally land as Tenhou. Take 3
        // tiles off the front of the live wall and put them in seat 0's
        // discard river — looks like the dealer has had a few prior turns,
        // and the engine's uninterrupted-first-round check sees discards
        // present so Tenhou is suppressed. Tenhou fixture opts out.
        if (breakFirstRoundUninterrupted()) {
            match = withFakedPriorTurns(match, /* count */ 3);
        }
        return match;
    }

    /** Take {@code count} tiles off the front of the live wall and append
     *  them to seat 0's discard river. Pure post-process; everything else
     *  in the round is unchanged. */
    private static TheMahjongMatch withFakedPriorTurns(TheMahjongMatch match, int count) {
        TheMahjongRound r = match.currentRound().orElse(null);
        if (r == null || count <= 0) return match;
        if (r.liveWall().size() < count) return match;
        List<TheMahjongTile> taken = r.liveWall().subList(0, count);
        List<TheMahjongTile> newLiveWall = new ArrayList<>(r.liveWall().subList(count, r.liveWall().size()));

        TheMahjongPlayer dealer = r.players().get(0);
        List<TheMahjongDiscard> newDiscards = new ArrayList<>(dealer.discards());
        for (TheMahjongTile t : taken) newDiscards.add(new TheMahjongDiscard(t, false));
        TheMahjongPlayer newDealer = new TheMahjongPlayer(
                dealer.points(), dealer.riichiState(), dealer.ippatsuEligible(),
                dealer.currentHand(), dealer.melds(), newDiscards,
                dealer.temporaryFuritenTiles(), dealer.riichiPermanentFuriten(),
                dealer.kitaCount());
        List<TheMahjongPlayer> newPlayers = new ArrayList<>(r.players());
        newPlayers.set(0, newDealer);

        TheMahjongRound newRound = new TheMahjongRound(
                r.roundWind(), r.handNumber(), r.honba(), r.riichiSticks(),
                r.dealerSeat(), r.state(),
                r.currentTurnSeat(), r.claimSourceSeat().orElse(-1),
                r.activeTile(),
                newLiveWall, r.rinshanTiles(),
                r.doraIndicators(), r.uraDoraIndicators(),
                r.revealedDoraCount(),
                newPlayers, r.pendingDeltas());
        return new TheMahjongMatch(
                match.playerCount(), match.startingPoints(), match.targetPoints(),
                match.roundCount(), match.state(), match.tileSet(), match.ruleSet(),
                match.completedRounds(), newRound);
    }
}
