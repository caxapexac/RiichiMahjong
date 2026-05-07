package com.riichimahjongforge.mahjongsolitaire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.riichimahjongforge.mahjongcore.MahjongTileItems;
import com.riichimahjongforge.RiichiMahjongForgeMod;
import com.riichimahjongforge.common.BaseMultipartBlock;
import com.riichimahjongforge.cuterenderer.BlockModelNode;
import com.riichimahjongforge.cuterenderer.CuteNode;
import com.riichimahjongforge.cuterenderer.CuteRenderer;
import com.riichimahjongforge.cuterenderer.HoverHighlightRenderer;
import com.riichimahjongforge.cuterenderer.InteractKey;
import com.riichimahjongforge.cuterenderer.Interactive;
import com.riichimahjongforge.cuterenderer.WorldButtonNode;
import com.riichimahjongforge.mahjongsolitaire.MahjongSolitaireBlockEntity;
import com.riichimahjongforge.mahjongsolitaire.MahjongSolitaireBoard;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.slf4j.Logger;

/**
 * Tabletop renderer for the solitaire — built entirely on top of {@link CuteRenderer}.
 *
 * <p>State is rebuilt declaratively each frame from the BE's tile array. We keep one
 * {@link BlockModelNode} per stable tile id; nodes whose tile has been removed are
 * dropped from the scene, and freedom is recomputed every frame so newly-freed tiles
 * become hoverable. Per-player selection is purely a render-time concern: the local
 * player's selected tile gets an orange wireframe outline (drawn separately from the
 * black hover outline so both can stack).
 *
 * <p>Geometry: boards are sized by their largest grid extent and centred on the
 * master cell. Tile size and grid spacing are derived from the tile model's native
 * dimensions so the rendered tiles touch cleanly without gaps or overlap.
 */
