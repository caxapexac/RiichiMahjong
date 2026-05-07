package com.riichimahjongforge.mahjongsolitaire;

import java.util.List;

/**
 * One parsed mahjong-solitaire board: ordered list of {@link Slot}s plus a bounding
 * box for centring on the table. Slot coordinates are in the boards.json grid
 * (X/Z step 2, Y is the layer index, 0 = bottom). Each tile occupies a 2×2 cell
 * footprint at its layer.
 *
 * @param id stable id from boards.json
 * @param name display name
 * @param category display category (decorative; not used for rules)
 * @param slots all tile slots in the board, in source order
 * @param minX inclusive bounds, in grid units
 * @param maxX inclusive bounds (rightmost cell occupied — note tiles are 2 wide so
 *             the visual maxX is {@code maxX + 1})
 * @param minZ inclusive bounds
 * @param maxZ inclusive bounds (analogous to maxX)
 * @param maxLayer highest layer index used (0-based)
 */
public record MahjongSolitaireBoard(
        String id,
        String name,
        String category,
        List<Slot> slots,
        int minX, int maxX, int minZ, int maxZ, int maxLayer) {

    /** Tile footprint width / depth in grid units. */
    public static final int TILE_GRID = 2;

    /** A single slot — position only. Tile assignment is BE state, not board state. */
    public record Slot(int x, int y, int z) {}

    public int width()  { return maxX - minX + TILE_GRID; }
    public int depth()  { return maxZ - minZ + TILE_GRID; }
    public int layers() { return maxLayer + 1; }
}
