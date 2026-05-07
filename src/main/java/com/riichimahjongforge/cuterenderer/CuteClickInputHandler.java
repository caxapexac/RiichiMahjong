package com.riichimahjongforge.cuterenderer;

import com.riichimahjongforge.RiichiMahjongForgeMod;
import com.riichimahjongforge.common.BaseMultipartBlock;
import com.riichimahjongforge.cuterenderer.net.C2SCuteClickPacket;
import com.riichimahjongforge.cuterenderer.net.CuteNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Single global client subscriber that turns right-clicks into cute-click packets.
 *
 * <p>Iterates the live {@link CuteRenderer}s; the first one whose hovered node
 * matches the player's targeted block consumes the click. We piggyback on
 * {@link PlayerInteractEvent.RightClickBlock} (cancel + send packet) so vanilla
 * place / use flow doesn't fire when a cute click is consumed.
 */
@Mod.EventBusSubscriber(modid = RiichiMahjongForgeMod.MODID, value = Dist.CLIENT)
public final class CuteClickInputHandler {

    private CuteClickInputHandler() {}

    /**
     * HIGHEST priority + default {@code receiveCanceled=false} so this fires
     * before any other RightClickBlock subscriber. When a cute interactive is
     * hovered, this consumes the click via cancel — preventing both vanilla's
     * use packet and any sibling Forge subscribers (e.g. table sneak-bypass)
     * from also firing on the same RMB and double-handling the input.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // RightClickBlock fires on both logical sides in single-player (the
        // integrated server fires it on the server thread, the client also
        // fires it on the render thread). We only want this handler to run
        // on the logical client — otherwise the packet is sent twice and the
        // server processes it twice, which for click-toggling actions like
        // "select tile" cancels itself out.
        if (!event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // For multipart blocks the player may click any cell; resolve to the
        // master pos so the renderer (registered only at master) is found.
        BlockPos clicked = event.getPos();
        BlockState clickedState = event.getLevel().getBlockState(clicked);
        BlockPos lookupPos = clickedState.getBlock() instanceof BaseMultipartBlock
                ? BaseMultipartBlock.masterPos(clicked, clickedState)
                : clicked;
        CuteRenderer renderer = CuteRenderer.find(mc.level.dimension(), lookupPos);
        if (renderer == null) return;
        CuteNode hovered = renderer.hovered();
        if (hovered == null) return;
        Interactive i = hovered.interactive();
        if (i == null) return;

        CuteNetwork.CHANNEL.sendToServer(new C2SCuteClickPacket(
                mc.level.dimension(),
                renderer.key().masterPos(),
                i.key()));
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        // Reserved hook: future air-clicks (no targeted block) for buttons that
        // float without a backing block. Currently unused — RightClickBlock covers
        // the in-table case which is what we need today.
    }
}
