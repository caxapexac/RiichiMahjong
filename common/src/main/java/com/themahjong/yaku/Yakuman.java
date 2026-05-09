package com.themahjong.yaku;

import com.themahjong.TheMahjongRuleSet;
import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongTile;
import com.themahjong.yaku.HandShape.Chitoitsu;
import com.themahjong.yaku.HandShape.ConcealedGroup;
import com.themahjong.yaku.HandShape.Kokushimusou;
import com.themahjong.yaku.HandShape.Standard;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public enum Yakuman {
    KOKUSHIMUSOU,
    SUUANKOU,
    CHUURENPOUTOU,
    DAISANGEN,
    TSUISOU,
    SHOUSUUSHI,
    DAISUUSHI,
    RYUUIISOU,
    CHINROUTOU,
    SUUKANTSU,
    RENHOU,
    CHIHOU,
    TENHOU;

    // -------------------------------------------------------------------------
    // Aggregate
    // -------------------------------------------------------------------------

    /**
     * Returns every yakuman present in the hand. May return multiple (e.g. Daisuushi + Tsuisou).
     * Callers are responsible for scoring rules around stacking / overriding.
     * RENHOU is only included when {@code rules.renhouAllowed()} is true.
     */
    public static List<Yakuman> check(HandShape shape, WinContext ctx, TheMahjongRuleSet rules) {
        List<Yakuman> result = new ArrayList<>();
        if (checkKokushimusou(shape))                       result.add(KOKUSHIMUSOU);
        if (checkSuuankou(shape, ctx))                      result.add(SUUANKOU);
        if (checkChuurenpoutou(shape))                      result.add(CHUURENPOUTOU);
        if (checkDaisangen(shape))                          result.add(DAISANGEN);
        if (checkTsuisou(shape))                            result.add(TSUISOU);
        if (checkShousuushi(shape))                         result.add(SHOUSUUSHI);
        if (checkDaisuushi(shape))                          result.add(DAISUUSHI);
        if (checkRyuuiisou(shape))                          result.add(RYUUIISOU);
        if (checkChinroutou(shape))                         result.add(CHINROUTOU);
        if (checkSuukantsu(shape))                          result.add(SUUKANTSU);
        if (checkTenhou(ctx))                               result.add(TENHOU);
        if (checkChihou(ctx))                               result.add(CHIHOU);
        if (rules.renhouAllowed() && checkRenhou(shape, ctx)) result.add(RENHOU);
        return result;
    }

    // -------------------------------------------------------------------------
    // Individual checkers
    // -------------------------------------------------------------------------

    /** Thirteen orphans: hand shape is already the Kokushimusou form. */
    public static boolean checkKokushimusou(HandShape shape) {
        return shape instanceof Kokushimusou;
    }

    /**
     * Four concealed triplets/quads.
     * Declared melds must all be Ankan (closed quad); concealed groups must all be Triplet.
     * By ron, valid only as tanki wait — the winning tile must complete the pair, not a triplet.
     */
    public static boolean checkSuuankou(HandShape shape, WinContext ctx) {
        if (!(shape instanceof Standard s)) return false;
        boolean meldsOk = s.melds().stream().allMatch(m -> m instanceof TheMahjongMeld.Ankan);
        boolean groupsOk = s.closedGroups().stream().allMatch(g -> g instanceof ConcealedGroup.Triplet);
        if (!meldsOk || !groupsOk) return false;
        if (!ctx.tsumo()) return ctx.winningTile().matchesSuitRank(s.pair());
        return true;
    }

    /**
     * Nine gates: closed, single-suit hand matching 1-1-1-2-3-4-5-6-7-8-9-9-9 plus one extra tile.
     *
     * Fix vs legacy: legacy had a potential ArrayIndexOutOfBoundsException when a shuntsu
     * started at rank 1 (index -1), and did not verify the hand is fully closed.
     */
    public static boolean checkChuurenpoutou(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        if (!s.closed()) return false;

        List<TheMahjongTile> tiles = allTiles(s);

        TheMahjongTile.Suit suit = tiles.get(0).suit();
        if (suit == TheMahjongTile.Suit.WIND || suit == TheMahjongTile.Suit.DRAGON) return false;

        int[] counts = new int[9]; // index i = rank (i+1)
        for (TheMahjongTile t : tiles) {
            if (t.suit() != suit) return false;
            counts[t.rank() - 1]++;
        }

        int[] pattern = {3, 1, 1, 1, 1, 1, 1, 1, 3};
        int extras = 0;
        for (int i = 0; i < 9; i++) {
            int diff = counts[i] - pattern[i];
            if (diff < 0 || diff > 1) return false;
            if (diff == 1) extras++;
        }
        return extras == 1;
    }

    /** Big three dragons: triplets or quads of all three dragon tiles. */
    public static boolean checkDaisangen(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        long fromMelds = s.melds().stream()
                .filter(m -> !(m instanceof TheMahjongMeld.Chi) && isDragon(m.tiles().get(0)))
                .count();
        long fromClosed = s.closedGroups().stream()
                .filter(g -> g instanceof ConcealedGroup.Triplet && isDragon(g.tiles().get(0)))
                .count();
        return fromMelds + fromClosed == 3;
    }

    /**
     * All honors: every tile is a wind or dragon.
     * Valid in both Standard (triplets/quads + pair) and Chitoitsu forms.
     */
    public static boolean checkTsuisou(HandShape shape) {
        if (shape instanceof Standard s) {
            if (s.melds().stream().anyMatch(m -> m instanceof TheMahjongMeld.Chi)) return false;
            if (s.closedGroups().stream().anyMatch(g -> g instanceof ConcealedGroup.Sequence)) return false;
            return allTiles(s).stream().allMatch(TheMahjongTile::honor);
        }
        if (shape instanceof Chitoitsu c) {
            return c.pairs().stream().allMatch(TheMahjongTile::honor);
        }
        return false;
    }

    /** Little four winds: three wind triplets/quads plus one wind pair. */
    public static boolean checkShousuushi(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        if (!isWind(s.pair())) return false;
        long fromMelds = s.melds().stream()
                .filter(m -> !(m instanceof TheMahjongMeld.Chi) && isWind(m.tiles().get(0)))
                .count();
        long fromClosed = s.closedGroups().stream()
                .filter(g -> g instanceof ConcealedGroup.Triplet && isWind(g.tiles().get(0)))
                .count();
        return fromMelds + fromClosed == 3;
    }

    /** Big four winds: triplets or quads of all four wind tiles. */
    public static boolean checkDaisuushi(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        long fromMelds = s.melds().stream()
                .filter(m -> !(m instanceof TheMahjongMeld.Chi) && isWind(m.tiles().get(0)))
                .count();
        long fromClosed = s.closedGroups().stream()
                .filter(g -> g instanceof ConcealedGroup.Triplet && isWind(g.tiles().get(0)))
                .count();
        return fromMelds + fromClosed == 4;
    }

    /**
     * All green: every tile in the hand is one of S2, S3, S4, S6, S8, or Hatsu.
     * Valid in Standard and Chitoitsu forms.
     *
     * Fix vs legacy: legacy checked {@code shuntsu.getTile() == S3} (the 3-4-5 sequence, where
     * 5-sou is not green). Correct green shuntsu is 2-3-4 souzu. This checker validates all
     * individual tiles directly, so the sequence start tile is irrelevant.
     */
    public static boolean checkRyuuiisou(HandShape shape) {
        // Kokushi includes 13 terminal/honor tile types, almost none of which are green —
        // it can never be ryuuiisou even when its pair tile happens to be Hatsu. The
        // shared {@code allTiles} helper only returns the pair representative for
        // Kokushi, so reject the shape explicitly here.
        if (shape instanceof Kokushimusou) return false;
        return allTiles(shape).stream().allMatch(Yakuman::isGreen);
    }

    /**
     * All terminals: every tile is rank 1 or rank 9 of a numbered suit, with no sequences.
     * Only valid in Standard form (Chitoitsu-chinroutou is covered by Honroutou, not this yakuman).
     */
    public static boolean checkChinroutou(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        if (s.melds().stream().anyMatch(m -> m instanceof TheMahjongMeld.Chi)) return false;
        if (s.closedGroups().stream().anyMatch(g -> g instanceof ConcealedGroup.Sequence)) return false;
        return allTiles(s).stream().allMatch(TheMahjongTile::terminal);
    }

    /**
     * Four quads: all four groups are declared kans (Daiminkan, Kakan, or Ankan).
     * Requires no concealed groups since all groups must be quads.
     */
    public static boolean checkSuukantsu(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        if (!s.closedGroups().isEmpty()) return false;
        return s.melds().stream().allMatch(m ->
                m instanceof TheMahjongMeld.Daiminkan ||
                m instanceof TheMahjongMeld.Kakan ||
                m instanceof TheMahjongMeld.Ankan);
    }

    /** Heaven's hand: dealer wins via tsumo on the very first draw. */
    public static boolean checkTenhou(WinContext ctx) {
        return ctx.dealer() && ctx.tsumo() && ctx.uninterruptedFirstRound();
    }

    /** Earth's hand: non-dealer wins via tsumo on their first draw (no prior actions). */
    public static boolean checkChihou(WinContext ctx) {
        return !ctx.dealer() && ctx.tsumo() && ctx.uninterruptedFirstRound();
    }

    /**
     * Human's hand (local rule): non-dealer wins via ron before their first draw,
     * and the hand has no open melds.
     *
     * Fix vs legacy: legacy did not verify the hand is closed.
     */
    public static boolean checkRenhou(HandShape shape, WinContext ctx) {
        if (ctx.dealer() || ctx.tsumo() || !ctx.uninterruptedFirstRound()) return false;
        if (shape instanceof Standard s) return s.closed();
        return shape instanceof Chitoitsu || shape instanceof Kokushimusou;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isWind(TheMahjongTile t) {
        return t.suit() == TheMahjongTile.Suit.WIND;
    }

    private static boolean isDragon(TheMahjongTile t) {
        return t.suit() == TheMahjongTile.Suit.DRAGON;
    }

    private static boolean isGreen(TheMahjongTile t) {
        if (t.suit() == TheMahjongTile.Suit.DRAGON)
            return t.rank() == TheMahjongTile.Dragon.HATSU.tileRank();
        if (t.suit() == TheMahjongTile.Suit.SOUZU) {
            int r = t.rank();
            return r == 2 || r == 3 || r == 4 || r == 6 || r == 8;
        }
        return false;
    }

    /**
     * Collects all tile instances from a Standard hand: every tile from every declared meld,
     * every tile from every concealed group, plus two copies of the pair tile.
     */
    private static List<TheMahjongTile> allTiles(Standard s) {
        return Stream.concat(
                Stream.concat(
                        s.melds().stream().flatMap(m -> m.tiles().stream()),
                        s.closedGroups().stream().flatMap(g -> g.tiles().stream())),
                Stream.of(s.pair(), s.pair())
        ).toList();
    }

    /**
     * Collects representative tile instances for all HandShape variants.
     * For Standard, includes pair × 2. For Chitoitsu, one representative per pair is sufficient
     * for per-tile property checks (e.g. isGreen).
     */
    private static List<TheMahjongTile> allTiles(HandShape shape) {
        if (shape instanceof Standard s) return allTiles(s);
        if (shape instanceof Chitoitsu c) return c.pairs();
        if (shape instanceof Kokushimusou k) return List.of(k.pairTile());
        throw new IllegalStateException("unhandled HandShape: " + shape);
    }
}
