package com.mahjongcore.yaku.normals;

/**
 * Interface for checking a single yaku. Win detection is handled separately.
 */
public interface NormalYakuResolver {

    /**
     * Call isMatch() first to check eligibility.
     *
     * @return the yaku enum for this resolver
     */
    NormalYaku getNormalYaku();

    /**
     * @return true if this yaku applies
     */
    boolean isMatch();
}
