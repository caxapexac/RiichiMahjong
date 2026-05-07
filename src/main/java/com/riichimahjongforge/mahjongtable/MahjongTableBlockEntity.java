package com.riichimahjongforge.mahjongtable;

import com.riichimahjongforge.mahjongcore.MahjongRoundResolvedEvent;
import com.riichimahjongforge.mahjongcore.MahjongWinEffects;
import com.riichimahjongforge.RiichiMahjongForgeMod;
import com.riichimahjongforge.cuterenderer.CuteClickHandler;
import com.riichimahjongforge.cuterenderer.InteractKey;
import com.riichimahjongforge.themahjongcompat.DriverNbt;
import com.themahjong.TheMahjongMatch;
import com.themahjong.driver.MahjongPlayerInterface;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.TheMahjongDriver;
import com.themahjong.driver.bots.StupidActiveBot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;

/**
 * Mahjong table block entity. Holds:
 *
 * <ul>
 *   <li>A {@link State} indicating whether the table is in free-play decoration mode
 *       ({@link State#IDLE}) or running a match ({@link State#GAME}).</li>
 *   <li>A flat 136-slot tile inventory used as a "blackboard" while idle.</li>
 *   <li>A non-null per-seat {@link SeatInfo} list (default: all BOT) that survives
 *       across matches — players who claimed a seat stay seated for the next round.</li>
 *   <li>An optional {@link TheMahjongDriver} that owns gameplay state while in {@code GAME}.</li>
 *   <li>A master {@code randomSeed} that future shuffles ({@code advanceRound}) use.</li>
 * </ul>
 *
 * <p>Inventory access is locked while in {@code GAME}: {@link #canPlaceItem} returns
 * false and {@link #stillValid} rejects opening the menu. The driver and player
 * implementations are server-only state; this BE never touches them on the client.
 */
public class MahjongTableBlockEntity extends BlockEntity implements Container, MenuProvider, CuteClickHandler {

    /** Top-level table mode. Decoration vs. live gameplay. */
    public enum State {
        IDLE,
        GAME
    }

    /**
     * Per-seat configuration. A seat is either open ({@code enabled=true}) or closed
     * ({@code enabled=false}). Closed seats don't participate in the match — closing
     * one or more turns a 4p game into sanma / 2p / 1p.
     *
     * <p>For open seats: {@code occupant.isPresent()} → that human plays;
     * empty → a bot fills the seat at match start. {@code occupant} is always empty
     * for closed seats.
     */
    public record SeatInfo(boolean enabled, Optional<UUID> occupant) {
        public static SeatInfo open() {
            return new SeatInfo(true, Optional.empty());
        }

        public static SeatInfo claimed(UUID uuid) {
            return new SeatInfo(true, Optional.of(uuid));
        }

        public static SeatInfo closed() {
            return new SeatInfo(false, Optional.empty());
        }
    }

    /** Outcome of {@link #tryStartMatch}. {@link #STARTED} on success; otherwise the reason. */
    public enum StartMatchResult {
        STARTED,
        NOT_IDLE,
        SEATS_MISMATCH_PRESET
    }

    public static final int INVENTORY_SIZE = 136;
    /** Default seat count; matches {@link TheMahjongMatch#defaults()} player count. */
    public static final int DEFAULT_SEAT_COUNT = 4;
    /** Radius around the table to scan for nearby players when auto-seating. */
    public static final double AUTO_SEAT_RADIUS = 8.0;

    private static final double SECONDS_PER_TICK = 1.0 / 20.0;
    private static final String NBT_INVENTORY = "MtItemsInt";
    private static final String NBT_INVENTORY_SLOT = "Slot";
    private static final String NBT_INVENTORY_ITEM = "Item";
    private static final String NBT_STATE = "State";
    private static final String NBT_LAST_POWERED = "LastPowered";
    private static final String NBT_DRIVER = "Driver";
    private static final String NBT_SEATS = "Seats";
    private static final String NBT_SEAT_ENABLED = "enabled";
    private static final String NBT_SEAT_OCCUPANT = "uuid";
    /** Pre-rename key used when SeatType{BOT,HUMAN} existed; presence implies {@code enabled=true}. */
    private static final String NBT_SEAT_LEGACY_TYPE = "type";
    private static final String NBT_PRESET = "Preset";
    private static final String NBT_RANDOM_SEED = "RandomSeed";
    private static final String NBT_MINT_TILES = "MintTilesFromNothing";
    private static final String NBT_DELIVER_TO_MAIN_HAND = "DeliverToMainHand";
    private static final String NBT_HUMAN_PLAYERS = "HumanPlayers";
    private static final String NBT_RESULT_ANIM_STAGE = "ResultAnimStage";
    private static final String NBT_RESULT_ANIM_YAKU_IDX = "ResultAnimYakuIdx";

    private final NonNullList<ItemStack> inventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private State state = State.IDLE;
    private boolean lastPowered;
    private List<SeatInfo> seats = defaultSeats();
    private RuleSetPreset preset = RuleSetPreset.MAHJONG_SOUL_4P;
    private long randomSeed;
    /** When {@code true}, a draw for a human seat that finds no matching tile in
     *  the table inventory mints a fresh tile item rather than skipping the
     *  hand-over. Convenient for dev/testing with an empty table. Once the deck
     *  is sourced from the inventory at match start (see pending work), the
     *  expected default will flip to {@code false} for item conservation. */
    private boolean mintTilesFromNothing = true;
    /** When {@code true}, the drawn tile lands specifically in the seat
     *  occupant's currently-selected hotbar slot — and only there. If that
     *  slot isn't empty, delivery fails and the tile returns to the table.
     *  When {@code false}, the tile goes anywhere in the player's main
     *  inventory via {@link Inventory#add}. Default {@code true} keeps the
     *  drawn tile literally "in your hand" for immediate use. */
    private boolean deliverToMainHand = true;

    @Nullable
    private TheMahjongDriver driver;

