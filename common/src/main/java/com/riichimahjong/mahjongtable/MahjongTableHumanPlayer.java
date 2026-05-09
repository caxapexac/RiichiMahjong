package com.riichimahjong.mahjongtable;

import com.riichimahjong.mahjongcore.MahjongTileItems;
import com.riichimahjong.cuterenderer.InteractKey;
import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;
import com.themahjong.driver.MahjongHumanPlayer;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.PlayerAction;
import com.themahjong.driver.TheMahjongDriver;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Seat-bound player for the mahjong table. Inherits queue + auto-timer
 * behaviour from {@link MahjongHumanPlayer} and binds the seat to a specific
 * Minecraft player ({@code occupant}, may be empty for an open seat).
 *
 * <p>Owns all human-side gameplay plumbing:
 * <ul>
 *   <li>Per-tick: when a fresh draw arrives, attempt to deliver the matching
 *       tile item to the occupant's inventory (single attempt per
 *       {@code AwaitingDiscard} entry — see {@link #serverTick}).</li>
 *   <li>Click input: translate cute clicks / table RMB into queued
 *       {@link PlayerAction}s, verifying the player still physically holds
 *       the tile and consuming it on a successful discard.</li>
 * </ul>
 *
 * <p>The BE owns the table-wide config (mint, deliver-to-main-hand, table
 * inventory) and exposes helpers; this class reads them and acts on the
 * occupant's inventory.
 */
public class MahjongTableHumanPlayer extends MahjongHumanPlayer {

    private UUID occupant;

    /** True once this {@code AwaitingDiscard} entry's delivery has been
     *  attempted (success or failure). Cleared on phase exit. Persisted, so
     *  break+place / chunk-reload doesn't re-deliver. */
    private boolean drawnTileDeliveryAttempted;
    /** True once delivery actually landed the drawn tile in the occupant's
     *  inventory this turn — i.e. the player did, at some point, physically
     *  hold the tile. Used by the renderer (to hide the drawn tile from the
     *  rendered hand even if the player later moved it elsewhere) and by
     *  {@link #tryDiscard} (to require the tile be in inv before allowing
     *  the discard, when delivery succeeded). Cleared on phase exit. */
    private boolean drawnTileDelivered;
    /** Tile pending inventory-consume after the driver applies our queued
     *  discard. Cleared by {@link #serverTick} either by consuming (phase
     *  moved past our discard turn → action was applied) or by dropping
     *  (queue cleared while still our turn → action was rejected). */
    private TheMahjongTile pendingConsume;
    /** True when the player has armed riichi via the table's RIICHI button —
     *  the next legal hand-tile click queues {@link PlayerAction.DiscardWithRiichi}
     *  instead of plain {@link PlayerAction.Discard}. Toggled by clicking the
     *  RIICHI button. Cleared on phase exit and on a successful discard. */
    private boolean riichiPending;
    /** Seconds elapsed in the current AwaitingDiscard{us} entry while the
     *  player is locked in riichi with only plain Discard legal. Drives the
     *  server-side auto-discard trigger — see {@link #serverTick}. Resets on
     *  phase exit. Transient (re-derived per turn). */
    private transient double riichiAutoDiscardElapsed;

    /** A renderer/click-handler-shared button entry. The driver only knows
     *  {@link PlayerAction}s; the riichi toggle is a UI-only mode flip with no
     *  matching engine action, so we model both as {@link TableButton}. */
    public sealed interface TableButton {
        record SubmitAction(PlayerAction action) implements TableButton {}
        record RiichiToggle(boolean active) implements TableButton {}
    }

    public Optional<UUID> occupant() {
        return Optional.ofNullable(occupant);
    }

    public void setOccupant(UUID uuid) {
        this.occupant = uuid;
    }

    public void clearOccupant() {
        this.occupant = null;
    }

    // ---- per-tick: drawn-tile delivery -----------------------------------

    /**
     * Pre-advance hook: validate the queued action against the current
     * inventory state. If the drawn tile was moved out of the occupant's
     * inv between queue time and now, drop the queue and hint — the next
     * {@code driver.advance} will see no queued action and the round
     * won't progress until the tile comes back.
     *
     * <p>This runs in the SAME BE tick as {@code driver.advance} and the
     * post-advance {@link #tickAfterAdvance}. Splitting validation from
     * consume keeps everything intra-tick: a queued action either submits
     * AND consumes the drawn tile in the same tick, or it does neither.
     */
    public void validateBeforeAdvance(ServerLevel level) {
        if (!hasQueued()) return;
        if (!drawnTileDelivered || pendingConsume == null || occupant == null) return;
        ServerPlayer sp = level.getPlayerByUUID(occupant) instanceof ServerPlayer s ? s : null;
        Item item = MahjongTileItems.itemForCode(MahjongTileItems.codeForTile(pendingConsume));
        if (sp == null || item == null || !inventoryContains(sp.getInventory(), item)) {
            clearQueued();
            if (sp != null) hint(sp, "riichi_mahjong.hint.player.return_drawn");
        }
    }

    /**
     * Post-advance hook: runs after {@code driver.advance} in the same BE
     * tick. Detects phase transitions (consume on exit, latch reset) and
     * delivers a freshly-drawn tile into the occupant's inv; fires the
     * auto-riichi-discard timer at the end. Returns {@code true} when any
     * sync-relevant flag changed.
     */
    public boolean tickAfterAdvance(ServerLevel level, MahjongTableBlockEntity table,
                                    int seatIndex, MatchPhase phase) {
        boolean ourDiscardTurn = phase instanceof MatchPhase.AwaitingDiscard ad
                && ad.seat() == seatIndex;
        boolean priorAttempted = drawnTileDeliveryAttempted;
        boolean priorDelivered = drawnTileDelivered;
        boolean priorRiichi = riichiPending;
        TheMahjongTile priorPending = pendingConsume;

        // Resolve a pending consume: armed on delivery, consumed only when the
        // drawn tile genuinely leaves the player's hold. Two trigger cases:
        //  - Phase moved past ourDiscardTurn (Discard / Tsumo / Riichi /
        //    KyuushuAbort): drawn went out with the action.
        //  - Still ourDiscardTurn but the drawn tile changed (Ankan / Kakan /
        //    DeclareKita → rinshan replacement): prior drawn went into the
        //    meld / kita stack; new drawn is a fresh rinshan tile, so reset
        //    the delivery latches and re-deliver.
        // If neither is true (driver rejected an action, validation cleared
        // the queue, or nothing happened yet), pendingConsume stays armed —
        // the next valid action will consume it on its phase exit.
        if (pendingConsume != null) {
            if (!ourDiscardTurn) {
                consumeFromOccupant(level, pendingConsume);
                pendingConsume = null;
            } else {
                TheMahjongTile activeDrawn = currentDrawnTile(table);
                if (activeDrawn != null && !activeDrawn.equals(pendingConsume)) {
                    consumeFromOccupant(level, pendingConsume);
                    pendingConsume = null;
                    drawnTileDeliveryAttempted = false;
                    drawnTileDelivered = false;
                }
            }
        }

        if (!ourDiscardTurn) {
            drawnTileDeliveryAttempted = false;
            drawnTileDelivered = false;
            riichiPending = false;
            riichiAutoDiscardElapsed = 0.0;
            return changedSince(priorAttempted, priorDelivered, priorPending, priorRiichi);
        }
        if (drawnTileDeliveryAttempted) {
            return changedSince(priorAttempted, priorDelivered, priorPending, priorRiichi);
        }
        if (occupant == null) return changedSince(priorAttempted, priorDelivered, priorPending, priorRiichi);
        Player p = level.getPlayerByUUID(occupant);
        if (!(p instanceof ServerPlayer serverPlayer)) {
            return changedSince(priorAttempted, priorDelivered, priorPending, priorRiichi);
        }
        if (table.driver() == null) return changedSince(priorAttempted, priorDelivered, priorPending, priorRiichi);
        var roundOpt = table.driver().match().currentRound();
        if (roundOpt.isEmpty()) return changedSince(priorAttempted, priorDelivered, priorPending, priorRiichi);
        if (!(roundOpt.get().activeTile() instanceof TheMahjongRound.ActiveTile.Drawn drawn)) {
            return changedSince(priorAttempted, priorDelivered, priorPending, priorRiichi);
        }
        Item targetItem = MahjongTileItems.itemForCode(MahjongTileItems.codeForTile(drawn.tile()));
        if (targetItem == null) return changedSince(priorAttempted, priorDelivered, priorPending, priorRiichi);

        ItemStack consumed = table.takeOneTileFromTableInventory(targetItem);
        boolean fromTable = !consumed.isEmpty();
        if (!fromTable) {
            if (!table.mintTilesFromNothing()) {
                drawnTileDeliveryAttempted = true;
                return changedSince(priorAttempted, priorDelivered, priorPending, priorRiichi);
            }
            consumed = new ItemStack(targetItem);
        }
        boolean given = tryGiveToPlayer(serverPlayer, consumed, targetItem, table.deliverToMainHand());
        if (!given && fromTable) {
            table.restoreToTableInventory(consumed, targetItem);
        }
        drawnTileDeliveryAttempted = true;
        if (given) {
            drawnTileDelivered = true;
            // Pin the consume rule state-side: regardless of how the next
            // action gets submitted (manual click, riichi auto-discard, bot
            // takeover, etc.), the drawn tile leaves the player's inventory
            // when AwaitingDiscard{us} exits or the drawn tile changes.
            pendingConsume = drawn.tile();
        }
        maybeAutoQueueRiichiDiscard(level, table, seatIndex);
        return changedSince(priorAttempted, priorDelivered, priorPending, priorRiichi);
    }

    /** Riichi auto-discard delay (seconds). Once locked into riichi with no
     *  other meaningful choice (Tsumo / Ankan / Kakan / Kita / Kyuushu absent),
     *  the drawn tile gets discarded after this much time. Routes through
     *  {@link #tryDiscard} so the inv-check and hint apply uniformly. */
    private static final double RIICHI_AUTO_DISCARD_SECONDS = 0.3;
    private static final double SECONDS_PER_TICK = 1.0 / 20.0;

    /**
     * Server-side auto-riichi-discard trigger. Polled at the end of each
     * {@code serverTick} during AwaitingDiscard{us}: when the player is
     * locked in riichi with no winning/kan choice to consider, fire the
     * discard via {@link #tryDiscard} after {@link #RIICHI_AUTO_DISCARD_SECONDS}.
     *
     * <p>Routing through {@code tryDiscard} keeps the inv-check + hint flow
     * consistent with manual clicks — there is no path that submits an action
     * while the player physically owes a tile they no longer hold.
     */
    private void maybeAutoQueueRiichiDiscard(ServerLevel level,
                                             MahjongTableBlockEntity table, int seatIndex) {
        if (hasQueued()) { riichiAutoDiscardElapsed = 0.0; return; }
        TheMahjongDriver driver = table.driver();
        if (driver == null) return;
        var roundOpt = driver.match().currentRound();
        if (roundOpt.isEmpty()) return;
        TheMahjongRound round = roundOpt.get();
        if (seatIndex < 0 || seatIndex >= round.players().size()) return;
        if (!round.players().get(seatIndex).riichi()) {
            riichiAutoDiscardElapsed = 0.0;
            return;
        }
        if (!(round.activeTile() instanceof TheMahjongRound.ActiveTile.Drawn drawn)) {
            riichiAutoDiscardElapsed = 0.0;
            return;
        }
        java.util.List<PlayerAction> legal = driver.legalActions(seatIndex);
        if (legal.isEmpty()) return;
        for (PlayerAction a : legal) {
            // If anything other than a plain Discard is legal (Tsumo / Ankan /
            // Kakan / Kita / Kyuushu), let the player decide.
            if (!(a instanceof PlayerAction.Discard)) return;
        }
        riichiAutoDiscardElapsed += SECONDS_PER_TICK;
        if (riichiAutoDiscardElapsed < RIICHI_AUTO_DISCARD_SECONDS) return;
        if (occupant == null) return;
        if (!(level.getPlayerByUUID(occupant) instanceof ServerPlayer sp)) return;
        // Same path as a manual click: legality + inv-presence + hint, then queue.
        tryDiscard(sp, drawn.tile(), driver, seatIndex);
    }

    private static TheMahjongTile currentDrawnTile(MahjongTableBlockEntity table) {
        if (table.driver() == null) return null;
        var roundOpt = table.driver().match().currentRound();
        if (roundOpt.isEmpty()) return null;
        if (roundOpt.get().activeTile() instanceof TheMahjongRound.ActiveTile.Drawn d) {
            return d.tile();
        }
        return null;
    }

    private boolean changedSince(boolean priorAttempted, boolean priorDelivered,
                                  @org.jetbrains.annotations.Nullable TheMahjongTile priorPending,
                                  boolean priorRiichi) {
        return priorAttempted != drawnTileDeliveryAttempted
                || priorDelivered != drawnTileDelivered
                || priorRiichi != riichiPending
                || !java.util.Objects.equals(priorPending, pendingConsume);
    }

    /** True iff the drawn tile was successfully delivered to the occupant's
     *  inventory at some point this AwaitingDiscard entry. Read by the BE
     *  to expose to the renderer (so the rendered hand stays consistent
     *  even if the player later drops/moves the item) and to {@link #tryDiscard}
     *  to enforce that the player must have the tile in inv to discard. */
    public boolean drawnTileDelivered() { return drawnTileDelivered; }

    /** Persistence — write transient turn-state into a tag for round-trip. */
    public void writeNbt(net.minecraft.nbt.CompoundTag tag) {
        tag.putBoolean("DeliveryAttempted", drawnTileDeliveryAttempted);
        tag.putBoolean("Delivered", drawnTileDelivered);
        tag.putBoolean("RiichiPending", riichiPending);
        if (pendingConsume != null) {
            tag.putString("PendingConsumeSuit", pendingConsume.suit().name());
            tag.putInt("PendingConsumeRank", pendingConsume.rank());
            tag.putBoolean("PendingConsumeRedDora", pendingConsume.redDora());
        }
    }

    /** Persistence — read state previously written by {@link #writeNbt}. */
    public void readNbt(net.minecraft.nbt.CompoundTag tag) {
        drawnTileDeliveryAttempted = tag.getBoolean("DeliveryAttempted");
        drawnTileDelivered = tag.getBoolean("Delivered");
        riichiPending = tag.getBoolean("RiichiPending");
        if (tag.contains("PendingConsumeSuit")) {
            try {
                TheMahjongTile.Suit suit = TheMahjongTile.Suit.valueOf(tag.getString("PendingConsumeSuit"));
                int rank = tag.getInt("PendingConsumeRank");
                boolean red = tag.getBoolean("PendingConsumeRedDora");
                pendingConsume = new TheMahjongTile(suit, rank, red);
            } catch (IllegalArgumentException ignored) {
                pendingConsume = null;
            }
        } else {
            pendingConsume = null;
        }
    }

    /**
     * Puts {@code stack} in the player's inventory: the selected hotbar slot
     * when {@code mainHand} is true (and that slot is empty), else anywhere in
     * the main 36-slot inventory. Pre-checks fullness so creative mode's
     * silent void-overflow on {@code add} can't lose the tile.
     */
    private static boolean tryGiveToPlayer(ServerPlayer player, ItemStack stack,
                                           Item targetItem, boolean mainHand) {
        Inventory inv = player.getInventory();
        if (mainHand) {
            int slot = inv.selected;
            if (!inv.getItem(slot).isEmpty()) return false;
            inv.setItem(slot, stack);
            return true;
        }
        if (!hasRoomForOneItem(inv, targetItem)) return false;
        return inv.add(stack) && stack.isEmpty();
    }

    private static boolean hasRoomForOneItem(Inventory inv, Item targetItem) {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack here = inv.getItem(slot);
            if (here.isEmpty()) return true;
            if (here.getItem() == targetItem
                    && here.getCount() < here.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    // ---- input → queued action -------------------------------------------

    /**
     * Translate a cute click into a queued {@link PlayerAction}. The BE has
     * already resolved that {@code sender} owns {@code seatIndex}. Hand-tile
     * clicks become {@link PlayerAction.Discard}; verified against the
     * occupant's actual inventory and consumed on success — refused if the
     * player has moved/dropped the tile.
     */
    public void onCuteClick(InteractKey key, TheMahjongDriver driver, int seatIndex,
                            ServerPlayer sender) {
        if (!(key instanceof InteractKey.SeatSlot ss)) return;
        if (ss.seat() != seatIndex) return;
        TheMahjongRound round = driver.match().currentRound().orElse(null);
        if (round == null) return;
        if (ss.area() == InteractKey.SeatSlot.AREA_HAND) {
            List<TheMahjongTile> hand = round.handDisplayOrder(seatIndex);
            int idx = ss.index();
            if (idx < 0 || idx >= hand.size()) return;
            tryDiscard(sender, hand.get(idx), driver, seatIndex);
        } else if (ss.area() == InteractKey.SeatSlot.AREA_BUTTON) {
            // Map button index to TableButton via the same unified list the
            // renderer used (RIICHI toggle + filtered actions). Both sides
            // recompute it from driver state + this player's riichiPending,
            // so they agree on the index → button mapping.
            List<TableButton> buttons = tableButtons(driver, seatIndex, riichiPending);
            int idx = ss.index();
            if (idx < 0 || idx >= buttons.size()) return;
            TableButton chosen = buttons.get(idx);
            if (chosen instanceof TableButton.RiichiToggle) {
                riichiPending = !riichiPending;
            } else if (chosen instanceof TableButton.SubmitAction sa) {
                tryQueueButtonAction(sender, sa.action(), driver, seatIndex);
            }
        }
    }

    /**
     * Universal queue path for any {@link PlayerAction} produced by a button click.
     * If we're in {@code AwaitingDiscard{us}} and the drawn tile has been delivered
     * to the player's inventory, every action ends our hold on that tile (whether
     * directly — Tsumo, Discard-with-Riichi, KyuushuAbort — or indirectly via a
     * rinshan-replacement path — Ankan, Kakan, DeclareKita): mark the drawn for
     * consume, refusing if the player has moved it elsewhere.
     *
     * <p>For claim-window actions (Pass, Ron, Chankan, Chi, Pon, Daiminkan) we
     * have no Drawn active tile and nothing in inv to consume — the action just
     * gets queued.
     */
    private void tryQueueButtonAction(ServerPlayer sender, PlayerAction action,
                                      TheMahjongDriver driver, int seatIndex) {
        TheMahjongRound round = driver.match().currentRound().orElse(null);
        if (round == null) {
            queue(action);
            return;
        }
        boolean ourDiscardTurn =
                driver.currentPhase() instanceof MatchPhase.AwaitingDiscard ad
                        && ad.seat() == seatIndex;
        if (ourDiscardTurn
                && drawnTileDelivered
                && round.activeTile() instanceof TheMahjongRound.ActiveTile.Drawn drawn) {
            Item drawnItem = MahjongTileItems.itemForCode(MahjongTileItems.codeForTile(drawn.tile()));
            if (drawnItem != null && !inventoryContains(sender.getInventory(), drawnItem)) {
                hint(sender, "riichi_mahjong.hint.player.retrieve_drawn_first");
                return;
            }
            pendingConsume = drawn.tile();
        }
        queue(action);
    }

    /**
     * Filter {@code legal} to actions that should appear as buttons and
     * return them in visual priority order: <b>RON / TSUMO / CHANKAN</b>
     * first, then <b>CHI</b>, then <b>PON</b>, then any KAN variant, then
     * KITA / abort, then PASS last. Within each group the driver's relative
     * order is preserved (e.g. multiple CHI shapes stay grouped). Both the
     * renderer and the click handler call this, so they agree on the
     * index → action mapping.
     */
    /** True iff the player has armed riichi via the RIICHI button — synced to
     *  clients so the renderer can show the toggle as "active". */
    public boolean riichiPending() { return riichiPending; }

    /**
     * Unified button list shared by the renderer and the click handler. Both
     * recompute it from authoritative driver state + the synced
     * {@link #riichiPending} flag, so index-to-button mapping always agrees.
     *
     * <p>RIICHI is a UI mode toggle with no engine equivalent: clicking it
     * flips {@link #riichiPending}, which then redirects the next hand-tile
     * click into {@link PlayerAction.DiscardWithRiichi}. The toggle is shown
     * whenever any DiscardWithRiichi is currently legal, OR while the toggle
     * is already active (so the player can cancel).
     */
    public static List<TableButton> tableButtons(TheMahjongDriver driver, int seat, boolean riichiPending) {
        List<PlayerAction> legal = driver.legalActions(seat);
        boolean riichiAvailable = false;
        for (PlayerAction a : legal) {
            if (a instanceof PlayerAction.DiscardWithRiichi) { riichiAvailable = true; break; }
        }
        List<TableButton> out = new ArrayList<>();
        if (riichiAvailable || riichiPending) {
            out.add(new TableButton.RiichiToggle(riichiPending));
        }
        for (PlayerAction a : buttonEligibleActions(legal)) {
            out.add(new TableButton.SubmitAction(a));
        }
        return out;
    }

    public static List<PlayerAction> buttonEligibleActions(List<PlayerAction> legal) {
        List<PlayerAction> out = new ArrayList<>(legal.size());
        for (PlayerAction a : legal) if (a instanceof PlayerAction.DeclareRon) out.add(a);
        for (PlayerAction a : legal) if (a instanceof PlayerAction.DeclareTsumo) out.add(a);
        for (PlayerAction a : legal) if (a instanceof PlayerAction.DeclareChankan) out.add(a);
        for (PlayerAction a : legal) if (a instanceof PlayerAction.Chi) out.add(a);
        for (PlayerAction a : legal) if (a instanceof PlayerAction.Pon) out.add(a);
        for (PlayerAction a : legal) if (a instanceof PlayerAction.Daiminkan) out.add(a);
        for (PlayerAction a : legal) if (a instanceof PlayerAction.Ankan) out.add(a);
        for (PlayerAction a : legal) if (a instanceof PlayerAction.Kakan) out.add(a);
        for (PlayerAction a : legal) if (a instanceof PlayerAction.DeclareKita) out.add(a);
        for (PlayerAction a : legal) if (a instanceof PlayerAction.KyuushuAbort) out.add(a);
        for (PlayerAction a : legal) if (a instanceof PlayerAction.Pass) out.add(a);
        return out;
    }

    /**
     * RMB on the table block (no cute interactive hit) — queue the contextual
     * default action for the current phase, with hint messages for the
     * "nothing happened" cases so beginners understand what state the table
     * is in. AwaitingDiscard on our seat → discard the drawn tile (tsumogiri)
     * or, post-claim, hint to choose a tile; AwaitingClaims → pass; other
     * phases → "wait for your turn".
     */
    public void onTableRightClick(TheMahjongDriver driver, MatchPhase phase, int seatIndex,
                                  ServerPlayer sender) {
        TheMahjongRound round = driver.match().currentRound().orElse(null);
        if (phase instanceof MatchPhase.AwaitingDiscard ad && ad.seat() == seatIndex) {
            if (round != null
                    && round.activeTile() instanceof TheMahjongRound.ActiveTile.Drawn drawn) {
                tryDiscard(sender, drawn.tile(), driver, seatIndex);
            } else {
                // Post-claim: phase is AwaitingDiscard{us}, but no Drawn tile
                // because we just claimed (Pon/Chi/Kan). Player must pick a
                // hand tile to discard.
                hint(sender, "riichi_mahjong.hint.player.choose_discard");
            }
        } else if (phase instanceof MatchPhase.AwaitingClaims) {
            queue(PlayerAction.Pass.INSTANCE);
        } else if (phase instanceof MatchPhase.AwaitingDiscard
                || phase instanceof MatchPhase.AwaitingDraw) {
            hint(sender, "riichi_mahjong.hint.player.wait_turn");
        }
        // Other phases (RoundEnded, BetweenRounds, MatchEnded, Dealing,
        // Resolving): no hint here; they're handled elsewhere or are
        // self-evident (e.g. result screen).
    }

    /**
     * Queue a discard, with the consume rules governed by whether delivery
     * actually succeeded this turn:
     *
     * <ul>
     *   <li><b>{@code drawnTileDelivered=false}</b> (delivery never landed —
     *       slot was occupied, mint disabled and table empty, etc.): the
     *       drawn tile lives only in driver state. Allow the discard with no
     *       inventory operation. Player has nothing to retrieve.</li>
     *   <li><b>{@code drawnTileDelivered=true}</b>: the player physically
     *       received the tile. Require it to still be in their inventory at
     *       discard time, otherwise refuse — they moved it (chest, drop,
     *       etc.) and allowing the discard would dupe.</li>
     * </ul>
     *
     * <p>Hints surface when an interaction would otherwise silently no-op so
     * beginners understand why nothing happened: drawn tile missing from inv,
     * tile not legally discardable right now (e.g. riichi-locked), wrong
     * phase, etc.
     */
    private void tryDiscard(ServerPlayer sender, TheMahjongTile tile,
                            TheMahjongDriver driver, int seatIndex) {
        TheMahjongRound round = driver.match().currentRound().orElse(null);
        if (round == null) {
            hint(sender, "riichi_mahjong.hint.player.wait_next_round");
            return;
        }
        // Phase check first — the click should only land during our discard.
        if (!(driver.currentPhase() instanceof MatchPhase.AwaitingDiscard ad)
                || ad.seat() != seatIndex) {
            hint(sender, "riichi_mahjong.hint.player.wait_turn");
            return;
        }
        // Legality check — the driver knows whether this tile is currently
        // discardable (riichi locks tiles other than the drawn one, kuikae
        // restrictions after chi, etc.). When riichi is armed, look for
        // DiscardWithRiichi(tile); otherwise plain Discard(tile).
        PlayerAction action = null;
        for (PlayerAction a : driver.legalActions(seatIndex)) {
            if (riichiPending) {
                if (a instanceof PlayerAction.DiscardWithRiichi r2 && r2.tile().equals(tile)) {
                    action = a;
                    break;
                }
            } else {
                if (a instanceof PlayerAction.Discard d2 && d2.tile().equals(tile)) {
                    action = a;
                    break;
                }
            }
        }
        if (action == null) {
            hint(sender, riichiPending
                    ? "riichi_mahjong.hint.player.not_tenpai_for_riichi"
                    : "riichi_mahjong.hint.player.illegal_discard");
            return;
        }
        if (round.activeTile() instanceof TheMahjongRound.ActiveTile.Drawn d && drawnTileDelivered) {
            Item drawnItem = MahjongTileItems.itemForCode(MahjongTileItems.codeForTile(d.tile()));
            if (drawnItem == null) return;
            if (!inventoryContains(sender.getInventory(), drawnItem)) {
                hint(sender, "riichi_mahjong.hint.player.retrieve_drawn_to_discard");
                return;
            }
            pendingConsume = d.tile();
        }
        queue(action);
    }

    /** Action-bar message to {@code p} explaining a silent-failure case.
     *  Action bar (the {@code true} second arg) auto-dismisses after a few
     *  seconds and doesn't spam chat — right venue for "what happened" UX. */
    private static void hint(ServerPlayer p, net.minecraft.network.chat.Component msg) {
        p.displayClientMessage(msg, true);
    }

    private static void hint(ServerPlayer p, String key) {
        hint(p, net.minecraft.network.chat.Component.translatable(key));
    }

    /** True iff {@code inv} holds at least one item of {@code targetItem}. */
    private static boolean inventoryContains(Inventory inv, Item targetItem) {
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack here = inv.getItem(slot);
            if (!here.isEmpty() && here.getItem() == targetItem) return true;
        }
        return false;
    }

    /** Resolve {@link #occupant} on {@code level} and consume one matching tile
     *  item from their inventory (selected slot first, then anywhere). Silent
     *  no-op if the player is offline or no longer holds the tile. */
    private void consumeFromOccupant(ServerLevel level, TheMahjongTile tile) {
        if (occupant == null) return;
        if (!(level.getPlayerByUUID(occupant) instanceof ServerPlayer sp)) return;
        Item targetItem = MahjongTileItems.itemForCode(MahjongTileItems.codeForTile(tile));
        if (targetItem == null) return;
        Inventory inv = sp.getInventory();
        ItemStack selected = inv.getItem(inv.selected);
        if (!selected.isEmpty() && selected.getItem() == targetItem) {
            selected.shrink(1);
            if (selected.isEmpty()) inv.setItem(inv.selected, ItemStack.EMPTY);
            return;
        }
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack here = inv.getItem(slot);
            if (here.isEmpty() || here.getItem() != targetItem) continue;
            here.shrink(1);
            if (here.isEmpty()) inv.setItem(slot, ItemStack.EMPTY);
            return;
        }
    }
}
