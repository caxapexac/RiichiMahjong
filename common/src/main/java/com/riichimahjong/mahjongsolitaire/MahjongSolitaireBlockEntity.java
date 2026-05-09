package com.riichimahjong.mahjongsolitaire;

import com.mojang.logging.LogUtils;
import com.riichimahjong.cuterenderer.CuteClickHandler;
import com.riichimahjong.cuterenderer.InteractKey;
import com.riichimahjong.themahjongcompat.MatchNbt;
import com.themahjong.TheMahjongTile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * Pair-the-tiles mahjong solitaire on a 3×3 multiblock table.
 *
 * <p>Server-authoritative: holds the current board id, the tile-code-per-slot array
 * (-1 = removed), and a per-player selection map. Clients receive the full state via
 * BE sync NBT and render it locally. Per-player selections are <b>session-only</b>:
 * they ride along in the sync packet so every client sees its own player's
 * selection, but they are intentionally not written to disk in
 * {@link #saveAdditional} — a world reload starts everyone with no selection.
 *
 * <p>Click flow comes from {@link com.riichimahjong.cuterenderer.CuteRenderer}
 * via {@link CuteClickHandler}. The {@link InteractKey} is always a
 * {@link InteractKey.Slot}; everything else is silently dropped.
 */
public final class MahjongSolitaireBlockEntity extends BlockEntity implements CuteClickHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** mB of Liquid XP generated per pair revealed via hopper input. */
    public static final int LIQUID_XP_PER_PAIR_MB = 10;
    /** Internal tank capacity in mB (one bucket). */
    public static final int LIQUID_XP_CAPACITY_MB = 1000;

    /** Liquid XP currently stored. Drained by per-loader fluid storage adapters
     *  (NeoForge {@code Capabilities.FluidHandler.BLOCK}, Fabric {@code FluidStorage.SIDED}). */
    private int liquidXpStored;

    @Nullable private String boardId;
    /** Length matches {@code board.slots().size()}; {@code null} entry = removed.
     *  Empty if no board loaded. Solitaire ignores the {@code redDora} flag for
     *  pair-matching (handled via {@link TheMahjongTile#matchesSuitRank}). */
    private TheMahjongTile[] tiles = new TheMahjongTile[0];
    /**
     * Stable tile-instance id per slot; entry -1 = removed. Same length as
     * {@link #tiles}. Used by the renderer to follow a tile across shuffles
     * (slot reassignments) so it can animate the glide instead of teleporting.
     */
    private int[] tileIds = new int[0];
    private long randomSeed;
    /**
     * Player UUID → currently-selected slot index. Session-only: included in the
     * sync packet (so clients can render their own player's highlight) but not
     * written to disk in {@link #saveAdditional}.
     */
    private final Map<UUID, Integer> selections = new HashMap<>();
    /** Tracks redstone rising-edge for {@link #resetBoard} triggers. */
    private boolean lastPowered;
    /**
     * True while a hopper / pipe-driven hint reveal is in progress. Used by
     * {@link #emitBoardClearEffects} to skip the celebration ding when the
     * win is purely automated — particles still fire so the player can see it
     * happened from a distance.
     */
    private boolean automatedContext = false;

    public boolean wasPowered() { return lastPowered; }
    public void setPowered(boolean powered) {
        if (this.lastPowered != powered) {
            this.lastPowered = powered;
            markChangedAndSynced();
        }
    }

    public MahjongSolitaireBlockEntity(BlockPos pos, BlockState state) {
        super(com.riichimahjong.registry.ModBlockEntities.MAHJONG_SOLITAIRE_BLOCK_ENTITY.get(), pos, state);
    }

    // -------- Read accessors used by the client renderer --------

    @Nullable public MahjongSolitaireBoard board() {
        if (boardId == null) return null;
        for (var b : MahjongSolitaireBoards.all()) {
            if (b.id().equals(boardId)) return b;
        }
        return null;
    }

    /** Tile at slot index, or {@code null} if removed / no board. */
    @Nullable
    public TheMahjongTile tileAt(int slot) {
        return (slot < 0 || slot >= tiles.length) ? null : tiles[slot];
    }

    /** Stable tile-instance id at slot index, or -1 if removed. */
    public int tileIdAt(int slot) {
        return (slot < 0 || slot >= tileIds.length) ? -1 : tileIds[slot];
    }

    /** Seed used for the current deal. The renderer watches this so a re-deal
     *  of the same board id triggers a scene rebuild. */
    public long randomSeed() { return randomSeed; }

    public int slotCount() { return tiles.length; }

    public int remainingTiles() {
        int n = 0;
        for (TheMahjongTile t : tiles) if (t != null) n++;
        return n;
    }

    public boolean isBoardEmpty() {
        return tiles.length == 0 || remainingTiles() == 0;
    }

    /** Currently-selected slot for a player, or -1. Read by both sides. */
    public int selectionFor(UUID player) {
        Integer s = selections.get(player);
        return s == null ? -1 : s;
    }

    /** True if slot {@code i} is removable per standard mahjong-solitaire rules. */
    public boolean isFree(int slotIndex) {
        MahjongSolitaireBoard b = board();
        if (b == null) return false;
        if (slotIndex < 0 || slotIndex >= tiles.length) return false;
        if (tiles[slotIndex] == null) return false;
        var s = b.slots().get(slotIndex);
        boolean above = anyTileIn(b, s.x(), s.x() + 1, s.z(), s.z() + 1, s.y() + 1);
        boolean left  = anyTileIn(b, s.x() - 1, s.x() - 1, s.z(), s.z() + 1, s.y());
        boolean right = anyTileIn(b, s.x() + 2, s.x() + 2, s.z(), s.z() + 1, s.y());
        return !above && (!left || !right);
    }

    private boolean anyTileIn(MahjongSolitaireBoard b, int xLo, int xHi, int zLo, int zHi, int y) {
        var slots = b.slots();
        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] == null) continue;
            var t = slots.get(i);
            if (t.y() != y) continue;
            int tx0 = t.x(), tx1 = t.x() + 1;
            int tz0 = t.z(), tz1 = t.z() + 1;
            if (tx1 < xLo || tx0 > xHi) continue;
            if (tz1 < zLo || tz0 > zHi) continue;
            return true;
        }
        return false;
    }

    // -------- Server-side mutations --------

    /**
     * Pick a random board, deal a solvable layout, regenerate tile ids and seed,
     * clear selections, and sync. No-op on the client side.
     */
    public void resetBoard() {
        if (level == null || level.isClientSide()) return;
        List<MahjongSolitaireBoard> all = MahjongSolitaireBoards.all();
        if (all.isEmpty()) {
            LOGGER.warn("solitaire reset: no boards available");
            this.boardId = null;
            this.tiles = new TheMahjongTile[0];
            this.tileIds = new int[0];
            selections.clear();
            markChangedAndSynced();
            return;
        }
        Random rng = new Random();
        this.randomSeed = rng.nextLong();
        Random shuffler = new Random(randomSeed);
        MahjongSolitaireBoard pick = all.get(shuffler.nextInt(all.size()));
        this.boardId = pick.id();
        this.tiles = dealSolvable(pick, shuffler);
        // Stable ids match slot indices on initial deal — every tile starts at
        // its own slot, so id == slot. Subsequent shuffles permute them.
        this.tileIds = deriveDefaultTileIds(tiles);
        selections.clear();
        markChangedAndSynced();
    }

    /**
     * Deal codes so the board is guaranteed solvable.
     *
     * <p>We simulate clearing the board from the top down: at each step pick two
     * currently-free slots in a fully-occupied board, assign them the same code,
     * and mark them removed. The recorded assignment <i>is</i> the deal — replaying
     * the recorded removal order clears the board, so the player can always solve it.
     *
     * <p>Code selection prefers tile types still under the 4-of-each cap (the size
     * of a single-deck mahjong set: 4 × 34 = 136 tiles). For boards with more than
     * 136 slots — e.g. the 144-slot Turtle — the cap is relaxed once exhausted.
     *
     * <p>Odd slot counts: the last slot is left unassigned (-1) so the board can
     * still be fully cleared by matching pairs. The boards.json data may produce
     * odd counts; we just drop the orphan.
     */
    private static int[] deriveDefaultTileIds(TheMahjongTile[] tiles) {
        int[] ids = new int[tiles.length];
        for (int i = 0; i < tiles.length; i++) ids[i] = tiles[i] != null ? i : -1;
        return ids;
    }

    /** All 34 standard suit/rank tiles (no aka-dora — solitaire treats aka and
     *  non-aka as the same tile, so dealing aka would just waste deck slots). */
    private static final List<TheMahjongTile> STANDARD_DECK_TILES = buildStandardDeckTiles();

    private static List<TheMahjongTile> buildStandardDeckTiles() {
        List<TheMahjongTile> out = new ArrayList<>(34);
        for (TheMahjongTile.Suit suit : TheMahjongTile.Suit.values()) {
            for (int rank = 1; rank <= suit.maxRank(); rank++) {
                out.add(new TheMahjongTile(suit, rank, false));
            }
        }
        return out;
    }

    private static TheMahjongTile[] dealSolvable(MahjongSolitaireBoard board, Random rng) {
        int slotCount = board.slots().size();
        int usable = slotCount - (slotCount % 2);
        boolean[] present = new boolean[slotCount];
        for (int i = 0; i < usable; i++) present[i] = true;

        TheMahjongTile[] dealt = new TheMahjongTile[slotCount];

        Map<TheMahjongTile, Integer> usage = new HashMap<>();
        ArrayList<Integer> freeBuf = new ArrayList<>();
        ArrayList<TheMahjongTile> tileBuf = new ArrayList<>();

        while (true) {
            freeBuf.clear();
            for (int i = 0; i < slotCount; i++) {
                if (present[i] && isFreeInState(board, present, i)) freeBuf.add(i);
            }
            if (freeBuf.size() < 2) break;
            int a = freeBuf.get(rng.nextInt(freeBuf.size()));
            int b;
            do { b = freeBuf.get(rng.nextInt(freeBuf.size())); } while (b == a);

            TheMahjongTile tile = pickTile(usage, rng, tileBuf);
            dealt[a] = tile;
            dealt[b] = tile;
            usage.merge(tile, 2, Integer::sum);
            present[a] = false;
            present[b] = false;
        }
        return dealt;
    }

    private static TheMahjongTile pickTile(Map<TheMahjongTile, Integer> usage, Random rng,
                                            ArrayList<TheMahjongTile> buf) {
        buf.clear();
        for (TheMahjongTile t : STANDARD_DECK_TILES) {
            if (usage.getOrDefault(t, 0) < 4) buf.add(t);
        }
        if (!buf.isEmpty()) {
            return buf.get(rng.nextInt(buf.size()));
        }
        // All 34 tiles already at 4 — board exceeds the natural deck size. Relax.
        return STANDARD_DECK_TILES.get(rng.nextInt(STANDARD_DECK_TILES.size()));
    }

    private static boolean isFreeInState(MahjongSolitaireBoard board, boolean[] present, int slotIndex) {
        var s = board.slots().get(slotIndex);
        boolean above = anyPresentTileIn(board, present, s.x(), s.x() + 1, s.z(), s.z() + 1, s.y() + 1);
        boolean left  = anyPresentTileIn(board, present, s.x() - 1, s.x() - 1, s.z(), s.z() + 1, s.y());
        boolean right = anyPresentTileIn(board, present, s.x() + 2, s.x() + 2, s.z(), s.z() + 1, s.y());
        return !above && (!left || !right);
    }

    private static boolean anyPresentTileIn(MahjongSolitaireBoard board, boolean[] present,
                                            int xLo, int xHi, int zLo, int zHi, int y) {
        var slots = board.slots();
        for (int i = 0; i < slots.size(); i++) {
            if (!present[i]) continue;
            var t = slots.get(i);
            if (t.y() != y) continue;
            int tx0 = t.x(), tx1 = t.x() + 1;
            int tz0 = t.z(), tz1 = t.z() + 1;
            if (tx1 < xLo || tx0 > xHi) continue;
            if (tz1 < zLo || tz0 > zHi) continue;
            return true;
        }
        return false;
    }

    @Override
    public void onCuteClick(ServerPlayer player, InteractKey key) {
        if (key instanceof InteractKey.Named named && "HINT".equals(named.name())) {
            handleHintClick(player);
            return;
        }
        if (!(key instanceof InteractKey.Slot s)) return;
        int slotIndex = s.index();
        if (slotIndex < 0 || slotIndex >= tiles.length) return;
        if (tiles[slotIndex] == null) return;
        if (!isFree(slotIndex)) {
            // Blocked tile — quiet feedback, no state change.
            player.playNotifySound(
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(),
                    SoundSource.BLOCKS, 0.4f, 0.6f);
            return;
        }
        UUID uuid = player.getUUID();
        Integer prev = selections.get(uuid);
        if (prev == null) {
            selections.put(uuid, slotIndex);
            markChangedAndSynced();
            return;
        }
        if (prev == slotIndex) {
            selections.remove(uuid);
            markChangedAndSynced();
            return;
        }
        if (prev >= 0 && prev < tiles.length
                && tiles[prev] != null
                && tiles[prev].matchesSuitRank(tiles[slotIndex])
                && isFree(prev)) {
            removePair(prev, slotIndex);
            // Manual two-click pair removal earns 1 XP per pair. The hopper-iron
            // automation path fills the Liquid Experience tank instead; player-
            // initiated paths (this and the hint button) drop a real XP orb.
            spawnExperienceOrb(1);
            player.playNotifySound(
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(),
                    SoundSource.BLOCKS, 0.6f, 1.4f);
            return;
        }
        // Non-matching pick — replace selection.
        selections.put(uuid, slotIndex);
        markChangedAndSynced();
    }

    /** Common pair-removal: clears tiles + ids, drops referencing selections, fires board-clear, syncs. */
    private void removePair(int a, int b) {
        tiles[a] = null;
        tiles[b] = null;
        tileIds[a] = -1;
        tileIds[b] = -1;
        selections.values().removeIf(idx -> idx == a || idx == b);
        if (remainingTiles() == 0) {
            dropFakeStarsOnClear();
        } else if (!hasAvailablePair()) {
            // No removable pair remains — shuffle the surviving tiles into a
            // state with at least one available pair so the player can continue.
            shuffleRemaining();
        }
        markChangedAndSynced();
    }

    /** Probe — true if at least one removable same-suit/rank pair exists right now. */
    private boolean hasAvailablePair() {
        if (tiles.length == 0) return false;
        return revealOneHintPairOn(tiles.clone());
    }

    /**
     * How many hint pairs the BE will yield in exchange for one of the given
     * stack's items. Higher tiers buy more pairs per item:
     * iron = 1, gold = 2, diamond = 4. Returns 0 for any non-payment item.
     */
    private static int hintPairsFor(ItemStack stack) {
        if (stack.is(Items.IRON_INGOT)) return 1;
        if (stack.is(Items.GOLD_INGOT)) return 2;
        if (stack.is(Items.DIAMOND))    return 4;
        return 0;
    }

    // -------- Hopper / pipe input: accept iron / gold / diamond --------
    //
    // Per-loader storage adapters (NeoForge IItemHandler, Fabric Storage<ItemVariant>)
    // route to {@link #tryInsertHintItem}. 1 iron = 1 hint pair, 1 gold = 2 pairs,
    // 1 diamond = 4 pairs (see {@link #hintPairsFor}). For every pair revealed, we add
    // {@link #LIQUID_XP_PER_PAIR_MB} to the internal tank; overflow voided silently.
    // A piped item is consumed only if its full pair count can be revealed
    // (no partial-payment).

    /** Accepts only items that pay for at least one hint pair. Used by storage
     *  adapters to filter what hoppers can insert. */
    public static boolean isHintItem(ItemStack stack) {
        return hintPairsFor(stack) > 0;
    }

    /**
     * Server-side: tries to insert {@code stack} for hint reveal + XP generation.
     * Returns the number of items consumed (≤ {@code stack.getCount()}). When
     * {@code simulate} is true, no state mutates. Returns 0 if the BE can't
     * currently accept the item (no available pairs, etc.).
     *
     * <p>Side-effects when not simulating: tiles array shrinks, liquid XP grows,
     * sync packets fire via {@link #markChangedAndSynced()}.
     */
    public int tryInsertHintItem(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return 0;
        int perItem = hintPairsFor(stack);
        if (perItem <= 0) return 0;
        if (level == null || level.isClientSide()) return 0;
        int max = stack.getCount();
        int consumed = 0;
        if (simulate) {
            TheMahjongTile[] copy = tiles.clone();
            for (int i = 0; i < max; i++) {
                int revealed = 0;
                for (int j = 0; j < perItem; j++) {
                    if (revealOneHintPairOn(copy)) revealed++;
                    else break;
                }
                if (revealed < perItem) break;
                consumed++;
            }
        } else {
            automatedContext = true;
            try {
                for (int i = 0; i < max; i++) {
                    // Pre-check on a copy: if we can't reveal perItem pairs in
                    // the current static state, skip this item rather than
                    // consume it for a partial reveal.
                    TheMahjongTile[] preCheck = tiles.clone();
                    int simulated = 0;
                    for (int j = 0; j < perItem; j++) {
                        if (revealOneHintPairOn(preCheck)) simulated++;
                        else break;
                    }
                    if (simulated < perItem) break;
                    int realRevealed = 0;
                    for (int j = 0; j < perItem; j++) {
                        if (!revealOneHintPair()) break;
                        fillLiquidXpVoidingOverflow(LIQUID_XP_PER_PAIR_MB);
                        realRevealed++;
                    }
                    if (realRevealed == 0) break;
                    consumed++;
                }
            } finally {
                automatedContext = false;
            }
        }
        return consumed;
    }

    /** Read accessor for storage adapters. */
    public int getLiquidXpStored() {
        return liquidXpStored;
    }

    /**
     * Drain up to {@code maxMb} from the internal tank. Returns mB actually drained.
     * Used by per-loader fluid storage adapters. Sync fires when state mutates.
     */
    public int extractLiquidXp(int maxMb, boolean simulate) {
        int drained = Math.max(0, Math.min(maxMb, liquidXpStored));
        if (!simulate && drained > 0) {
            liquidXpStored -= drained;
            markChangedAndSynced();
        }
        return drained;
    }

    /** Fill the tank, silently dropping any portion that doesn't fit. */
    private void fillLiquidXpVoidingOverflow(int amountMb) {
        int accepted = Math.max(0, Math.min(amountMb, LIQUID_XP_CAPACITY_MB - liquidXpStored));
        if (accepted > 0) {
            liquidXpStored += accepted;
            markChangedAndSynced();
        }
    }

    /**
     * Re-place the surviving (code, id) pairs over their existing slot positions
     * using the same top-down construction as {@link #dealSolvable}: at each
     * step pick two currently-free slots and assign them a same-code pair drawn
     * from the surviving pool, mark them removed, repeat. The resulting layout
     * is guaranteed solvable, so a freshly-shuffled board can't immediately
     * deadlock again. Slot positions never change; only which tile sits where.
     */
    private void shuffleRemaining() {
        MahjongSolitaireBoard board = board();
        if (board == null) return;
        List<Integer> presentSlots = new ArrayList<>();
        // Pool surviving tiles by suit/rank (red-dora variants are pair-equivalent
        // to non-aka — see the matchesSuitRank-driven pair-match in onCuteClick).
        // Keep the actual TheMahjongTile instances per pool so we preserve the
        // exact aka flag on whichever surviving tiles we redeal.
        Map<Long, java.util.Deque<Integer>> idsByKey = new HashMap<>();
        Map<Long, java.util.Deque<TheMahjongTile>> tilesByKey = new HashMap<>();
        for (int i = 0; i < tiles.length; i++) {
            TheMahjongTile t = tiles[i];
            if (t == null) continue;
            presentSlots.add(i);
            long key = suitRankKey(t);
            idsByKey.computeIfAbsent(key, k -> new java.util.ArrayDeque<>()).add(tileIds[i]);
            tilesByKey.computeIfAbsent(key, k -> new java.util.ArrayDeque<>()).add(t);
        }
        if (presentSlots.size() < 2) return;

        TheMahjongTile[] newTiles = tiles.clone();
        int[] newIds = tileIds.clone();
        for (int s : presentSlots) { newTiles[s] = null; newIds[s] = -1; }
        boolean[] working = new boolean[tiles.length];
        for (int s : presentSlots) working[s] = true;

        Random rng = new Random();
        ArrayList<Integer> freeBuf = new ArrayList<>();
        ArrayList<Long> keyBuf = new ArrayList<>();
        while (true) {
            freeBuf.clear();
            for (int i = 0; i < tiles.length; i++) {
                if (working[i] && isFreeInState(board, working, i)) freeBuf.add(i);
            }
            if (freeBuf.size() < 2) break;
            int a = freeBuf.get(rng.nextInt(freeBuf.size()));
            int b;
            do { b = freeBuf.get(rng.nextInt(freeBuf.size())); } while (b == a);

            keyBuf.clear();
            for (var e : idsByKey.entrySet()) {
                if (e.getValue().size() >= 2) keyBuf.add(e.getKey());
            }
            if (keyBuf.isEmpty()) break;
            long key = keyBuf.get(rng.nextInt(keyBuf.size()));
            java.util.Deque<Integer> ids = idsByKey.get(key);
            java.util.Deque<TheMahjongTile> pool = tilesByKey.get(key);

            newTiles[a] = pool.poll();
            newTiles[b] = pool.poll();
            newIds[a] = ids.poll();
            newIds[b] = ids.poll();
            working[a] = false;
            working[b] = false;
        }

        // If any surviving slot stayed unassigned, the geometry is unsolvable —
        // e.g. one tall column of stacked tiles where only the top can ever be
        // free. We can't deal a solvable layout from such a subset, so we treat
        // it as an auto-win: clear the board and drop the same reward as a
        // clean clear. Quiet, non-intrusive, and identical for manual play and
        // hopper automation.
        for (int s : presentSlots) {
            if (newTiles[s] == null) {
                LOGGER.info("solitaire shuffle: surviving subset unsolvable, auto-completing the board");
                java.util.Arrays.fill(tiles, null);
                java.util.Arrays.fill(tileIds, -1);
                selections.clear();
                dropFakeStarsOnClear();
                return;
            }
        }
        tiles = newTiles;
        tileIds = newIds;
        selections.clear();
    }

    /** Pair-equivalence key for a tile (suit ordinal × 16 + rank). Red-dora flag is
     *  ignored — solitaire treats aka and non-aka as the same tile. */
    private static long suitRankKey(TheMahjongTile t) {
        return ((long) t.suit().ordinal() << 4) | (long) t.rank();
    }

    /** Look up a removable pair (two free tiles of the same suit/rank) and remove them. Returns success. */
    private boolean revealOneHintPair() {
        if (tiles.length == 0) return false;
        Map<Long, Integer> firstByKey = new HashMap<>();
        for (int i = 0; i < tiles.length; i++) {
            TheMahjongTile t = tiles[i];
            if (t == null) continue;
            if (!isFree(i)) continue;
            long key = suitRankKey(t);
            Integer prev = firstByKey.get(key);
            if (prev != null) {
                removePair(prev, i);
                return true;
            }
            firstByKey.put(key, i);
        }
        return false;
    }

    /**
     * Player-pressed Hint button: locate the cheapest hint payment in the
     * player's inventory (iron → gold → diamond), reveal up to that item's
     * pair count, and only deduct the item + drop XP if at least one pair was
     * actually revealed. Player never loses an item without getting at least
     * one hint. Creative grants 1 free hint per click. Sends the
     * "hint cost" message if no payment item is on hand. Silently no-ops when
     * no hint is available (board cleared / deadlocked).
     */
    private void handleHintClick(ServerPlayer player) {
        if (!hasAvailablePair()) return;
        boolean creative = player.getAbilities().instabuild;
        Inventory inv = player.getInventory();
        int paySlot = -1;
        int payPairs = 0;
        if (!creative) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                int p = hintPairsFor(inv.getItem(i));
                // Prefer the cheapest available item (lowest pair yield = lowest tier).
                if (p > 0 && (payPairs == 0 || p < payPairs)) {
                    paySlot = i;
                    payPairs = p;
                }
            }
            if (paySlot < 0) {
                player.sendSystemMessage(Component.translatable(
                        "riichi_mahjong.solitaire.hint_cost"));
                return;
            }
        } else {
            payPairs = 1;
        }
        int revealed = 0;
        for (int j = 0; j < payPairs; j++) {
            if (!revealOneHintPair()) break;
            revealed++;
        }
        if (revealed == 0) return;
        if (!creative) inv.getItem(paySlot).shrink(1);
        spawnExperienceOrb(revealed);
    }

    /** Spawns an XP orb at the table centre. Server-side only. */
    private void spawnExperienceOrb(int amount) {
        if (!(level instanceof ServerLevel sl)) return;
        BlockPos p = getBlockPos();
        ExperienceOrb.award(sl, new net.minecraft.world.phys.Vec3(
                p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5), amount);
    }

    /** Drop 3 Fake Star item entities at the table centre. Server-side only. */
    private void dropFakeStarsOnClear() {
        if (!(level instanceof ServerLevel sl)) return;
        BlockPos p = getBlockPos();
        double cx = p.getX() + 0.5;
        double cy = p.getY() + 0.6;
        double cz = p.getZ() + 0.5;
        for (int i = 0; i < 3; i++) {
            ItemEntity e = new ItemEntity(sl, cx, cy, cz,
                    new ItemStack(com.riichimahjong.registry.ModItems.MAHJONG_FAKE_STAR.get()));
            e.setDefaultPickUpDelay();
            sl.addFreshEntity(e);
        }
        emitBoardClearEffects();
    }

    /**
     * Particles always fire on board clear so the win is visible. The ding only
     * plays when a real player triggered the clear — fully-automated clears
     * (hopper-fed iron/gold/diamond) skip the sound to avoid spamming nearby
     * players with notifications.
     */
    private void emitBoardClearEffects() {
        if (!(level instanceof ServerLevel sl)) return;
        BlockPos p = getBlockPos();
        double cx = p.getX() + 0.5, cy = p.getY() + 0.5, cz = p.getZ() + 0.5;
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                cx, cy + 0.25, cz, 18, 0.7, 0.2, 0.7, 0.0);
        sl.sendParticles(ParticleTypes.FIREWORK,
                cx, cy + 0.4, cz, 8, 0.5, 0.15, 0.5, 0.05);
        if (!automatedContext) {
            sl.playSound(null, p,
                    SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.6f, 1.4f);
        }
    }

    // -------- Hopper / pipe input + Liquid XP tank stripped for Phase 2.4 --------
    // Forge IItemHandler / IFluidHandler / FluidTank don't translate cleanly to the
    // Architectury common module (Fabric vs NeoForge fluid models diverge). The hint-
    // automation surface and Liquid XP generation come back in Phase 2.5 with proper
    // cross-loader item-cap + fluid impls. Manual hint reveal via player inventory
    // (handleHintClick) is unaffected.

    /** Counts hint pairs revealable on a copy of the current state (no mutation). */
    private int countAvailableHintsSimulated() {
        if (tiles.length == 0) return 0;
        TheMahjongTile[] copy = tiles.clone();
        int count = 0;
        while (revealOneHintPairOn(copy)) count++;
        return count;
    }

    private boolean revealOneHintPairOn(TheMahjongTile[] state) {
        MahjongSolitaireBoard board = board();
        if (board == null) return false;
        boolean[] present = new boolean[state.length];
        for (int i = 0; i < state.length; i++) present[i] = state[i] != null;
        Map<Long, Integer> firstByKey = new HashMap<>();
        for (int i = 0; i < state.length; i++) {
            if (!present[i]) continue;
            if (!isFreeInState(board, present, i)) continue;
            long key = suitRankKey(state[i]);
            Integer prev = firstByKey.get(key);
            if (prev != null) {
                state[prev] = null;
                state[i] = null;
                return true;
            }
            firstByKey.put(key, i);
        }
        return false;
    }

    // Forge capability/lifecycle hooks (getCapability, invalidateCaps, onChunkUnloaded,
    // clearRemoved) are no-ops here — automation surface stripped (see comment above).
    // Phase 2.5 adds them back via Architectury's cross-loader cap/fluid pattern.
    /* removed:
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side)
    public void invalidateCaps()
    public void onChunkUnloaded()
    public void clearRemoved() // re-creates lazy
    */

    private void markChangedAndSynced() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            // Comparators read getAnalogOutputSignal — notify neighbours of ALL
            // 9 multiblock cells so a comparator placed against any face of the
            // table re-evaluates after a tile change.
            BlockPos master = getBlockPos();
            net.minecraft.world.level.block.Block block = getBlockState().getBlock();
            int r = com.riichimahjong.common.BaseMultipartBlock.CENTER;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    level.updateNeighbourForOutputSignal(master.offset(dx, 0, dz), block);
                }
            }
        }
    }

    // -------- NBT --------

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.boardId = tag.contains("BoardId") ? tag.getString("BoardId") : null;
        // Sparse list: entries are either a full tile compound or an empty
        // compound (= removed slot) — see saveAdditional.
        ListTag raw = tag.getList("Tiles", Tag.TAG_COMPOUND);
        this.tiles = new TheMahjongTile[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            CompoundTag entry = raw.getCompound(i);
            this.tiles[i] = entry.contains("suit", Tag.TAG_STRING) ? MatchNbt.readTile(entry) : null;
        }
        if (tag.contains("TileIds")) {
            this.tileIds = tag.getIntArray("TileIds");
            if (this.tileIds.length != this.tiles.length) {
                this.tileIds = deriveDefaultTileIds(tiles);
            }
        } else {
            // Older saves without tileIds — assume each tile's id matches its
            // current slot (no shuffle has happened yet).
            this.tileIds = deriveDefaultTileIds(tiles);
        }
        this.randomSeed = tag.getLong("Seed");
        this.lastPowered = tag.getBoolean("LastPowered");
        this.liquidXpStored = Math.max(0, Math.min(LIQUID_XP_CAPACITY_MB, tag.getInt("LiquidXp")));
        selections.clear();
        if (tag.contains("Selections", 9)) {
            ListTag list = tag.getList("Selections", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                if (e.hasUUID("U") && e.contains("S")) {
                    selections.put(e.getUUID("U"), e.getInt("S"));
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (boardId != null) tag.putString("BoardId", boardId);
        // Sparse tile list — empty compound = removed slot.
        ListTag tileList = new ListTag();
        for (TheMahjongTile t : tiles) {
            tileList.add(t == null ? new CompoundTag() : MatchNbt.writeTile(t));
        }
        tag.put("Tiles", tileList);
        tag.putIntArray("TileIds", tileIds);
        tag.putLong("Seed", randomSeed);
        tag.putBoolean("LastPowered", lastPowered);
        tag.putInt("LiquidXp", liquidXpStored);
        // Selections are intentionally NOT persisted to disk (session-only);
        // they are re-added in getUpdateTag for client sync.
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (var e : selections.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("U", e.getKey());
            t.putInt("S", e.getValue());
            list.add(t);
        }
        if (!list.isEmpty()) tag.put("Selections", list);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    // 1.21 vanilla BlockEntity provides default handleUpdateTag/onDataPacket that
    // delegate to loadAdditional via loadWithComponents — overrides not needed.
}
