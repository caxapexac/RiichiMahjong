package com.themahjong.yaku;

import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongPlayer.RiichiState;
import com.themahjong.TheMahjongTile;
import com.themahjong.TheMahjongTile.Suit;
import com.themahjong.yaku.HandShape.Chitoitsu;
import com.themahjong.yaku.HandShape.ConcealedGroup;
import com.themahjong.yaku.HandShape.Kokushimusou;
import com.themahjong.yaku.HandShape.Standard;

import java.util.stream.Stream;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

public enum NonYakuman {

    // 1-han
    RIICHI(1),
    DOUBLE_RIICHI(2),
    IPPATSU(1),
    MENZEN_TSUMO(1),
    PINFU(1),
    IIPEIKO(1),
    TANYAO(1, 1),
    HAKU(1, 1),
    HATSU(1, 1),
    CHUN(1, 1),
    SEAT_WIND(1, 1),
    ROUND_WIND(1, 1),
    HAITEI(1, 1),
    HOUTEI(1, 1),
    RINSHAN_KAIHOU(1, 1),
    CHANKAN(1, 1),

    // 2-han
    CHANTA(2, 1),
    HONROUTOU(2, 2),
    SANSHOKU_DOUJUN(2, 1),
    ITTSU(2, 1),
    TOITOI(2, 2),
    SANSHOKU_DOUKOU(2, 2),
    SANANKOU(2, 2),
    SANKANTSU(2, 2),
    SHOUSANGEN(2, 2),
    CHIITOI(2),

    // 3-han
    RYANPEIKOU(3),
    JUNCHAN(3, 2),
    HONITSU(3, 2),

    // 6-han
    CHINITSU(6, 5);

    private final int closedHan;
    private final int rawOpenHan; // -1 = closed-only

    NonYakuman(int closedHan) {
        this.closedHan = closedHan;
        this.rawOpenHan = -1;
    }

    NonYakuman(int closedHan, int openHan) {
        this.closedHan = closedHan;
        this.rawOpenHan = openHan;
    }

    public int closedHan() {
        return closedHan;
    }

    /** Empty if this yaku cannot be scored with an open hand. */
    public OptionalInt openHan() {
        return rawOpenHan == -1 ? OptionalInt.empty() : OptionalInt.of(rawOpenHan);
    }

    // -------------------------------------------------------------------------
    // Aggregate
    // -------------------------------------------------------------------------

    public static List<NonYakuman> check(HandShape shape, WinContext ctx) {
        List<NonYakuman> result = new ArrayList<>();
        if (checkRiichi(ctx))             result.add(RIICHI);
        if (checkDoubleRiichi(ctx))       result.add(DOUBLE_RIICHI);
        if (checkIppatsu(ctx))            result.add(IPPATSU);
        if (checkMenzenTsumo(shape, ctx)) result.add(MENZEN_TSUMO);
        if (checkPinfu(shape, ctx))       result.add(PINFU);
        if (checkIipeiko(shape))          result.add(IIPEIKO);
        if (checkTanyao(shape))           result.add(TANYAO);
        if (checkHaku(shape))             result.add(HAKU);
        if (checkHatsu(shape))            result.add(HATSU);
        if (checkChun(shape))             result.add(CHUN);
        if (checkSeatWind(shape, ctx))    result.add(SEAT_WIND);
        if (checkRoundWind(shape, ctx))   result.add(ROUND_WIND);
        if (checkHaitei(ctx))             result.add(HAITEI);
        if (checkHoutei(ctx))             result.add(HOUTEI);
        if (checkRinshanKaihou(ctx))      result.add(RINSHAN_KAIHOU);
        if (checkChankan(ctx))            result.add(CHANKAN);
        if (checkChanta(shape))           result.add(CHANTA);
        if (checkHonroutou(shape))        result.add(HONROUTOU);
        if (checkSanshokuDoujun(shape))   result.add(SANSHOKU_DOUJUN);
        if (checkIttsu(shape))            result.add(ITTSU);
        if (checkToitoi(shape))           result.add(TOITOI);
        if (checkSanshokuDoukou(shape))   result.add(SANSHOKU_DOUKOU);
        if (checkSanankou(shape, ctx))    result.add(SANANKOU);
        if (checkSankantsu(shape))        result.add(SANKANTSU);
        if (checkShousangen(shape))       result.add(SHOUSANGEN);
        if (checkChiitoi(shape))          result.add(CHIITOI);
        if (checkRyanpeikou(shape))       result.add(RYANPEIKOU);
        if (checkJunchan(shape))          result.add(JUNCHAN);
        if (checkHonitsu(shape))          result.add(HONITSU);
        if (checkChinitsu(shape))         result.add(CHINITSU);
        removeSubsumed(result);
        return result;
    }

