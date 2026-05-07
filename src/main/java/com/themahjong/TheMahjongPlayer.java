package com.themahjong;

import java.util.List;
import java.util.Objects;

public final class TheMahjongPlayer {

    public enum RiichiState { NONE, RIICHI, DOUBLE_RIICHI }

    private final int points;
    private final RiichiState riichiState;
    private final boolean ippatsuEligible;
    private final List<TheMahjongTile> currentHand;
    private final List<TheMahjongMeld> melds;
    private final List<TheMahjongDiscard> discards;
    private final List<TheMahjongTile> temporaryFuritenTiles;
    /**
     * Set when this player declines a ron opportunity while in riichi. Once set, the
     * player cannot ron for the remainder of the round regardless of subsequent draws.
     * Always false outside the riichi-furiten rule path.
     */
    private final boolean riichiPermanentFuriten;
    /**
     * Number of North tiles this player has set aside via {@link TheMahjongRound#declareKita}
     * (sanma only; always 0 in 4-player). Each kita counts as +1 dora when
     * {@code TheMahjongRuleSet.kitaCountsAsDora()} is true.
     */
    private final int kitaCount;

    /**
     * Backward-compatible constructor: defaults {@code riichiPermanentFuriten} to false
     * and {@code kitaCount} to 0.
     */
    public TheMahjongPlayer(
            int points,
            RiichiState riichiState,
            boolean ippatsuEligible,
            List<TheMahjongTile> currentHand,
            List<TheMahjongMeld> melds,
            List<TheMahjongDiscard> discards,
            List<TheMahjongTile> temporaryFuritenTiles) {
        this(points, riichiState, ippatsuEligible,
                currentHand, melds, discards, temporaryFuritenTiles, false, 0);
    }

    /** Backward-compatible constructor: defaults {@code kitaCount} to 0. */
    public TheMahjongPlayer(
            int points,
            RiichiState riichiState,
            boolean ippatsuEligible,
            List<TheMahjongTile> currentHand,
            List<TheMahjongMeld> melds,
            List<TheMahjongDiscard> discards,
            List<TheMahjongTile> temporaryFuritenTiles,
            boolean riichiPermanentFuriten) {
        this(points, riichiState, ippatsuEligible,
                currentHand, melds, discards, temporaryFuritenTiles, riichiPermanentFuriten, 0);
    }

    public TheMahjongPlayer(
            int points,
            RiichiState riichiState,
            boolean ippatsuEligible,
            List<TheMahjongTile> currentHand,
            List<TheMahjongMeld> melds,
            List<TheMahjongDiscard> discards,
            List<TheMahjongTile> temporaryFuritenTiles,
            boolean riichiPermanentFuriten,
            int kitaCount) {
        if (riichiState == null) {
            throw new IllegalArgumentException("riichiState cannot be null");
        }
        if (currentHand == null) {
            throw new IllegalArgumentException("currentHand cannot be null");
        }
        if (melds == null) {
            throw new IllegalArgumentException("melds cannot be null");
        }
        if (discards == null) {
            throw new IllegalArgumentException("discards cannot be null");
        }
        if (temporaryFuritenTiles == null) {
            throw new IllegalArgumentException("temporaryFuritenTiles cannot be null");
        }
        if (kitaCount < 0) {
            throw new IllegalArgumentException("kitaCount cannot be negative");
        }

        this.points = points;
        this.riichiState = riichiState;
        this.ippatsuEligible = ippatsuEligible;
        this.currentHand = List.copyOf(currentHand);
        this.melds = List.copyOf(melds);
        this.discards = List.copyOf(discards);
        this.temporaryFuritenTiles = List.copyOf(temporaryFuritenTiles);
        this.riichiPermanentFuriten = riichiPermanentFuriten;
        this.kitaCount = kitaCount;
    }

    public static TheMahjongPlayer initial(int points) {
        return new TheMahjongPlayer(
                points,
                RiichiState.NONE,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                0);
    }

    public int points() {
        return points;
    }

    public RiichiState riichiState() {
        return riichiState;
    }

    public boolean riichi() {
        return riichiState == RiichiState.RIICHI || riichiState == RiichiState.DOUBLE_RIICHI;
    }

    public boolean doubleRiichi() {
        return riichiState == RiichiState.DOUBLE_RIICHI;
    }

    public boolean ippatsuEligible() {
        return ippatsuEligible;
    }

    public boolean handOpen() {
        return melds.stream().anyMatch(TheMahjongMeld::open);
    }

    public List<TheMahjongTile> currentHand() {
        return currentHand;
    }

    public List<TheMahjongMeld> melds() {
        return melds;
    }

    public List<TheMahjongDiscard> discards() {
        return discards;
    }

    public List<TheMahjongTile> temporaryFuritenTiles() {
        return temporaryFuritenTiles;
    }

    public boolean temporaryFuriten() {
        return !temporaryFuritenTiles.isEmpty();
    }

    public boolean riichiPermanentFuriten() {
        return riichiPermanentFuriten;
    }

    public int kitaCount() {
        return kitaCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TheMahjongPlayer other)) return false;
        return points == other.points
                && ippatsuEligible == other.ippatsuEligible
                && riichiPermanentFuriten == other.riichiPermanentFuriten
                && kitaCount == other.kitaCount
                && riichiState == other.riichiState
                && currentHand.equals(other.currentHand)
                && melds.equals(other.melds)
                && discards.equals(other.discards)
                && temporaryFuritenTiles.equals(other.temporaryFuritenTiles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(points, riichiState, ippatsuEligible,
                currentHand, melds, discards, temporaryFuritenTiles, riichiPermanentFuriten, kitaCount);
    }

    @Override
    public String toString() {
        return "TheMahjongPlayer{points=" + points
                + ", riichiState=" + riichiState
                + ", ippatsuEligible=" + ippatsuEligible
                + ", hand=" + currentHand.size() + " tiles"
                + ", melds=" + melds.size()
                + ", discards=" + discards.size()
                + ", tempFuriten=" + temporaryFuriten()
                + ", riichiPermFuriten=" + riichiPermanentFuriten
                + ", kita=" + kitaCount
                + '}';
    }

}
