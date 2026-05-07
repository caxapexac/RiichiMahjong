package com.riichimahjongforge.cuterenderer;

import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side counterpart for cute clicks. Implemented by {@link
 * net.minecraft.world.level.block.entity.BlockEntity}s that host a {@link CuteRenderer}.
 *
 * <p><b>Implementations must treat the {@link InteractKey} as untrusted client input.</b>
 * The packet layer only validates transport-level concerns (protocol version, sender
 * dimension, within 8 blocks of the BE). Whether the key still corresponds to a real,
 * actionable node in the current game state is domain knowledge that lives here.
 *
 * <p>Why this matters: there is a 50–200ms gap between when the client hovered a node
 * and when the click arrives. The driver may have advanced (turn changed, claim
 * window closed, tile discarded) in that window, so a click for a node that no
 * longer exists or no longer belongs to this player is normal, not adversarial.
 *
 * <p>Required validation, per click:
 * <ul>
 *   <li>Resolve the sender's seat / role from {@code player.getUUID()}; drop if absent.</li>
 *   <li>Confirm the {@link InteractKey} variant is one this BE actually exposes
 *       right now (e.g. a {@code SeatSlot{seat=2, area=HAND}} click is only valid if
 *       seat 2 belongs to the sender and a tile is currently in that hand slot).</li>
 *   <li>Confirm the current phase / state allows the action.</li>
 * </ul>
 *
 * <p>Stale or invalid clicks should be dropped silently — the next driver-state sync
 * will correct the client's view, so there is no need to NACK.
 */
public interface CuteClickHandler {
    /**
     * Called on the server thread after the packet's transport-level checks have
     * passed. Implementations are responsible for all game-state validation; see
     * the interface javadoc.
     */
    void onCuteClick(ServerPlayer player, InteractKey key);
}