    // ---- Round-end result animation ---------------------------------------
    /** Stages of the round-result reveal animation. {@code AWAITING_ADVANCE}
     *  means the animation finished and the result screen stays visible
     *  until someone RMB-clicks the centre cell — see
     *  {@link #advanceRoundAfterResult}. */
    public enum ResultAnimStage { NONE, SHOW_HEADER, SHOW_YAKU_LINES, SHOW_FINAL, AWAITING_ADVANCE }
    /** Stage of the result reveal. NONE outside of result-hold. Synced to clients;
     *  client renderer keys its own local clock off observed transitions. */
    private ResultAnimStage resultAnimStage = ResultAnimStage.NONE;
    /** Number of yaku lines revealed so far during {@code SHOW_YAKU_LINES}. Synced
     *  so the client can render the partial reveal in lockstep with the server. */
    private int resultAnimYakuIdx = 0;
    /** Server game time at which the next animation step fires. Transient — server
     *  authority over stage flips; the client interpolates within a stage on its
     *  own local nano clock. */
    private transient long resultAnimNextTick;

    /** Game time of the last cute click processed on the server. Used by
     *  {@link #onTableRightClick} to suppress a same-tick duplicate when the
     *  same RMB fires both the cute click handler (consumed a tile/button)
     *  and the vanilla {@code Block.use} path. Transient — clicks have no
     *  meaning across save/load. */
    private transient long lastCuteClickGameTime = -1L;

    public MahjongTableBlockEntity(BlockPos pos, BlockState blockState) {
        super(RiichiMahjongForgeMod.MAHJONG_TABLE_BLOCK_ENTITY.get(), pos, blockState);
    }

    private static List<SeatInfo> defaultSeats() {
        List<SeatInfo> out = new ArrayList<>(DEFAULT_SEAT_COUNT);
        for (int i = 0; i < DEFAULT_SEAT_COUNT; i++) {
            out.add(SeatInfo.open());
        }
        return out;
    }

    // ---- read accessors ---------------------------------------------------

    public State state() { return state; }

    @Nullable
    public TheMahjongDriver driver() { return driver; }

    public List<SeatInfo> seats() { return List.copyOf(seats); }

    public boolean hasActiveMatch() { return driver != null; }

