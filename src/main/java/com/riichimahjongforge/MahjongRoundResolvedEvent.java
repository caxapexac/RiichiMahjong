package com.riichimahjongforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

public class MahjongRoundResolvedEvent extends Event {
    private final ServerLevel level;
    private final BlockPos sourcePos;
    private final int han;

    public MahjongRoundResolvedEvent(ServerLevel level, BlockPos sourcePos, int han) {
        this.level = level;
        this.sourcePos = sourcePos.immutable();
        this.han = Math.max(0, han);
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos sourcePos() {
        return sourcePos;
    }

    public int han() {
        return han;
    }
}
