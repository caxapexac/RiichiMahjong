package com.mahjongcore.hands;

import com.mahjongcore.MahjongTileOverFlowException;
import com.mahjongcore.tile.Tile;

import java.util.ArrayList;
import java.util.List;

public class Toitsu extends Mentsu {

    /**
     * Use when the pair is already validated.
     *
     * @param identifierTile the tile this pair is made of
     */
    public Toitsu(Tile identifierTile) {
        this.identifierTile = identifierTile;
        this.isMentsu = true;
    }

    /**
     * Validates and creates a toitsu from two tiles.
     */
    public Toitsu(Tile tile1, Tile tile2) {
        if (this.isMentsu = Toitsu.check(tile1, tile2)) {
            this.identifierTile = tile1;
        }
    }

    /**
     * @return true if both tiles match
     */
    public static boolean check(Tile tile1, Tile tile2) {
        return tile1 == tile2;
    }

    /**
     * Returns all tiles that can form a pair, as jantou candidates.
     *
     * @param tiles tile count array
     * @return list of jantou candidate pairs
     */
    public static List<Toitsu> findJantoCandidate(int[] tiles) throws MahjongTileOverFlowException {
        List<Toitsu> result = new ArrayList<>(7);
        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] > 4) {
                throw new MahjongTileOverFlowException(i, tiles[i]);
            }
            if (tiles[i] >= 2) {
                result.add(new Toitsu(Tile.valueOf(i)));
            }
        }
        return result;
    }

    @Override
    public int getFu() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Toitsu)) return false;

        Toitsu toitsu = (Toitsu) o;

        if (isMentsu != toitsu.isMentsu) return false;
        return identifierTile == toitsu.identifierTile;

    }

    @Override
    public int hashCode() {
        int result = identifierTile != null ? identifierTile.hashCode() : 0;
        result = 31 * result + (isMentsu ? 1 : 0);
        return result;
    }
}
