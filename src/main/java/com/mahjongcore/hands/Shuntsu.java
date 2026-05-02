package com.mahjongcore.hands;

import com.mahjongcore.MahjongIllegalShuntsuIdentifierException;
import com.mahjongcore.tile.Tile;

/**
 * Handles a sequence (shuntsu); supports both open and closed variants.
 */
public class Shuntsu extends Mentsu {

    /**
     * Use when the sequence is already validated.
     *
     * @param isOpen         true for open, false for closed
     * @param identifierTile the middle tile of the sequence
     */
    public Shuntsu(boolean isOpen, Tile identifierTile) throws MahjongIllegalShuntsuIdentifierException {
        setIdentifierTile(identifierTile);
        this.isOpen = isOpen;
        this.isMentsu = true;
    }

    /**
     * Validates and creates a shuntsu from three tiles.
     *
     * @param isOpen true for open, false for closed
     * @param tile1  first tile
     * @param tile2  second tile
     * @param tile3  third tile
     */
    public Shuntsu(boolean isOpen, Tile tile1, Tile tile2, Tile tile3) {
        this.isOpen = isOpen;

        // TODO: deduplicate sort logic with check()
        Tile s;
        if (tile1.getNumber() > tile2.getNumber()) {
            s = tile1;
            tile1 = tile2;
            tile2 = s;
        }
        if (tile1.getNumber() > tile3.getNumber()) {
            s = tile1;
            tile1 = tile3;
            tile3 = s;
        }
        if (tile2.getNumber() > tile3.getNumber()) {
            s = tile2;
            tile2 = tile3;
            tile3 = s;
        }
        if (this.isMentsu = check(tile1, tile2, tile3)) {
            identifierTile = tile2;
        }
    }

    /**
     * @return true if the three tiles form a valid sequence
     */
    public static boolean check(Tile tile1, Tile tile2, Tile tile3) {

        if (tile1.getType() != tile2.getType() || tile2.getType() != tile3.getType()) {
            return false;
        }

        if (tile1.getNumber() == 0 || tile2.getNumber() == 0 || tile3.getNumber() == 0) {
            return false;
        }

        Tile s;
        if (tile1.getNumber() > tile2.getNumber()) {
            s = tile1;
            tile1 = tile2;
            tile2 = s;
        }
        if (tile1.getNumber() > tile3.getNumber()) {
            s = tile1;
            tile1 = tile3;
            tile3 = s;
        }
        if (tile2.getNumber() > tile3.getNumber()) {
            s = tile2;
            tile2 = tile3;
            tile3 = s;
        }

        return tile1.getNumber() + 1 == tile2.getNumber() && tile2.getNumber() + 1 == tile3.getNumber();
    }

    private void setIdentifierTile(Tile identifierTile) throws MahjongIllegalShuntsuIdentifierException {
        if (identifierTile.isYaochu()) {
            throw new MahjongIllegalShuntsuIdentifierException(identifierTile);
        }
        this.identifierTile = identifierTile;
    }

    @Override
    public int getFu() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Shuntsu)) return false;

        Shuntsu shuntsu = (Shuntsu) o;

        if (isMentsu != shuntsu.isMentsu) return false;
        if (isOpen != shuntsu.isOpen) return false;
        return identifierTile == shuntsu.identifierTile;

    }

    @Override
    public int hashCode() {
        int result = identifierTile != null ? identifierTile.hashCode() : 0;
        result = 31 * result + (isMentsu ? 1 : 0);
        result = 31 * result + (isOpen ? 1 : 0);
        return result;
    }
}
