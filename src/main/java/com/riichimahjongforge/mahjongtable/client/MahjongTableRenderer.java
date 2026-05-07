package com.riichimahjongforge.mahjongtable.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.riichimahjongforge.mahjongcore.MahjongTileItems;
import com.riichimahjongforge.RiichiMahjongForgeMod;
import com.riichimahjongforge.common.BaseMultipartBlock;
import com.riichimahjongforge.common.client.WorldSpaceTextRenderer;
import com.riichimahjongforge.cuterenderer.BlockModelNode;
import com.riichimahjongforge.cuterenderer.CuteNode;
import com.riichimahjongforge.cuterenderer.CuteRenderer;
import com.riichimahjongforge.cuterenderer.GroupNode;
import com.riichimahjongforge.cuterenderer.HoverHighlightRenderer;
import com.riichimahjongforge.cuterenderer.WorldButtonNode;
import com.riichimahjongforge.cuterenderer.editor.CuteEditor;
import com.riichimahjongforge.cuterenderer.editor.LayoutEntry;
import com.riichimahjongforge.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjongforge.mahjongtable.MahjongTableBlock;
import com.riichimahjongforge.mahjongcore.MahjongWinEffects;
import com.riichimahjongforge.mahjongtable.MahjongTableBlockEntity.ResultAnimStage;
import com.riichimahjongforge.mahjongtable.MahjongTableHumanPlayer;
import com.themahjong.TheMahjongMatch;
import com.themahjong.TheMahjongPlayer;
import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.TheMahjongDriver;
import com.themahjong.yaku.TenpaiChecker;
import com.themahjong.yaku.WinResult;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

/**
 * In-world renderer for the mahjong table. Two modes:
 *
 * <ul>
 *   <li><b>Live</b> — renders real driver state: hand, wall, dead wall, river,
 *       melds, action buttons + ghost-tile previews, round-result banner.</li>
 *   <li><b>Editor preview</b> (F8) — every layout at max capacity with seat-
 *       keyed placeholders so anchor poses can be tuned with no live game.</li>
 * </ul>
 *
 * <p>Each layout is a {@link CuteEditor} anchor (pos/scale + per-layout
 * extras: spacing, count, pivot, rotation knobs).
 */
