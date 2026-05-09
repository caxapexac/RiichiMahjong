package com.riichimahjong.themahjongcompat;

import com.themahjong.TheMahjongMatch;
import com.themahjong.TheMahjongTile;
import com.themahjong.driver.MahjongPlayerInterface;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.TheMahjongDriver;
import com.themahjong.yaku.NonYakuman;
import com.themahjong.yaku.WinResult;
import com.themahjong.yaku.Yakuman;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Serializes/deserializes a {@link TheMahjongDriver} to/from NBT. The match itself is
 * delegated to {@link MatchNbt}; this class covers the driver's mutable extras
 * ({@link MatchPhase}, {@code lastDrawWasRinshan}, {@code pendingRiichiCommit}) plus
 * the version tag.
 *
 * <p><b>Deliberately not persisted:</b> {@code submitted} and {@code claimDecisions}.
 * Both are transient input buffers — a player in the middle of pressing a button or
 * answering a claim window must re-press / re-decide after a reload. The state of the
 * match, the round, scores, melds, walls, dora, and the current phase are preserved
 * exactly, which is what "100% stable" actually means for gameplay continuity.
 *
 * <p>{@link MatchPhase.RoundEnded} round-trips its {@code winResults} list both
 * over live server→client sync and across save/load so the client renderer can
 * build the result-screen text from authoritative driver state (no English
 * crosses the wire) and the result overlay survives break+place / chunk reload.
 *
 * <p>{@link MatchPhase.Resolving} is auto-drained inside {@code advance()} and is
 * never observed externally; the driver contract guarantees it is gone before
 * {@code currentPhase()} returns to a caller. We still encode it for completeness so
 * round-trip never throws, but in practice it should never appear in NBT.
 */
public final class DriverNbt {

    private static final int VERSION = 1;

    private DriverNbt() {}