    public OptionalInt seatOfPlayer(UUID uuid) {
        for (int i = 0; i < seats.size(); i++) {
            if (seats.get(i).occupant().filter(uuid::equals).isPresent()) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    public int humanSeatCount() {
        int count = 0;
        for (SeatInfo info : seats) {
            if (info.occupant().isPresent()) count++;
        }
        return count;
    }

    public int enabledSeatCount() {
        int count = 0;
        for (SeatInfo info : seats) {
            if (info.enabled()) count++;
        }
        return count;
    }

    public RuleSetPreset preset() { return preset; }

    /**
     * The preset that will actually be used at match start. Same as {@link #preset()}
     * except for the soft fallback: if the stored preset is {@link RuleSetPreset#MAHJONG_SOUL_4P}
     * and the North seat is closed, returns {@link RuleSetPreset#MAHJONG_SOUL_SANMA_3P}
     * — letting a freshly placed table be played as either 4-player or sanma without
     * the user opening settings.
     */
    public RuleSetPreset effectivePreset() {
        if (preset == RuleSetPreset.MAHJONG_SOUL_4P
                && seats.size() >= 4 && !seats.get(3).enabled()) {
            return RuleSetPreset.MAHJONG_SOUL_SANMA_3P;
        }
        return preset;
    }

    // ---- seat mutation (IDLE only) ---------------------------------------

    /** Claims the seat for {@code uuid}; rejects closed seats. Idempotent. */
    public boolean claimSeat(int seat, UUID uuid) {
        if (state != State.IDLE) return false;
        if (seat < 0 || seat >= seats.size()) return false;
        if (!seats.get(seat).enabled()) return false;
        seatOfPlayer(uuid).ifPresent(prev -> {
            if (prev != seat) seats.set(prev, SeatInfo.open());
        });
        seats.set(seat, SeatInfo.claimed(uuid));
        markChangedAndSynced();
        return true;
    }

    /** Removes the occupant from an enabled seat. */
    public void releaseSeat(int seat) {
        if (state != State.IDLE) return;
        if (seat < 0 || seat >= seats.size()) return;
        SeatInfo info = seats.get(seat);
        if (info.occupant().isPresent()) {
            seats.set(seat, SeatInfo.open());
            markChangedAndSynced();
        }
    }

    /**
     * Sets the active preset and applies its canonical seat layout (seats {@code 0..N-1}
     * open, rest closed). Destructive: any occupant of a seat being closed is dropped.
     */
    public void selectPreset(RuleSetPreset newPreset) {
        if (state != State.IDLE) return;
        this.preset = newPreset;
        padSeatsAtLeast(DEFAULT_SEAT_COUNT);
        int wantOpen = newPreset.playerCount();
        for (int i = 0; i < seats.size(); i++) {
            boolean shouldBeOpen = i < wantOpen;
            if (seats.get(i).enabled() != shouldBeOpen) {
                seats.set(i, shouldBeOpen ? SeatInfo.open() : SeatInfo.closed());
            }
        }
        markChangedAndSynced();
    }

    /** Toggles the seat between open and closed. Closing also drops the occupant. */
    public void toggleSeatEnabled(int seat) {
        if (state != State.IDLE) return;
        if (seat < 0 || seat >= seats.size()) return;
        SeatInfo current = seats.get(seat);
        seats.set(seat, current.enabled() ? SeatInfo.closed() : SeatInfo.open());
        markChangedAndSynced();
    }

    // ---- match lifecycle --------------------------------------------------

    /**
     * Edge-triggered redstone handler called by the block on neighbour updates.
     * Rising edge in {@link State#IDLE} starts a default bot match (no auto-seating);
     * rising edge in {@link State#GAME} when the driver is terminal returns to idle.
     */
    public void onRedstone(boolean nowPowered) {
        if (nowPowered != lastPowered) {
            lastPowered = nowPowered;
            setChanged();
            if (nowPowered) {
                onRisingEdge();
            }
        }
    }

    private void onRisingEdge() {
        switch (state) {
            case IDLE -> tryStartMatch(new Random().nextLong());
            case GAME -> {
                if (driverIsTerminal()) {
                    endGame();
                }
            }
        }
    }

    private boolean driverIsTerminal() {
        return driver == null || driver.currentPhase() instanceof MatchPhase.MatchEnded;
    }

    /**
     * Server-only: transition IDLE → GAME using {@link #effectivePreset()}. Requires the
     * seat layout to match the preset's canonical layout (seats {@code 0..N-1} open,
     * rest closed); rejects with {@link StartMatchResult#SEATS_MISMATCH_PRESET} otherwise.
     * Inventory contents are <b>ignored</b> for now — tile→deck construction is the next step.
     */
    public StartMatchResult tryStartMatch(long seed) {
        if (level == null || level.isClientSide()) {
            throw new IllegalStateException("tryStartMatch must be called on the server");
        }
        if (state != State.IDLE) return StartMatchResult.NOT_IDLE;

        RuleSetPreset effective = effectivePreset();
        if (!seatsMatchCanonicalLayout(effective.playerCount())) {
            return StartMatchResult.SEATS_MISMATCH_PRESET;
        }

        TheMahjongMatch match = effective.newMatch().validate();
        List<SeatInfo> activeSeats = new ArrayList<>(match.playerCount());
        for (int i = 0; i < match.playerCount(); i++) activeSeats.add(seats.get(i));

        this.randomSeed = seed;
        this.driver = new TheMahjongDriver(match, buildPlayersFromSeats(activeSeats), new Random(seed));
        this.driver.setAnimationsEnabled(true);
        this.driver.startMatch();
        this.state = State.GAME;
        markChangedAndSynced();
        return StartMatchResult.STARTED;
    }

    private boolean seatsMatchCanonicalLayout(int wantOpen) {
        if (seats.size() < wantOpen) return false;
        for (int i = 0; i < seats.size(); i++) {
            if (seats.get(i).enabled() != (i < wantOpen)) return false;
        }
        return true;
    }

    /**
     * Convenience: when called with no humans seated, finds nearby players and assigns
     * each to their closest available open seat before starting. With at least one
     * human already seated, behaves identically to {@link #tryStartMatch(long)}.
     */
    public StartMatchResult tryStartMatchWithAutoSeating(long seed) {
        if (state != State.IDLE) return StartMatchResult.NOT_IDLE;
        if (humanSeatCount() == 0) {
            autoSeatNearbyPlayers();
        }
        return tryStartMatch(seed);
    }

    /**
     * Server-only: drop straight into a pre-built {@link TheMahjongMatch} (typically from
     * {@link com.themahjong.TheMahjongFixedDeal}) with the dealer already in
     * {@code AwaitingDiscard} on the drawn tile. Used by the predefined record items
     * to load test fixtures without going through the dealing animation.
     *
     * <p>Resets seats to {@code preset}'s canonical layout, places {@code hostUuid} at
     * seat 0 (the rest become bots), then constructs the driver via a phase snapshot —
     * bypassing {@code startMatch()} which would re-shuffle the wall.
     *
     * <p>Caller is responsible for ensuring {@code fixedMatch.currentRound()} is already
     * in {@code TURN} state with the dealer holding 14 tiles (use
     * {@code FixedDeal.builder().drawForDealer(true)}). Returns {@code NOT_IDLE} if the
     * table isn't currently idle.
     */
    public StartMatchResult tryApplyPredefined(
            long seed, RuleSetPreset preset, TheMahjongMatch fixedMatch, java.util.UUID hostUuid) {
        if (level == null || level.isClientSide()) {
            throw new IllegalStateException("tryApplyPredefined must be called on the server");
        }
        if (state != State.IDLE) return StartMatchResult.NOT_IDLE;
        if (fixedMatch.playerCount() != preset.playerCount()) {
            throw new IllegalArgumentException(
                    "fixedMatch playerCount " + fixedMatch.playerCount()
                            + " != preset.playerCount " + preset.playerCount());
        }

        this.preset = preset;
        // Canonical seat layout: 0..N-1 open with seat 0 = host; rest closed.
        padSeatsAtLeast(DEFAULT_SEAT_COUNT);
        for (int i = 0; i < seats.size(); i++) {
            if (i == 0) {
                seats.set(i, SeatInfo.claimed(hostUuid));
            } else if (i < preset.playerCount()) {
                seats.set(i, SeatInfo.open());
            } else {
                seats.set(i, SeatInfo.closed());
            }
        }
        List<SeatInfo> activeSeats = new ArrayList<>(preset.playerCount());
        for (int i = 0; i < preset.playerCount(); i++) activeSeats.add(seats.get(i));

        // Snapshot constructor — phase = AwaitingDiscard(0). Dealer's hand already
        // holds the drawn tile (FixedDeal.drawForDealer); the round is in TURN.
        TheMahjongDriver.Snapshot snap = new TheMahjongDriver.Snapshot(
                new MatchPhase.AwaitingDiscard(0),
                /* lastDrawWasRinshan */ false,
                java.util.OptionalInt.empty());
        this.randomSeed = seed;
        this.driver = new TheMahjongDriver(
                fixedMatch, buildPlayersFromSeats(activeSeats), new Random(seed), snap);
        this.driver.setAnimationsEnabled(true);
        this.state = State.GAME;
        markChangedAndSynced();
        return StartMatchResult.STARTED;
    }

    /**
     * Server-only: capture the current BE state as a record snapshot. The returned
     * {@link CompoundTag} can be stored on a {@code MahjongTableRecordItem} stack and
     * later applied to any (idle) new-table BE via {@link #tryApplyRecordSnapshot}.
     * Returns the same shape as {@link #saveAdditional(CompoundTag)}.
     */
    public CompoundTag exportRecordSnapshot() {
        return saveWithoutMetadata();
    }

    /**
     * Server-only: apply a previously captured record snapshot. Replaces the BE's
     * entire state and reassigns seat 0 to {@code hostUuid}; remaining occupied seats
     * are vacated (re-filled by bots when the loaded match resumes). Rejects with
     * {@link StartMatchResult#NOT_IDLE} if the table isn't currently idle.
     */
    public StartMatchResult tryApplyRecordSnapshot(CompoundTag snapshot, java.util.UUID hostUuid) {
        if (level == null || level.isClientSide()) {
            throw new IllegalStateException("tryApplyRecordSnapshot must be called on the server");
        }
        if (state != State.IDLE) return StartMatchResult.NOT_IDLE;

        CompoundTag patched = snapshot.copy();
        // Substitute seat occupants — host at seat 0, bots elsewhere — so the
        // saved-by-someone-else case doesn't leave dangling UUIDs.
        if (patched.contains(NBT_SEATS, Tag.TAG_LIST)) {
            ListTag seatsTag = patched.getList(NBT_SEATS, Tag.TAG_COMPOUND);
            for (int i = 0; i < seatsTag.size(); i++) {
                CompoundTag entry = seatsTag.getCompound(i);
                if (entry.hasUUID(NBT_SEAT_OCCUPANT)) {
                    if (i == 0) entry.putUUID(NBT_SEAT_OCCUPANT, hostUuid);
                    else entry.remove(NBT_SEAT_OCCUPANT);
                } else if (i == 0 && entry.getBoolean(NBT_SEAT_ENABLED)) {
                    entry.putUUID(NBT_SEAT_OCCUPANT, hostUuid);
                }
            }
        }
        // Re-load all BE state from the patched snapshot.
        load(patched);
        markChangedAndSynced();
        return StartMatchResult.STARTED;
    }

    private void autoSeatNearbyPlayers() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        Vec3 centerVec = Vec3.atCenterOf(getBlockPos());
        AABB area = new AABB(getBlockPos()).inflate(AUTO_SEAT_RADIUS);
        List<ServerPlayer> nearby = new ArrayList<>(
                serverLevel.getEntitiesOfClass(ServerPlayer.class, area, p -> true));
        nearby.sort(Comparator.comparingDouble(p -> p.position().distanceToSqr(centerVec)));
        Set<Integer> taken = new HashSet<>();
        for (ServerPlayer p : nearby) {
            if (taken.size() >= seats.size()) break;
            int chosen = -1;
            double bestDist = Double.MAX_VALUE;
            for (int seat = 0; seat < seats.size(); seat++) {
                if (taken.contains(seat) || !seats.get(seat).enabled()) continue;
                // Without rotation-aware visual seat positions, all seats are equidistant
                // from the player's perspective; fall back to seat index. When real seat
                // positions land, replace this with per-seat cell-centre distance.
                double d = seat;
                if (d < bestDist) {
                    bestDist = d;
                    chosen = seat;
                }
            }
            if (chosen >= 0) {
                seats.set(chosen, SeatInfo.claimed(p.getUUID()));
                taken.add(chosen);
            }
        }
        setChanged();
    }

    /** Server-only: transition GAME → IDLE. Preserves {@link #seats()} for the next match. */
    public void endGame() {
        if (level == null || level.isClientSide()) {
            throw new IllegalStateException("endGame must be called on the server");
        }
        if (state != State.GAME) return;
        this.driver = null;
        this.randomSeed = 0L;
        this.state = State.IDLE;
        markChangedAndSynced();
    }

    // ---- input forwarding -------------------------------------------------

    /**
     * Routes a cute click from the local player to the {@link
     * MahjongTableHumanPlayer} that owns the sender's seat. Transport-level
     * checks (sender exists, dimension matches) already ran in the packet
     * handler; here we resolve the seat and let the player decide what (if
     * any) {@link com.themahjong.driver.PlayerAction} to queue.
     *
     * <p>Drops silently when:
     * <ul>
     *   <li>match isn't running,</li>
     *   <li>sender isn't seated at this table,</li>
     *   <li>resolved seat's player isn't a human (clicks shouldn't happen for
     *       a bot seat anyway).</li>
     * </ul>
     */
    @Override
    public void onCuteClick(ServerPlayer player, InteractKey key) {
        if (driver == null) return;
        int seat = seatOfOccupant(player.getUUID());
        if (seat < 0) return;
        if (!(driver.playerAt(seat) instanceof MahjongTableHumanPlayer human)) return;
        var roundOpt = driver.match().currentRound();
        if (roundOpt.isEmpty()) return;
        if (level != null) lastCuteClickGameTime = level.getGameTime();
        human.onCuteClick(key, driver, seat, player);
        // Click might have flipped a UI-only flag (e.g. riichiPending) that
        // serverTick can't detect after the fact (its prior-state snapshot is
        // taken AFTER the click already mutated the field). Sync unconditionally
        // — the cost is one redundant block update per click, which is fine.
        markChangedAndSynced();
    }

    /**
     * Routes a non-modifier RMB on the table block (no cute interactive hit)
     * to the sender's seat as a contextual default — discard drawn / pass
     * claim. See {@link MahjongTableHumanPlayer#onTableRightClick}.
     */
    public void onTableRightClick(ServerPlayer player) {
        if (driver == null) return;
        // Suppress when a cute click was already processed this tick — both
        // events can fire from the same RMB (tile cute interactive doesn't
        // block the table block's vanilla raycast). The cute handler should
        // cancel the event client-side, but this is a server-side belt-and-
        // suspenders for any race or stale-hover edge case.
        if (level != null && level.getGameTime() == lastCuteClickGameTime) return;
        int seat = seatOfOccupant(player.getUUID());
        if (seat < 0) return;
        if (!(driver.playerAt(seat) instanceof MahjongTableHumanPlayer human)) return;
        var roundOpt = driver.match().currentRound();
        if (roundOpt.isEmpty()) return;
        human.onTableRightClick(driver, driver.currentPhase(), seat, player);
    }

    /** Returns the seat index for {@code uuid}, or -1 if not seated. */
    private int seatOfOccupant(UUID uuid) {
        for (int i = 0; i < seats.size(); i++) {
            SeatInfo info = seats.get(i);
            if (info.enabled() && info.occupant().isPresent()
                    && info.occupant().get().equals(uuid)) {
                return i;
            }
        }
        return -1;
    }

    /** Pads {@link #seats} up to {@code wanted} with open seats; never shrinks
     *  (so a sanma table's closed North seat survives a load). */
    private void padSeatsAtLeast(int wanted) {
        while (seats.size() < wanted) seats.add(SeatInfo.open());
    }

    private static List<MahjongPlayerInterface> buildPlayersFromSeats(List<SeatInfo> seats) {
        List<MahjongPlayerInterface> out = new ArrayList<>(seats.size());
        for (SeatInfo info : seats) out.add(buildPlayer(info));
        return out;
    }

    private static MahjongPlayerInterface buildPlayer(SeatInfo info) {
        if (info.occupant().isPresent()) {
            MahjongTableHumanPlayer p = new MahjongTableHumanPlayer();
            p.setOccupant(info.occupant().get());
            p.setAutoDrawAfterSeconds(0.0);
            return p;
        }
        return StupidActiveBot.humanLike();
    }

    // ---- ticking ----------------------------------------------------------

    private void serverTick() {
        if (driver == null) return;
        if (!(level instanceof ServerLevel sl)) return;

        // Single-tick flow:
        //   1. validateBeforeAdvance — clear stale queues whose drawn tile is
        //      no longer in the player's inv.
        //   2. driver.advance(dt) — applies queue entries that survived (1).
        //   3. tickAfterAdvance — consume on phase exit, deliver new draws,
        //      run the auto-riichi-discard timer.
        // The result-animation gate only suppresses (2). (1) and (3) always
        // run, so consume-on-tsumo / consume-on-ron etc. fire same-tick as
        // the action that triggered them — no inter-tick fragility.
        MatchPhase prevPhase = driver.currentPhase();
        TheMahjongMatch prevMatch = driver.match();

        // 1. Validate queues against current inv.
        for (int i = 0; i < seats.size(); i++) {
            if (driver.playerAt(i) instanceof MahjongTableHumanPlayer human) {
                human.validateBeforeAdvance(sl);
            }
        }

        // 2. Driver advance (paused during result animation).
        boolean animating = resultAnimStage != ResultAnimStage.NONE;
        if (!animating) {
            driver.advance(SECONDS_PER_TICK);
        }
        MatchPhase nextPhase = driver.currentPhase();

        // 3. Post-advance: consume / deliver / auto-discard. Always runs.
        boolean playerStateChanged = false;
        for (int i = 0; i < seats.size(); i++) {
            if (driver.playerAt(i) instanceof MahjongTableHumanPlayer human) {
                if (human.tickAfterAdvance(sl, this, i, nextPhase)) playerStateChanged = true;
            }
        }

        if (animating) {
            tickResultAnimation(sl);
            if (playerStateChanged) markChangedAndSynced();
            return;
        }

        // Round just ended → start the staged result reveal.
        if (!(prevPhase instanceof MatchPhase.RoundEnded)
                && nextPhase instanceof MatchPhase.RoundEnded re) {
            startResultAnimation(sl, re);
        }
        if (prevMatch != driver.match() || prevPhase != nextPhase || playerStateChanged) {
            // setChanged() flags the BE dirty for chunk-save — without this,
            // tick-driven state (phase advance, delivery latches, discards,
            // etc.) never persists, and on reload we resume from whatever
            // snapshot was last written (typically right after match start),
            // re-running the deal animation and re-delivering already-given
            // tiles. markChangedAndSynced does both: setChanged + sync packet.
            markChangedAndSynced();
        }
    }

    // ---- Round-result animation ------------------------------------------
    // Server is authoritative on stage flips and the centre-RMB advance gate.
    // The client renderer reads (a) the replicated driver's RoundEnded.winResults
    // for the data, and (b) the synced `resultAnimStage` + `resultAnimYakuIdx`
    // for the reveal cursor. No English text crosses the wire — every visible
    // line is built client-side via Component.translatable from driver state.

    private static final long RESULT_ANIM_HEADER_TICKS = 20L;   // 1.0s
    private static final long RESULT_ANIM_YAKU_TICKS   = 10L;   // 0.5s per yaku line

    private void startResultAnimation(ServerLevel sl, MatchPhase.RoundEnded phase) {
        resultAnimStage = ResultAnimStage.SHOW_HEADER;
        resultAnimYakuIdx = 0;
        resultAnimNextTick = sl.getGameTime() + RESULT_ANIM_HEADER_TICKS;
        playResultStepSound(sl);
        markChangedAndSynced();
    }

    private void tickResultAnimation(ServerLevel sl) {
        if (resultAnimStage == ResultAnimStage.NONE) return;
        if (!(driver.currentPhase() instanceof MatchPhase.RoundEnded re)) {
            // Driver left RoundEnded behind us — clean up.
            clearResultAnimState();
            markChangedAndSynced();
            return;
        }
        long now = sl.getGameTime();
        if (now < resultAnimNextTick) return;
        boolean changed = false;
        switch (resultAnimStage) {
            case SHOW_HEADER -> {
                resultAnimStage = ResultAnimStage.SHOW_YAKU_LINES;
                resultAnimNextTick = now + RESULT_ANIM_YAKU_TICKS;
                changed = true;
            }
            case SHOW_YAKU_LINES -> {
                int totalYaku = totalYakuLineCount(re);
                if (resultAnimYakuIdx < totalYaku) {
                    resultAnimYakuIdx++;
                    playResultStepSound(sl);
                    resultAnimNextTick = now + RESULT_ANIM_YAKU_TICKS;
                    changed = true;
                } else {
                    resultAnimStage = ResultAnimStage.SHOW_FINAL;
                    resultAnimNextTick = now + RESULT_ANIM_YAKU_TICKS;
                }
            }
            case SHOW_FINAL -> {
                playFinalWinEffects(sl, re);
                playResultStepSound(sl);
                resultAnimStage = ResultAnimStage.AWAITING_ADVANCE;
                resultAnimNextTick = Long.MAX_VALUE;
                changed = true;
            }
            case AWAITING_ADVANCE, NONE -> {
                // Idle — wait for centre-RMB.
            }
        }
        if (changed) markChangedAndSynced();
    }

    /** Total yaku-line count for the primary winner (yakuman + yaku + optional dora line). */
    private static int totalYakuLineCount(MatchPhase.RoundEnded re) {
        if (re.winResults().isEmpty()) return 0;
        var primary = re.winResults().get(0);
        int n = primary.yakuman().size() + primary.yaku().size();
        // Yakuman ignores dora — keep the renderer and the reveal cursor
        // in lockstep by not counting a dora line on yakuman wins.
        boolean yakuman = !primary.yakuman().isEmpty();
        if (!yakuman && primary.doraCount() > 0) n += 1;
        return n;
    }

    /** Per-winner: advancements + chat broadcast + sound + star reward
     *  (N {@link RiichiMahjongForgeMod#MAHJONG_STAR} per N han; yakuman = 13).
     *  Iterates every {@code WinResult} so multi-ron rewards every winner. */
    private void playFinalWinEffects(ServerLevel sl, MatchPhase.RoundEnded re) {
        if (re.winResults().isEmpty()) {
            // Exhaustive draw — let the altar tick (a han=0 round still resolves).
            MinecraftForge.EVENT_BUS.post(new MahjongRoundResolvedEvent(sl, worldPosition, 0));
            return;
        }
        for (var win : re.winResults()) {
            int winnerSeat = -1;
            for (int i = 0; i < win.pointDeltas().size(); i++) {
                if (win.pointDeltas().get(i) > 0) { winnerSeat = i; break; }
            }
            if (winnerSeat < 0) continue;
            UUID winnerUuid = winnerSeat < seats.size() ? seats.get(winnerSeat).occupant().orElse(null) : null;
            ServerPlayer winnerPlayer = winnerUuid == null ? null
                    : sl.getServer().getPlayerList().getPlayer(winnerUuid);
            String winnerName = winnerPlayer != null ? winnerPlayer.getName().getString()
                    : fallbackSeatName(winnerSeat);
            List<String> yakuNames = new ArrayList<>();
            for (var y : win.yaku()) yakuNames.add(y.name());
            List<String> yakumanNames = new ArrayList<>();
            for (var y : win.yakuman()) yakumanNames.add(y.name());
            MahjongWinEffects.playWinEffects(sl, winnerPlayer, winnerName,
                    win.han(), !yakumanNames.isEmpty(), yakuNames, yakumanNames);
            if (winnerPlayer != null && win.han() > 0) {
                giveOrDropStars(sl, winnerPlayer, win.han());
            }
            MinecraftForge.EVENT_BUS.post(new MahjongRoundResolvedEvent(sl, worldPosition, win.han()));
        }
    }

    /** Give {@code count} stars to {@code player}; overflow drops at their feet. */
    private static void giveOrDropStars(ServerLevel sl, ServerPlayer player, int count) {
        ItemStack stack = new ItemStack(RiichiMahjongForgeMod.MAHJONG_STAR.get(), count);
        if (!player.getInventory().add(stack) || !stack.isEmpty()) {
            // {@code Inventory.add} mutates {@code stack} in place to whatever
            // didn't fit. Drop the leftover at the player's position.
            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                    sl, player.getX(), player.getY(), player.getZ(), stack);
            drop.setDefaultPickUpDelay();
            sl.addFreshEntity(drop);
        }
    }

