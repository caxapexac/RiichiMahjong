package com.riichimahjong.themahjongcompat;

import com.themahjong.*;
import net.minecraft.nbt.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializes/deserializes a {@link TheMahjongMatch} (and all nested state) to/from NBT.
 * Version tag "v" must be present; currently only version 1 is supported.
 */
public final class MatchNbt {

    private static final int VERSION = 1;

    private MatchNbt() {}

    // -------------------------------------------------------------------------
    // Match
    // -------------------------------------------------------------------------

    public static CompoundTag writeMatch(TheMahjongMatch match) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", VERSION);
        tag.putInt("playerCount", match.playerCount());
        tag.putInt("startingPoints", match.startingPoints());
        tag.putInt("targetPoints", match.targetPoints());
        tag.putInt("roundCount", match.roundCount());
        tag.putString("state", match.state().name());
        tag.put("tileSet", writeTileSet(match.tileSet()));
        tag.putBoolean("kiriageMangan", match.ruleSet().kiriageMangan());
        tag.putBoolean("doubleWindPairFu4", match.ruleSet().doubleWindPairFu4());
        tag.putBoolean("renhouAllowed", match.ruleSet().renhouAllowed());
        tag.putBoolean("paoOnSuukantsu", match.ruleSet().paoOnSuukantsu());
        tag.putBoolean("abortiveDrawsAllowed", match.ruleSet().abortiveDrawsAllowed());
        tag.putBoolean("nagashiManganAllowed", match.ruleSet().nagashiManganAllowed());
        tag.putBoolean("doubleRonAllowed", match.ruleSet().doubleRonAllowed());
        tag.putBoolean("bustingEndsGame", match.ruleSet().bustingEndsGame());
        tag.putBoolean("riichiRequires1000Points", match.ruleSet().riichiRequires1000Points());
        tag.putBoolean("depositsToFirstAtEnd", match.ruleSet().depositsToFirstAtEnd());
        tag.putBoolean("agariYameAllowed", match.ruleSet().agariYameAllowed());
        tag.putBoolean("suddenDeathRound", match.ruleSet().suddenDeathRound());
        tag.putBoolean("openKanDoraDelayedReveal", match.ruleSet().openKanDoraDelayedReveal());
        tag.putBoolean("kuikaeForbidden", match.ruleSet().kuikaeForbidden());
        tag.putBoolean("strictRiichiAnkan", match.ruleSet().strictRiichiAnkan());
        tag.putBoolean("chiDisabled", match.ruleSet().chiDisabled());
        tag.putBoolean("kitaCountsAsDora", match.ruleSet().kitaCountsAsDora());
        tag.putBoolean("tsumoLoss", match.ruleSet().tsumoLoss());
        tag.putBoolean("kitaDeclineCausesFuriten", match.ruleSet().kitaDeclineCausesFuriten());
        tag.putBoolean("sanchahouAbortive", match.ruleSet().sanchahouAbortive());

        ListTag completedRounds = new ListTag();
        for (TheMahjongRound r : match.completedRounds()) {
            completedRounds.add(writeRound(r));
        }
        tag.put("completedRounds", completedRounds);