    public static CompoundTag writeDriver(TheMahjongDriver driver) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", VERSION);
        tag.put("match", MatchNbt.writeMatch(driver.match()));
        TheMahjongDriver.Snapshot snap = driver.snapshot();
        tag.put("phase", writePhase(snap.phase()));
        tag.putBoolean("lastDrawWasRinshan", snap.lastDrawWasRinshan());
        if (snap.pendingRiichiCommit().isPresent()) {
            tag.putInt("pendingRiichiCommit", snap.pendingRiichiCommit().getAsInt());
        }
        return tag;
    }

    /**
     * Restore a driver from NBT. Caller supplies the freshly constructed player
     * implementations and the {@link Random} to use for any future shuffles
     * ({@code advanceRound}); neither is persisted by this class.
     */
    public static TheMahjongDriver readDriver(
            CompoundTag tag,
            List<MahjongPlayerInterface> players,
            Random random) {
        int v = tag.getInt("v");
        if (v != VERSION) {
            throw new IllegalArgumentException("Unsupported DriverNbt version: " + v);
        }
        TheMahjongMatch match = MatchNbt.readMatch(tag.getCompound("match"));
        MatchPhase phase = readPhase(tag.getCompound("phase"));
        boolean lastDrawWasRinshan = tag.getBoolean("lastDrawWasRinshan");
        OptionalInt pendingRiichiCommit = tag.contains("pendingRiichiCommit", Tag.TAG_INT)
                ? OptionalInt.of(tag.getInt("pendingRiichiCommit"))
                : OptionalInt.empty();
        TheMahjongDriver.Snapshot snapshot = new TheMahjongDriver.Snapshot(
                phase, lastDrawWasRinshan, pendingRiichiCommit);
        return new TheMahjongDriver(match, players, random, snapshot);
    }

    // -------------------------------------------------------------------------
    // MatchPhase
    // -------------------------------------------------------------------------

    private static CompoundTag writePhase(MatchPhase phase) {
        CompoundTag tag = new CompoundTag();
        if (phase instanceof MatchPhase.NotStarted) {
            tag.putString("type", "NOT_STARTED");
        } else if (phase instanceof MatchPhase.Dealing d) {
            tag.putString("type", "DEALING");
            tag.putString("stage", d.stage().name());
            tag.putDouble("elapsed", d.elapsed());
        } else if (phase instanceof MatchPhase.AwaitingDraw d) {
            tag.putString("type", "AWAITING_DRAW");
            tag.putInt("seat", d.seat());
            tag.putBoolean("rinshan", d.rinshan());
        } else if (phase instanceof MatchPhase.AwaitingDiscard d) {
            tag.putString("type", "AWAITING_DISCARD");
            tag.putInt("seat", d.seat());
        } else if (phase instanceof MatchPhase.AwaitingClaims c) {
            tag.putString("type", "AWAITING_CLAIMS");
            ListTag pending = new ListTag();
            for (Integer s : c.pendingSeats()) {
                pending.add(IntTag.valueOf(s));
            }
            tag.put("pendingSeats", pending);
            tag.put("heldTile", MatchNbt.writeTile(c.heldTile()));
            tag.putString("source", c.source().name());
        } else if (phase instanceof MatchPhase.Resolving) {
            tag.putString("type", "RESOLVING");
        } else if (phase instanceof MatchPhase.RoundEnded re) {
            tag.putString("type", "ROUND_ENDED");
            ListTag wins = new ListTag();
            for (WinResult wr : re.winResults()) {
                wins.add(writeWinResult(wr));
            }
            tag.put("winResults", wins);
        } else if (phase instanceof MatchPhase.BetweenRounds) {
            tag.putString("type", "BETWEEN_ROUNDS");
        } else if (phase instanceof MatchPhase.MatchEnded) {
            tag.putString("type", "MATCH_ENDED");
        } else {
            throw new IllegalArgumentException("Unknown MatchPhase: " + phase);
        }
        return tag;
    }

    private static MatchPhase readPhase(CompoundTag tag) {
        String type = tag.getString("type");
        return switch (type) {
            case "NOT_STARTED" -> new MatchPhase.NotStarted();
            case "DEALING" -> new MatchPhase.Dealing(
                    MatchPhase.Stage.valueOf(tag.getString("stage")),
                    tag.getDouble("elapsed"));
            case "AWAITING_DRAW" -> new MatchPhase.AwaitingDraw(tag.getInt("seat"), tag.getBoolean("rinshan"));
            case "AWAITING_DISCARD" -> new MatchPhase.AwaitingDiscard(tag.getInt("seat"));
            case "AWAITING_CLAIMS" -> {
                ListTag pendingTag = tag.getList("pendingSeats", Tag.TAG_INT);
                Set<Integer> pending = new LinkedHashSet<>();
                for (int i = 0; i < pendingTag.size(); i++) {
                    pending.add(pendingTag.getInt(i));
                }
                TheMahjongTile heldTile = MatchNbt.readTile(tag.getCompound("heldTile"));
                MatchPhase.ClaimSource source = MatchPhase.ClaimSource.valueOf(tag.getString("source"));
                yield new MatchPhase.AwaitingClaims(pending, heldTile, source);
            }
            case "RESOLVING" -> new MatchPhase.Resolving();
            case "ROUND_ENDED" -> {
                ListTag wins = tag.getList("winResults", Tag.TAG_COMPOUND);
                List<WinResult> results = new ArrayList<>(wins.size());
                for (int i = 0; i < wins.size(); i++) {
                    results.add(readWinResult(wins.getCompound(i)));
                }
                yield new MatchPhase.RoundEnded(results);
            }
            case "BETWEEN_ROUNDS" -> new MatchPhase.BetweenRounds();
            case "MATCH_ENDED" -> new MatchPhase.MatchEnded();
            default -> throw new IllegalArgumentException("Unknown MatchPhase type: " + type);
        };
    }

    // -------------------------------------------------------------------------
    // WinResult
    // -------------------------------------------------------------------------

    private static CompoundTag writeWinResult(WinResult wr) {
        CompoundTag tag = new CompoundTag();
        ListTag yaku = new ListTag();
        for (NonYakuman y : wr.yaku()) yaku.add(net.minecraft.nbt.StringTag.valueOf(y.name()));
        tag.put("yaku", yaku);
        ListTag yakuman = new ListTag();
        for (Yakuman y : wr.yakuman()) yakuman.add(net.minecraft.nbt.StringTag.valueOf(y.name()));
        tag.put("yakuman", yakuman);
        tag.putInt("han", wr.han());
        tag.putInt("fu", wr.fu());
        tag.putInt("doraCount", wr.doraCount());
        ListTag deltas = new ListTag();
        for (Integer d : wr.pointDeltas()) deltas.add(IntTag.valueOf(d));
        tag.put("pointDeltas", deltas);
        return tag;
    }

    private static WinResult readWinResult(CompoundTag tag) {
        ListTag yakuTag = tag.getList("yaku", Tag.TAG_STRING);
        List<NonYakuman> yaku = new ArrayList<>(yakuTag.size());
        for (int i = 0; i < yakuTag.size(); i++) yaku.add(NonYakuman.valueOf(yakuTag.getString(i)));
        ListTag yakumanTag = tag.getList("yakuman", Tag.TAG_STRING);
        List<Yakuman> yakuman = new ArrayList<>(yakumanTag.size());
        for (int i = 0; i < yakumanTag.size(); i++) yakuman.add(Yakuman.valueOf(yakumanTag.getString(i)));
        ListTag deltasTag = tag.getList("pointDeltas", Tag.TAG_INT);
        List<Integer> deltas = new ArrayList<>(deltasTag.size());
        for (int i = 0; i < deltasTag.size(); i++) deltas.add(deltasTag.getInt(i));
        return new WinResult(yaku, yakuman,
                tag.getInt("han"), tag.getInt("fu"), tag.getInt("doraCount"), deltas);
    }
}