    private static boolean hasTripletOfSuitRank(HandShape shape, Suit suit, int rank) {
        if (!(shape instanceof Standard s)) return false;
        TheMahjongTile ref = new TheMahjongTile(suit, rank, false);
        for (TheMahjongMeld meld : s.melds()) {
            if (meld instanceof TheMahjongMeld.Chi) continue;
            if (meld.tiles().get(0).matchesSuitRank(ref)) return true;
        }
        for (ConcealedGroup group : s.closedGroups()) {
            if (group instanceof ConcealedGroup.Triplet triplet
                    && triplet.tiles().get(0).matchesSuitRank(ref)) return true;
        }
        return false;
    }

    private static List<TheMahjongTile> allTiles(Standard s) {
        return Stream.concat(
                Stream.concat(
                        s.melds().stream().flatMap(m -> m.tiles().stream()),
                        s.closedGroups().stream().flatMap(g -> g.tiles().stream())),
                Stream.of(s.pair(), s.pair())
        ).toList();
    }

    private static boolean isYakuhaiPair(TheMahjongTile pair, WinContext ctx) {
        if (pair.suit() == Suit.DRAGON) return true;
        if (pair.suit() == Suit.WIND) {
            int rank = pair.rank();
            return rank == ctx.seatWind().tileRank() || rank == ctx.roundWind().tileRank();
        }
        return false;
    }

    /**
     * Returns true if the winning tile completes a ryanmen (two-sided) wait in any sequence.
     * Sequences are normalized ascending. A tile at the left end is ryanmen iff the right end
     * is not maxRank (so a tile one above the right end exists). A tile at the right end is
     * ryanmen iff the left end is not rank 1 (so a tile one below the left end exists).
     * Middle-tile (kanchan) completions are never ryanmen.
     */
    private static boolean hasRyanmenWait(List<ConcealedGroup> groups, TheMahjongTile winTile) {
        for (ConcealedGroup group : groups) {
            if (!(group instanceof ConcealedGroup.Sequence seq)) continue;
            List<TheMahjongTile> tiles = seq.tiles();
            TheMahjongTile left  = tiles.get(0);
            TheMahjongTile right = tiles.get(2);
            int maxRank = left.suit().maxRank();
            if (winTile.matchesSuitRank(left)  && right.rank() < maxRank) return true;
            if (winTile.matchesSuitRank(right) && left.rank()  > 1)       return true;
        }
        return false;
    }

    static void removeSubsumed(List<NonYakuman> result) {
        if (result.contains(RYANPEIKOU))  result.remove(IIPEIKO);
        if (result.contains(JUNCHAN))     result.remove(CHANTA);
        if (result.contains(CHINITSU))    result.remove(HONITSU);
    }

    // -------------------------------------------------------------------------
    // Individual checkers
    // -------------------------------------------------------------------------