        match.currentRound().ifPresent(r -> tag.put("currentRound", writeRound(r)));
        return tag;
    }

    public static TheMahjongMatch readMatch(CompoundTag tag) {
        // version guard — extend here when format changes
        int v = tag.getInt("v");
        if (v != VERSION) {
            throw new IllegalArgumentException("Unsupported MatchNbt version: " + v);
        }

        int playerCount = tag.getInt("playerCount");
        int startingPoints = tag.getInt("startingPoints");
        int targetPoints = tag.getInt("targetPoints");
        int roundCount = tag.getInt("roundCount");
        TheMahjongMatch.State state = TheMahjongMatch.State.valueOf(tag.getString("state"));
        TheMahjongTileSet tileSet = readTileSet(tag.getCompound("tileSet"));
        TheMahjongRuleSet ruleSet = new TheMahjongRuleSet(
                tag.getBoolean("kiriageMangan"),
                tag.getBoolean("doubleWindPairFu4"),
                tag.getBoolean("renhouAllowed"),
                tag.getBoolean("paoOnSuukantsu"),
                tag.getBoolean("abortiveDrawsAllowed"),
                tag.getBoolean("nagashiManganAllowed"),
                tag.getBoolean("doubleRonAllowed"),
                tag.getBoolean("bustingEndsGame"),
                tag.getBoolean("riichiRequires1000Points"),
                tag.getBoolean("depositsToFirstAtEnd"),
                tag.getBoolean("agariYameAllowed"),
                tag.getBoolean("suddenDeathRound"),
                tag.getBoolean("openKanDoraDelayedReveal"),
                tag.getBoolean("kuikaeForbidden"),
                tag.getBoolean("strictRiichiAnkan"),
                tag.getBoolean("chiDisabled"),
                tag.getBoolean("kitaCountsAsDora"),
                tag.getBoolean("tsumoLoss"),
                tag.getBoolean("kitaDeclineCausesFuriten"),
                tag.getBoolean("sanchahouAbortive"));

        List<TheMahjongRound> completedRounds = new ArrayList<>();
        ListTag completedTag = tag.getList("completedRounds", Tag.TAG_COMPOUND);
        for (int i = 0; i < completedTag.size(); i++) {
            completedRounds.add(readRound(completedTag.getCompound(i)));
        }

        TheMahjongRound currentRound = tag.contains("currentRound", Tag.TAG_COMPOUND)
                ? readRound(tag.getCompound("currentRound"))
                : null;

        return new TheMahjongMatch(playerCount, startingPoints, targetPoints, roundCount, state, tileSet, ruleSet, completedRounds, currentRound);
    }

    // -------------------------------------------------------------------------
    // TileSet
    // -------------------------------------------------------------------------

    private static CompoundTag writeTileSet(TheMahjongTileSet tileSet) {
        CompoundTag tag = new CompoundTag();
        ListTag entries = new ListTag();
        for (TheMahjongTile tile : tileSet.tiles()) {
            CompoundTag entry = new CompoundTag();
            entry.put("tile", writeTile(tile));
            entry.putInt("copies", tileSet.copiesPerTile().get(tile));
            entries.add(entry);
        }
        tag.put("entries", entries);
        return tag;
    }

    private static TheMahjongTileSet readTileSet(CompoundTag tag) {
        ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
        List<TheMahjongTile> tiles = new ArrayList<>();
        Map<TheMahjongTile, Integer> copies = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            TheMahjongTile tile = readTile(entry.getCompound("tile"));
            tiles.add(tile);
            copies.put(tile, entry.getInt("copies"));
        }
        return new TheMahjongTileSet(tiles, copies);
    }

    // -------------------------------------------------------------------------
    // Round
    // -------------------------------------------------------------------------

    static CompoundTag writeRound(TheMahjongRound round) {
        CompoundTag tag = new CompoundTag();
        tag.putString("roundWind", round.roundWind().name());
        tag.putInt("handNumber", round.handNumber());
        tag.putInt("honba", round.honba());
        tag.putInt("riichiSticks", round.riichiSticks());
        tag.putInt("dealerSeat", round.dealerSeat());
        tag.putString("state", round.state().name());
        tag.putInt("currentTurnSeat", round.currentTurnSeat());
        tag.putInt("claimSourceSeat", round.claimSourceSeat().orElse(-1));
        tag.put("activeTile", writeActiveTile(round.activeTile()));
        tag.put("liveWall", writeTileList(round.liveWall()));
        tag.put("rinshanTiles", writeTileList(round.rinshanTiles()));
        tag.put("doraIndicators", writeTileList(round.doraIndicators()));
        tag.put("uraDoraIndicators", writeTileList(round.uraDoraIndicators()));
        tag.putInt("revealedDoraCount", round.revealedDoraCount());
        tag.putInt("pendingKanDoraReveals", round.pendingKanDoraReveals());

        ListTag players = new ListTag();
        for (TheMahjongPlayer p : round.players()) {
            players.add(writePlayer(p));
        }
        tag.put("players", players);

        ListTag pendingDeltas = new ListTag();
        for (int delta : round.pendingDeltas()) {
            pendingDeltas.add(IntTag.valueOf(delta));
        }
        tag.put("pendingDeltas", pendingDeltas);
        return tag;
    }

    static TheMahjongRound readRound(CompoundTag tag) {
        TheMahjongTile.Wind roundWind = TheMahjongTile.Wind.valueOf(tag.getString("roundWind"));
        int handNumber = tag.getInt("handNumber");
        int honba = tag.getInt("honba");
        int riichiSticks = tag.getInt("riichiSticks");
        int dealerSeat = tag.getInt("dealerSeat");
        TheMahjongRound.State state = TheMahjongRound.State.valueOf(tag.getString("state"));
        int currentTurnSeat = tag.getInt("currentTurnSeat");
        int claimSourceSeat = tag.getInt("claimSourceSeat");
        TheMahjongRound.ActiveTile activeTile = readActiveTile(tag.getCompound("activeTile"));
        List<TheMahjongTile> liveWall = readTileList(tag.getList("liveWall", Tag.TAG_COMPOUND));
        List<TheMahjongTile> rinshanTiles = readTileList(tag.getList("rinshanTiles", Tag.TAG_COMPOUND));
        List<TheMahjongTile> doraIndicators = readTileList(tag.getList("doraIndicators", Tag.TAG_COMPOUND));
        List<TheMahjongTile> uraDoraIndicators = readTileList(tag.getList("uraDoraIndicators", Tag.TAG_COMPOUND));
        int revealedDoraCount = tag.getInt("revealedDoraCount");
        int pendingKanDoraReveals = tag.getInt("pendingKanDoraReveals");

        ListTag playersTag = tag.getList("players", Tag.TAG_COMPOUND);
        List<TheMahjongPlayer> players = new ArrayList<>();
        for (int i = 0; i < playersTag.size(); i++) {
            players.add(readPlayer(playersTag.getCompound(i)));
        }

        List<Integer> pendingDeltas = new ArrayList<>();
        ListTag deltasTag = tag.getList("pendingDeltas", Tag.TAG_INT);
        for (int i = 0; i < deltasTag.size(); i++) {
            pendingDeltas.add(deltasTag.getInt(i));
        }

        return new TheMahjongRound(
                roundWind, handNumber, honba, riichiSticks, dealerSeat,
                state, currentTurnSeat, claimSourceSeat, activeTile,
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators,
                revealedDoraCount, pendingKanDoraReveals, players, pendingDeltas);
    }

    // -------------------------------------------------------------------------
    // ActiveTile
    // -------------------------------------------------------------------------

    private static CompoundTag writeActiveTile(TheMahjongRound.ActiveTile activeTile) {
        CompoundTag tag = new CompoundTag();
        if (activeTile instanceof TheMahjongRound.ActiveTile.None) {
            tag.putString("type", "NONE");
        } else if (activeTile instanceof TheMahjongRound.ActiveTile.Drawn drawn) {
            tag.putString("type", "DRAWN");
            tag.put("tile", writeTile(drawn.tile()));
        } else if (activeTile instanceof TheMahjongRound.ActiveTile.HeldDiscard held) {
            tag.putString("type", "HELD_DISCARD");
            tag.put("tile", writeTile(held.tile()));
        } else if (activeTile instanceof TheMahjongRound.ActiveTile.HeldKita held) {
            tag.putString("type", "HELD_KITA");
            tag.put("tile", writeTile(held.tile()));
        } else if (activeTile instanceof TheMahjongRound.ActiveTile.HeldKakan held) {
            tag.putString("type", "HELD_KAKAN");
            tag.put("tile", writeTile(held.tile()));
        }
        return tag;
    }

    private static TheMahjongRound.ActiveTile readActiveTile(CompoundTag tag) {
        return switch (tag.getString("type")) {
            case "NONE" -> TheMahjongRound.ActiveTile.none();
            case "DRAWN" -> TheMahjongRound.ActiveTile.drawn(readTile(tag.getCompound("tile")));
            case "HELD_DISCARD" -> TheMahjongRound.ActiveTile.heldDiscard(readTile(tag.getCompound("tile")));
            case "HELD_KITA" -> TheMahjongRound.ActiveTile.heldKita(readTile(tag.getCompound("tile")));
            case "HELD_KAKAN" -> TheMahjongRound.ActiveTile.heldKakan(readTile(tag.getCompound("tile")));
            default -> throw new IllegalArgumentException("Unknown ActiveTile type: " + tag.getString("type"));
        };
    }

    // -------------------------------------------------------------------------
    // Player
    // -------------------------------------------------------------------------

    private static CompoundTag writePlayer(TheMahjongPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("points", player.points());
        tag.putString("riichiState", player.riichiState().name());
        tag.putBoolean("ippatsuEligible", player.ippatsuEligible());
        tag.put("currentHand", writeTileList(player.currentHand()));
        tag.put("melds", writeMeldList(player.melds()));
        tag.put("discards", writeDiscardList(player.discards()));
        tag.put("temporaryFuritenTiles", writeTileList(player.temporaryFuritenTiles()));
        tag.putBoolean("riichiPermanentFuriten", player.riichiPermanentFuriten());
        tag.putInt("kitaCount", player.kitaCount());
        return tag;
    }

    private static TheMahjongPlayer readPlayer(CompoundTag tag) {
        return new TheMahjongPlayer(
                tag.getInt("points"),
                TheMahjongPlayer.RiichiState.valueOf(tag.getString("riichiState")),
                tag.getBoolean("ippatsuEligible"),
                readTileList(tag.getList("currentHand", Tag.TAG_COMPOUND)),
                readMeldList(tag.getList("melds", Tag.TAG_COMPOUND)),
                readDiscardList(tag.getList("discards", Tag.TAG_COMPOUND)),
                readTileList(tag.getList("temporaryFuritenTiles", Tag.TAG_COMPOUND)),
                tag.getBoolean("riichiPermanentFuriten"),
                tag.getInt("kitaCount"));
    }

    // -------------------------------------------------------------------------
    // Meld
    // -------------------------------------------------------------------------

    private static ListTag writeMeldList(List<TheMahjongMeld> melds) {
        ListTag list = new ListTag();
        for (TheMahjongMeld meld : melds) {
            list.add(writeMeld(meld));
        }
        return list;
    }

    private static List<TheMahjongMeld> readMeldList(ListTag list) {
        List<TheMahjongMeld> melds = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            melds.add(readMeld(list.getCompound(i)));
        }
        return melds;
    }

    private static CompoundTag writeMeld(TheMahjongMeld meld) {
        CompoundTag tag = new CompoundTag();
        if (meld instanceof TheMahjongMeld.Chi chi) {
            tag.putString("type", "CHI");
            tag.put("tiles", writeTileList(chi.tiles()));
            tag.putInt("claimedTileIndex", chi.claimedTileIndex());
            tag.putInt("sourceSeat", chi.sourceSeat());
            tag.putInt("sourceDiscardIndex", chi.sourceDiscardIndex());
        } else if (meld instanceof TheMahjongMeld.Pon pon) {
            tag.putString("type", "PON");
            tag.put("tiles", writeTileList(pon.tiles()));
            tag.putInt("claimedTileIndex", pon.claimedTileIndex());
            tag.putInt("sourceSeat", pon.sourceSeat());
            tag.putInt("sourceDiscardIndex", pon.sourceDiscardIndex());
        } else if (meld instanceof TheMahjongMeld.Daiminkan daiminkan) {
            tag.putString("type", "DAIMINKAN");
            tag.put("tiles", writeTileList(daiminkan.tiles()));
            tag.putInt("claimedTileIndex", daiminkan.claimedTileIndex());
            tag.putInt("sourceSeat", daiminkan.sourceSeat());
            tag.putInt("sourceDiscardIndex", daiminkan.sourceDiscardIndex());
        } else if (meld instanceof TheMahjongMeld.Kakan kakan) {
            tag.putString("type", "KAKAN");
            tag.put("upgradedFrom", writeMeld(kakan.upgradedFrom()));
            tag.put("addedTile", writeTile(kakan.addedTile()));
        } else if (meld instanceof TheMahjongMeld.Ankan ankan) {
            tag.putString("type", "ANKAN");
            tag.put("tiles", writeTileList(ankan.tiles()));
        }
        return tag;
    }

    private static TheMahjongMeld readMeld(CompoundTag tag) {
        return switch (tag.getString("type")) {
            case "CHI" -> new TheMahjongMeld.Chi(
                    readTileList(tag.getList("tiles", Tag.TAG_COMPOUND)),
                    tag.getInt("claimedTileIndex"),
                    tag.getInt("sourceSeat"),
                    tag.getInt("sourceDiscardIndex"));
            case "PON" -> new TheMahjongMeld.Pon(
                    readTileList(tag.getList("tiles", Tag.TAG_COMPOUND)),
                    tag.getInt("claimedTileIndex"),
                    tag.getInt("sourceSeat"),
                    tag.getInt("sourceDiscardIndex"));
            case "DAIMINKAN" -> new TheMahjongMeld.Daiminkan(
                    readTileList(tag.getList("tiles", Tag.TAG_COMPOUND)),
                    tag.getInt("claimedTileIndex"),
                    tag.getInt("sourceSeat"),
                    tag.getInt("sourceDiscardIndex"));
            case "KAKAN" -> {
                TheMahjongMeld base = readMeld(tag.getCompound("upgradedFrom"));
                if (!(base instanceof TheMahjongMeld.Pon pon))
                    throw new IllegalArgumentException("Kakan upgradedFrom must be a Pon, got: " + base.getClass().getSimpleName());
                yield new TheMahjongMeld.Kakan(pon, readTile(tag.getCompound("addedTile")));
            }
            case "ANKAN" -> new TheMahjongMeld.Ankan(
                    readTileList(tag.getList("tiles", Tag.TAG_COMPOUND)));
            default -> throw new IllegalArgumentException("Unknown meld type: " + tag.getString("type"));
        };
    }

    // -------------------------------------------------------------------------
    // Discard
    // -------------------------------------------------------------------------

    private static ListTag writeDiscardList(List<TheMahjongDiscard> discards) {
        ListTag list = new ListTag();
        for (TheMahjongDiscard d : discards) {
            CompoundTag entry = new CompoundTag();
            entry.put("tile", writeTile(d.tile()));
            entry.putBoolean("riichiDeclared", d.riichiDeclared());
            list.add(entry);
        }
        return list;
    }

    private static List<TheMahjongDiscard> readDiscardList(ListTag list) {
        List<TheMahjongDiscard> discards = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            discards.add(new TheMahjongDiscard(
                    readTile(entry.getCompound("tile")),
                    entry.getBoolean("riichiDeclared")));
        }
        return discards;
    }

    // -------------------------------------------------------------------------
    // Tile helpers
    // -------------------------------------------------------------------------

    public static CompoundTag writeTile(TheMahjongTile tile) {
        CompoundTag tag = new CompoundTag();
        tag.putString("suit", tile.suit().name());
        tag.putInt("rank", tile.rank());
        tag.putBoolean("redDora", tile.redDora());
        return tag;
    }

    public static TheMahjongTile readTile(CompoundTag tag) {
        return new TheMahjongTile(
                TheMahjongTile.Suit.valueOf(tag.getString("suit")),
                tag.getInt("rank"),
                tag.getBoolean("redDora"));
    }

    public static ListTag writeTileList(List<TheMahjongTile> tiles) {
        ListTag list = new ListTag();
        for (TheMahjongTile tile : tiles) {
            list.add(writeTile(tile));
        }
        return list;
    }

    public static List<TheMahjongTile> readTileList(ListTag list) {
        List<TheMahjongTile> tiles = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            tiles.add(readTile(list.getCompound(i)));
        }
        return tiles;
    }
}