public class MahjongTableRenderer implements BlockEntityRenderer<MahjongTableBlockEntity> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final List<Direction> HORIZONTAL = List.of(
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    /** Indexed by seat number (0=East dealer, then CCW). */
    private static final String[] WIND_KEY_BY_SEAT = {
            "riichi_mahjong_forge.result.wind.east",
            "riichi_mahjong_forge.result.wind.south",
            "riichi_mahjong_forge.result.wind.west",
            "riichi_mahjong_forge.result.wind.north",
    };

    private static Component actionLabel(String key) {
        return Component.translatable("riichi_mahjong_forge.button.action." + key);
    }

    private static final WorldSpaceTextRenderer.Style STYLE_NORMAL =
            new WorldSpaceTextRenderer.Style(0.0105f, 1.10f, 0xE8E8E8, 0x80101010);
    private static final WorldSpaceTextRenderer.Style STYLE_TURN =
            new WorldSpaceTextRenderer.Style(0.0105f, 1.10f, 0xFFF0D040, 0x80101010);

    private static final double EDGE_PUSH = 1.01;
    private static final double TEXT_Y = 0.55;

    /** Single highlight colour shared by every "this tile is hot" outline:
     *  legal-to-discard tiles, the round's currently-active tile, etc. */
    private static final float HIGHLIGHT_R = 1.0f;
    private static final float HIGHLIGHT_G = 0.55f;
    private static final float HIGHLIGHT_B = 0.10f;
    private static final float HIGHLIGHT_A = 1.0f;

    /** Max-capacity counts used in editor-preview mode. */
    private static final int HAND_MAX_TILES   = 14;
    private static final int WALL_DEFAULT     = 17;     // tiles per side (one stack)
    private static final int RIVER_COLS       = 6;
    private static final int RIVER_ROWS       = 4;
    private static final int MELDS_PER_SEAT   = 4;
    private static final int TILES_PER_MELD   = 3;
    private static final int KITA_DEFAULT     = 4;

    /**
     * Anchor pos semantics across all layouts:
     * <ul>
     *   <li>{@code pos.x} = lateral offset along the seat's edge (sign aligned with line direction)</li>
     *   <li>{@code pos.y} = master-local Y of tile centres</li>
     *   <li>{@code pos.z} = outward distance from master cell centre (toward the seat)</li>
     * </ul>
     * {@code tileYaw / tilePitch / tileRoll} are extra per-tile rotations applied on
     * top of the seat-derived base rotation. {@code pivot} (0..1) shifts the layout
     * origin along the line: 0 = first tile at origin (grow forward), 1 = last
     * tile at origin (grow backward), 0.5 = centred.
     */
    private static final LayoutEntry HAND = CuteEditor.layout(
                    "hand",
                    new Vector3f(-0.44f, 0.65f, 0.85f), 0.10f)
            .floatField("spacing", 0.073f, 0f, 0.5f)
            .floatField("pivot",   0.0f,   0f, 1f)
            .floatField("tileYaw",   0f, -180f, 180f)
            .floatField("tilePitch", 0f, -180f, 180f)
            .floatField("tileRoll",  0f, -180f, 180f)
            .floatField("fill",      1f,    0f,   1f);

    /**
     * Live wall — face-down tile stacks along the seat's inner edge. Pre-deal
     * configuration is 17 stacks per side (dealer's side shortened to 10 by
     * the dead wall = 17+17+17+10 = 61 stacks = 122 tiles total live).
     *
     * <p>{@code dealtTiles} simulates the initial deal by removing tiles from
     * the live wall. WRC convention: drawing proceeds clockwise from the break
     * (on East/dealer's side), so tiles deplete in seat order 0 → 3 → 2 → 1.
     * Default 53 = standard deal (East 14, S/W/N 13 each), which leaves the
     * dealer's live wall empty, North's almost empty, and West/South untouched.
     * Set to 0 to see the pre-deal full live wall.
     */
    private static final LayoutEntry WALL = CuteEditor.layout(
                    "wall",
                    new Vector3f(-0.59f, 0.60f, 0.70f), 0.10f)
            .floatField("spacing",  0.073f, 0f, 0.5f)
            .floatField("pivot",    0.0f,   0f, 1f)
            .floatField("stackGap", 0.055f, 0f, 0.2f)
            .intField("count",       WALL_DEFAULT, 0, 36)
            .intField("stackHeight", 2, 1, 4)
            .intField("dealtTiles",  53, 0, 122)
            .floatField("tileYaw",   0f, -180f, 180f)
            .floatField("tilePitch", 0f, -180f, 180f)
            .floatField("tileRoll",  0f, -180f, 180f)
            .floatField("fill",      1f,    0f,   1f);

    /**
     * Dead wall — 7 stacks of 2 (= 14 tiles) sitting separately near the dealer's
     * right side. Rendered for seat 0 only. Tile flips per WRC §8.13:
     * <ul>
     *   <li><b>Initial dora indicator (D):</b> top tile of the 3rd stack from
     *       the rinshan end (i.e. stack {@code count - 3}, fixed by WRC §7.5).
     *       Always face-up.</li>
     *   <li><b>Kan dora indicators (K1..K4):</b> top tiles of the next
     *       {@code kanDoras} stacks toward the dora end. Face-up only when
     *       revealed (after a quad is declared).</li>
     *   <li><b>Ura dora (U0..U4):</b> when {@code uraDora=1}, the bottom tile of
     *       every active dora stack (initial + kan) flips face-up and slides
     *       {@code uraDoraOffset} along {@code +outward} so it pokes out from
     *       under the dora indicator. Matches the riichi convention of revealing
     *       ura at end-of-hand for a riichi winner.</li>
     * </ul>
     * Replacement tiles (R1-R4) at stacks 5-6 stay face-down. The dead wall always
     * holds 14 tiles per WRC: when a kan is declared, the last live wall tile
     * (L#) shifts into the dead wall to maintain count.
     */
    private static final LayoutEntry DEAD_WALL = CuteEditor.layout(
                    "deadWall",
                    new Vector3f(0.5900f, 0.6000f, 0.7000f), 0.1000f)
            .floatField("spacing",  0.073f, 0f, 0.5f)
            .floatField("pivot",    0.0f,   0f, 1f)
            .floatField("stackGap", 0.055f, 0f, 0.2f)
            .intField("count",        7,    0, 14)
            .intField("stackHeight",  2,    1, 4)
            .intField("kanDoras",     0,    0, 4)
            .intField("uraDora",      0,    0, 1)
            .intField("flipDir",      1,    0, 1)
            .intField("rinshanDrawn", 0,    0, 4)
            .floatField("uraDoraFraction", 0.95f, 0f, 1f)
            .floatField("tileYaw",   0f, -180f, 180f)
            .floatField("tilePitch", 0f, -180f, 180f)
            .floatField("tileRoll",  0f, -180f, 180f)
            .floatField("fill",      1f,    0f,   1f);

    /** River — discards grid in front of the seat. cols × rows tiles, lay-flat. */
    private static final LayoutEntry RIVER = CuteEditor.layout(
                    "river",
                    new Vector3f(0f, 0.59f, 0.26f), 0.10f)
            .floatField("spacing",    0.073f, 0f, 0.5f)
            .floatField("rowSpacing", 0.090f, 0f, 0.5f)
            .floatField("pivot",      0.5f,   0f, 1f)
            .intField("cols", RIVER_COLS, 0, 12)
            .intField("rows", RIVER_ROWS, 0, 8)
            .floatField("tileYaw",   0f, -180f, 180f)
            .floatField("tilePitch", 0f, -180f, 180f)
            .floatField("tileRoll",  0f, -180f, 180f)
            .floatField("fill",      1f,   0f,   1f);

    /** Melds — N meld-stacks stacked along the seat's right edge. Each stack = M tiles. */
    private static final LayoutEntry MELDS = CuteEditor.layout(
                    "melds",
                    new Vector3f(0.90f, 0.59f, 0.89f), 0.10f)
            .floatField("spacing", 0.073f, 0f, 0.5f)
            .floatField("meldGap", 0.020f, 0f, 0.2f)
            .floatField("pivot",   0.0f,   0f, 1f)
            .intField("meldCount",    MELDS_PER_SEAT, 0, 4)
            .intField("tilesPerMeld", 4,              0, 4)
            .intField("flipDir",      1,              0, 1)
            .floatField("tileYaw",   0f, -180f, 180f)
            .floatField("tilePitch", 0f, -180f, 180f)
            .floatField("tileRoll",  0f, -180f, 180f)
            .floatField("fill",      1f,   0f,   1f);

    /** Kita — line of removed-north tiles on the seat's left side. Sanma only in play, but rendered for all 4 in preview. */
    private static final LayoutEntry KITA = CuteEditor.layout(
                    "kita",
                    new Vector3f(-0.53f, 0.59f, 0.57f), 0.10f)
            .floatField("spacing", 0.073f, 0f, 0.5f)
            .floatField("pivot",   0.0f,   0f, 1f)
            .intField("count", KITA_DEFAULT, 0, 8)
            .floatField("tileYaw",   0f, -180f, 180f)
            .floatField("tilePitch", 0f, -180f, 180f)
            .floatField("tileRoll",  0f, -180f, 180f)
            .floatField("fill",      1f,   0f,   1f);

    /**
     * Action buttons grid (cols × rows). Cols extend along the seat edge
     * (lineDir); rows extend outward (in the table plane). Index order is
     * row-major. {@code rightPadPerTile} reserves empty plate space on the
     * right side of the text per ghost tile — drives plate width when
     * pairing buttons with {@link #BUTTON_GHOSTS}.
     */
    private static final LayoutEntry BUTTONS = CuteEditor.layout(
                    "buttons",
                    new Vector3f(0f, 0.71f, 0.70f), 0.007f)
            .floatField("colSpacing", 0.37f, 0f, 0.6f)
            .floatField("rowSpacing", 0.10f, 0f, 0.6f)
            .floatField("colPivot",   0.5f,  0f, 1f)
            .floatField("rowPivot",   0.0f,  0f, 1f)
            .intField("cols", 4, 1, 8)
            .intField("rows", 2, 1, 4)
            .floatField("tileYaw",     0f, -180f, 180f)
            .floatField("tilePitch",   0f, -180f, 180f)
            .floatField("tileRoll",  180f, -180f, 180f)
            // Reserved empty plate space on the right of the text, per ghost
            // tile (font px). 0 = symmetric plate, ghosts use BUTTON_GHOSTS'
            // outward-offset layout. >0 = plate extends right by
            // {@code rightPadPerTile * ghostCount}, ghosts inline inside it.
            .floatField("rightPadPerTile", 7f, 0f, 40f);

    /**
     * Ghost-tile preview row next to each action button. Treated as a
     * <em>relative</em> anchor: {@code pos.x} = extra lateral offset along
     * the seat edge from the button centre, {@code pos.y} = Y lift above the
     * button plate (so tiles don't z-fight), {@code pos.z} = offset toward
     * the table centre (-outward) so the row sits clear of the plate.
     * {@code scale} is the tile scale directly. Excluded from the triad
     * gizmo overlay since there's no single world position — the row repeats
     * per button.
     */
    private static final LayoutEntry BUTTON_GHOSTS = CuteEditor.layout(
                    "buttonGhosts",
                    new Vector3f(0.01f, 0.006f, 0f), 0.07f)
            .floatField("spacing",   0.05f, 0f, 0.3f)
            .floatField("tileYaw",   0f, -180f, 180f)
            .floatField("tilePitch", 0f, -180f, 180f)
            .floatField("tileRoll",  0f, -180f, 180f);

    private static final LayoutEntry[] ALL_ANCHORS = {HAND, WALL, DEAD_WALL, RIVER, MELDS, KITA, BUTTONS, BUTTON_GHOSTS};
    /** Anchors with a single master-relative world origin — these get triad
     *  gizmos in F8 mode. Relative anchors (e.g. {@link #BUTTON_GHOSTS}) are
     *  editable from the side panel but skip the triad. */
    private static final LayoutEntry[] WORLD_ANCHORS = {HAND, WALL, DEAD_WALL, RIVER, MELDS, KITA, BUTTONS};

    /** Palette + label config for one editor-preview button slot. Mirrors the
     *  legacy action chip palette so positioning is tuned against the real look. */
    private record PreviewBtn(Component label, int textColor, int bgColor, boolean pulsing) {}

    /** Stress-test claim window: every option a player could see at once when a
     *  discarded ⑤p simultaneously enables three CHI shapes (with/without aka),
     *  a PON/KAN, plus a winning RON (and a CHANKAN against an upgrade kan). */
    private static final PreviewBtn[] PREVIEW_BUTTONS = {
        new PreviewBtn(actionLabel("chi"),     0xFFFF8E8E, 0xB0444A58, false),
        new PreviewBtn(actionLabel("chi"),     0xFFE8E8E8, 0xB0444A58, false),
        new PreviewBtn(actionLabel("chi"),     0xFFE8E8E8, 0xB0444A58, false),
        new PreviewBtn(actionLabel("pon"),     0xFFE8E8E8, 0xB0444A58, false),
        new PreviewBtn(actionLabel("kan"),     0xFFE8E8E8, 0xB0444A58, false),
        new PreviewBtn(actionLabel("ron"),     0xFFFF5757, 0xC05A2222, true),
        new PreviewBtn(actionLabel("chankan"), 0xFFFF5757, 0xC05A2222, true),
        new PreviewBtn(actionLabel("pass"),    0xFFBFC6D2, 0xB0393D48, false),
    };

    /** Mock ghost-tile rows aligned with {@link #PREVIEW_BUTTONS} so the F8
     *  editor shows a representative preview alongside each placeholder button.
     *  Win declarations and PASS get no preview (matches live mode). */
    private static final List<List<TheMahjongTile>> PREVIEW_GHOSTS = List.of(
        List.of(  // CHI 4-5+aka — 4s, aka 5s, called 6s
            new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 4, false),
            new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 5, true),
            new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 6, false)),
        List.of(  // CHI 4-5 — 4s, plain 5s, called 6s
            new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 4, false),
            new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 5, false),
            new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 6, false)),
        List.of(  // CHI 6-7 — alt shape
            new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 5, false),
            new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 6, false),
            new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 7, false)),
        List.of(  // PON — triplet of 7p
            new TheMahjongTile(TheMahjongTile.Suit.PINZU, 7, false),
            new TheMahjongTile(TheMahjongTile.Suit.PINZU, 7, false),
            new TheMahjongTile(TheMahjongTile.Suit.PINZU, 7, false)),
        List.of(  // KAN — quad of 9m
            new TheMahjongTile(TheMahjongTile.Suit.MANZU, 9, false),
            new TheMahjongTile(TheMahjongTile.Suit.MANZU, 9, false),
            new TheMahjongTile(TheMahjongTile.Suit.MANZU, 9, false),
            new TheMahjongTile(TheMahjongTile.Suit.MANZU, 9, false)),
        List.of(),   // RON
        List.of(),   // CHANKAN
        List.of()    // PASS
    );

    private final Font font;

    @Nullable private CuteRenderer cute;
    @Nullable private MahjongTableBlockEntity boundBE;
    /** One root group per seat — cleared and rebuilt as a unit. */
    private final GroupNode[] seatRoots = new GroupNode[4];
    @Nullable private TheMahjongMatch lastMatch;
    @Nullable private Direction lastFacing;
    private boolean lastEditorActive;
    /** Nodes corresponding to the round's currently-active tile (drawn on
     *  turn, last discard awaiting claims, etc.). Outlined orange after the
     *  cute scene draws so the player sees the hot tile clearly. Populated
     *  during scene rebuild; cleared on every rebuild. */
    private final java.util.List<CuteNode> activeTileNodes = new java.util.ArrayList<>();
    /** Nodes corresponding to hand tiles the local player may legally discard
     *  this turn. Outlined in a softer colour than activeTileNodes so the
     *  active tile stays visually distinct. Populated only during the local
     *  player's AwaitingDiscard turn. */
    private final java.util.List<CuteNode> legalDiscardNodes = new java.util.ArrayList<>();
    /** Per-seat discard count observed at last rebuild. Used to detect
     *  growth → identify the last-discarder seat across phases (the round's
     *  {@code claimSourceSeat} only holds during the claim window) and to
     *  fire a tile-place sound on each new discard. */
    private final int[] lastSeenDiscardCount = new int[4];
    /** Per-seat meld count observed at last rebuild. Growth = a meld was
     *  formed → tile-place sound. */
    private final int[] lastSeenMeldCount = new int[4];
    /** Live-wall size at last rebuild. Shrinking = a tile was drawn → softer
     *  tile-place sound. {@code -1} until first observation. */
    private int lastSeenLiveWallSize = -1;
    /** Seat that most recently appended a discard, or -1 if unknown. Persists
     *  across phase transitions until another seat discards. */
    private int lastDiscarderSeat = -1;
    /** Local-player seat (0..3) at last rebuild, or -1 if not seated. Tracked in
     *  staleness so hand-tile interactivity flips on/off as the player sits/stands
     *  or as the turn passes through them. */
    private int lastLocalSeat = -1;
    /** Cached "is it local player's discard turn" at last rebuild — flips
     *  interactive bounds on hand tiles. */
    private boolean lastLocalDiscardTurn;
    /** Cached "does the local player physically hold the drawn tile in
     *  inventory" at last rebuild. Tracked so the renderer rebuilds when
     *  delivery succeeds/fails or the player picks up/drops the tile, without
     *  forcing per-frame rebuilds (which would reset the hover-lift animation
     *  every frame). */
    private boolean lastDrawnInLocalInv;
    /** Client-local animation clock for {@link MatchPhase.Dealing}. The driver is
     *  reconstructed on the client from synced NBT but does not tick — its
     *  {@code elapsed} only changes when a sync packet arrives. To animate
     *  smoothly between stage-transition syncs we measure stage progress against
     *  this locally-recorded start nanos instead of {@code Dealing.elapsed()}. */
    @Nullable private MatchPhase.Stage lastObservedDealingStage;
    private long localDealingStageStartNanos;
    /** Throttle for per-tile click sounds so a 60-stacks-in-1s build doesn't
     *  machine-gun the speakers. Min interval between any two clicks. */
    private long lastClickSoundNanos;
    private static final long CLICK_SOUND_MIN_INTERVAL_NANOS = 80_000_000L; // 80ms → ≤12.5/s
    /** Per-frame deltas for incremental click triggers. Reset on stage change
     *  so a new stage always starts from a clean baseline. */
    private int lastFrameRingStacks;
    private int lastFrameHandTiles;
    /** Game-tick of the last particle emit pass — guards against multi-fire on
     *  the same tick when {@code render()} runs more than once per tick. */
    private long lastParticleTick = Long.MIN_VALUE;

    public MahjongTableRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(
            MahjongTableBlockEntity table,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay) {
        Level level = table.getLevel();
        if (level == null) return;
        Direction facing = table.getBlockState().getValue(BaseMultipartBlock.FACING);
        List<MahjongTableBlockEntity.SeatInfo> seats = table.seats();
        int turnSeat = currentTurnSeat(table);

        // 1. Wind/score/name edge labels.
        for (Direction cellDir : HORIZONTAL) {
            int seat = MahjongTableBlock.seatForDirection(cellDir, facing);
            if (seat >= seats.size()) continue;
            MahjongTableBlockEntity.SeatInfo info = seats.get(seat);
            List<Component> lines = info.enabled()
                    ? List.of(buildWindLine(seat, table), buildNameLine(info, level))
                    : List.of(Component.literal("CLOSED"));
            WorldSpaceTextRenderer.drawOutwardLines(
                    poseStack, buffers, packedLight, font,
                    0.5 + cellDir.getStepX() * EDGE_PUSH, TEXT_Y, 0.5 + cellDir.getStepZ() * EDGE_PUSH,
                    cellDir,
                    seat == turnSeat ? STYLE_TURN : STYLE_NORMAL,
                    lines);
        }

        // 2. CuteRenderer-driven tile scene.
        // Note: we do NOT call cute.setFacing here. Tile positions are already
        // computed in world-aligned coordinates via outwardForSeat(seat, facing),
        // so an additional cute-root rotation would rotate the scene twice.
        // The seat text labels render outside cute (direct master-block pose)
        // which is why their placement reads correctly even when tiles drifted.
        ensureBound(table);
        rebuildSceneIfStale(table, facing, CuteEditor.isActive());
        cute.frame(poseStack, buffers, packedLight, packedOverlay, partialTick);

        // 2b/2c. Highlights — identical orange (R/G/B/A) for both legal-
        // discard tiles and the round's currently-active tile. Same colour so
        // the player gets a single consistent visual cue; the active outline
        // draws after the legal-discard pass so when both apply (the drawn
        // tile is also legal-to-discard) the active outline lands on top with
        // no visible difference.
        for (CuteNode node : legalDiscardNodes) {
            HoverHighlightRenderer.drawNodeOutline(poseStack, buffers, node,
                    HIGHLIGHT_R, HIGHLIGHT_G, HIGHLIGHT_B, HIGHLIGHT_A);
        }
        for (CuteNode node : activeTileNodes) {
            HoverHighlightRenderer.drawNodeOutline(poseStack, buffers, node,
                    HIGHLIGHT_R, HIGHLIGHT_G, HIGHLIGHT_B, HIGHLIGHT_A);
        }

        // 2d. Round-result reveal overlay — billboarded text floating above
        // the table centre with header / yaku lines / point deltas / verdict.
        // Built up progressively as the BE animation drives the visible state
        // through SHOW_HEADER → SHOW_YAKU_LINES → SHOW_FINAL → HOLD.
        if (table.isInResultPhase()) {
            drawResultOverlay(table, poseStack, buffers, packedLight);
        }

        // 2e. Particle layer — fires once per game tick. Three modes:
        //   - WIN  (red, local) — owner has a winning action available now
        //   - RIICHI (orange, global) — seat has declared riichi
        //   - TENPAI (white, local) — owner is one tile from winning
        // Skipped while the dealing animation is still running.
        if (table.driver() != null
                && !(table.driver().currentPhase() instanceof MatchPhase.Dealing)
                && !table.isInResultPhase()) {
            long gt = level.getGameTime();
            if (gt != lastParticleTick) {
                lastParticleTick = gt;
                emitSeatParticles(table, level, gt, facing);
            }
        }

        // 3. Editor gizmos — one triad per anchor per seat.
        if (CuteEditor.isActive()) {
            String selected = CuteEditor.selectedName();
            for (int seat = 0; seat < 4; seat++) {
                Direction outward = outwardForSeat(seat, facing);
                if (outward == null) continue;
                for (LayoutEntry e : WORLD_ANCHORS) {
                    double[] c = anchorWorldOrigin(e, outward);
                    CuteEditor.drawTriad(poseStack, buffers, c[0], c[1], c[2],
                            e.name().equals(selected));
                }
            }
        }
    }

    /** Round-result text scale. */
    private static final float RESULT_TEXT_SCALE = 0.0100f;
    /** Soft dark backplate alpha behind the result overlay text. */
    private static final int RESULT_TEXT_BG = 0x80101010;

    private static final int COLOR_ROUND_TITLE = 0xFFE8E8E8;
    private static final int COLOR_VERDICT_NORMAL  = 0xFFFF6A6A;
    private static final int COLOR_VERDICT_YAKUMAN = 0xFFFFD700;
    private static final int COLOR_HEADER_DRAW     = 0xFFA0E8F7;
    private static final int COLOR_YAKU_LINE      = 0xFFE0E0E0;
    private static final int COLOR_DELTA_LINE     = 0xFFE8F7FF;

    /**
     * Renders the round-result banner above the master block centre. Lines
     * are stacked vertically and billboarded so every player around the table
     * reads them upright. Order: round title, headline, yaku lines, delta
     * lines, footer.
     *
     * <p>Built entirely on the client from the replicated driver's
     * {@link MatchPhase.RoundEnded}. The server only sends the reveal cursor
     * ({@link MahjongTableBlockEntity#resultAnimStage()} +
     * {@link MahjongTableBlockEntity#resultAnimYakuIdx()}), no English text.
     */
    private void drawResultOverlay(MahjongTableBlockEntity table, PoseStack poseStack,
                                   MultiBufferSource buffers, int packedLight) {
        TheMahjongDriver driver = table.driver();
        if (driver == null) return;
        if (!(driver.currentPhase() instanceof MatchPhase.RoundEnded re)) return;
        TheMahjongRound round = driver.match().currentRound().orElse(null);
        if (round == null) return;

        ResultAnimStage stage = table.resultAnimStage();
        int yakuRevealed = table.resultAnimYakuIdx();
        boolean showFinal = stage == ResultAnimStage.SHOW_FINAL || stage == ResultAnimStage.AWAITING_ADVANCE;

        java.util.List<ResultLine> lines = new java.util.ArrayList<>();

        // Round title — always shown for the duration of the overlay.
        lines.add(new ResultLine(buildRoundTitle(round), COLOR_ROUND_TITLE));

        if (re.winResults().isEmpty()) {
            // Exhaustive draw — header + verdict only, no yaku/delta lines.
            lines.add(new ResultLine(
                    Component.translatable("riichi_mahjong_forge.result.draw.no_winner"),
                    COLOR_HEADER_DRAW));
            if (showFinal) {
                lines.add(new ResultLine(
                        Component.translatable("riichi_mahjong_forge.result.draw.all_zero"),
                        COLOR_HEADER_DRAW));
            }
        } else {
            WinResult primary = re.winResults().get(0);
            boolean yakuman = !primary.yakuman().isEmpty();

            // Yaku lines — progressive reveal. Order: yakuman, then non-yakuman, then dora.
            int idx = 0;
            for (var y : primary.yakuman()) {
                if (idx++ >= yakuRevealed) break;
                lines.add(new ResultLine(yakuComponent(y.name()), COLOR_YAKU_LINE));
            }
            for (var y : primary.yaku()) {
                if (idx++ >= yakuRevealed) break;
                lines.add(new ResultLine(
                        Component.translatable("riichi_mahjong_forge.result.yaku_line",
                                yakuComponent(y.name()),
                                Component.literal(String.valueOf(y.closedHan()))),
                        COLOR_YAKU_LINE));
            }
            // Dora doesn't contribute to yakuman scoring, so the result
            // screen suppresses the dora line entirely on yakuman wins —
            // even if {@code primary.doraCount() > 0} (the dora is still
            // physically in the hand, the engine reports it accurately, but
            // it's irrelevant to this win).
            if (!yakuman && primary.doraCount() > 0 && idx++ < yakuRevealed) {
                lines.add(new ResultLine(
                        Component.translatable("riichi_mahjong_forge.result.dora_line",
                                Component.translatable("riichi_mahjong_forge.yaku.dora"),
                                Component.literal(String.valueOf(primary.doraCount()))),
                        COLOR_YAKU_LINE));
            }

            // Final stage: verdict footer + delta lines.
            if (showFinal) {
                lines.add(new ResultLine(verdictComponent(primary.han(), yakuman),
                        yakuman ? COLOR_VERDICT_YAKUMAN : COLOR_VERDICT_NORMAL));
                for (int i = 0; i < primary.pointDeltas().size(); i++) {
                    int d = primary.pointDeltas().get(i);
                    if (d == 0) continue;
                    String sign = d > 0 ? "+" : "";
                    lines.add(new ResultLine(
                            Component.translatable("riichi_mahjong_forge.result.delta_line",
                                    seatName(table, i),
                                    Component.literal(sign),
                                    Component.literal(String.valueOf(d))),
                            COLOR_DELTA_LINE));
                }
            }
        }

        if (lines.isEmpty()) return;

        var mc = Minecraft.getInstance();
        poseStack.pushPose();
        poseStack.translate(0.5, 1.65, 0.5);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-RESULT_TEXT_SCALE, -RESULT_TEXT_SCALE, RESULT_TEXT_SCALE);
        float y = 0f;
        for (ResultLine line : lines) {
            int w = font.width(line.text());
            float x = -w / 2f;
            font.drawInBatch(line.text(), x, y, line.color(), false,
                    poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL,
                    RESULT_TEXT_BG, packedLight);
            y += font.lineHeight + 1f;
        }
        poseStack.popPose();
    }

    private record ResultLine(Component text, int color) {}

    private static int winnerSeatOf(WinResult wr) {
        for (int i = 0; i < wr.pointDeltas().size(); i++) {
            if (wr.pointDeltas().get(i) > 0) return i;
        }
        return -1;
    }

    /**
     * Hand display order for a winning seat during the result reveal: sort all
     * tiles except the last one (the winning/drawn tile for tsumo) and keep
     * that last tile pinned at the rightmost slot. For 13-tile hands (ron),
     * everything is sorted normally — the winning tile lives in the
     * discarder's river, not the hand.
     */
    private static List<TheMahjongTile> winnerDisplayOrder(List<TheMahjongTile> hand) {
        if (hand.size() <= 13) {
            List<TheMahjongTile> sorted = new java.util.ArrayList<>(hand);
            sorted.sort(TheMahjongTile.DISPLAY_ORDER);
            return sorted;
        }
        TheMahjongTile winning = hand.get(hand.size() - 1);
        List<TheMahjongTile> rest = new java.util.ArrayList<>(hand.subList(0, hand.size() - 1));
        rest.sort(TheMahjongTile.DISPLAY_ORDER);
        rest.add(winning);
        return rest;
    }

    /** True iff {@code seat} is a winning seat in the current
     *  {@link MatchPhase.RoundEnded}. Multi-ron returns true for every winner. */
    private static boolean isWinningSeat(TheMahjongDriver driver, int seat) {
        if (!(driver.currentPhase() instanceof MatchPhase.RoundEnded re)) return false;
        for (WinResult wr : re.winResults()) {
            if (winnerSeatOf(wr) == seat) return true;
        }
        return false;
    }

    /** True iff the round just ended with at least one winner who was in
     *  riichi — the standard trigger for revealing the ura dora indicators. */
    private static boolean shouldRevealUraDora(TheMahjongDriver driver, List<TheMahjongPlayer> players) {
        if (!(driver.currentPhase() instanceof MatchPhase.RoundEnded re)) return false;
        for (WinResult wr : re.winResults()) {
            int seat = winnerSeatOf(wr);
            if (seat >= 0 && seat < players.size() && players.get(seat).riichi()) return true;
        }
        return false;
    }

    /**
     * Single rule for the orange "active tile" highlight, derived from round
     * state. Returns {@code (handSeat, riverSeat)}; exactly one is &gt;= 0 when a
     * highlight applies, both are -1 otherwise.
     *
     * <ul>
     *   <li>Live AwaitingDiscard with Drawn active → currentTurnSeat's last hand tile.</li>
     *   <li>Live AwaitingClaims with HeldDiscard → lastDiscarderSeat's last river tile.</li>
     *   <li>RoundEnded — Tsumo (a winner has hand size &gt; 13) → that winner's last hand tile.</li>
     *   <li>RoundEnded — Ron (winners but no &gt;13 hand) → lastDiscarderSeat's last river tile.</li>
     *   <li>Anything else (Ryuukyoku, Dealing, etc.) → no highlight.</li>
     * </ul>
     */
    private record ActiveHighlight(int handSeat, int riverSeat) {
        static final ActiveHighlight NONE = new ActiveHighlight(-1, -1);
        static ActiveHighlight hand(int seat)  { return new ActiveHighlight(seat, -1); }
        static ActiveHighlight river(int seat) { return new ActiveHighlight(-1, seat); }
    }

    private static ActiveHighlight computeActiveHighlight(
            TheMahjongDriver driver,
            com.themahjong.TheMahjongRound round,
            int lastDiscarderSeat) {
        // Live: drawn → highlight the on-turn seat's hand last tile.
        if (round.activeTile() instanceof TheMahjongRound.ActiveTile.Drawn) {
            return ActiveHighlight.hand(round.currentTurnSeat());
        }
        // Live: held-discard / kita / kakan → highlight the discarder's last river tile.
        if (round.activeTile() instanceof TheMahjongRound.ActiveTile.HeldDiscard
                || round.activeTile() instanceof TheMahjongRound.ActiveTile.HeldKita
                || round.activeTile() instanceof TheMahjongRound.ActiveTile.HeldKakan) {
            return lastDiscarderSeat >= 0 ? ActiveHighlight.river(lastDiscarderSeat) : ActiveHighlight.NONE;
        }
        // Result phase — winner's win tile.
        if (driver.currentPhase() instanceof MatchPhase.RoundEnded re && !re.winResults().isEmpty()) {
            // Tsumo: any winner whose hand grew to 14 (drawn-still-there).
            for (WinResult wr : re.winResults()) {
                int seat = winnerSeatOf(wr);
                if (seat >= 0 && seat < round.players().size()
                        && round.players().get(seat).currentHand().size() > 13) {
                    return ActiveHighlight.hand(seat);
                }
            }
            // Ron: no winner has a 14-tile hand → tile is on the discarder's river.
            if (lastDiscarderSeat >= 0) return ActiveHighlight.river(lastDiscarderSeat);
        }
        return ActiveHighlight.NONE;
    }

    private static Component buildRoundTitle(TheMahjongRound round) {
        Component wind = Component.translatable(
                "riichi_mahjong_forge.result.wind." + round.roundWind().name().toLowerCase(java.util.Locale.ROOT));
        Component handNum = Component.literal(String.valueOf(round.handNumber()));
        if (round.honba() > 0) {
            return Component.translatable("riichi_mahjong_forge.result.round_label_honba",
                    wind, handNum, Component.literal(String.valueOf(round.honba())));
        }
        return Component.translatable("riichi_mahjong_forge.result.round_label", wind, handNum);
    }

    private Component seatName(MahjongTableBlockEntity table, int seat) {
        var seats = table.seats();
        if (seat >= 0 && seat < seats.size()) {
            var occ = seats.get(seat).occupant();
            if (occ.isPresent()) {
                Level lvl = table.getLevel();
                if (lvl != null) {
                    Player p = lvl.getPlayerByUUID(occ.get());
                    if (p != null) return p.getDisplayName().copy();
                }
            }
        }
        // Fallback to seat-wind name (East/South/West/North).
        String key = switch (seat) {
            case 0 -> "riichi_mahjong_forge.result.wind.east";
            case 1 -> "riichi_mahjong_forge.result.wind.south";
            case 2 -> "riichi_mahjong_forge.result.wind.west";
            case 3 -> "riichi_mahjong_forge.result.wind.north";
            default -> null;
        };
        return key != null ? Component.translatable(key) : Component.literal("Seat " + seat);
    }

    /** Builds the localized name component for a yaku enum's raw name. Falls back
     *  to the humanized name if the canonical lang key isn't present. */
    private static Component yakuComponent(String rawName) {
        String canonical = MahjongWinEffects.canonicalYakuKey(rawName);
        if (canonical == null || canonical.isEmpty()) {
            return Component.literal(MahjongWinEffects.humanizeYakuName(rawName));
        }
        return Component.translatableWithFallback(
                "riichi_mahjong_forge.yaku." + canonical,
                MahjongWinEffects.humanizeYakuName(rawName));
    }

    /** Score-bracket title (Mangan / Haneman / etc.) as a translatable component. */
    private static Component verdictComponent(int han, boolean yakuman) {
        if (yakuman)   return Component.translatable("riichi_mahjong_forge.result.verdict.yakuman");
        if (han >= 13) return Component.translatable("riichi_mahjong_forge.result.verdict.kazoe_yakuman");
        if (han >= 11) return Component.translatable("riichi_mahjong_forge.result.verdict.sanbaiman");
        if (han >= 8)  return Component.translatable("riichi_mahjong_forge.result.verdict.baiman");
        if (han >= 6)  return Component.translatable("riichi_mahjong_forge.result.verdict.haneman");
        if (han >= 5)  return Component.translatable("riichi_mahjong_forge.result.verdict.mangan");
        return Component.translatable("riichi_mahjong_forge.result.verdict.han",
                Component.literal(String.valueOf(han)));
    }

    private void ensureBound(MahjongTableBlockEntity be) {
        if (boundBE == be && cute != null) return;
        if (cute != null) cute.detach();
        cute = new CuteRenderer(be.getLevel().dimension(), be.getBlockPos());
        cute.setHoverSound(RiichiMahjongForgeMod.TILE_HOVER_ACTION_SOUND.get(), 0.55f, 1.0f);
        cute.attach();
        boundBE = be;
        for (int i = 0; i < seatRoots.length; i++) seatRoots[i] = null;
        activeTileNodes.clear();
        legalDiscardNodes.clear();
        for (int i = 0; i < lastSeenDiscardCount.length; i++) lastSeenDiscardCount[i] = 0;
        for (int i = 0; i < lastSeenMeldCount.length; i++) lastSeenMeldCount[i] = 0;
        lastSeenLiveWallSize = -1;
        lastDiscarderSeat = -1;
        lastMatch = null;
        lastFacing = null;
        lastEditorActive = false;
        lastLocalSeat = -1;
        lastLocalDiscardTurn = false;
        lastDrawnInLocalInv = false;
        lastObservedDealingStage = null;
        lastFrameRingStacks = 0;
        lastFrameHandTiles = 0;
    }

    private void rebuildSceneIfStale(MahjongTableBlockEntity table, Direction facing, boolean editorActive) {
        TheMahjongDriver driver = table.driver();
        TheMahjongMatch match = driver == null ? null : driver.match();
        Level level = table.getLevel();
        // Editor active = rebuild every frame so anchor edits feed back live.
        // Dealing active = rebuild every frame so the per-stage animation progress
        // (stored on the immutable Dealing phase, refreshed each driver tick) feeds
        // through. Otherwise rebuild only when something observable changed —
        // match ref / facing / editor toggle / local seat / local turn / whether
        // the drawn tile sits in our inventory. Per-frame rebuild during normal
        // play would reset hover-lift animations every frame.
        boolean inDealing = driver != null && driver.currentPhase() instanceof MatchPhase.Dealing;
        int localSeat = localSeat(table);
        boolean localDiscardTurn = driver != null
                && driver.currentPhase() instanceof MatchPhase.AwaitingDiscard ad
                && ad.seat() == localSeat;
        // Track the BE-synced "drawn was delivered to local inv" flag in
        // staleness so the rendered hand updates when the server reports
        // delivery success / failure.
        boolean drawnInLocalInv = localSeat >= 0 && table.drawnTileDeliveredForSeat(localSeat);
        if (!editorActive && !inDealing
                && match == lastMatch
                && facing == lastFacing
                && editorActive == lastEditorActive
                && localSeat == lastLocalSeat
                && localDiscardTurn == lastLocalDiscardTurn
                && drawnInLocalInv == lastDrawnInLocalInv) {
            return;
        }
        lastMatch = match;
        lastFacing = facing;
        lastEditorActive = editorActive;
        lastLocalSeat = localSeat;
        lastLocalDiscardTurn = localDiscardTurn;
        lastDrawnInLocalInv = drawnInLocalInv;

        for (int seat = 0; seat < seatRoots.length; seat++) {
            if (seatRoots[seat] != null) {
                cute.root().removeChild(seatRoots[seat]);
                seatRoots[seat] = null;
            }
        }
        activeTileNodes.clear();
        legalDiscardNodes.clear();

        if (editorActive) {
            // Preview mode: render every layout at max capacity with placeholders,
            // for all four seats. Real driver state is ignored — we want the
            // anchors to be tweakable even with no match running.
            for (int seat = 0; seat < 4; seat++) {
                Direction outward = outwardForSeat(seat, facing);
                if (outward == null) continue;
                seatRoots[seat] = buildPreviewSeat(seat, outward);
                cute.root().addChild(seatRoots[seat]);
            }
            return;
        }

        // Live mode (GAME state): render real driver state. During Dealing, a
        // client-local stage clock (localDealingStageStartNanos) drives the
        // build-up: stage 1 fills the wall, stage 2 fills the hands tile-by-tile,
        // stage 3 flips the initial dora face-up. The driver's authoritative
        // stage transitions arrive via NBT sync; per-frame interpolation is
        // local. After Dealing exits, hands and walls sit at their final state
        // and stay there until the round changes them.
        if (driver == null) return;
        var roundOpt = match.currentRound();
        if (roundOpt.isEmpty()) return;
        TheMahjongRound round = roundOpt.get();
        List<TheMahjongPlayer> players = round.players();
        // Track the last-discarder seat across phase transitions. Compare
        // each seat's discard count to the last observation; whichever grew
        // is the seat that just discarded. Survives the claim window so the
        // highlight stays after all seats pass. We never reset the counts
        // proactively — round restarts simply produce lower counts (no
        // growth fires); claim-removals also produce lower counts. Either
        // way, lastSeen tracks the current state and the next real growth
        // re-elects {@code lastDiscarderSeat}.
        // Per-event sounds for normal play (post-Dealing). Compare current
        // round counts to last observation; growth → tile-place click.
        // {@code lastSeenLiveWallSize == -1} on first observation, so the
        // initial post-Dealing sync doesn't blast a draw-sound for every
        // already-dealt tile.
        boolean playDiscardSound = false;
        boolean playMeldSound = false;
        boolean playDrawSound = false;
        for (int i = 0; i < players.size() && i < lastSeenDiscardCount.length; i++) {
            int discNow = players.get(i).discards().size();
            if (discNow > lastSeenDiscardCount[i]) {
                lastDiscarderSeat = i;
                playDiscardSound = true;
            }
            lastSeenDiscardCount[i] = discNow;
            int meldNow = players.get(i).melds().size();
            if (meldNow > lastSeenMeldCount[i]) playMeldSound = true;
            lastSeenMeldCount[i] = meldNow;
        }
        int liveWallNow = round.liveWall().size();
        if (lastSeenLiveWallSize >= 0 && liveWallNow < lastSeenLiveWallSize) {
            playDrawSound = true;
        }
        lastSeenLiveWallSize = liveWallNow;
        // Suppress per-event sounds while Dealing is animating — that stage
        // has its own dedicated build / hand-deal click loop and we don't
        // want both firing concurrently as the round materializes.
        if (!(driver.currentPhase() instanceof MatchPhase.Dealing)) {
            if (playDiscardSound || playMeldSound) {
                playClickSound(level, table.getBlockPos(),
                        RiichiMahjongForgeMod.TILE_PLACE_SOUND.get(), 0.55f);
            } else if (playDrawSound) {
                playClickSound(level, table.getBlockPos(),
                        RiichiMahjongForgeMod.TILE_PLACE_SOUND.get(), 0.35f);
            }
        }

        MatchPhase.Stage dealingStage = null;
        // buildProgress runs 0→1 over the stage's active build portion only;
        // during the trailing pause it stays clamped at 1.0 so visuals hold at
        // the stage's end state.
        double buildProgress = 1.0;
        if (driver.currentPhase() instanceof MatchPhase.Dealing dealing) {
            dealingStage = dealing.stage();
            // Reset the local clock on every observed stage transition. Skipping
            // this would let progress run past 1.0 across stages.
            if (lastObservedDealingStage != dealingStage) {
                lastObservedDealingStage = dealingStage;
                localDealingStageStartNanos = System.nanoTime();
                lastFrameRingStacks = 0;
                lastFrameHandTiles = 0;
                playStageEntrySound(level, table.getBlockPos(), dealingStage);
            }
            double localElapsed = (System.nanoTime() - localDealingStageStartNanos) / 1_000_000_000.0;
            double buildDuration = dealingStage.buildDuration();
            buildProgress = buildDuration <= 0 ? 1.0 : Math.min(1.0, localElapsed / buildDuration);
        } else {
            lastObservedDealingStage = null;
        }
        double handFraction  = handVisibleFraction(dealingStage, buildProgress);
        boolean doraFaceUp   = dealingStage == null || dealingStage == MatchPhase.Stage.DORA_FLIP
                || dealingStage.ordinal() > MatchPhase.Stage.DORA_FLIP.ordinal();
        // Smooth flip during DORA_FLIP's build portion; held at 1.0 during its
        // pause and afterward. Pre-DORA_FLIP stages don't render a dora
        // indicator at all (dead wall stays fully face-down).
        double doraFlipProgress = dealingStage == MatchPhase.Stage.DORA_FLIP
                ? buildProgress
                : (doraFaceUp ? 1.0 : 0.0);

        // Single source of truth for the orange "active tile" outline. See
        // computeActiveHighlight for the rules. Computed once per scene
        // build so both the hand and river branches consult the same answer.
        ActiveHighlight highlight = computeActiveHighlight(driver, round, lastDiscarderSeat);

        TheMahjongTile faceDownPlaceholder = FACE_DOWN_PLACEHOLDER;
        int wallStackCount = WALL.i("count");
        int deadStackCount = DEAD_WALL.i("count");
        int wallStackHeight = Math.max(1, WALL.i("stackHeight"));
        // Total tiles ever in the live wall pre-deal: dealer's side is shortened
        // by the dead wall, the other 3 sides are full. Used as the denominator
        // for post-Dealing depletion tracking.
        int liveWallTotalTiles =
                (wallStackCount - deadStackCount) * wallStackHeight
                + 3 * wallStackCount * wallStackHeight;
        int dealtTiles = computeDealtTiles(dealingStage, buildProgress, liveWallTotalTiles, round);

        // WALL_BUILDING uses a circular build order: seat 0 fills its entire
        // 17-stack row (live 10 + dead 7), then seat 1, then seat 2, then seat
        // 3 — one stack at a time around the table rather than all four sides
        // simultaneously. Per global step adds 1 stack (= 2 tiles) on a single
        // side. Total = 17 × 4 = 68 stacks over the build duration.
        int totalRingStacks = wallStackCount * 4;
        int ringStacksBuilt = dealingStage == MatchPhase.Stage.WALL_BUILDING
                ? Math.min(totalRingStacks, (int) Math.floor(buildProgress * totalRingStacks))
                : totalRingStacks;
        // Click per new ring-stack during WALL_BUILDING — throttled.
        if (dealingStage == MatchPhase.Stage.WALL_BUILDING && ringStacksBuilt > lastFrameRingStacks) {
            playClickSound(level, table.getBlockPos(),
                    RiichiMahjongForgeMod.TILE_PLACE_SOUND.get(), 0.45f);
            lastFrameRingStacks = ringStacksBuilt;
        }
        int seat0DeadMaxTiles = deadStackCount * wallStackHeight;

        int handTilesThisFrame = 0;
        for (int seat = 0; seat < 4; seat++) {
            Direction outward = outwardForSeat(seat, facing);
            if (outward == null) continue;
            GroupNode root = new GroupNode();

            // Live wall on this seat's side, face-down. Seat 0 is the dealer's
            // side and only fits (count − deadCount) stacks of live wall — the
            // remaining `deadCount` stacks of that 17-stack row are the dead
            // wall (rendered separately below). Other seats hold full `count`.
            int seatVisibleTiles;
            if (dealingStage == MatchPhase.Stage.WALL_BUILDING) {
                // How many stacks of this seat's row have been built so far in
                // the circular sweep — seat S's stacks occupy global indices
                // [S × wallStackCount, (S+1) × wallStackCount).
                int seatStart = seat * wallStackCount;
                int seatStacksThisFrame = Math.max(0,
                        Math.min(wallStackCount, ringStacksBuilt - seatStart));
                if (seat == 0) {
                    // Seat 0 row layout: first (wallStackCount − deadStackCount)
                    // stacks are live wall, remaining deadStackCount are dead wall.
                    int liveStacksMax = wallStackCount - deadStackCount;
                    int liveStacks = Math.min(seatStacksThisFrame, liveStacksMax);
                    seatVisibleTiles = liveStacks * wallStackHeight;
                } else {
                    seatVisibleTiles = seatStacksThisFrame * wallStackHeight;
                }
            } else {
                seatVisibleTiles = liveTilesAfterDeal(
                        seat, wallStackCount, deadStackCount, dealtTiles);
            }
            if (seatVisibleTiles > 0) {
                int positions = (seatVisibleTiles + wallStackHeight - 1) / wallStackHeight;
                int lastStackLayers = positions > 0
                        ? seatVisibleTiles - (positions - 1) * wallStackHeight
                        : -1;
                root.addChild(buildLineGroup(WALL,
                        placeholderTiles(positions, faceDownPlaceholder),
                        outward, /* layFlat= */ true,
                        /* doraStackIdx= */ -1, /* splitAfterIdx= */ -1, /* splitGap= */ 0,
                        /* lastStackLayers= */ lastStackLayers));
            }

            // Dead wall — sits on the dealer's row, after the live wall portion.
            // In the circular build it fills as part of seat 0's row: once the
            // 10 live stacks of seat 0 are placed, the next (up to 7) stacks
            // built on seat 0 are dead-wall stacks.
            if (seat == 0) {
                int deadVisibleTiles;
                if (dealingStage == MatchPhase.Stage.WALL_BUILDING) {
                    int liveStacksMax = wallStackCount - deadStackCount;
                    int seat0Stacks = Math.max(0, Math.min(wallStackCount, ringStacksBuilt));
                    int deadStacks = Math.max(0, seat0Stacks - liveStacksMax);
                    deadVisibleTiles = deadStacks * wallStackHeight;
                } else {
                    deadVisibleTiles = seat0DeadMaxTiles;
                }
                if (deadVisibleTiles > 0) {
                    boolean revealUra = shouldRevealUraDora(driver, players);
                    root.addChild(buildLiveDeadWall(DEAD_WALL, outward, faceDownPlaceholder,
                            doraFlipProgress, deadVisibleTiles, round, revealUra));
                }
            }

            // Hand — real tiles. During DEALING_HANDS, deal them in sequentially
            // so a partial hand reads as "still being dealt". When the drawn
            // tile has been handed off to the seat occupant as a physical
            // item, omit it here so it doesn't render in two places.
            if (seat < players.size() && handFraction > 0) {
                // Winner's hand is revealed face-up flat on the table during
                // the result phase so every viewer reads the winning shape.
                // Multi-ron: each winner reveals their own hand on their seat.
                boolean revealWinnerFlat = table.isInResultPhase()
                        && isWinningSeat(driver, seat);
                // For the winner during result phase, keep the winning (drawn)
                // tile at the rightmost slot. For tsumo, currentHand() already
                // has the drawn tile appended last (round.draw() adds it
                // there); we sort the leading 13 for readability and leave
                // the last untouched. For ron the hand is 13 tiles already
                // and we just sort. For everyone else, defer to the engine's
                // handDisplayOrder (active-tile-aware ordering).
                List<TheMahjongTile> rendered;
                if (revealWinnerFlat) {
                    rendered = winnerDisplayOrder(players.get(seat).currentHand());
                } else {
                    rendered = round.handDisplayOrder(seat);
                }
                boolean drawnStripped = false;
                if (!revealWinnerFlat
                        && seat == localSeat
                        && driver.currentPhase() instanceof MatchPhase.AwaitingDiscard ad
                        && ad.seat() == seat
                        && round.activeTile() instanceof TheMahjongRound.ActiveTile.Drawn
                        && table.drawnTileDeliveredForSeat(seat)) {
                    // Drawn tile is the rightmost entry — drop it; delivery
                    // succeeded at some point this turn so the player has the
                    // item (in inv, in a chest, on the floor — wherever they
                    // moved it). Stays hidden from the rendered hand for the
                    // whole turn so the visible state doesn't flicker when the
                    // player moves the item; tryDiscard requires it back in
                    // inv to actually fire.
                    rendered = rendered.subList(0, rendered.size() - 1);
                    drawnStripped = true;
                }
                int n = Math.max(0, Math.min(rendered.size(),
                        (int) Math.round(rendered.size() * handFraction)));
                if (n > 0) {
                    GroupNode handGroup = buildLineGroup(HAND,
                            rendered.subList(0, n), outward, /* layFlat= */ revealWinnerFlat);
                    // Tag each tile clickable when it's the local player's
                    // discard turn so the cute hover/highlight + click pipeline
                    // engages. HAND has stackHeight=1, so children index = tile
                    // position. Other seats / off-turn renders stay inert. Also
                    // collect the legal-discard set so we can outline the tiles
                    // the player may pick.
                    java.util.Set<TheMahjongTile> legalDiscardTiles = java.util.Set.of();
                    if (seat == localSeat && localDiscardTurn) {
                        legalDiscardTiles = collectLegalDiscardTiles(
                                driver.legalActions(seat), table.riichiPendingForSeat(seat));
                        int tileIdx = 0;
                        int handChildIdx = 0;
                        for (com.riichimahjongforge.cuterenderer.CuteNode child : handGroup.children()) {
                            if (child instanceof BlockModelNode bm) {
                                bm.makeClickable(new com.riichimahjongforge.cuterenderer.InteractKey.SeatSlot(
                                        (byte) seat,
                                        com.riichimahjongforge.cuterenderer.InteractKey.SeatSlot.AREA_HAND,
                                        (short) tileIdx));
                                if (handChildIdx < n
                                        && legalDiscardTiles.contains(rendered.get(handChildIdx))) {
                                    legalDiscardNodes.add(bm);
                                }
                                tileIdx++;
                                handChildIdx++;
                            }
                        }
                    }
                    // Active-tile outline on the rightmost hand tile when the
                    // unified highlight points here (live drawn-in-hand, or
                    // tsumo result reveal). Suppressed if we stripped the
                    // drawn tile or if dealing truncated the hand short.
                    if (highlight.handSeat() == seat
                            && !drawnStripped
                            && n == rendered.size()) {
                        var children = handGroup.children();
                        if (!children.isEmpty()
                                && children.get(children.size() - 1) instanceof BlockModelNode last) {
                            activeTileNodes.add(last);
                        }
                    }
                    root.addChild(handGroup);
                }
                handTilesThisFrame += n;
            }

            // Action buttons — render whatever the local player can legally
            // do right now, mapped from driver.legalActions(seat). Only on the
            // local seat and only after Dealing finishes. Skipped when the
            // filtered list is empty (off-turn during AwaitingDiscard, no
            // claim options during AwaitingClaims, etc.).
            if (dealingStage == null && seat == localSeat) {
                List<MahjongTableHumanPlayer.TableButton> buttons =
                        MahjongTableHumanPlayer.tableButtons(driver, seat, table.riichiPendingForSeat(seat));
                if (!buttons.isEmpty()) {
                    List<PreviewBtn> configs = new java.util.ArrayList<>(buttons.size());
                    List<List<TheMahjongTile>> ghosts = new java.util.ArrayList<>(buttons.size());
                    for (var b : buttons) {
                        configs.add(configForButton(b));
                        ghosts.add(ghostTilesForButton(b, round));
                    }
                    root.addChild(buildButtonsGroup(seat, outward, configs, ghosts, /* clickable= */ true));
                }
            }

            // River — real discards, oldest first, growing along +outward
            // (toward table centre) row by row. Shown after Dealing finishes.
            // Engine keeps claimed tiles in {@code discards()} for history /
            // {@code sourceDiscardIndex} back-references; skip them so they
            // don't double-render alongside the claimant's meld.
            if (dealingStage == null && seat < players.size()) {
                List<com.themahjong.TheMahjongDiscard> discards = players.get(seat).discards();
                if (!discards.isEmpty()) {
                    java.util.Set<Integer> claimed = claimedIndicesForSeat(players, seat);
                    List<TheMahjongTile> tiles = new java.util.ArrayList<>(discards.size());
                    for (int i = 0; i < discards.size(); i++) {
                        if (claimed.contains(i)) continue;
                        tiles.add(discards.get(i).tile());
                    }
                    if (!tiles.isEmpty()) {
                        GroupNode riverGroup = buildGridGroup(RIVER, RIVER.i("cols"), RIVER.i("rows"),
                                tiles, outward);
                        // Highlight the last rendered tile only if it's also
                        // the actual last discard (i.e. wasn't claimed).
                        // Otherwise the rendered last is an older tile that
                        // shouldn't carry the "most recent action" outline.
                        boolean actualLastUnclaimed = !claimed.contains(discards.size() - 1);
                        // Active-tile outline on the last river tile when the
                        // unified highlight points here (live held-discard, or
                        // ron result reveal). Suppressed when the actual last
                        // discard was claimed (we don't render claimed tiles
                        // in the river anyway).
                        if (highlight.riverSeat() == seat && actualLastUnclaimed) {
                            var children = riverGroup.children();
                            if (!children.isEmpty()
                                    && children.get(children.size() - 1) instanceof BlockModelNode last) {
                                activeTileNodes.add(last);
                            }
                        }
                        root.addChild(riverGroup);
                    }
                }
            }

            // Open + closed melds — chi/pon/kan groups along the seat's right
            // edge. The claimed tile of each open meld is rotated 90° to mark
            // its source. Shown after Dealing finishes.
            if (dealingStage == null && seat < players.size()) {
                List<com.themahjong.TheMahjongMeld> melds = players.get(seat).melds();
                if (!melds.isEmpty()) {
                    root.addChild(buildMeldsLive(MELDS, melds, outward));
                }
            }

            seatRoots[seat] = root;
            cute.root().addChild(root);
        }

        // Click per new dealt hand tile during DEALING_HANDS — throttled.
        if (dealingStage == MatchPhase.Stage.DEALING_HANDS && handTilesThisFrame > lastFrameHandTiles) {
            playClickSound(level, table.getBlockPos(),
                    RiichiMahjongForgeMod.TILE_PLACE_SOUND.get(), 0.4f);
            lastFrameHandTiles = handTilesThisFrame;
        }
    }

    // ---- particle layer ---------------------------------------------------
    // Ports the legacy "tenpai/riichi/win" hand-row dust per the legacy
    // MahjongTableHandSlotsRenderer constants (colours, scale, lifetime).
    private static final org.joml.Vector3f DUST_TENPAI = new org.joml.Vector3f(0.92f, 0.98f, 1.0f);
    private static final org.joml.Vector3f DUST_RIICHI = new org.joml.Vector3f(1.0f,  0.58f, 0.16f);
    private static final org.joml.Vector3f DUST_WIN    = new org.joml.Vector3f(1.0f,  0.20f, 0.20f);
    private static final float DUST_SCALE = 0.10f;
    private static final int   DUST_LIFETIME_TICKS = 10;
    private static final double DUST_RISE_SPEED = 0.02;
    /** Dust spawns just above the top edge of an upright hand tile (HAND scale
     *  is 0.10, tile centred on anchor.y). */
    private static final double DUST_Y_OFFSET = 0.06;

    /** Per-mode tick parity:
     *  TENPAI = 1 every 5 ticks, RIICHI / WIN = 1 every 2 ticks. */
    private static int tickStrideFor(int mode) {
        return switch (mode) {
            case 0 -> 5; // TENPAI
            case 1 -> 2; // RIICHI
            case 2 -> 2; // WIN
            default -> Integer.MAX_VALUE;
        };
    }

    private static void emitSeatParticles(MahjongTableBlockEntity table, Level level,
                                          long gameTick, Direction facing) {
        TheMahjongDriver driver = table.driver();
        if (driver == null) return;
        TheMahjongRound round = driver.match().currentRound().orElse(null);
        if (round == null) return;
        List<TheMahjongPlayer> players = round.players();
        int localSeat = localSeat(table);

        for (int seat = 0; seat < players.size(); seat++) {
            Direction outward = outwardForSeat(seat, facing);
            if (outward == null) continue;
            TheMahjongPlayer player = players.get(seat);
            boolean isLocal = seat == localSeat;
            boolean winAvailable = isLocal && hasWinAction(driver.legalActions(seat));
            boolean riichiActive = player.riichi();
            boolean tenpai = isLocal && !winAvailable && !riichiActive
                    && playerIsTenpaiSafe(player);
            int mode;
            org.joml.Vector3f color;
            if (winAvailable) { mode = 2; color = DUST_WIN; }
            else if (riichiActive) { mode = 1; color = DUST_RIICHI; }
            else if (tenpai) { mode = 0; color = DUST_TENPAI; }
            else continue;
            int stride = tickStrideFor(mode);
            if ((gameTick + seat) % stride != 0) continue;
            // One dust per visible hand tile — matches the legacy density.
            int handTiles = Math.min(player.currentHand().size(), HAND_MAX_TILES);
            for (int i = 0; i < handTiles; i++) {
                spawnHandDust(table, level, outward, color, i);
            }
        }
    }

    /** Tenpai check that's safe regardless of hand size — only checks when the
     *  player's hand is at the canonical pre-draw size {@code 13 - 3*meldCount},
     *  since {@link TenpaiChecker#winningTiles} requires that shape. In other
     *  states (post-draw 14-tile, mid-claim) we skip — the hand bounces back to
     *  the 13-tile shape between turns, so the hint reliably fires there. */
    private static boolean playerIsTenpaiSafe(TheMahjongPlayer player) {
        int expected = 13 - 3 * player.melds().size();
        if (player.currentHand().size() != expected) return false;
        return TenpaiChecker.inTenpai(player);
    }

    private static boolean hasWinAction(List<com.themahjong.driver.PlayerAction> legal) {
        for (var a : legal) {
            if (a instanceof com.themahjong.driver.PlayerAction.DeclareTsumo) return true;
            if (a instanceof com.themahjong.driver.PlayerAction.DeclareRon) return true;
            if (a instanceof com.themahjong.driver.PlayerAction.DeclareChankan) return true;
        }
        return false;
    }

    /** Spawn one dust particle in the seat's hand-row, at a random offset along
     *  the row direction. World position is derived from the master block + the
     *  HAND anchor + outward seat orientation. */
    /**
     * Spawns one dust particle just above the {@code tileIndex}'th tile of a
     * seat's hand row. The HAND anchor sits at the LEFT end of the row
     * (pivot=0), so position {@code i} is {@code anchor + i × spacing} along
     * lineDir; we add a small random jitter inside one tile width so the
     * column-per-tile pattern reads as a soft cloud rather than a strict line.
     */
    private static void spawnHandDust(MahjongTableBlockEntity table, Level level,
                                      Direction outward, org.joml.Vector3f color,
                                      int tileIndex) {
        BlockPos pos = table.getBlockPos();
        double[] anchor = anchorWorldOrigin(HAND, outward);
        Direction lineDir = outward.getCounterClockWise();
        double spacing = HAND.f("spacing");
        double t = tileIndex * spacing
                + (level.getRandom().nextDouble() - 0.5) * spacing * 0.6;
        double dx = lineDir.getStepX() * t;
        double dz = lineDir.getStepZ() * t;
        double x = pos.getX() + anchor[0] + dx;
        double y = pos.getY() + anchor[1] + DUST_Y_OFFSET;
        double z = pos.getZ() + anchor[2] + dz;
        var mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.particle.Particle particle = mc.particleEngine.createParticle(
                new net.minecraft.core.particles.DustParticleOptions(color, DUST_SCALE),
                x, y, z, 0.0, DUST_RISE_SPEED, 0.0);
        if (particle != null) {
            particle.setParticleSpeed(0.0, DUST_RISE_SPEED, 0.0);
            particle.setLifetime(DUST_LIFETIME_TICKS);
        }
    }

    /** Plays a one-shot stage-entry sound at the table position. */
    private void playStageEntrySound(Level level, BlockPos pos, MatchPhase.Stage stage) {
        SoundEvent sound = RiichiMahjongForgeMod.TILE_PLACE_SOUND.get();
        // Dora flip is a single tile flipping face up — same sound, slightly
        // higher pitch reads as the "click" moment vs the rumble of stage start.
        float pitch = stage == MatchPhase.Stage.DORA_FLIP ? 1.25f : 0.9f;
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                sound, SoundSource.BLOCKS, 0.6f, pitch, false);
    }

    /**
     * Throttled tile-click sound. Skips if another click was played within
     * {@link #CLICK_SOUND_MIN_INTERVAL_NANOS}, so a 60-stacks-per-second build
     * doesn't produce 60 sounds per second.
     */
    private void playClickSound(Level level, BlockPos pos, SoundEvent sound, float volume) {
        long now = System.nanoTime();
        if (now - lastClickSoundNanos < CLICK_SOUND_MIN_INTERVAL_NANOS) return;
        lastClickSoundNanos = now;
        float pitch = 0.9f + level.getRandom().nextFloat() * 0.25f;
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                sound, SoundSource.BLOCKS, volume, pitch, false);
    }

    /**
     * Total live-wall tiles dealt out so far. During DEALING_HANDS, ramps 0→52 with
     * stage progress (the standard 13×4 deal). After Dealing, derives from the real
     * round's remaining live wall — captures ongoing draws as the round progresses.
     */
    private static int computeDealtTiles(
            @Nullable MatchPhase.Stage stage, double progress,
            int liveWallTotalTiles, TheMahjongRound round) {
        if (stage == MatchPhase.Stage.WALL_BUILDING) return 0;
        if (stage == MatchPhase.Stage.DEALING_HANDS) {
            return (int) Math.round(52 * Math.max(0, Math.min(1.0, progress)));
        }
        if (stage == MatchPhase.Stage.DORA_FLIP) return 52;
        // Post-Dealing.
        return Math.max(0, liveWallTotalTiles - round.liveWall().size());
    }

    /** Any tile — used for face-down wall / dead-wall fillers where identity is hidden. */
    private static final TheMahjongTile FACE_DOWN_PLACEHOLDER =
            new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);

    private static double handVisibleFraction(@Nullable MatchPhase.Stage stage, double progress) {
        if (stage == null) return 1.0;
        return switch (stage) {
            case WALL_BUILDING -> 0.0;
            case DEALING_HANDS -> progress;
            case DORA_FLIP -> 1.0;
        };
    }

    /**
     * Editor-preview seat: hand (14) + wall + river + melds + kita, all placeholders.
     * Placeholder tiles are seat-keyed (East=Man, South=Sou, West=Pin, North=Winds)
     * so the seat any anchor sits on reads at a glance — and within a seat each
     * layout uses a different rank so they're still distinguishable from one another.
     */
    private GroupNode buildPreviewSeat(int seat, Direction outward) {
        GroupNode root = new GroupNode();
        root.addChild(buildLineGroup(HAND, placeholderTiles(filled(HAND_MAX_TILES, HAND), placeholderFor(seat, 0)),
                outward, /* layFlat= */ false));
        // Live wall: simulate the initial deal by depleting tiles in CW draw order
        // (seat 0 → 3 → 2 → 1) starting from the break on the dealer's side. The
        // last stack may be partial (1 tile remaining) when the deal stopped
        // mid-stack — render it as the bottom tile only since the top was drawn off.
        int wallStackHeight = Math.max(1, WALL.i("stackHeight"));
        int wallTiles = liveTilesAfterDeal(seat, WALL.i("count"), DEAD_WALL.i("count"),
                WALL.i("dealtTiles"));
        int wallPositions = (wallTiles + wallStackHeight - 1) / wallStackHeight; // ceil
        int wallLastLayers = wallPositions > 0
                ? wallTiles - (wallPositions - 1) * wallStackHeight
                : -1;
        root.addChild(buildLineGroup(WALL,
                placeholderTiles(wallPositions, placeholderFor(seat, 1)),
                outward, /* layFlat= */ true,
                /* doraStackIdx= */ -1, /* splitAfterIdx= */ -1, /* splitGap= */ 0,
                /* lastStackLayers= */ wallLastLayers));

        // Dead wall: only on the dealer's seat. Handled by a dedicated builder
        // because of its multi-flip semantics (initial dora + kan doras + ura).
        if (seat == 0) {
            root.addChild(buildDeadWallGroup(DEAD_WALL, outward, placeholderFor(seat, 5)));
        }
        int riverCells = RIVER.i("cols") * RIVER.i("rows");
        root.addChild(buildGridGroup(RIVER, RIVER.i("cols"), RIVER.i("rows"),
                filled(riverCells, RIVER), outward, placeholderFor(seat, 2)));
        int meldsTiles = MELDS.i("meldCount") * MELDS.i("tilesPerMeld");
        root.addChild(buildMeldsGroup(MELDS, filled(meldsTiles, MELDS), outward, placeholderFor(seat, 3)));
        root.addChild(buildLineGroup(KITA, placeholderTiles(filled(KITA.i("count"), KITA), placeholderFor(seat, 4)),
                outward, /* layFlat= */ true));
        root.addChild(buildButtonsGroup(seat, outward));
        return root;
    }

    /**
     * Compute live wall TILES remaining for a given seat after {@code dealtTiles}
     * have been drawn. Pre-deal: dealer (seat 0) has {@code (count − deadCount) × 2}
     * tiles (20 with defaults), others have {@code count × 2} (34). The deal walks
     * CW from the break on dealer's side: order is seats 0 → 3 → 2 → 1.
     */
    private static int liveTilesAfterDeal(int seat, int wallCount, int deadCount, int dealtTiles) {
        int[] preTiles = {
                Math.max(0, wallCount - deadCount) * 2, // seat 0: 20
                wallCount * 2,                          // seat 1: 34
                wallCount * 2,                          // seat 2: 34
                wallCount * 2,                          // seat 3: 34
        };
        int[] dealOrder = {0, 3, 2, 1};
        int leftToDeal = Math.max(0, dealtTiles);
        int[] remainingTiles = {preTiles[0], preTiles[1], preTiles[2], preTiles[3]};
        for (int s : dealOrder) {
            int take = Math.min(leftToDeal, remainingTiles[s]);
            remainingTiles[s] -= take;
            leftToDeal -= take;
            if (leftToDeal <= 0) break;
        }
        if (seat < 0 || seat >= remainingTiles.length) return 0;
        return remainingTiles[seat];
    }

    /** Apply the anchor's {@code fill} extra (0..1) to a max count. Clamped to [0, max]. */
    private static int filled(int max, LayoutEntry e) {
        int n = Math.round(max * Math.max(0f, Math.min(1f, e.f("fill"))));
        return Math.max(0, Math.min(max, n));
    }

    /**
     * Generic line layout — used by hand, wall, kita.
     *
     * <p>Honours optional anchor extras when present:
     * <ul>
     *   <li>{@code stackHeight} (int) — tiles per vertical stack (default 1).</li>
     *   <li>{@code stackGap}    (float) — Y centre-to-centre spacing within a stack.</li>
     *   <li>{@code faceDown} when {@code layFlat} is true — flips art to face the table.</li>
     * </ul>
     * For walls these turn the row into 2-tall face-down stacks.
     *
     * <p>{@code doraStackIdx} ≥ 0 flips the top tile of that stack face-up despite
     * any face-down rule (used for the dealer's first dora indicator). -1 disables.
     */
    private GroupNode buildLineGroup(LayoutEntry e, List<TheMahjongTile> tiles,
                                      Direction outward, boolean layFlat, int doraStackIdx,
                                      int splitAfterIdx, double splitGap, int lastStackLayers) {
        Direction lineDir = outward.getCounterClockWise();
        double[] c = anchorWorldOrigin(e, outward);
        double cx = c[0], cy = c[1], cz = c[2];
        float spacing  = e.f("spacing");
        float scale    = e.scale();
        float pivot    = e.f("pivot");
        int stackHeight = Math.max(1, e.i("stackHeight"));
        float stackGap  = e.f("stackGap");
        // Walls are conventionally face-down. Detect via name — both the live wall
        // and the dead wall opt in this way.
        boolean faceDown = e.name().equals("wall") || e.name().equals("deadWall");

        Quaternionf rotFaceDown = baseTileRotation(e, outward, layFlat, faceDown);
        Quaternionf rotFaceUp   = faceDown ? baseTileRotation(e, outward, layFlat, false) : rotFaceDown;
        int n = tiles.size();
        // Total length of the row including any split gap, used by pivot.
        double extraSpan = (splitAfterIdx >= 0 && splitAfterIdx < n - 1) ? splitGap : 0;
        double pivotShift = pivot * ((n - 1) * spacing + extraSpan);

        // Centroid compensation for the dora flip: the tile model's geometric centre
        // generally isn't at the model origin (our tile is 14 px tall in a 16 px
        // block and anchored to the floor, so its Y centroid sits below origin).
        // Rotating face-up vs face-down maps that Y offset to opposite sides of the
        // outward axis, so a face-up tile sitting in a row of face-down tiles ends
        // up shifted in the outward direction. Read the actual offset from the
        // baked model AABB rather than hardcoding it — works for any tile model.
        double doraShiftX = 0, doraShiftZ = 0;
        if (faceDown && doraStackIdx >= 0 && !tiles.isEmpty()) {
            var aabb = makeTileNode(tiles.get(0)).naturalLocalAabb();
            double yMidLocal = (aabb.minY + aabb.maxY) * 0.5;
            // Shift = (face_down_centroid - face_up_centroid) along world =
            //        2 × yMidLocal × outward × scale.
            double along = 2 * yMidLocal * scale;
            doraShiftX = outward.getStepX() * along;
            doraShiftZ = outward.getStepZ() * along;
        }

        GroupNode group = new GroupNode();
        for (int i = 0; i < n; i++) {
            double splitOffset = (splitAfterIdx >= 0 && i > splitAfterIdx) ? splitGap : 0;
            double offset = i * spacing + splitOffset - pivotShift;
            double x = cx + lineDir.getStepX() * offset;
            double z = cz + lineDir.getStepZ() * offset;
            // Last stack may be partial: drawn-from stacks have only the bottom tile
            // remaining (the top was drawn off). lastStackLayers < 0 means full stack.
            int layersHere = (i == n - 1 && lastStackLayers >= 0) ? lastStackLayers : stackHeight;
            for (int layer = 0; layer < layersHere; layer++) {
                BlockModelNode node = makeTileNode(tiles.get(i));
                // The dora indicator: top tile of doraStackIdx stack flips face-up.
                boolean isDoraIndicator = (i == doraStackIdx) && (layer == stackHeight - 1);
                double tx = isDoraIndicator ? x + doraShiftX : x;
                double tz = isDoraIndicator ? z + doraShiftZ : z;
                node.transform.setPos(tx, cy + layer * stackGap, tz);
                node.transform.setRotation(isDoraIndicator ? rotFaceUp : rotFaceDown);
                node.transform.setScale(scale);
                group.addChild(node);
            }
        }
        return group;
    }

    /** Convenience overload — no dora flip, no split, full last stack. */
    private GroupNode buildLineGroup(LayoutEntry e, List<TheMahjongTile> tiles,
                                      Direction outward, boolean layFlat) {
        return buildLineGroup(e, tiles, outward, layFlat,
                /* doraStackIdx= */ -1, /* splitAfterIdx= */ -1, /* splitGap= */ 0,
                /* lastStackLayers= */ -1);
    }

    /** Convenience overload — dora flip, no split, full last stack. */
    private GroupNode buildLineGroup(LayoutEntry e, List<TheMahjongTile> tiles,
                                      Direction outward, boolean layFlat, int doraStackIdx) {
        return buildLineGroup(e, tiles, outward, layFlat,
                doraStackIdx, /* splitAfterIdx= */ -1, /* splitGap= */ 0,
                /* lastStackLayers= */ -1);
    }

    /**
     * Live-mode dead wall: {@code count} stacks of {@code stackHeight}, all face-down
     * except the dora indicator (top tile of stack {@code count-3}) which rotates
     * smoothly from face-down to face-up as {@code doraFlipProgress} runs 0→1.
     * Kan doras / ura doras / rinshan-drawn flips are not yet wired here — those
     * land when their gameplay events do.
     */
    /**
     * Live dead-wall builder, driven entirely by round state:
     * <ul>
     *   <li><b>Initial dora indicator</b> at stack {@code count − 3}, top tile —
     *       face-up via {@code doraFlipProgress} (which animates 0→1 during
     *       {@link MatchPhase.Stage#DORA_FLIP} and stays at 1 thereafter).</li>
     *   <li><b>Kan dora indicators</b> at stacks {@code count − 4 ..} as kans
     *       declare. Count = {@code round.revealedDoraCount() − 1}; tile values
     *       come from {@code round.doraIndicators().get(k)}.</li>
     *   <li><b>Rinshan-drawn tiles removed</b>: each kan drops one of the four
     *       rinshan tiles. Count = {@code 4 − round.rinshanTiles().size()}.
     *       Drawn tiles are skipped from the render and replaced (visually)
     *       by L# tiles shifted in from the live wall, keeping the dead wall
     *       at 14 visible tiles.</li>
     *   <li><b>Ura dora reveal</b>: when {@code revealUraDora} is true (a riichi
     *       player just won), the bottom tile of every revealed dora stack
     *       flips face-up showing {@code round.uraDoraIndicators().get(k)} and
     *       slides outward along {@code -outward} so it pokes out from beneath
     *       the dora indicator above.</li>
     * </ul>
     *
     * <p>{@code visibleTiles} clamps the build for the {@link MatchPhase.Stage#WALL_BUILDING}
     * deal-animation; once dealing finishes it's the full count and game-state
     * features kick in.
     */
    private GroupNode buildLiveDeadWall(LayoutEntry e, Direction outward,
                                         TheMahjongTile placeholder, double doraFlipProgress,
                                         int visibleTiles, com.themahjong.TheMahjongRound round,
                                         boolean revealUraDora) {
        Direction lineDir = outward.getCounterClockWise();
        double[] c = anchorWorldOrigin(e, outward);
        double cx = c[0], cy = c[1], cz = c[2];
        float spacing  = e.f("spacing");
        float scale    = e.scale();
        float pivot    = e.f("pivot");
        int stackHeight = Math.max(1, e.i("stackHeight"));
        float stackGap  = e.f("stackGap");
        int n           = e.i("count");
        int doraStackIdx = n - 3;
        int dirSign = e.i("flipDir") == 0 ? 1 : -1;
        float uraFraction = e.f("uraDoraFraction");

        // Game-state derived counters. During WALL_BUILDING / setup these all
        // collapse to "initial dora face-down + no kan doras + no rinshan
        // drawn", so the dealing animation reads the same as before.
        int revealedDora = Math.max(1, Math.min(
                com.themahjong.TheMahjongRound.MAX_DORA_INDICATORS, round.revealedDoraCount()));
        int kanDoras = revealedDora - 1;
        int rinshanDrawn = Math.max(0, Math.min(4,
                4 - round.rinshanTiles().size()));
        int extraStacks = (rinshanDrawn + 1) / 2;

        Quaternionf rotFaceDown = baseTileRotation(e, outward, /* layFlat= */ true, /* faceDown= */ true);
        Quaternionf rotFaceUp   = baseTileRotation(e, outward, /* layFlat= */ true, /* faceDown= */ false);
        double pivotShift = pivot * (n - 1) * spacing;

        var aabb = makeTileNode(placeholder).naturalLocalAabb();
        double yMidLocal = (aabb.minY + aabb.maxY) * 0.5;
        double tileDepth = aabb.maxY - aabb.minY;
        double centroidAlong = 2 * yMidLocal * scale;
        double centroidShiftX = outward.getStepX() * centroidAlong;
        double centroidShiftZ = outward.getStepZ() * centroidAlong;
        double uraSlide = uraFraction * tileDepth * scale;

        // Map stackIdx → which dora index lives there (0 = initial, 1+ = kan).
        java.util.Map<Integer, Integer> doraStackIdxToK = new java.util.HashMap<>();
        for (int k = 0; k <= kanDoras; k++) {
            int idx = doraStackIdx - k;
            if (idx >= 0 && idx < n) doraStackIdxToK.put(idx, k);
        }

        // visibleTiles is only meaningful during WALL_BUILDING — it gates how
        // many stacks have been laid down so far. Outside that stage the wall
        // is full and game-state branches drive content. We approximate by:
        // when visibleTiles < fullCount, render only the WALL_BUILDING subset
        // (face-down placeholders, no dora/kan/ura/shift). When full, render
        // the rich state.
        int fullCount = n * stackHeight;
        boolean partialBuild = visibleTiles < fullCount;

        GroupNode group = new GroupNode();

        if (partialBuild) {
            int totalVisibleTiles = Math.max(0, visibleTiles);
            int renderedStacks = (totalVisibleTiles + stackHeight - 1) / stackHeight;
            int lastStackLayers = renderedStacks > 0
                    ? totalVisibleTiles - (renderedStacks - 1) * stackHeight
                    : 0;
            // Build leftmost-first (stack n-1 → 0) so the deal animation reads
            // as one continuous left-to-right sweep on the dealer's row.
            for (int orderIdx = 0; orderIdx < renderedStacks; orderIdx++) {
                int i = (n - 1) - orderIdx;
                double offset = (i * spacing - pivotShift) * dirSign;
                double x = cx + lineDir.getStepX() * offset;
                double z = cz + lineDir.getStepZ() * offset;
                boolean isNewest = (orderIdx == renderedStacks - 1);
                int layersHere = isNewest ? lastStackLayers : stackHeight;
                for (int layer = 0; layer < layersHere; layer++) {
                    BlockModelNode node = makeTileNode(placeholder);
                    boolean isTop = (layer == stackHeight - 1);
                    boolean isInitialDora = (i == doraStackIdx) && isTop;
                    double tx = x;
                    double ty = cy + layer * stackGap;
                    double tz = z;
                    Quaternionf rot;
                    if (isInitialDora && doraFlipProgress > 0) {
                        rot = new Quaternionf(rotFaceDown).slerp(rotFaceUp, (float) doraFlipProgress);
                        double along = centroidAlong * doraFlipProgress;
                        tx += outward.getStepX() * along;
                        tz += outward.getStepZ() * along;
                    } else {
                        rot = rotFaceDown;
                    }
                    node.transform.setPos(tx, ty, tz);
                    node.transform.setRotation(rot);
                    node.transform.setScale(scale);
                    group.addChild(node);
                }
            }
            return group;
        }

        // Post-dealing: rich render with kan-dora flips, rinshan-drawn skips,
        // shifted-in L# stacks, optional ura-dora reveal.
        //
        // Stack indexing: original dead-wall stacks live at i = 0..n-1, with
        // stack 0 adjacent to where the live wall ends and stack n-1 at the
        // rinshan-pile end. R# tiles drain from n-1 / n-2 (rinshan side).
        // Kan-replacement compensation comes from the LIVE WALL — so the
        // L# stacks appear at NEGATIVE indices (i = -1, -2), extending the
        // dead wall back toward the seat-edge end. Putting them at i ≥ n
        // (past the rinshan side) was the bug.
        for (int i = -extraStacks; i < n; i++) {
            double offset = (i * spacing - pivotShift) * dirSign;
            double x = cx + lineDir.getStepX() * offset;
            double z = cz + lineDir.getStepZ() * offset;
            Integer kHere = i >= 0 ? doraStackIdxToK.get(i) : null;
            boolean isDoraStack = kHere != null;
            for (int layer = 0; layer < stackHeight; layer++) {
                if (i >= 0) {
                    // Original dead-wall stacks: skip R# tiles already drawn
                    // out as kan-replacement.
                    int drawnIdx = -1;
                    if (i == n - 1 && layer == stackHeight - 1) drawnIdx = 1;
                    else if (i == n - 1 && layer == stackHeight - 2) drawnIdx = 2;
                    else if (i == n - 2 && layer == stackHeight - 1) drawnIdx = 3;
                    else if (i == n - 2 && layer == stackHeight - 2) drawnIdx = 4;
                    if (drawnIdx > 0 && rinshanDrawn >= drawnIdx) continue;
                } else {
                    // L# compensation stacks shifted in from the live wall.
                    // i = -1 holds L1 (bottom) / L2 (top); i = -2 holds L3/L4.
                    int extIdx = -1 - i; // 0 for i=-1, 1 for i=-2
                    int presentIdx;
                    if (layer == 0) presentIdx = extIdx * 2 + 1;          // L1, L3
                    else if (layer == 1) presentIdx = extIdx * 2 + 2;     // L2, L4
                    else presentIdx = Integer.MAX_VALUE;
                    if (rinshanDrawn < presentIdx) continue;
                }

                boolean isTop = (layer == stackHeight - 1);
                TheMahjongTile tileToRender = placeholder;
                boolean tileFaceUp = false;
                double tx = x;
                double ty = cy + layer * stackGap;
                double tz = z;
                Quaternionf rot;

                if (isTop && isDoraStack) {
                    // Top tile is a dora indicator — show real value face-up.
                    int k = kHere;
                    if (k < round.doraIndicators().size()) {
                        tileToRender = round.doraIndicators().get(k);
                    }
                    if (k == 0) {
                        // Animate the initial dora flip during DORA_FLIP stage;
                        // post-stage doraFlipProgress is held at 1.
                        rot = new Quaternionf(rotFaceDown).slerp(rotFaceUp, (float) doraFlipProgress);
                        double along = centroidAlong * doraFlipProgress;
                        tx += outward.getStepX() * along;
                        tz += outward.getStepZ() * along;
                        tileFaceUp = doraFlipProgress >= 1.0;
                    } else {
                        rot = rotFaceUp;
                        tx += centroidShiftX;
                        tz += centroidShiftZ;
                        tileFaceUp = true;
                    }
                } else if (!isTop && isDoraStack && revealUraDora) {
                    // Bottom tile becomes ura dora when revealed (riichi-win).
                    int k = kHere;
                    if (k < round.uraDoraIndicators().size()) {
                        tileToRender = round.uraDoraIndicators().get(k);
                    }
                    rot = rotFaceUp;
                    tx += -outward.getStepX() * uraSlide + centroidShiftX;
                    tz += -outward.getStepZ() * uraSlide + centroidShiftZ;
                    tileFaceUp = true;
                } else {
                    rot = rotFaceDown;
                }

                BlockModelNode node = makeTileNode(tileFaceUp ? tileToRender : placeholder);
                node.transform.setPos(tx, ty, tz);
                node.transform.setRotation(rot);
                node.transform.setScale(scale);
                group.addChild(node);
            }
        }
        return group;
    }

    /**
     * Dead wall builder. {@code count} stacks of {@code stackHeight} tiles, all
     * face-down by default. Top tile of {@code doraStackIdx} stack is the initial
     * dora indicator (face-up). Top tiles of the next {@code kanDoras} stacks to
     * the left are kan dora indicators (face-up). When {@code uraDora=1}, the
     * bottom tile of every revealed-dora stack flips face-up and slides
     * {@code uraDoraOffset} along {@code +outward} so it pokes out from beneath
     * the indicator above it.
     *
     * <p>Centroid compensation (see {@link #buildLineGroup}) is applied to every
     * face-up tile so they sit aligned with the surrounding face-down tiles.
     */
    private GroupNode buildDeadWallGroup(LayoutEntry e, Direction outward, TheMahjongTile placeholder) {
        Direction lineDir = outward.getCounterClockWise();
        double[] c = anchorWorldOrigin(e, outward);
        double cx = c[0], cy = c[1], cz = c[2];
        float spacing = e.f("spacing");
        float scale   = e.scale();
        float pivot   = e.f("pivot");
        int stackHeight = Math.max(1, e.i("stackHeight"));
        float stackGap  = e.f("stackGap");
        int n           = e.i("count");
        // WRC §7.5: dora indicator sits on the 3rd stack from the rinshan end.
        int doraStackIdx = n - 3;
        int kanDoras     = Math.max(0, Math.min(4, e.i("kanDoras")));
        boolean showUra      = e.i("uraDora") > 0;
        float uraFraction    = e.f("uraDoraFraction");
        // flipDir reverses the line direction: stack 0 sits at anchor, stack N-1
        // grows -lineDir instead of +lineDir. Use this to put the rinshan tiles
        // (stacks 5, 6 in standard layout) at whichever side reads as "the end
        // far from the live wall break".
        int dirSign = e.i("flipDir") == 0 ? 1 : -1;
        int rinshanDrawn = Math.max(0, Math.min(4, e.i("rinshanDrawn")));

        Quaternionf rotFaceDown = baseTileRotation(e, outward, /* layFlat= */ true, /* faceDown= */ true);
        Quaternionf rotFaceUp   = baseTileRotation(e, outward, /* layFlat= */ true, /* faceDown= */ false);
        double pivotShift = pivot * (n - 1) * spacing;

        // Centroid compensation: face-up tiles in a face-down row need to be shifted
        // by 2 × yMidLocal × scale × outward to align with face-down centroids.
        // Also derive the ura slide distance from the tile's native depth-along-outward
        // (= native Y extent post lay-flat = aabb.maxY - aabb.minY) so the fraction
        // is independent of which tile model is used.
        var aabb = makeTileNode(placeholder).naturalLocalAabb();
        double yMidLocal = (aabb.minY + aabb.maxY) * 0.5;
        double tileDepth = aabb.maxY - aabb.minY;
        double centroidAlong = 2 * yMidLocal * scale;
        double centroidShiftX = outward.getStepX() * centroidAlong;
        double centroidShiftZ = outward.getStepZ() * centroidAlong;
        double uraSlide = uraFraction * tileDepth * scale;

        // Stacks whose top tile is a dora indicator (and bottom tile is an ura dora).
        java.util.Set<Integer> doraStacks = new java.util.HashSet<>();
        if (doraStackIdx >= 0 && doraStackIdx < n) doraStacks.add(doraStackIdx);
        for (int k = 1; k <= kanDoras; k++) {
            int idx = doraStackIdx - k;
            if (idx >= 0 && idx < n) doraStacks.add(idx);
        }

        // WRC §8.13 rinshan model: each kan removes one R# tile from the far-end
        // stacks (n-1, n-2) and shifts one L# tile from the live wall into the
        // dead wall at new stacks (n, n+1), so the dead wall always holds 14
        // tiles. Rinshan draw order: R1=top of n-1, R2=bottom of n-1, R3=top of
        // n-2, R4=bottom of n-2. Shifted L# fills new stacks bottom-then-top:
        // L1=bottom of n, L2=top of n, L3=bottom of n+1, L4=top of n+1.
        int extraStacks = (rinshanDrawn + 1) / 2;  // 0,1,1,2,2 for N=0..4
        int totalStacks = n + extraStacks;

        GroupNode group = new GroupNode();
        for (int i = 0; i < totalStacks; i++) {
            double offset = (i * spacing - pivotShift) * dirSign;
            double x = cx + lineDir.getStepX() * offset;
            double z = cz + lineDir.getStepZ() * offset;
            boolean isDoraStack = i < n && doraStacks.contains(i);
            for (int layer = 0; layer < stackHeight; layer++) {
                if (i < n) {
                    // Original dead-wall stacks: skip R# tiles already drawn.
                    int drawnIdx = -1;
                    if (i == n - 1 && layer == stackHeight - 1) drawnIdx = 1;
                    else if (i == n - 1 && layer == stackHeight - 2) drawnIdx = 2;
                    else if (i == n - 2 && layer == stackHeight - 1) drawnIdx = 3;
                    else if (i == n - 2 && layer == stackHeight - 2) drawnIdx = 4;
                    if (drawnIdx > 0 && rinshanDrawn >= drawnIdx) continue;
                } else {
                    // Shifted-in L# stacks: include only those filled so far.
                    // Layer 0 (bottom) fills first, then layer 1 (top).
                    int extIdx = i - n;
                    int presentIdx;
                    if (layer == 0) presentIdx = extIdx * 2 + 1;          // L1, L3
                    else if (layer == 1) presentIdx = extIdx * 2 + 2;     // L2, L4
                    else presentIdx = Integer.MAX_VALUE;                  // higher layers unused
                    if (rinshanDrawn < presentIdx) continue;
                }

                BlockModelNode node = makeTileNode(placeholder);
                boolean isTop = (layer == stackHeight - 1);
                boolean tileFaceUp = false;
                double tx = x;
                double ty = cy + layer * stackGap;
                double tz = z;

                if (isTop && isDoraStack) {
                    // Top tile is a dora indicator: face-up + centroid shift.
                    tileFaceUp = true;
                    tx += centroidShiftX;
                    tz += centroidShiftZ;
                } else if (!isTop && isDoraStack && showUra) {
                    // Bottom tile is the ura dora: face-up, slid out from under the
                    // dora toward the table centre (-outward direction). At
                    // uraDoraFraction=0.95 the ura sits with a 5% overlap with the
                    // dora above — visually associated but readable. Plus the same
                    // centroid shift any face-up tile needs.
                    tileFaceUp = true;
                    tx += -outward.getStepX() * uraSlide + centroidShiftX;
                    tz += -outward.getStepZ() * uraSlide + centroidShiftZ;
                }

                node.transform.setPos(tx, ty, tz);
                node.transform.setRotation(tileFaceUp ? rotFaceUp : rotFaceDown);
                node.transform.setScale(scale);
                group.addChild(node);
            }
        }
        return group;
    }

    /**
     * Grid layout — used by river. cols along lineDir, rows away from player
     * (toward table centre). Renders the first {@code tiles.size()} cells in
     * row-major order, capped at {@code cols * rows}.
     */
    private GroupNode buildGridGroup(LayoutEntry e, int cols, int rows,
                                     List<TheMahjongTile> tiles, Direction outward) {
        Direction lineDir = outward.getCounterClockWise();
        double[] c = anchorWorldOrigin(e, outward);
        double cx = c[0], cy = c[1], cz = c[2];
        float spacing    = e.f("spacing");
        float rowSpacing = e.f("rowSpacing");
        float scale      = e.scale();
        float pivot      = e.f("pivot");

        Quaternionf rot = baseTileRotation(e, outward, /* layFlat= */ true);
        double colPivotShift = pivot * (cols - 1) * spacing;
        int itemsToShow = Math.min(tiles.size(), cols * rows);

        GroupNode group = new GroupNode();
        int idx = 0;
        for (int row = 0; row < rows && idx < itemsToShow; row++) {
            for (int col = 0; col < cols && idx < itemsToShow; col++, idx++) {
                BlockModelNode node = makeTileNode(tiles.get(idx));
                double colOff = col * spacing - colPivotShift;
                double rowOff = row * rowSpacing;
                double x = cx + lineDir.getStepX() * colOff + outward.getStepX() * rowOff;
                double z = cz + lineDir.getStepZ() * colOff + outward.getStepZ() * rowOff;
                node.transform.setPos(x, cy, z);
                node.transform.setRotation(rot);
                node.transform.setScale(scale);
                group.addChild(node);
            }
        }
        return group;
    }

    /** Editor-preview overload — fills the grid with a single placeholder tile. */
    private GroupNode buildGridGroup(LayoutEntry e, int cols, int rows, int itemsToShow,
                                     Direction outward, TheMahjongTile placeholder) {
        return buildGridGroup(e, cols, rows,
                placeholderTiles(itemsToShow, placeholder), outward);
    }

    /**
     * Live-mode melds — renders the player's actual {@link com.themahjong.TheMahjongMeld}s.
     * Iterates melds along lineDir with {@code meldGap} between each. The
     * meld's {@code sidewaysTileIndex} (the tile claimed from another seat)
     * is rotated 90° around its local-up axis to mark the call source — the
     * extra width that produces matches mahjong-table convention. Ankan tiles
     * render face-up for now (traditional middle-pair-face-down can be added
     * later).
     */
    private GroupNode buildMeldsLive(LayoutEntry e, List<com.themahjong.TheMahjongMeld> melds,
                                     Direction outward) {
        Direction lineDir = outward.getCounterClockWise();
        double[] c = anchorWorldOrigin(e, outward);
        double cx = c[0], cy = c[1], cz = c[2];
        float spacing = e.f("spacing");
        float meldGap = e.f("meldGap");
        float scale   = e.scale();
        float pivot   = e.f("pivot");
        int dirSign   = e.i("flipDir") == 0 ? 1 : -1;

        Quaternionf rot = baseTileRotation(e, outward, /* layFlat= */ true);
        Quaternionf sidewaysRot = new Quaternionf(rot)
                .rotateLocalY((float) Math.toRadians(90.0));

        // Total span = sum of meld widths + gaps. Used by pivot only.
        double totalSpan = 0;
        for (int i = 0; i < melds.size(); i++) {
            totalSpan += melds.get(i).tiles().size() * spacing;
            if (i < melds.size() - 1) totalSpan += meldGap;
        }
        double pivotShift = pivot * Math.max(0, totalSpan - spacing);

        GroupNode group = new GroupNode();
        double cursor = 0;
        for (com.themahjong.TheMahjongMeld meld : melds) {
            List<TheMahjongTile> tiles = meld.tiles();
            java.util.OptionalInt sideways = meld.sidewaysTileIndex();
            for (int t = 0; t < tiles.size(); t++) {
                double offset = (cursor + t * spacing - pivotShift) * dirSign;
                double x = cx + lineDir.getStepX() * offset;
                double z = cz + lineDir.getStepZ() * offset;
                BlockModelNode node = makeTileNode(tiles.get(t));
                node.transform.setPos(x, cy, z);
                node.transform.setRotation(
                        sideways.isPresent() && sideways.getAsInt() == t ? sidewaysRot : rot);
                node.transform.setScale(scale);
                group.addChild(node);
            }
            cursor += tiles.size() * spacing + meldGap;
        }
        return group;
    }

    /**
     * Melds layout — meldCount stacks along lineDir, each stack tilesPerMeld
     * tiles wide. Renders only the first {@code tilesToShow} tiles in
     * (meld, tile) row-major order.
     */
    private GroupNode buildMeldsGroup(LayoutEntry e, int tilesToShow, Direction outward,
                                      TheMahjongTile placeholder) {
        Direction lineDir = outward.getCounterClockWise();
        double[] c = anchorWorldOrigin(e, outward);
        double cx = c[0], cy = c[1], cz = c[2];
        float spacing       = e.f("spacing");
        float meldGap       = e.f("meldGap");
        float scale         = e.scale();
        float pivot         = e.f("pivot");
        int meldCount       = e.i("meldCount");
        int tilesPerMeld    = e.i("tilesPerMeld");

        Quaternionf rot = baseTileRotation(e, outward, /* layFlat= */ true);

        // Total span = each meld is tilesPerMeld tiles wide + (meldCount-1) gaps between melds.
        double meldWidth = tilesPerMeld * spacing;
        double totalSpan = meldCount * meldWidth + Math.max(0, meldCount - 1) * meldGap;
        // Anchor pivot acts on the total span — first tile of first meld at offset 0
        // when pivot=0; last tile of last meld at offset 0 when pivot=1.
        double pivotShift = pivot * (totalSpan - spacing);  // span less one tile, since indices count tile centres
        // flipDir reverses the line direction so growth goes -lineDir instead of +lineDir.
        // Combined with pivot you can place the anchor anywhere and choose either growth direction.
        int dirSign = e.i("flipDir") == 0 ? 1 : -1;

        GroupNode group = new GroupNode();
        int idx = 0;
        for (int m = 0; m < meldCount && idx < tilesToShow; m++) {
            double meldStart = m * (meldWidth + meldGap);
            for (int t = 0; t < tilesPerMeld && idx < tilesToShow; t++, idx++) {
                BlockModelNode node = makeTileNode(placeholder);
                double offset = (meldStart + t * spacing - pivotShift) * dirSign;
                double x = cx + lineDir.getStepX() * offset;
                double z = cz + lineDir.getStepZ() * offset;
                node.transform.setPos(x, cy, z);
                node.transform.setRotation(rot);
                node.transform.setScale(scale);
                group.addChild(node);
            }
        }
        return group;
    }

    /**
     * Builds the 4×2 action button grid for {@code seat}. Cols extend laterally
     * along {@code lineDir} (= outward.getCounterClockWise()), rows extend
     * outward (in the table plane). Buttons face the seat occupant.
     *
     * <p>{@code configs} provides label / palette / pulse per slot; the grid
     * renders {@code min(configs.size(), cols*rows)} buttons. {@code ghostTiles}
     * (per-slot, may be empty) renders a small row of preview tiles next to
     * each button — used for chi/pon/kan to show which combination the button
     * commits to. {@code clickable} attaches {@code AREA_BUTTON} interactivity;
     * editor preview passes {@code false} so the placeholder buttons hover but
     * don't dispatch real actions.
     */
    private GroupNode buildButtonsGroup(int seat, Direction outward,
                                        List<PreviewBtn> configs,
                                        @Nullable List<List<TheMahjongTile>> ghostTiles,
                                        boolean clickable) {
        Direction lineDir = outward.getCounterClockWise();
        double[] c = anchorWorldOrigin(BUTTONS, outward);
        double cx = c[0], cy = c[1], cz = c[2];
        int cols = Math.max(1, BUTTONS.i("cols"));
        int rows = Math.max(1, BUTTONS.i("rows"));
        float colSpacing = BUTTONS.f("colSpacing");
        float rowSpacing = BUTTONS.f("rowSpacing");
        float colPivot   = BUTTONS.f("colPivot");
        float rowPivot   = BUTTONS.f("rowPivot");
        float textScale  = BUTTONS.scale();
        int slotCount    = Math.min(configs.size(), cols * rows);

        Quaternionf rot = baseTileRotation(BUTTONS, outward, /* layFlat= */ true);
        // Ghost-tile rotation/scale/spacing/offsets all live on BUTTON_GHOSTS
        // so they're tunable from the F8 editor side panel.
        Quaternionf tileRot = baseTileRotation(BUTTON_GHOSTS, outward, /* layFlat= */ true);
        float ghostScale = BUTTON_GHOSTS.scale();
        double ghostSpacing       = BUTTON_GHOSTS.f("spacing");
        double ghostLateral       = BUTTON_GHOSTS.pos().x;
        double ghostYLift         = BUTTON_GHOSTS.pos().y;
        double ghostOutwardOffset = BUTTON_GHOSTS.pos().z;
        float rightPadPerTilePx   = BUTTONS.f("rightPadPerTile");
        // Plate's right side in world (the side where text reads to). The
        // BUTTONS layout entry uses {@code tileRoll=180} to flip the plate
        // so text reads correctly from the seat's viewpoint, which mirrors
        // plate-local +X to {@code lineDir = outward.getCounterClockWise()}.
        // Anchoring ghosts on this direction keeps the row count-invariant —
        // a 3-tile CHI and 4-tile KAN both start at the plate's right edge
        // and grow further right.
        Direction plateRightDir = outward.getCounterClockWise();

        // Per-row button count so each row is centred on its own count rather
        // than the full {@code cols} width — a single button lands at the
        // grid centre instead of slot 0 (which would push it to one side via
        // colPivot=0.5). Last row may be partial; earlier rows are full.
        int[] perRowCount = new int[Math.max(rows, 1)];
        for (int r = 0; r < rows; r++) {
            int start = r * cols;
            perRowCount[r] = Math.max(0, Math.min(cols, slotCount - start));
        }
        double rowShift = rowPivot * (rows - 1) * rowSpacing;

        GroupNode group = new GroupNode();
        int idx = 0;
        outer: for (int r = 0; r < rows; r++) {
            int countInRow = perRowCount[r];
            if (countInRow == 0) break;
            // Centre this row on its own button count so a partial row reads
            // as centred under the grid axis, not left-aligned.
            double rowColShift = colPivot * (countInRow - 1) * colSpacing;
            for (int col = 0; col < countInRow; col++, idx++) {
                if (idx >= slotCount) break outer;
                double colOff = col * colSpacing - rowColShift;
                double rowOff = r * rowSpacing - rowShift;
                double x = cx + lineDir.getStepX() * colOff + outward.getStepX() * rowOff;
                double z = cz + lineDir.getStepZ() * colOff + outward.getStepZ() * rowOff;
                double y = cy;
                PreviewBtn cfg = configs.get(idx);
                int ghostCount = (ghostTiles != null && idx < ghostTiles.size())
                        ? ghostTiles.get(idx).size() : 0;
                float extraRightPad = rightPadPerTilePx * ghostCount;
                WorldButtonNode btn = new WorldButtonNode(cfg.label())
                        .setTextScale(textScale)
                        .setTextColor(cfg.textColor())
                        .setBgColor(cfg.bgColor())
                        .setPulsing(cfg.pulsing())
                        .setExtraRightPad(extraRightPad);
                if (clickable) {
                    btn.makeClickable(new com.riichimahjongforge.cuterenderer.InteractKey.SeatSlot(
                            (byte) seat,
                            com.riichimahjongforge.cuterenderer.InteractKey.SeatSlot.AREA_BUTTON,
                            (short) idx));
                }
                btn.transform.setPos(x, y, z);
                btn.transform.setRotation(rot);
                group.addChild(btn);

                // Ghost-tile preview row: laid out along lineDir, centred on
                // the button's x, offset toward table centre (-outward) so it
                // sits between the button and the table interior. No hover-
                // lift coupling for now; tiles stay put while the button lifts.
                if (ghostTiles != null && idx < ghostTiles.size()) {
                    List<TheMahjongTile> preview = ghostTiles.get(idx);
                    int n = preview.size();
                    if (n > 0) {
                        // Ghost-tile row anchors on the plate's text+padding
                        // right edge and grows further right. Count-invariant:
                        // a 3-tile CHI and a 4-tile KAN both start at the
                        // same anchor. BUTTON_GHOSTS.pos.x shifts the whole
                        // row along plateRight from that anchor.
                        // Ghosts attach as CHILDREN of the button so they
                        // inherit its hover-lift; their poses are computed in
                        // button-local space (rot.inverse applied to the
                        // world-space target).
                        double plateRightEdgeWorld = btn.plateHalfWidthWorld();
                        double ox = -outward.getStepX() * ghostOutwardOffset;
                        double oz = -outward.getStepZ() * ghostOutwardOffset;
                        Quaternionf btnInvRot = new Quaternionf(rot).invert();
                        for (int i = 0; i < n; i++) {
                            double along = plateRightEdgeWorld + i * ghostSpacing + ghostLateral;
                            double tx = x + plateRightDir.getStepX() * along + ox;
                            double tz = z + plateRightDir.getStepZ() * along + oz;
                            // World-relative offset → button-local (apply rot⁻¹).
                            Vector3f localOffset = new Vector3f(
                                    (float) (tx - x),
                                    (float) ghostYLift,
                                    (float) (tz - z));
                            btnInvRot.transform(localOffset);
                            // World tile rotation → button-local (rot⁻¹ * tileRot).
                            Quaternionf localTileRot = new Quaternionf(btnInvRot).mul(tileRot);

                            BlockModelNode node = makeTileNode(preview.get(i));
                            node.transform.setPos(localOffset.x, localOffset.y, localOffset.z);
                            node.transform.setRotation(localTileRot);
                            node.transform.setScale(ghostScale);
                            btn.addChild(node);
                        }
                    }
                }
            }
        }
        return group;
    }

    /** Editor-preview convenience — fills with the hardcoded {@link #PREVIEW_BUTTONS}
     *  showcase plus their {@link #PREVIEW_GHOSTS} preview rows so the F8
     *  editor can tune ghost-tile layout. Not clickable in preview. */
    private GroupNode buildButtonsGroup(int seat, Direction outward) {
        return buildButtonsGroup(seat, outward,
                java.util.Arrays.asList(PREVIEW_BUTTONS),
                PREVIEW_GHOSTS,
                /* clickable= */ false);
    }

    /** Map a {@link PlayerAction} to its button look. Win declarations get the
     *  red pulsing palette; PASS gets gray; everything else default. */
    private static PreviewBtn configForButton(MahjongTableHumanPlayer.TableButton btn) {
        if (btn instanceof MahjongTableHumanPlayer.TableButton.RiichiToggle r) {
            // Active = brighter gold + pulsing so the player sees the mode is armed.
            return r.active()
                    ? new PreviewBtn(actionLabel("riichi"), 0xFFFFE066, 0xC07A5A1A, true)
                    : new PreviewBtn(actionLabel("riichi"), 0xFFFFD700, 0xB05A4218, false);
        }
        if (btn instanceof MahjongTableHumanPlayer.TableButton.SubmitAction sa) {
            return configForAction(sa.action());
        }
        return new PreviewBtn(Component.literal("?"), 0xFFE8E8E8, 0xB0444A58, false);
    }

    private static List<TheMahjongTile> ghostTilesForButton(
            MahjongTableHumanPlayer.TableButton btn, com.themahjong.TheMahjongRound round) {
        if (btn instanceof MahjongTableHumanPlayer.TableButton.SubmitAction sa) {
            return ghostTilesForAction(sa.action(), round);
        }
        return List.of();
    }

    private static PreviewBtn configForAction(com.themahjong.driver.PlayerAction action) {
        if (action instanceof com.themahjong.driver.PlayerAction.DeclareRon)
            return new PreviewBtn(actionLabel("ron"),     0xFFFF5757, 0xC05A2222, true);
        if (action instanceof com.themahjong.driver.PlayerAction.DeclareChankan)
            return new PreviewBtn(actionLabel("chankan"), 0xFFFF5757, 0xC05A2222, true);
        if (action instanceof com.themahjong.driver.PlayerAction.DeclareTsumo)
            return new PreviewBtn(actionLabel("tsumo"),   0xFFFF6A6A, 0xC05A2626, true);
        if (action instanceof com.themahjong.driver.PlayerAction.Pon)
            return new PreviewBtn(actionLabel("pon"),     0xFFE8E8E8, 0xB0444A58, false);
        if (action instanceof com.themahjong.driver.PlayerAction.Daiminkan)
            return new PreviewBtn(actionLabel("kan"),     0xFFE8E8E8, 0xB0444A58, false);
        if (action instanceof com.themahjong.driver.PlayerAction.Ankan)
            return new PreviewBtn(actionLabel("ankan"),   0xFFE8E8E8, 0xB0444A58, false);
        if (action instanceof com.themahjong.driver.PlayerAction.Kakan)
            return new PreviewBtn(actionLabel("kan"),     0xFFE8E8E8, 0xB0444A58, false);
        if (action instanceof com.themahjong.driver.PlayerAction.Chi)
            return new PreviewBtn(actionLabel("chi"),     0xFFE8E8E8, 0xB0444A58, false);
        if (action instanceof com.themahjong.driver.PlayerAction.DeclareKita)
            return new PreviewBtn(actionLabel("kita"),    0xFFE8E8E8, 0xB0444A58, false);
        if (action instanceof com.themahjong.driver.PlayerAction.KyuushuAbort)
            return new PreviewBtn(actionLabel("abort"),   0xFFE8E8E8, 0xB0393D48, false);
        if (action instanceof com.themahjong.driver.PlayerAction.Pass)
            return new PreviewBtn(actionLabel("pass"),    0xFFBFC6D2, 0xB0393D48, false);
        return new PreviewBtn(Component.literal("?"),     0xFFE8E8E8, 0xB0444A58, false);
    }

    /** Tiles to display as ghost preview next to {@code action}'s button. The
     *  full meld it would form (hand tiles + the called/added tile when
     *  applicable). Empty for actions without a tile preview (PASS, ABORT,
     *  declarations whose composition is trivial / showed elsewhere). */
    private static List<TheMahjongTile> ghostTilesForAction(
            com.themahjong.driver.PlayerAction action,
            com.themahjong.TheMahjongRound round) {
        TheMahjongTile called = activeTileOrNull(round);
        if (action instanceof com.themahjong.driver.PlayerAction.Pon p) {
            return appendNullable(p.handTiles(), called);
        }
        if (action instanceof com.themahjong.driver.PlayerAction.Daiminkan dk) {
            return appendNullable(dk.handTiles(), called);
        }
        if (action instanceof com.themahjong.driver.PlayerAction.Chi c) {
            return appendNullable(c.handTiles(), called);
        }
        if (action instanceof com.themahjong.driver.PlayerAction.Ankan ak) {
            return ak.handTiles();
        }
        if (action instanceof com.themahjong.driver.PlayerAction.Kakan kk) {
            List<TheMahjongTile> out = new java.util.ArrayList<>(kk.upgradedFrom().tiles());
            out.add(kk.addedTile());
            return out;
        }
        if (action instanceof com.themahjong.driver.PlayerAction.DeclareKita dk) {
            return List.of(dk.tile());
        }
        return List.of();
    }

    private static List<TheMahjongTile> appendNullable(List<TheMahjongTile> base, @Nullable TheMahjongTile extra) {
        if (extra == null) return base;
        List<TheMahjongTile> out = new java.util.ArrayList<>(base.size() + 1);
        out.addAll(base);
        out.add(extra);
        return out;
    }

    @Nullable
    private static TheMahjongTile activeTileOrNull(com.themahjong.TheMahjongRound round) {
        var at = round.activeTile();
        if (at instanceof com.themahjong.TheMahjongRound.ActiveTile.Drawn d) return d.tile();
        if (at instanceof com.themahjong.TheMahjongRound.ActiveTile.HeldDiscard h) return h.tile();
        if (at instanceof com.themahjong.TheMahjongRound.ActiveTile.HeldKita h) return h.tile();
        if (at instanceof com.themahjong.TheMahjongRound.ActiveTile.HeldKakan h) return h.tile();
        return null;
    }

    /**
     * Tile values mentioned by any {@code Discard} or {@code DiscardWithRiichi}
     * legal action. Used to outline hand tiles the local player may legally
     * pick during their AwaitingDiscard turn — covers post-draw, post-pon,
     * post-chi, and riichi-eligible cases uniformly (kuikae-restricted tiles
     * are simply absent from the legal list, so they don't get highlighted).
     */
    private static java.util.Set<TheMahjongTile> collectLegalDiscardTiles(
            List<com.themahjong.driver.PlayerAction> legal, boolean riichiArmed) {
        java.util.Set<TheMahjongTile> out = new java.util.HashSet<>();
        for (com.themahjong.driver.PlayerAction a : legal) {
            if (riichiArmed) {
                // Riichi armed → only outline tiles that keep tenpai (i.e. those
                // the engine returns as DiscardWithRiichi candidates). Plain
                // Discard tiles that don't keep tenpai shouldn't read as legal.
                if (a instanceof com.themahjong.driver.PlayerAction.DiscardWithRiichi r) {
                    out.add(r.tile());
                }
            } else {
                if (a instanceof com.themahjong.driver.PlayerAction.Discard d) out.add(d.tile());
                else if (a instanceof com.themahjong.driver.PlayerAction.DiscardWithRiichi r) out.add(r.tile());
            }
        }
        return out;
    }

    /**
     * Discard indices in {@code targetSeat}'s river that were claimed by
     * some other seat (their meld back-references the index via
     * {@code sourceSeat}/{@code sourceDiscardIndex}). The engine keeps
     * claimed tiles in {@code discards()} as full history; the renderer
     * skips them so they don't appear in both the river and the claimant's
     * meld. {@link com.themahjong.TheMahjongMeld.Ankan} contributes nothing
     * — closed kan has no source discard.
     */
    private static java.util.Set<Integer> claimedIndicesForSeat(
            List<TheMahjongPlayer> players, int targetSeat) {
        java.util.Set<Integer> out = new java.util.HashSet<>();
        for (TheMahjongPlayer p : players) {
            for (com.themahjong.TheMahjongMeld meld : p.melds()) {
                int srcSeat = -1;
                int srcIdx = -1;
                if (meld instanceof com.themahjong.TheMahjongMeld.Chi c) {
                    srcSeat = c.sourceSeat(); srcIdx = c.sourceDiscardIndex();
                } else if (meld instanceof com.themahjong.TheMahjongMeld.Pon pn) {
                    srcSeat = pn.sourceSeat(); srcIdx = pn.sourceDiscardIndex();
                } else if (meld instanceof com.themahjong.TheMahjongMeld.Daiminkan dk) {
                    srcSeat = dk.sourceSeat(); srcIdx = dk.sourceDiscardIndex();
                } else if (meld instanceof com.themahjong.TheMahjongMeld.Kakan kk) {
                    srcSeat = kk.upgradedFrom().sourceSeat();
                    srcIdx = kk.upgradedFrom().sourceDiscardIndex();
                }
                if (srcSeat == targetSeat && srcIdx >= 0) out.add(srcIdx);
            }
        }
        return out;
    }

    /** Local-player seat at {@code table}, or -1 if not seated. */
    private static int localSeat(MahjongTableBlockEntity table) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return -1;
        UUID uuid = mc.player.getUUID();
        List<MahjongTableBlockEntity.SeatInfo> seats = table.seats();
        for (int i = 0; i < seats.size(); i++) {
            var info = seats.get(i);
            if (info.enabled() && info.occupant().isPresent() && info.occupant().get().equals(uuid)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Build a per-tile rotation as: base orientation derived from the seat's
     * outward direction and {@code layFlat} flag, then the anchor's own
     * {@code tileYaw}/{@code tilePitch}/{@code tileRoll} extras.
     *
     * <p>Standing baseline: yaw rotates the upright tile's north art-face to
     * point along {@code outward}. Lay-flat baseline: rotateX(+90°) tips it
     * face up, then a yaw chosen so the top-of-art points toward the player —
     * mirrors {@link com.riichimahjongforge.cuterenderer.Transform#layFlatReadableFrom}.
     */
    private static Quaternionf baseTileRotation(LayoutEntry e, Direction outward, boolean layFlat) {
        return baseTileRotation(e, outward, layFlat, /* faceDown= */ false);
    }

    private static Quaternionf baseTileRotation(LayoutEntry e, Direction outward,
                                                boolean layFlat, boolean faceDown) {
        Quaternionf q = new Quaternionf();
        if (layFlat) {
            // Yaw values are offset 180° from a naive "match outward" mapping so the
            // top of the art ends up at the FAR edge (away from the player) — i.e.
            // the standard tabletop reading orientation, like reading a sign on the
            // table. Without this offset every layout needed tileRoll=180 in the
            // editor to compensate.
            float yaw = switch (outward) {
                case NORTH ->   0f;
                case EAST  -> -90f;
                case WEST  ->  90f;
                default    -> 180f; // SOUTH
            };
            q.rotateX((float) Math.toRadians(90f))
             .rotateLocalY((float) Math.toRadians(yaw));
            // Flip 180° around the tile's local X axis so art points down toward the table.
            // The tile is centred so the rotation pivots in place; Y position stays put.
            if (faceDown) q.rotateLocalX((float) Math.toRadians(180f));
        } else {
            float yaw = switch (outward) {
                case NORTH ->   0f;
                case EAST  -> -90f;
                case SOUTH -> 180f;
                case WEST  ->  90f;
                default    ->   0f;
            };
            q.rotateY((float) Math.toRadians(yaw));
        }
        // Apply per-tile rotations around the tile's NATIVE axes — independent of
        // seat orientation, so all 4 seats get the same intrinsic rotation. For a
        // standing hand tile: yaw=around tile-up, pitch=tip forward/back, roll=lean
        // sideways. For a lay-flat tile the same axes still produce consistent
        // rotation across all four sides; semantics differ but the four seats stay
        // visually identical (which is what the user wants).
        float ty = e.f("tileYaw");
        float tp = e.f("tilePitch");
        float tr = e.f("tileRoll");
        if (ty != 0f) q.rotateY((float) Math.toRadians(ty));
        if (tp != 0f) q.rotateX((float) Math.toRadians(tp));
        if (tr != 0f) q.rotateZ((float) Math.toRadians(tr));
        return q;
    }

    /** Master-local origin of an anchor for a given seat, transformed from seat-local pose. */
    private static double[] anchorWorldOrigin(LayoutEntry e, Direction outward) {
        Vector3f p = e.pos();
        Direction lineDir = outward.getCounterClockWise();
        double cx = 0.5 + outward.getStepX() * p.z + lineDir.getStepX() * p.x;
        double cz = 0.5 + outward.getStepZ() * p.z + lineDir.getStepZ() * p.x;
        return new double[] {cx, p.y, cz};
    }

    // ---- placeholders ------------------------------------------------------

    /**
     * Per-seat × per-layout placeholder tile. Seat selects the suit so the seat
     * any anchor sits on reads at a glance:
     * <ul>
     *   <li>seat 0 (East)  → MANZU</li>
     *   <li>seat 1 (South) → SOUZU</li>
     *   <li>seat 2 (West)  → PINZU</li>
     *   <li>seat 3 (North) → WIND (with HAKU dragon for the 5th layout, since
     *       there are only 4 wind ranks).</li>
     * </ul>
     * Layout-kind selects the rank within the suit so different layouts on the
     * same seat are also distinguishable: 0=hand, 1=wall, 2=river, 3=melds, 4=kita.
     */
    private static TheMahjongTile placeholderFor(int seat, int layoutKind) {
        return switch (seat) {
            case 0 -> new TheMahjongTile(TheMahjongTile.Suit.MANZU, layoutKind + 1, false);
            case 1 -> new TheMahjongTile(TheMahjongTile.Suit.SOUZU, layoutKind + 1, false);
            case 2 -> new TheMahjongTile(TheMahjongTile.Suit.PINZU, layoutKind + 1, false);
            case 3 -> layoutKind < 4
                    ? new TheMahjongTile(TheMahjongTile.Suit.WIND, layoutKind + 1, false)
                    : new TheMahjongTile(TheMahjongTile.Suit.DRAGON, 1, false); // HAKU
            default -> new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);
        };
    }

    private static List<TheMahjongTile> placeholderTiles(int count, TheMahjongTile tile) {
        TheMahjongTile[] arr = new TheMahjongTile[Math.max(0, count)];
        for (int i = 0; i < arr.length; i++) arr[i] = tile;
        return List.of(arr);
    }

    private static BlockModelNode makeTileNode(TheMahjongTile tile) {
        Block tileBlock = MahjongTileItems.blockForTile(tile);
        BlockState state;
        if (tileBlock != null) {
            state = tileBlock.defaultBlockState();
        } else {
            LOGGER.warn("Mahjong table: no tile block registered for {}; rendering STONE placeholder", tile);
            state = Blocks.STONE.defaultBlockState();
        }
        return new BlockModelNode(state);
    }

    @Nullable
    private static Direction outwardForSeat(int seat, Direction facing) {
        for (Direction cellDir : HORIZONTAL) {
            if (MahjongTableBlock.seatForDirection(cellDir, facing) == seat) {
                return cellDir;
            }
        }
        return null;
    }

    private static Component buildWindLine(int seat, MahjongTableBlockEntity table) {
        Component wind = seat >= 0 && seat < WIND_KEY_BY_SEAT.length
                ? Component.translatable(WIND_KEY_BY_SEAT[seat])
                : Component.literal("?");
        if (table.driver() == null) {
            return wind;
        }
        var roundOpt = table.driver().match().currentRound();
        if (roundOpt.isEmpty() || seat >= roundOpt.get().players().size()) {
            return wind;
        }
        return Component.translatable("riichi_mahjong_forge.label.wind_points",
                wind, roundOpt.get().players().get(seat).points());
    }

    private static Component buildNameLine(MahjongTableBlockEntity.SeatInfo info, Level level) {
        return info.occupant().map(uuid -> {
            Player p = level.getPlayerByUUID(uuid);
            return Component.literal(p != null ? p.getName().getString() : "(offline)");
        }).orElse(Component.literal("BOT"));
    }

    private static int currentTurnSeat(MahjongTableBlockEntity table) {
        if (table.driver() == null) return -1;
        return table.driver().match().currentRound()
                .map(TheMahjongRound::currentTurnSeat)
                .orElse(-1);
    }
}