    public static boolean checkRiichi(WinContext ctx)       { return ctx.riichiState() == RiichiState.RIICHI; }
    public static boolean checkDoubleRiichi(WinContext ctx) { return ctx.riichiState() == RiichiState.DOUBLE_RIICHI; }
    public static boolean checkIppatsu(WinContext ctx)      { return ctx.ippatsuEligible(); }
    public static boolean checkMenzenTsumo(HandShape shape, WinContext ctx) {
        if (!ctx.tsumo()) return false;
        if (shape instanceof Standard s) return s.closed();
        return shape instanceof Chitoitsu || shape instanceof Kokushimusou;
    }
    public static boolean checkPinfu(HandShape shape, WinContext ctx) {
        if (!(shape instanceof Standard s)) return false;
        if (!s.closed()) return false;
        // Pinfu requires the hand to be fully concealed: no declared melds at all.
        // Ankan is technically "closed" but still a kan, which pinfu forbids.
        if (!s.melds().isEmpty()) return false;
        if (s.closedGroups().stream().anyMatch(g -> g instanceof ConcealedGroup.Triplet)) return false;
        if (isYakuhaiPair(s.pair(), ctx)) return false;
        return hasRyanmenWait(s.closedGroups(), ctx.winningTile());
    }
    public static boolean checkIipeiko(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        if (!s.closed()) return false;
        List<ConcealedGroup.Sequence> seqs = s.closedGroups().stream()
                .filter(g -> g instanceof ConcealedGroup.Sequence)
                .map(g -> (ConcealedGroup.Sequence) g)
                .toList();
        for (int i = 0; i < seqs.size(); i++)
            for (int j = i + 1; j < seqs.size(); j++)
                if (sequencesMatch(seqs.get(i).tiles(), seqs.get(j).tiles())) return true;
        return false;
    }

