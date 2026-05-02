package com.riichimahjongforge.client;

import com.riichimahjongforge.MatchAbortReason;
import com.riichimahjongforge.network.S2CMatchLifecyclePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Runs on logical client when {@link S2CMatchLifecyclePacket} is received. */
public final class MatchLifecycleClientHandler {

    private MatchLifecycleClientHandler() {}

    public static void handle(S2CMatchLifecyclePacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Component text;
        if (msg.started()) {
            text = Component.translatable(
                    "riichi_mahjong_forge.chat.match.started",
                    msg.tablePos().getX(),
                    msg.tablePos().getY(),
                    msg.tablePos().getZ(),
                    msg.roundPlaceholder());
        } else {
            text = Component.translatable(abortLangKey(msg.abortReason()));
        }
        mc.player.displayClientMessage(text, true);
    }

    private static String abortLangKey(MatchAbortReason reason) {
        return switch (reason) {
            case TABLE_BROKEN -> "riichi_mahjong_forge.chat.match.aborted.table_broken";
            case PLAYER_LEFT -> "riichi_mahjong_forge.chat.match.aborted.player_left";
            case KICK -> "riichi_mahjong_forge.chat.match.aborted.kick";
            case SIDE_CLOSED -> "riichi_mahjong_forge.chat.match.aborted.side_closed";
            case RESET -> "riichi_mahjong_forge.chat.match.aborted.reset";
            case GENERIC -> "riichi_mahjong_forge.chat.match.aborted.generic";
        };
    }
}