    private static String fallbackSeatName(int seat) {
        return switch (seat) {
            case 0 -> "East";
            case 1 -> "South";
            case 2 -> "West";
            case 3 -> "North";
            default -> "Seat " + seat;
        };
    }

    /**
     * Manually advance past {@link MatchPhase.RoundEnded} after the result
     * screen is up. Triggered by an RMB click on the table's centre cell —
     * see {@link MahjongTableBlock#use}. Computes renchan from the
     * driver's RoundEnded payload. No-op if we're not currently in
     * result-hold state.
     */
    public void advanceRoundAfterResult() {
        if (driver == null) return;
        if (resultAnimStage != ResultAnimStage.AWAITING_ADVANCE) return;
        if (!(driver.currentPhase() instanceof MatchPhase.RoundEnded re)) {
            clearResultAnimState();
            markChangedAndSynced();
            return;
        }
        boolean renchan = computeRenchan(re);
        int nextHonba = 0; // simple default; honba progression rules TBD
        clearResultAnimState();
        try {
            driver.advanceRound(renchan, nextHonba);
        } catch (Exception ignored) {
            // Match may have just ended; clearing local state above already
            // returns the table to a sane visible state.
        }
        markChangedAndSynced();
    }

    private boolean computeRenchan(MatchPhase.RoundEnded re) {
        var roundOpt = driver.match().currentRound();
        if (roundOpt.isEmpty() || re.winResults().isEmpty()) return false;
        var primary = re.winResults().get(0);
        int winnerSeat = -1;
        for (int i = 0; i < primary.pointDeltas().size(); i++) {
            if (primary.pointDeltas().get(i) > 0) { winnerSeat = i; break; }
        }
        return winnerSeat >= 0 && winnerSeat == roundOpt.get().dealerSeat();
    }

