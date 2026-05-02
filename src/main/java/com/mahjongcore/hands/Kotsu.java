package com.mahjongcore.hands;

import com.mahjongcore.tile.Tile;

/**
 * Handles a triplet (kotsu); supports both open (minko) and closed (anko) variants.
 */
public class Kotsu extends Mentsu {

    /**
     * Use when the triplet is already validated.
     *
     * @param isOpen         true for open (minko), false for closed (anko)
     * @param identifierTile the tile this triplet is made of
     */
    public Kotsu(boolean isOpen, Tile identifierTile) {
        this.identifierTile = identifierTile;
        this.isOpen = isOpen;
        this.isMentsu = true;
    }

    /**
     * Validates the triplet: isMentsu is true if all three tiles match.
     *
     * @param isOpen true for open (minko), false for closed (anko)
     * @param tile1  first tile
     * @param tile2  second tile
     * @param tile3  third tile
     */
    public Kotsu(boolean isOpen, Tile tile1, Tile tile2, Tile tile3) {
        this.isOpen = isOpen;
        if (this.isMentsu = check(tile1, tile2, tile3)) {
            identifierTile = tile1;
        }
    }

    /**
     * @return true if all three tiles are identical
     */
    public static boolean check(Tile tile1, Tile tile2, Tile tile3) {
        return tile1 == tile2 && tile2 == tile3;
    }

    @Override
    public int getFu() {
        int mentsuFu = 2;
        if (!isOpen) {
            mentsuFu *= 2;
        }
        if (identifierTile.isYaochu()) {
            mentsuFu *= 2;
        }
        return mentsuFu;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Kotsu)) return false;

        Kotsu kotsu = (Kotsu) o;

        if (isMentsu != kotsu.isMentsu) return false;
        if (isOpen != kotsu.isOpen) return false;
        return identifierTile == kotsu.identifierTile;

    }

    @Override
    public int hashCode() {
        int result = identifierTile != null ? identifierTile.hashCode() : 0;
        result = 31 * result + (isMentsu ? 1 : 0);
        result = 31 * result + (isOpen ? 1 : 0);
        return result;
    }
}
