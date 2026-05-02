package com.mahjongcore.hands;

import com.mahjongcore.tile.Tile;

/**
 * Abstract base for all meld types: shuntsu, kotsu, kantsu, and toitsu.
 */
public abstract class Mentsu {
    protected Tile identifierTile;

    protected boolean isMentsu;

    /** true for open (called) melds; false for concealed melds, except ankan */
    protected boolean isOpen;

    /**
     * For shuntsu, this is the middle tile of the sequence.
     *
     * @return the identifying tile of this meld
     */
    public Tile getTile() {
        return identifierTile;
    }

    public boolean isMentsu() {
        return isMentsu;
    }

    /**
     * Used for kuisagari (open-hand han reduction). True if open, false for ankan.
     *
     * @return whether the meld was called (open)
     */
    public boolean isOpen() {
        return isOpen;
    }

    /**
     * @return fu bonus contributed by this meld
     */
    public abstract int getFu();
}
