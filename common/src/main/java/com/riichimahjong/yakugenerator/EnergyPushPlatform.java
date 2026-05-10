package com.riichimahjong.yakugenerator;

import dev.architectury.injectables.annotations.ExpectPlatform;

/**
 * Cross-loader bridge for pushing the yaku generator's buffered RF to receiving
 * neighbours. Per-loader implementations stash a cap-cache array on the BE's
 * {@link YakuGeneratorBlockEntity#loaderEnergyPushState} field so the tick path
 * stays cheap and lifetime is bound to the BE.
 *
 * <p>Pushing (rather than relying on neighbours to pull) matches the de-facto
 * Forge/NeoForge convention for generators — cables that require per-side I/O
 * configuration (Powah, Pipez, ...) won't drain the gen otherwise.
 */
public final class EnergyPushPlatform {
    private EnergyPushPlatform() {}

    /** Push as much of {@code be}'s buffered RF as receivers will accept across
     *  all six faces. Returns total RF drained from the BE this tick. */
    @ExpectPlatform
    public static int tickPush(YakuGeneratorBlockEntity be) {
        throw new AssertionError();
    }
}
