package com.mahjongcore.hands;

import com.mahjongcore.tile.Tile;

/**
 * Handles a kan (quad); supports both open (minkan) and closed (ankan) variants.
 */
public class Kantsu extends Mentsu {

    /**
     * Assumes the kan is already complete; does not validate.
     *
     * @param isOpen         true for open (minkan), false for closed (ankan)
     * @param identifierTile the tile this kan is made of
     */
    public Kantsu(boolean isOpen, Tile identifierTile) {
        this.isOpen = isOpen;
        this.identifierTile = identifierTile;
        this.isMentsu = true;
    }

    /**
     * Validates the kan: isMentsu is true if all four tiles match.
     *
     * @param isOpen true for open (minkan), false for closed (ankan)
     * @param tile1  first tile
     * @param tile2  second tile
     * @param tile3  third tile
     * @param tile4  fourth tile
     */
    public Kantsu(boolean isOpen, Tile tile1, Tile tile2, Tile tile3, Tile tile4) {
        this.isOpen = isOpen;
        if (this.isMentsu = check(tile1, tile2, tile3, tile4)) {
            identifierTile = tile1;
        }
    }

    /**
     * @return true if all four tiles are identical
     */
    public static boolean check(Tile tile1, Tile tile2, Tile tile3, Tile tile4) {
        return tile1 == tile2 && tile2 == tile3 && tile3 == tile4;
    }

    @Override
    public int getFu() {
        int mentsuFu = 8;
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
        if (!(o instanceof Kantsu)) return false;

        Kantsu kantsu = (Kantsu) o;

        if (isMentsu != kantsu.isMentsu) return false;
        if (isOpen != kantsu.isOpen) return false;
        return identifierTile == kantsu.identifierTile;

    }

    @Override
    public int hashCode() {
        int result = identifierTile != null ? identifierTile.hashCode() : 0;
        result = 31 * result + (isMentsu ? 1 : 0);
        result = 31 * result + (isOpen ? 1 : 0);
        return result;
    }
}
