package com.riichimahjong.mahjongcore;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Cross-loader mod event hooks. Replaces the Forge {@code MahjongRoundResolvedEvent}
 * (a class extending the Forge event bus) with Architectury's functional-interface
 * listener pattern.
 *
 * <p>Posters call e.g. {@code MahjongModEvents.ROUND_RESOLVED.invoker().onRoundResolved(...)}.
 * Subscribers call {@code MahjongModEvents.ROUND_RESOLVED.register((level, pos, han) -> ...)}.
 */
public final class MahjongModEvents {

    /**
     * Posted when a round resolves, by the {@code MahjongTableBlockEntity} (once per
     * winner with that winner's han, plus once with han=0 on exhaustive draw) and by
     * {@code YakuGeneratorBlockEntity} on its win effect path. Listeners include the
     * mahjong altar — a silent resolution breaks altar crafting.
     *
     * <p>{@code sourcePos} is the BlockPos of the BE that resolved the round (table or
     * generator). {@code han} is clamped to {@code [0, ∞)} by callers; {@code 0}
     * signals an exhaustive draw with no winner.
     */
    public static final Event<RoundResolved> ROUND_RESOLVED = EventFactory.of(listeners ->
            (level, pos, han) -> {
                for (RoundResolved listener : listeners) {
                    listener.onRoundResolved(level, pos, han);
                }
            });

    @FunctionalInterface
    public interface RoundResolved {
        void onRoundResolved(ServerLevel level, BlockPos sourcePos, int han);
    }

    private MahjongModEvents() {}
}
