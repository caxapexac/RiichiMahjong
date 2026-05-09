package com.themahjong.yaku;

import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongTile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Discriminated union of the three legal winning hand shapes in Riichi Mahjong.
 *
 * <p>Use {@link #decompose(List, List)} to obtain all valid decompositions of a
 * 14-tile winning hand. Callers that construct shapes directly (e.g. in tests) are
 * responsible for passing a valid decomposition.
 */
public sealed interface HandShape permits HandShape.Standard, HandShape.Chitoitsu, HandShape.Kokushimusou {

    /**
     * Wait shape of the winning tile within a {@link Standard} decomposition — populated
     * only by {@link #decomposeForWin}. The two-arg {@link #decompose} sets this to empty.
     *
     * <p>Each value carries its wait fu directly so {@link FuCalculator} and yaku checkers
     * never need to scan groups to derive it. {@code SHANPON} additionally signals a
     * "ron-completed concealed triplet" for fu / sanankou demotion.
     */
    enum WaitShape {
        RYANMEN(0),
        KANCHAN(2),
        PENCHAN(2),
        SHANPON(0),
        TANKI(2);

        private final int fu;
        WaitShape(int fu) { this.fu = fu; }
        public int fu() { return fu; }
    }

    /**
     * A group within the concealed part of a {@link Standard} hand — always closed.
     * Declared melds (open or closed) use {@link TheMahjongMeld} directly.
     */
    sealed interface ConcealedGroup permits ConcealedGroup.Sequence, ConcealedGroup.Triplet {

        List<TheMahjongTile> tiles();

        record Sequence(List<TheMahjongTile> tiles) implements ConcealedGroup {
            public Sequence {
                if (tiles.size() != 3) throw new IllegalArgumentException("Sequence must have 3 tiles, got " + tiles.size());
                tiles = List.copyOf(tiles);
            }
        }

        record Triplet(List<TheMahjongTile> tiles) implements ConcealedGroup {
            public Triplet {
                if (tiles.size() != 3) throw new IllegalArgumentException("Triplet must have 3 tiles, got " + tiles.size());
                tiles = List.copyOf(tiles);
            }
        }
    }

    /**
     * Standard 4-meld + 1-pair form.
     *
     * <p>{@code melds} holds declared melds ({@link TheMahjongMeld}: Chi/Pon/Daiminkan/Kakan/Ankan).
     * {@code closedGroups} holds the decomposed concealed tiles (sequences/triplets inferred by
     * the win evaluator). Together they total exactly 4 groups.
     *
     * <p>{@code pair} is one representative tile of the jantai pair.
     */
    record Standard(List<TheMahjongMeld> melds, List<ConcealedGroup> closedGroups,
                    TheMahjongTile pair, Optional<WaitShape> waitShape)
            implements HandShape {

        public Standard {
            if (melds.size() + closedGroups.size() != 4)
                throw new IllegalArgumentException(
                        "Standard hand must have 4 groups total (melds + closedGroups), got "
                                + (melds.size() + closedGroups.size()));
            if (pair == null) throw new IllegalArgumentException("pair cannot be null");
            if (waitShape == null) throw new IllegalArgumentException("waitShape cannot be null; use Optional.empty()");
            melds = List.copyOf(melds);
            closedGroups = List.copyOf(closedGroups);
        }

        /** Backward-compat constructor for callers that don't track wait-shape (tests,
         *  the unannotated {@link #decompose} path). Defaults {@code waitShape} to empty. */
        public Standard(List<TheMahjongMeld> melds, List<ConcealedGroup> closedGroups, TheMahjongTile pair) {
            this(melds, closedGroups, pair, Optional.empty());
        }

        /** True when no declared meld is open (Chi/Pon/Daiminkan/Kakan all have {@code open()=true}; Ankan is false). */
        public boolean closed() {
            return melds.stream().noneMatch(TheMahjongMeld::open);
        }
    }

    /**
     * Seven-pairs form. {@code pairs} holds one representative tile per pair (7 tiles total),
     * all distinct by suit+rank (no four-of-a-kind split into two pairs).
     */
    record Chitoitsu(List<TheMahjongTile> pairs) implements HandShape {
        public Chitoitsu {
            if (pairs.size() != 7) throw new IllegalArgumentException("Chitoitsu must have 7 pairs, got " + pairs.size());
            pairs = List.copyOf(pairs);
        }

        /**
         * Detects chitoitsu in {@code sorted} (14 tiles, sorted by suit then rank).
         * Returns empty if the tiles do not form exactly 7 distinct pairs.
         */
        static Optional<Chitoitsu> tryFrom(List<TheMahjongTile> sorted) {
            Map<Long, Integer> counts = countsBySuitRank(sorted);
            if (counts.size() != 7) return Optional.empty();
            if (counts.values().stream().anyMatch(c -> c != 2)) return Optional.empty();
            List<TheMahjongTile> pairs = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            for (TheMahjongTile t : sorted) {
                if (seen.add(suitRankKey(t))) pairs.add(t);
            }
            return Optional.of(new Chitoitsu(pairs));
        }
    }

    /**
     * Thirteen-orphans form. {@code pairTile} is the terminal or honor tile that forms the pair;
     * the other 12 required terminals/honors are implicit.
     */
    record Kokushimusou(TheMahjongTile pairTile) implements HandShape {
        public Kokushimusou {
            if (pairTile == null) throw new IllegalArgumentException("pairTile cannot be null");
            if (!pairTile.terminal() && !pairTile.honor())
                throw new IllegalArgumentException("Kokushimusou pairTile must be a terminal or honor");
        }

        /**
         * Detects kokushi in {@code sorted} (14 tiles, sorted by suit then rank).
         * Returns empty if the tiles do not contain all 13 terminals/honors plus exactly one pair.
         */
        static Optional<Kokushimusou> tryFrom(List<TheMahjongTile> sorted) {
            Set<Long> required = kokushiRequiredKeys();
            Map<Long, Integer> counts = countsBySuitRank(sorted);
            if (!counts.keySet().equals(required)) return Optional.empty();
            TheMahjongTile pairTile = null;
            for (Map.Entry<Long, Integer> e : counts.entrySet()) {
                if (e.getValue() == 2) {
                    if (pairTile != null) return Optional.empty(); // two pairs → invalid
                    long key = e.getKey();
                    pairTile = sorted.stream().filter(t -> suitRankKey(t) == key).findFirst().orElseThrow();
                }
            }
            if (pairTile == null) return Optional.empty(); // all singletons → no pair
            return Optional.of(new Kokushimusou(pairTile));
        }

        private static Set<Long> kokushiRequiredKeys() {
            Set<Long> keys = new HashSet<>();
            for (TheMahjongTile.Suit suit : new TheMahjongTile.Suit[]{
                    TheMahjongTile.Suit.MANZU, TheMahjongTile.Suit.PINZU, TheMahjongTile.Suit.SOUZU}) {
                keys.add(suitRankKey(suit, 1));
                keys.add(suitRankKey(suit, suit.maxRank()));
            }
            for (TheMahjongTile.Wind w : TheMahjongTile.Wind.values()) {
                keys.add(suitRankKey(TheMahjongTile.Suit.WIND, w.tileRank()));
            }
            for (TheMahjongTile.Dragon d : TheMahjongTile.Dragon.values()) {
                keys.add(suitRankKey(TheMahjongTile.Suit.DRAGON, d.tileRank()));
            }
            return keys;
        }
    }

    // =========================================================================
    // Decomposition
    // =========================================================================

    /**
     * Decomposes {@code concealedTiles} (the winner's concealed tiles <em>including</em> the
     * winning tile) into all valid {@link HandShape} forms, given the already-declared
     * {@code melds}.
     *
     * <p>For a tsumo win, pass {@code player.currentHand()} directly — the drawn tile is
     * already included. For a ron win, append the winning tile to {@code player.currentHand()}
     * before calling.
     *
     * @param concealedTiles all concealed tiles including the winning tile;
     *                       must have exactly {@code (4 - melds.size()) * 3 + 2} tiles
     * @param melds          declared melds (Chi/Pon/Daiminkan/Kakan/Ankan)
     * @return all valid decompositions; empty list if the hand is not a winning hand
     */
    public static List<HandShape> decompose(List<TheMahjongTile> concealedTiles, List<TheMahjongMeld> melds) {
        int closedNeeded = 4 - melds.size();
        if (closedNeeded < 0) return List.of();
        if (concealedTiles.size() != closedNeeded * 3 + 2) return List.of();

        List<TheMahjongTile> sorted = sort(concealedTiles);
        List<HandShape> results = new ArrayList<>();

        decomposeStandard(sorted, melds, closedNeeded, results);

        if (melds.isEmpty()) {
            Chitoitsu.tryFrom(sorted).ifPresent(results::add);
            Kokushimusou.tryFrom(sorted).ifPresent(results::add);
        }

        return results;
    }

    /**
     * Win-aware decomposition. Same as {@link #decompose} but each {@link Standard} is
     * additionally annotated with the {@link WaitShape} of the winning tile. When the
     * winning tile fits multiple positions in a single decomposition (e.g. middle of one
     * sequence and pair of another), each interpretation is emitted as a separate
     * annotated {@code Standard} so {@link WinCalculator} can score each independently
     * and pick the highest. {@code Chitoitsu} and {@code Kokushimusou} are returned
     * unannotated — both have a unique tanki wait that needs no resolution.
     */
    public static List<HandShape> decomposeForWin(
            List<TheMahjongTile> concealedTiles,
            List<TheMahjongMeld> melds,
            TheMahjongTile winningTile) {
        if (winningTile == null) throw new IllegalArgumentException("winningTile cannot be null");
        List<HandShape> base = decompose(concealedTiles, melds);
        List<HandShape> out = new ArrayList<>();
        for (HandShape h : base) {
            if (h instanceof Standard s) {
                List<Standard> variants = annotateWaits(s, winningTile);
                if (variants.isEmpty()) {
                    // Defensive: winning tile didn't slot anywhere. Should not happen for
                    // a genuinely winning hand, but we surface the un-annotated shape so
                    // the caller can still proceed.
                    out.add(s);
                } else {
                    out.addAll(variants);
                }
            } else {
                out.add(h);
            }
        }
        return out;
    }

    /**
     * Enumerate every position the winning tile could occupy in {@code s}, emitting one
     * annotated Standard per position. A tile may match the pair (TANKI) and one or more
     * groups simultaneously when the hand contains repeated suit-rank patterns; each is
     * a separate playable interpretation.
     */
    private static List<Standard> annotateWaits(Standard s, TheMahjongTile winTile) {
        List<Standard> out = new ArrayList<>();
        if (winTile.matchesSuitRank(s.pair())) {
            out.add(new Standard(s.melds(), s.closedGroups(), s.pair(), Optional.of(WaitShape.TANKI)));
        }
        for (ConcealedGroup g : s.closedGroups()) {
            if (g instanceof ConcealedGroup.Triplet t) {
                if (winTile.matchesSuitRank(t.tiles().get(0))) {
                    out.add(new Standard(s.melds(), s.closedGroups(), s.pair(), Optional.of(WaitShape.SHANPON)));
                }
            } else if (g instanceof ConcealedGroup.Sequence seq) {
                TheMahjongTile low  = seq.tiles().get(0);
                TheMahjongTile mid  = seq.tiles().get(1);
                TheMahjongTile high = seq.tiles().get(2);
                if (winTile.matchesSuitRank(mid)) {
                    out.add(new Standard(s.melds(), s.closedGroups(), s.pair(), Optional.of(WaitShape.KANCHAN)));
                } else if (winTile.matchesSuitRank(low)) {
                    // PENCHAN when the formed sequence ends at the suit's high terminal
                    // (e.g. winning 7 to complete 7-8-9). Otherwise RYANMEN.
                    WaitShape ws = (high.rank() == low.suit().maxRank()) ? WaitShape.PENCHAN : WaitShape.RYANMEN;
                    out.add(new Standard(s.melds(), s.closedGroups(), s.pair(), Optional.of(ws)));
                } else if (winTile.matchesSuitRank(high)) {
                    // PENCHAN when the formed sequence starts at 1 (e.g. winning 3 to complete 1-2-3).
                    WaitShape ws = (low.rank() == 1) ? WaitShape.PENCHAN : WaitShape.RYANMEN;
                    out.add(new Standard(s.melds(), s.closedGroups(), s.pair(), Optional.of(ws)));
                }
            }
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Standard decomposition (private)

    private static void decomposeStandard(List<TheMahjongTile> sorted, List<TheMahjongMeld> melds,
                                           int closedNeeded, List<HandShape> out) {
        for (int i = 0; i < sorted.size(); i++) {
            TheMahjongTile pairCandidate = sorted.get(i);
            if (i > 0 && sorted.get(i - 1).matchesSuitRank(pairCandidate)) continue;

            boolean hasSecond = false;
            for (int j = i + 1; j < sorted.size(); j++) {
                if (sorted.get(j).matchesSuitRank(pairCandidate)) { hasSecond = true; break; }
            }
            if (!hasSecond) continue;

            List<TheMahjongTile> remaining = new ArrayList<>(sorted);
            removeFirstBySuitRank(remaining, pairCandidate);
            removeFirstBySuitRank(remaining, pairCandidate);

            List<List<ConcealedGroup>> groupDecomps = new ArrayList<>();
            decomposeGroups(remaining, closedNeeded, groupDecomps, new ArrayList<>());
            for (List<ConcealedGroup> groups : groupDecomps) {
                out.add(new Standard(melds, groups, pairCandidate));
            }
        }
    }

    /**
     * Recursively decomposes {@code tiles} into exactly {@code needed} concealed groups.
     * Always processes the leftmost tile first — in a sorted list, the leftmost tile can
     * only be the start of any group it belongs to, which guarantees correctness.
     */
    private static void decomposeGroups(List<TheMahjongTile> tiles, int needed,
                                         List<List<ConcealedGroup>> out,
                                         List<ConcealedGroup> current) {
        if (needed == 0) {
            if (tiles.isEmpty()) out.add(new ArrayList<>(current));
            return;
        }
        if (tiles.isEmpty()) return;

        TheMahjongTile first = tiles.get(0);

        // Try triplet
        if (countBySuitRank(tiles, first) >= 3) {
            List<TheMahjongTile> rem = new ArrayList<>(tiles);
            List<TheMahjongTile> trip = new ArrayList<>();
            for (int i = 0; i < 3; i++) trip.add(removeFirstBySuitRank(rem, first));
            current.add(new ConcealedGroup.Triplet(trip));
            decomposeGroups(rem, needed - 1, out, current);
            current.remove(current.size() - 1);
        }

        // Try sequence (number suits only; `first` must be the lowest of the three)
        TheMahjongTile.Suit suit = first.suit();
        if (suit.isNumber() && first.rank() <= suit.maxRank() - 2) {
            TheMahjongTile mid = findFirstBySuitRank(tiles, suit, first.rank() + 1);
            TheMahjongTile end = findFirstBySuitRank(tiles, suit, first.rank() + 2);
            if (mid != null && end != null) {
                List<TheMahjongTile> rem = new ArrayList<>(tiles);
                TheMahjongTile t1 = removeFirstBySuitRank(rem, first);
                TheMahjongTile t2 = removeFirstBySuitRank(rem, mid);
                TheMahjongTile t3 = removeFirstBySuitRank(rem, end);
                current.add(new ConcealedGroup.Sequence(List.of(t1, t2, t3)));
                decomposeGroups(rem, needed - 1, out, current);
                current.remove(current.size() - 1);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Shared helpers (private)

    private static List<TheMahjongTile> sort(List<TheMahjongTile> tiles) {
        return tiles.stream()
                .sorted(Comparator.comparingInt((TheMahjongTile t) -> t.suit().ordinal())
                                  .thenComparingInt(TheMahjongTile::rank))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static long suitRankKey(TheMahjongTile t) {
        return suitRankKey(t.suit(), t.rank());
    }

    private static long suitRankKey(TheMahjongTile.Suit suit, int rank) {
        return (long) suit.ordinal() * 100 + rank;
    }

    private static int countBySuitRank(List<TheMahjongTile> tiles, TheMahjongTile ref) {
        int count = 0;
        for (TheMahjongTile t : tiles) if (t.matchesSuitRank(ref)) count++;
        return count;
    }

    private static Map<Long, Integer> countsBySuitRank(List<TheMahjongTile> tiles) {
        Map<Long, Integer> map = new HashMap<>();
        for (TheMahjongTile t : tiles) map.merge(suitRankKey(t), 1, Integer::sum);
        return map;
    }

    private static TheMahjongTile findFirstBySuitRank(List<TheMahjongTile> tiles,
                                                       TheMahjongTile.Suit suit, int rank) {
        for (TheMahjongTile t : tiles) if (t.suit() == suit && t.rank() == rank) return t;
        return null;
    }

    private static TheMahjongTile removeFirstBySuitRank(List<TheMahjongTile> tiles, TheMahjongTile ref) {
        for (int i = 0; i < tiles.size(); i++) {
            if (tiles.get(i).matchesSuitRank(ref)) return tiles.remove(i);
        }
        throw new IllegalStateException("Tile not found in list: " + ref);
    }
}