    private void clearResultAnimState() {
        resultAnimStage = ResultAnimStage.NONE;
        resultAnimYakuIdx = 0;
        resultAnimNextTick = 0L;
    }

    private void playResultStepSound(ServerLevel sl) {
        sl.playSound(null, getBlockPos(),
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(),
                net.minecraft.sounds.SoundSource.BLOCKS, 0.45f, 1.40f);
    }

    /** Removes one item of {@code targetItem} from the table inventory and returns
     *  it as a 1-count {@code ItemStack}. Returns empty stack if none present.
     *  Used by {@link MahjongTableHumanPlayer} when delivering the drawn tile. */
    public ItemStack takeOneTileFromTableInventory(Item targetItem) {
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            ItemStack here = inventory.get(slot);
            if (here.isEmpty() || here.getItem() != targetItem) continue;
            ItemStack one = here.split(1);
            if (here.isEmpty()) inventory.set(slot, ItemStack.EMPTY);
            return one;
        }
        return ItemStack.EMPTY;
    }

    /** Returns {@code stack} to the table inventory, merging into existing
     *  partial stacks of the same item before falling back to an empty slot.
     *  Used by {@link MahjongTableHumanPlayer} on failed delivery. */
    public void restoreToTableInventory(ItemStack stack, Item targetItem) {
        if (stack.isEmpty()) return;
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            ItemStack here = inventory.get(slot);
            if (here.getItem() == targetItem
                    && here.getCount() < here.getMaxStackSize()) {
                here.grow(stack.getCount());
                return;
            }
        }
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            if (inventory.get(slot).isEmpty()) {
                inventory.set(slot, stack);
                return;
            }
        }
    }

    public boolean mintTilesFromNothing() { return mintTilesFromNothing; }

    /** True iff the drawn tile for {@code seat}'s current AwaitingDiscard
     *  turn was successfully delivered to the occupant's inventory at some
     *  point this turn. Persisted on the human player and synced via NBT
     *  so the client renderer can hide the drawn tile from the rendered
     *  hand consistently — even if the player later moved the item. */
    public boolean drawnTileDeliveredForSeat(int seat) {
        if (driver == null || seat < 0 || seat >= driver.match().playerCount()) return false;
        return driver.playerAt(seat) instanceof MahjongTableHumanPlayer h && h.drawnTileDelivered();
    }

    /** Read by the renderer to display the RIICHI button as toggled-on. */
    public boolean riichiPendingForSeat(int seat) {
        if (driver == null || seat < 0 || seat >= driver.match().playerCount()) return false;
        return driver.playerAt(seat) instanceof MahjongTableHumanPlayer h && h.riichiPending();
    }

    // ---- Round-end result state accessors (read by client renderer) -------

    /** True iff the result-screen overlay should currently be drawn. */
    public boolean isInResultPhase() { return resultAnimStage != ResultAnimStage.NONE; }

    /** Current stage of the result-reveal animation. */
    public ResultAnimStage resultAnimStage() { return resultAnimStage; }

    /** Number of yaku lines that have been revealed so far during {@code SHOW_YAKU_LINES}.
     *  At {@code SHOW_FINAL}/{@code AWAITING_ADVANCE} this equals the total yaku-line count. */
    public int resultAnimYakuIdx() { return resultAnimYakuIdx; }

    public boolean deliverToMainHand() { return deliverToMainHand; }

    public void setDeliverToMainHand(boolean enabled) {
        if (this.deliverToMainHand == enabled) return;
        this.deliverToMainHand = enabled;
        markChangedAndSynced();
    }

    public void setMintTilesFromNothing(boolean enabled) {
        if (this.mintTilesFromNothing == enabled) return;
        this.mintTilesFromNothing = enabled;
        markChangedAndSynced();
    }

    /** Marks the BE dirty for save and immediately syncs to clients. */
    private void markChangedAndSynced() {
        setChanged();
        pushClientSync();
    }

    private void pushClientSync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public static <T extends BlockEntity> BlockEntityTicker<T> serverTicker() {
        return (level, pos, blockState, be) -> {
            if (be instanceof MahjongTableBlockEntity table) {
                table.serverTick();
            }
        };
    }

    // ---- Container --------------------------------------------------------

    @Override
    public int getContainerSize() { return INVENTORY_SIZE; }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : inventory) if (!stack.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot < 0 || slot >= INVENTORY_SIZE ? ItemStack.EMPTY : inventory.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(inventory, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(inventory, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= INVENTORY_SIZE) return;
        inventory.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(getBlockPos()) != this) return false;
        if (state != State.IDLE) return false;
        return player.distanceToSqr(
                getBlockPos().getX() + 0.5, getBlockPos().getY() + 0.5, getBlockPos().getZ() + 0.5)
                <= 64.0;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return state == State.IDLE;
    }

    @Override
    public void clearContent() {
        inventory.clear();
        setChanged();
    }

    // ---- MenuProvider -----------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.riichi_mahjong_forge.mahjong_table_new");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new MahjongTableMenu(containerId, playerInv, this);
    }

    // ---- NBT --------------------------------------------------------------

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString(NBT_STATE, state.name());
        tag.putBoolean(NBT_LAST_POWERED, lastPowered);
        tag.putString(NBT_PRESET, preset.name());
        tag.putBoolean(NBT_MINT_TILES, mintTilesFromNothing);
        tag.putBoolean(NBT_DELIVER_TO_MAIN_HAND, deliverToMainHand);

        // Result-reveal cursor — synced so clients drive the overlay in lockstep.
        // The textual content is built client-side from the replicated driver's
        // RoundEnded.winResults; only the stage + yaku-reveal index ride along here.
        tag.putString(NBT_RESULT_ANIM_STAGE, resultAnimStage.name());
        tag.putInt(NBT_RESULT_ANIM_YAKU_IDX, resultAnimYakuIdx);

        ListTag seatsTag = new ListTag();
        for (SeatInfo info : seats) {
            CompoundTag entry = new CompoundTag();
            entry.putBoolean(NBT_SEAT_ENABLED, info.enabled());
            info.occupant().ifPresent(uuid -> entry.putUUID(NBT_SEAT_OCCUPANT, uuid));
            seatsTag.add(entry);
        }
        tag.put(NBT_SEATS, seatsTag);

        ListTag list = new ListTag();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.get(i);
            if (stack.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt(NBT_INVENTORY_SLOT, i);
            entry.put(NBT_INVENTORY_ITEM, stack.save(new CompoundTag()));
            list.add(entry);
        }
        tag.put(NBT_INVENTORY, list);

        if (driver != null) {
            tag.put(NBT_DRIVER, DriverNbt.writeDriver(driver));
            tag.putLong(NBT_RANDOM_SEED, randomSeed);
            // Per-seat human player state (delivery latches, pending consume).
            // Must be persisted so save/load (chunk unload, break+place) doesn't
            // re-attempt delivery and dupe items. Indexed by seat.
            ListTag humans = new ListTag();
            for (int i = 0; i < seats.size(); i++) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("Seat", i);
                if (driver.playerAt(i) instanceof MahjongTableHumanPlayer h) {
                    h.writeNbt(entry);
                }
                humans.add(entry);
            }
            tag.put(NBT_HUMAN_PLAYERS, humans);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        if (tag.contains(NBT_STATE)) {
            try {
                state = State.valueOf(tag.getString(NBT_STATE));
            } catch (IllegalArgumentException ignored) {
                state = State.IDLE;
            }
        }
        lastPowered = tag.getBoolean(NBT_LAST_POWERED);
        mintTilesFromNothing = !tag.contains(NBT_MINT_TILES) || tag.getBoolean(NBT_MINT_TILES);
        deliverToMainHand    = !tag.contains(NBT_DELIVER_TO_MAIN_HAND) || tag.getBoolean(NBT_DELIVER_TO_MAIN_HAND);
        if (tag.contains(NBT_RESULT_ANIM_STAGE)) {
            try {
                resultAnimStage = ResultAnimStage.valueOf(tag.getString(NBT_RESULT_ANIM_STAGE));
            } catch (IllegalArgumentException ignored) {
                resultAnimStage = ResultAnimStage.NONE;
            }
        } else {
            resultAnimStage = ResultAnimStage.NONE;
        }
        resultAnimYakuIdx = tag.getInt(NBT_RESULT_ANIM_YAKU_IDX);
        if (tag.contains(NBT_PRESET)) {
            try {
                preset = RuleSetPreset.valueOf(tag.getString(NBT_PRESET));
            } catch (IllegalArgumentException ignored) {
                preset = RuleSetPreset.MAHJONG_SOUL_4P;
            }
        }

        if (tag.contains(NBT_SEATS, Tag.TAG_LIST)) {
            ListTag seatsTag = tag.getList(NBT_SEATS, Tag.TAG_COMPOUND);
            List<SeatInfo> loaded = new ArrayList<>(seatsTag.size());
            for (int i = 0; i < seatsTag.size(); i++) {
                CompoundTag entry = seatsTag.getCompound(i);
                // Legacy format: presence of "type" (BOT|HUMAN) implies enabled=true.
                boolean enabled = entry.contains(NBT_SEAT_ENABLED)
                        ? entry.getBoolean(NBT_SEAT_ENABLED)
                        : entry.contains(NBT_SEAT_LEGACY_TYPE);
                UUID uuid = entry.hasUUID(NBT_SEAT_OCCUPANT) ? entry.getUUID(NBT_SEAT_OCCUPANT) : null;
                loaded.add(new SeatInfo(enabled, enabled ? Optional.ofNullable(uuid) : Optional.empty()));
            }
            seats = loaded;
        }
        if (seats.isEmpty()) seats = defaultSeats();

        inventory.clear();
        if (tag.contains(NBT_INVENTORY, Tag.TAG_LIST)) {
            ListTag list = tag.getList(NBT_INVENTORY, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                int slot = entry.getInt(NBT_INVENTORY_SLOT);
                if (slot < 0 || slot >= INVENTORY_SIZE) continue;
                inventory.set(slot, ItemStack.of(entry.getCompound(NBT_INVENTORY_ITEM)));
            }
        }

        if (state == State.GAME && tag.contains(NBT_DRIVER, Tag.TAG_COMPOUND)) {
            this.randomSeed = tag.getLong(NBT_RANDOM_SEED);
            int playerCount = tag.getCompound(NBT_DRIVER).getCompound("match").getInt("playerCount");
            padSeatsAtLeast(DEFAULT_SEAT_COUNT);
            // Driver expects exactly playerCount players, built from the enabled subset
            // (sanma keeps a closed seat in the table layout but only 3 active players).
            List<SeatInfo> activeSeats = new ArrayList<>(playerCount);
            for (SeatInfo info : seats) {
                if (info.enabled()) activeSeats.add(info);
                if (activeSeats.size() == playerCount) break;
            }
            while (activeSeats.size() < playerCount) activeSeats.add(SeatInfo.open()); // defensive
            this.driver = DriverNbt.readDriver(
                    tag.getCompound(NBT_DRIVER),
                    buildPlayersFromSeats(activeSeats),
                    new Random(this.randomSeed));
            this.driver.setAnimationsEnabled(true);
            // Restore per-seat human-player state (delivery latches, pending
            // consume). buildPlayersFromSeats made fresh instances; readNbt
            // reapplies turn-state so we don't re-deliver / forget pending
            // consumes after reload.
            if (tag.contains(NBT_HUMAN_PLAYERS, Tag.TAG_LIST)) {
                ListTag humans = tag.getList(NBT_HUMAN_PLAYERS, Tag.TAG_COMPOUND);
                for (int i = 0; i < humans.size(); i++) {
                    CompoundTag entry = humans.getCompound(i);
                    int seat = entry.getInt("Seat");
                    if (seat >= 0 && seat < driver.match().playerCount()
                            && driver.playerAt(seat) instanceof MahjongTableHumanPlayer h) {
                        h.readNbt(entry);
                    }
                }
            }
        } else if (state == State.GAME) {
            // GAME flag without driver tag — corrupt/legacy state; bail to IDLE.
            state = State.IDLE;
        }
    }

}
