package com.riichimahjong.cuterenderer;

import com.riichimahjong.common.BaseMultipartBlock;
import com.riichimahjong.cuterenderer.net.C2SCuteClickPacket;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.InteractionEvent;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Single global client subscriber that turns right-clicks into cute-click packets.
 *
 * <p>Iterates the live {@link CuteRenderer}s; the first one whose hovered node
 * matches the player's targeted block consumes the click. We piggyback on
 * Architectury's {@link InteractionEvent#RIGHT_CLICK_BLOCK} (interrupt + send packet)
 * so vanilla place / use flow doesn't fire when a cute click is consumed.
 *
 * <p>Architectury delivers RIGHT_CLICK_BLOCK on both sides in singleplayer; we filter
 * to client-only so the packet isn't sent twice.
 *
 * <p>Note: the 1.20.1 {@code InputEvent.InteractionKeyMappingTriggered} subscriber
 * (reserved for future air-clicks) was unused in 1.20.1 too and is dropped here.
 * The reserved hook can be added back via Architectury's keymap events when needed.
 */
public final class CuteClickInputHandler {

    private CuteClickInputHandler() {}

    /** Call once from CLIENT init. */
    public static void register() {
        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            if (!player.level().isClientSide()) return EventResult.pass();
            if (hand != InteractionHand.MAIN_HAND) return EventResult.pass();
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return EventResult.pass();

            // For multipart blocks the player may click any cell; resolve to the
            // master pos so the renderer (registered only at master) is found.
            BlockState clickedState = mc.level.getBlockState(pos);
            BlockPos lookupPos = clickedState.getBlock() instanceof BaseMultipartBlock
                    ? BaseMultipartBlock.masterPos(pos, clickedState)
                    : pos;
            CuteRenderer renderer = CuteRenderer.find(mc.level.dimension(), lookupPos);
            if (renderer == null) return EventResult.pass();
            CuteNode hovered = renderer.hovered();
            if (hovered == null) return EventResult.pass();
            Interactive i = hovered.interactive();
            if (i == null) return EventResult.pass();

            NetworkManager.sendToServer(new C2SCuteClickPacket(
                    mc.level.dimension(),
                    renderer.key().masterPos(),
                    i.key()));
            return EventResult.interruptTrue();
        });
    }
}