public final class MahjongSolitaireRenderer
        implements BlockEntityRenderer<MahjongSolitaireBlockEntity> {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Wireframe colour for the local player's selected tile — distinct from hover (black). */
    private static final float SELECTION_R = 1.00f;
    private static final float SELECTION_G = 0.55f;
    private static final float SELECTION_B = 0.10f;
    private static final float SELECTION_A = 0.85f;

    /** Height of the table top in master-local coords (model y=1px = 1/16). */
    private static final double TABLETOP_TOP_Y = 1.0 / 16.0;
    /**
     * Width of one boards.json X-grid-unit, world units. Tile = 2 X-grid-units →
     * tile width = 2 × GRID_UNIT_WORLD_X. The Z-grid-unit is derived from the
     * tile's native aspect ratio (see {@link #GRID_UNIT_WORLD_Z}) so the tile
     * keeps its real proportions while still touching neighbours cleanly in Z.
     */
    private static final double GRID_UNIT_WORLD_X = 1.0 / 17.0;
    /**
     * Native tile model bounds (pre-rotation), blocks: 11×14×9 px = front-facing
     * upright tile. We rotate +90° around X to lay it flat face-up; after rotation
     * the footprint is 11 wide × 14 deep × 9 tall.
     */
    private static final double TILE_NATIVE_WIDTH  = 11.0 / 16.0;
    private static final double TILE_NATIVE_DEPTH  = 14.0 / 16.0;
    private static final double TILE_NATIVE_HEIGHT =  9.0 / 16.0;
    /**
     * Uniform tile scale: chosen so the rendered tile width equals two X-grid-units.
     * Scale is uniform so the model's native 11:14:9 proportions are preserved.
     */
    private static final double TILE_SCALE =
            GRID_UNIT_WORLD_X * MahjongSolitaireBoard.TILE_GRID / TILE_NATIVE_WIDTH;
    /**
     * Z-grid-unit derived so two grid units exactly match the tile's native depth at
     * the same uniform scale. This is asymmetric vs. {@link #GRID_UNIT_WORLD_X}
     * because the tile is elongated (14 depth vs 11 width); using equal grid
     * steps would either gap or squish.
     */
    private static final double GRID_UNIT_WORLD_Z =
            TILE_NATIVE_DEPTH * TILE_SCALE / MahjongSolitaireBoard.TILE_GRID;

    /** Cream-coloured 0.3-scale dust particle — small enough not to cover the tabletop view. */
    private static final DustParticleOptions TILE_DUST =
            new DustParticleOptions(new Vector3f(0.95f, 0.92f, 0.78f), 0.3f);

    @Nullable private CuteRenderer cute;
    @Nullable private MahjongSolitaireBlockEntity boundBE;
    /** tile-instance id → its node. Keyed by id so a tile that moves to a new
     *  slot during a shuffle keeps the same node, and {@code transform.targetPos}
     *  glides it to the new slot's canonical position. */
    private final Map<Integer, BlockModelNode> nodes = new HashMap<>();
    /** slot index → canonical (rest) centroid; recomputed on board rebuild. */
    private final Map<Integer, SlotPos> canonicalPos = new HashMap<>();
    @Nullable private String lastBoardId;
    private int lastSlotCount = -1;
    private long lastRandomSeed = 0L;
    private boolean haveRebuiltOnce = false;

    public MahjongSolitaireRenderer(BlockEntityRendererProvider.Context ctx) {
        // ctx unused — we build everything from BE state and CuteRenderer.
    }

    @Override
    public void render(MahjongSolitaireBlockEntity be, float partialTick,
                       PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (be.getLevel() == null) return;
        ensureBound(be);

        MahjongSolitaireBoard board = be.board();
        if (board == null) {
            cute.frame(pose, buffers, packedLight, packedOverlay, partialTick);
            return;
        }

        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(BaseMultipartBlock.FACING)
                ? state.getValue(BaseMultipartBlock.FACING)
                : Direction.NORTH;
        cute.setFacing(facing);
        rebuildSceneIfBoardChanged(be, board);
        syncTilesToBoardState(be, board);

        cute.frame(pose, buffers, packedLight, packedOverlay, partialTick);
        drawSelectionHighlight(be, pose, buffers);
    }

    /** Orange wireframe around the local player's selected tile, visible to them only. */
    private void drawSelectionHighlight(MahjongSolitaireBlockEntity be, PoseStack pose, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int selectedSlot = be.selectionFor(mc.player.getUUID());
        if (selectedSlot < 0) return;
        int tileId = be.tileIdAt(selectedSlot);
        if (tileId < 0) return;
        BlockModelNode node = nodes.get(tileId);
        if (node == null) return;
        // Distinct colour from hover (black) so a hover-over-selected reads
        // both highlights stacked.
        HoverHighlightRenderer.drawNodeOutline(pose, buffers, node,
                SELECTION_R, SELECTION_G, SELECTION_B, SELECTION_A);
    }

    private void ensureBound(MahjongSolitaireBlockEntity be) {
        if (boundBE == be && cute != null) return;
        if (cute != null) cute.detach();
        cute = new CuteRenderer(be.getLevel().dimension(), be.getBlockPos());
        cute.setHoverLift(0.04f);
        cute.setHoverSound(RiichiMahjongForgeMod.TILE_HOVER_TILE_SOUND.get(), 0.55f, 1.0f);
        cute.attach();
        boundBE = be;
        nodes.clear();
        lastBoardId = null;
        lastSlotCount = -1;
        lastRandomSeed = 0L;
        haveRebuiltOnce = false;
        addHintButton();
    }

    private void rebuildSceneIfBoardChanged(MahjongSolitaireBlockEntity be, MahjongSolitaireBoard board) {
        long seed = be.randomSeed();
        if (haveRebuiltOnce
                && board.id().equals(lastBoardId)
                && board.slots().size() == lastSlotCount
                && seed == lastRandomSeed) {
            return;
        }
        // Board changed (reset, re-deal of same board id, etc) — drop existing
        // tile nodes and rebuild canonical slot positions. Tile nodes themselves
        // are respawned lazily in syncTilesToBoardState so we don't have to
        // duplicate that logic. Non-tile children (hint button) are kept since
        // they don't depend on the board.
        for (BlockModelNode tile : nodes.values()) cute.root().removeChild(tile);
        nodes.clear();
        canonicalPos.clear();
        lastBoardId = board.id();
        lastSlotCount = board.slots().size();
        lastRandomSeed = seed;
        haveRebuiltOnce = true;

        float scale = (float) TILE_SCALE;
        double layerHeight = TILE_NATIVE_HEIGHT * scale;

        double originX = 0.5 - board.width() * GRID_UNIT_WORLD_X * 0.5;
        double originZ = 0.5 - board.depth() * GRID_UNIT_WORLD_Z * 0.5;

        for (int i = 0; i < board.slots().size(); i++) {
            canonicalPos.put(i, computeCentroidPos(board.slots().get(i), board, layerHeight, originX, originZ));
        }
    }

    /**
     * Hint button anchored on the north edge of the master cell (X-centred,
     * just inside the north face), lying flat on the tabletop and oriented for
     * a viewer standing to the north. Clicking it sends {@code InteractKey.Named("HINT")}.
     */
    private void addHintButton() {
        WorldButtonNode btn = new WorldButtonNode(Component.translatable(
                "riichi_mahjong_forge.solitaire.button.hint"))
                .setTextColor(0xFFFFE066)
                .setBgColor(0xC0202020)
                .setTextScale(0.012f)
                .makeClickable(new InteractKey.Named("HINT"));
        btn.transform.layFlatReadableFrom(Direction.NORTH);
        btn.transform.setPos(0.5, TABLETOP_TOP_Y + 0.001, -0.35);
        cute.root().addChild(btn);
    }

    private void syncTilesToBoardState(MahjongSolitaireBlockEntity be, MahjongSolitaireBoard board) {
        // Walk slots: spawn a node per new tile id, glide existing nodes if their
        // slot changed (shuffle), refresh Interactive every frame so free-ness
        // and slot index stay current. Allocates a HashSet + per-tile Interactive
        // per frame — fine at solitaire's tile counts; revisit if profiled.
        HashSet<Integer> presentIds = new HashSet<>();
        float scale = (float) TILE_SCALE;
        for (int slot = 0; slot < be.slotCount(); slot++) {
            com.themahjong.TheMahjongTile tile = be.tileAt(slot);
            if (tile == null) continue;
            int id = be.tileIdAt(slot);
            if (id < 0) continue;
            presentIds.add(id);

            SlotPos rest = canonicalPos.get(slot);
            if (rest == null) continue;

            BlockModelNode node = nodes.get(id);
            boolean fresh = node == null;
            if (fresh) {
                node = makeTileNode(tile, scale);
                nodes.put(id, node);
                cute.root().addChild(node);
            }
            // Centroid → node-origin compensation: under the layFlatReadableFrom
            // rotation the model's local Y axis maps to world Z, so a node origin
            // placed at the centroid target shifts the visible centre along Z by
            // (yMid_local × scale). Subtract it so the visible centroid lands on
            // the slot. Pulled from naturalLocalAabb() so any tile.json edit just
            // works. X-mid and Z-mid are zero for our tile so they need no shift.
            var aabb = node.naturalLocalAabb();
            double yMidLocal = (aabb.minY + aabb.maxY) * 0.5;
            double pz = rest.z() - yMidLocal * scale;
            if (fresh) {
                node.transform.setPos(rest.x(), rest.y(), pz);
            } else {
                node.transform.targetPos(rest.x(), rest.y(), pz);
            }
            boolean free = be.isFree(slot);
            node.setInteractive(free
                    ? Interactive.button(new InteractKey.Slot(slot), aabb)
                    : null);
        }
        // Drop nodes whose tile-id is no longer present. Spawn a small sparkle
        // at each removed tile's last visible position before deleting it.
        nodes.entrySet().removeIf(entry -> {
            if (!presentIds.contains(entry.getKey())) {
                spawnTileRemovalSparkle(be, entry.getValue());
                cute.root().removeChild(entry.getValue());
                return true;
            }
            return false;
        });
    }

    /**
     * Small dust burst at a removed tile's exact world centre, with random
     * outward velocity so the particles drift away from the spawn point.
     * Client-local — every viewer's renderer fires its own particles, so
     * server-broadcast isn't needed and per-tile precision is cheap. Uses
     * {@link CuteNode#worldBoundsOrNull} so FACING rotation and any in-flight
     * glide animation are honoured automatically.
     */
    private void spawnTileRemovalSparkle(MahjongSolitaireBlockEntity be, BlockModelNode node) {
        var box = node.worldBoundsOrNull();
        if (box == null || be.getLevel() == null) return;
        var level = be.getLevel();
        var master = be.getBlockPos();
        double cx = master.getX() + (box.minX + box.maxX) * 0.5;
        double cy = master.getY() + (box.minY + box.maxY) * 0.5;
        double cz = master.getZ() + (box.minZ + box.maxZ) * 0.5;
        for (int i = 0; i < 5; i++) {
            double vx = (level.random.nextDouble() - 0.5) * 0.08;
            double vy = level.random.nextDouble() * 0.05 + 0.01;
            double vz = (level.random.nextDouble() - 0.5) * 0.08;
            level.addParticle(TILE_DUST, cx, cy, cz, vx, vy, vz);
        }
    }

    private BlockModelNode makeTileNode(com.themahjong.TheMahjongTile tile, float scale) {
        Block tileBlock = MahjongTileItems.blockForTile(tile);
        BlockState state;
        if (tileBlock != null) {
            state = tileBlock.defaultBlockState();
        } else {
            LOGGER.warn("Solitaire: no tile block registered for tile {}; rendering STONE placeholder", tile);
            state = Blocks.STONE.defaultBlockState();
        }
        BlockModelNode node = new BlockModelNode(state);
        node.transform.setScale(scale);
        // The tile model is upright (artwork on the north face). Lay it flat
        // face-up; reading direction is moot for tiles so SOUTH (the unrotated
        // baseline = pure rotateX(+90°)) is fine.
        node.transform.layFlatReadableFrom(Direction.SOUTH);
        return node;
    }

    private SlotPos computeCentroidPos(MahjongSolitaireBoard.Slot slot, MahjongSolitaireBoard board,
                                       double layerHeight, double originX, double originZ) {
        double cx = originX + ((slot.x() - board.minX()) + MahjongSolitaireBoard.TILE_GRID * 0.5) * GRID_UNIT_WORLD_X;
        double cz = originZ + ((slot.z() - board.minZ()) + MahjongSolitaireBoard.TILE_GRID * 0.5) * GRID_UNIT_WORLD_Z;
        double cy = TABLETOP_TOP_Y + slot.y() * layerHeight + layerHeight * 0.5;
        return new SlotPos(cx, cy, cz);
    }

    /** Master-local centroid target for a slot (where the tile's visible centre should sit). */
    private record SlotPos(double x, double y, double z) {}
}