    /** Compares two sequence tile lists ignoring the redDora flag. */
    private static boolean sequencesMatch(List<TheMahjongTile> a, List<TheMahjongTile> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).matchesSuitRank(b.get(i))) return false;
        }
        return true;
    }
    public static boolean checkTanyao(HandShape shape) {
        if (shape instanceof Standard s)  return allTiles(s).stream().allMatch(t -> !t.terminal() && !t.honor());
        if (shape instanceof Chitoitsu c) return c.pairs().stream().allMatch(t -> !t.terminal() && !t.honor());
        return false;
    }
    public static boolean checkHaku(HandShape shape) {
        return hasTripletOfSuitRank(shape, Suit.DRAGON, TheMahjongTile.Dragon.HAKU.tileRank());
    }

    public static boolean checkHatsu(HandShape shape) {
        return hasTripletOfSuitRank(shape, Suit.DRAGON, TheMahjongTile.Dragon.HATSU.tileRank());
    }

    public static boolean checkChun(HandShape shape) {
        return hasTripletOfSuitRank(shape, Suit.DRAGON, TheMahjongTile.Dragon.CHUN.tileRank());
    }

    public static boolean checkSeatWind(HandShape shape, WinContext ctx) {
        return hasTripletOfSuitRank(shape, Suit.WIND, ctx.seatWind().tileRank());
    }

    public static boolean checkRoundWind(HandShape shape, WinContext ctx) {
        return hasTripletOfSuitRank(shape, Suit.WIND, ctx.roundWind().tileRank());
    }
    public static boolean checkHaitei(WinContext ctx)      { return ctx.tsumo() && ctx.lastTile() && !ctx.rinshanDraw(); }
    public static boolean checkHoutei(WinContext ctx)      { return ctx.winType() == WinContext.WinType.RON && ctx.lastTile(); }
    public static boolean checkRinshanKaihou(WinContext ctx) { return ctx.tsumo() && ctx.rinshanDraw(); }
    public static boolean checkChankan(WinContext ctx)     { return ctx.winType() == WinContext.WinType.CHANKAN; }

    public static boolean checkChanta(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        if (!s.pair().terminal() && !s.pair().honor()) return false;
        for (TheMahjongMeld m : s.melds())
            if (m.tiles().stream().noneMatch(t -> t.terminal() || t.honor())) return false;
        for (ConcealedGroup g : s.closedGroups())
            if (g.tiles().stream().noneMatch(t -> t.terminal() || t.honor())) return false;
        // at least one sequence — otherwise it would be honroutou
        return s.melds().stream().anyMatch(m -> m instanceof TheMahjongMeld.Chi)
            || s.closedGroups().stream().anyMatch(g -> g instanceof ConcealedGroup.Sequence);
    }

    public static boolean checkHonroutou(HandShape shape) {
        if (shape instanceof Standard s)
            return allTiles(s).stream().allMatch(t -> t.terminal() || t.honor());
        if (shape instanceof Chitoitsu c)
            return c.pairs().stream().allMatch(t -> t.terminal() || t.honor());
        return false;
    }

    public static boolean checkSanshokuDoujun(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        List<List<TheMahjongTile>> seqs = new ArrayList<>();
        for (TheMahjongMeld m : s.melds())
            if (m instanceof TheMahjongMeld.Chi) seqs.add(m.tiles());
        for (ConcealedGroup g : s.closedGroups())
            if (g instanceof ConcealedGroup.Sequence sq) seqs.add(sq.tiles());
        for (int r = 1; r <= 7; r++) {
            final int startRank = r;
            boolean manzu = seqs.stream().anyMatch(sq -> sq.get(0).suit() == Suit.MANZU && sq.get(0).rank() == startRank);
            boolean pinzu = seqs.stream().anyMatch(sq -> sq.get(0).suit() == Suit.PINZU && sq.get(0).rank() == startRank);
            boolean souzu = seqs.stream().anyMatch(sq -> sq.get(0).suit() == Suit.SOUZU && sq.get(0).rank() == startRank);
            if (manzu && pinzu && souzu) return true;
        }
        return false;
    }

    public static boolean checkIttsu(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        List<List<TheMahjongTile>> seqs = new ArrayList<>();
        for (TheMahjongMeld m : s.melds())
            if (m instanceof TheMahjongMeld.Chi) seqs.add(m.tiles());
        for (ConcealedGroup g : s.closedGroups())
            if (g instanceof ConcealedGroup.Sequence sq) seqs.add(sq.tiles());
        for (Suit suit : new Suit[]{Suit.MANZU, Suit.PINZU, Suit.SOUZU}) {
            boolean has123 = seqs.stream().anyMatch(sq -> sq.get(0).suit() == suit && sq.get(0).rank() == 1);
            boolean has456 = seqs.stream().anyMatch(sq -> sq.get(0).suit() == suit && sq.get(0).rank() == 4);
            boolean has789 = seqs.stream().anyMatch(sq -> sq.get(0).suit() == suit && sq.get(0).rank() == 7);
            if (has123 && has456 && has789) return true;
        }
        return false;
    }

    public static boolean checkToitoi(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        if (s.melds().stream().anyMatch(m -> m instanceof TheMahjongMeld.Chi)) return false;
        return s.closedGroups().stream().allMatch(g -> g instanceof ConcealedGroup.Triplet);
    }

    public static boolean checkSanshokuDoukou(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        for (int r = 1; r <= 9; r++) {
            if (hasTripletOfSuitRank(shape, Suit.MANZU, r)
             && hasTripletOfSuitRank(shape, Suit.PINZU, r)
             && hasTripletOfSuitRank(shape, Suit.SOUZU, r)) return true;
        }
        return false;
    }

    /**
     * Three concealed triplets. On a ron win, the triplet completed by the winning tile is
     * considered open — only pre-formed triplets count. Three pre-formed triplets suffice,
     * even if the winning tile completed a 4th (shanpon wait).
     */
    public static boolean checkSanankou(HandShape shape, WinContext ctx) {
        if (!(shape instanceof Standard s)) return false;
        long ankans = s.melds().stream().filter(m -> m instanceof TheMahjongMeld.Ankan).count();
        long triplets = s.closedGroups().stream().filter(g -> g instanceof ConcealedGroup.Triplet).count();

        if (ctx.tsumo()) return ankans + triplets >= 3;

        // Ron: a triplet is demoted to "open" only when the winning tile genuinely
        // completed it via shanpon. If the decomposition is annotated (came from
        // {@link HandShape#decomposeForWin}), use the wait shape directly; otherwise
        // scan groups via {@link FuCalculator#winTileCanOnlyCompleteTriplet}.
        boolean shanponRon = s.waitShape().isPresent()
                ? s.waitShape().get() == HandShape.WaitShape.SHANPON
                : FuCalculator.winTileCanOnlyCompleteTriplet(s, ctx.winningTile());
        if (!shanponRon) return ankans + triplets >= 3;

        TheMahjongTile win = ctx.winningTile();
        long preFormedTriplets = s.closedGroups().stream()
                .filter(g -> g instanceof ConcealedGroup.Triplet t
                        && !win.matchesSuitRank(t.tiles().get(0)))
                .count();
        return ankans + preFormedTriplets >= 3;
    }

    public static boolean checkSankantsu(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        long kans = s.melds().stream()
                .filter(m -> m instanceof TheMahjongMeld.Ankan
                          || m instanceof TheMahjongMeld.Daiminkan
                          || m instanceof TheMahjongMeld.Kakan)
                .count();
        return kans >= 3;
    }

    public static boolean checkShousangen(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        long dragonTriplets =
            s.melds().stream().filter(m -> !(m instanceof TheMahjongMeld.Chi) && m.tiles().get(0).suit() == Suit.DRAGON).count()
          + s.closedGroups().stream().filter(g -> g instanceof ConcealedGroup.Triplet t && t.tiles().get(0).suit() == Suit.DRAGON).count();
        return dragonTriplets == 2 && s.pair().suit() == Suit.DRAGON;
    }

    public static boolean checkChiitoi(HandShape shape) {
        return shape instanceof Chitoitsu;
    }

    public static boolean checkRyanpeikou(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        if (!s.closed()) return false;
        List<List<TheMahjongTile>> seqs = s.closedGroups().stream()
                .filter(g -> g instanceof ConcealedGroup.Sequence)
                .map(g -> g.tiles())
                .toList();
        if (seqs.size() != 4) return false;
        return (sequencesMatch(seqs.get(0), seqs.get(1)) && sequencesMatch(seqs.get(2), seqs.get(3)))
            || (sequencesMatch(seqs.get(0), seqs.get(2)) && sequencesMatch(seqs.get(1), seqs.get(3)))
            || (sequencesMatch(seqs.get(0), seqs.get(3)) && sequencesMatch(seqs.get(1), seqs.get(2)));
    }

    public static boolean checkJunchan(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        if (!s.pair().terminal()) return false;
        for (TheMahjongMeld m : s.melds())
            if (m.tiles().stream().noneMatch(TheMahjongTile::terminal)) return false;
        for (ConcealedGroup g : s.closedGroups())
            if (g.tiles().stream().noneMatch(TheMahjongTile::terminal)) return false;
        return s.melds().stream().anyMatch(m -> m instanceof TheMahjongMeld.Chi)
            || s.closedGroups().stream().anyMatch(g -> g instanceof ConcealedGroup.Sequence);
    }

    public static boolean checkHonitsu(HandShape shape) {
        List<TheMahjongTile> tiles = suitCheckTiles(shape);
        Suit numSuit = null;
        for (TheMahjongTile t : tiles) {
            if (t.honor()) continue;
            if (numSuit == null) numSuit = t.suit();
            else if (t.suit() != numSuit) return false;
        }
        return numSuit != null;
    }

    public static boolean checkChinitsu(HandShape shape) {
        List<TheMahjongTile> tiles = suitCheckTiles(shape);
        Suit numSuit = null;
        for (TheMahjongTile t : tiles) {
            if (t.honor()) return false;
            if (numSuit == null) numSuit = t.suit();
            else if (t.suit() != numSuit) return false;
        }
        return numSuit != null;
    }

    /** Returns a representative tile list for suit-purity checks (one tile per unique suit+rank combination). */
    private static List<TheMahjongTile> suitCheckTiles(HandShape shape) {
        if (shape instanceof Standard s) return allTiles(s);
        if (shape instanceof Chitoitsu c) return c.pairs();
        return List.of();
    }
}
