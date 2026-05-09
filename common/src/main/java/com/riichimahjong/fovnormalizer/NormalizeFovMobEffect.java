package com.riichimahjong.fovnormalizer;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Marker effect used to drive client-side smooth FOV normalization near a fov_normalizer.
 *
 * <p>Hidden from the HUD and inventory effect list per-loader (Architectury has no
 * unified hook for this): on NeoForge via {@code NormalizeFovEffectClientExtensions}
 * (IClientMobEffectExtensions), on Fabric via {@code HudHideNormalizeFovMixin} +
 * {@code InventoryHideNormalizeFovMixin}.
 */
public final class NormalizeFovMobEffect extends MobEffect {

    public NormalizeFovMobEffect() {
        super(MobEffectCategory.NEUTRAL, 0x7A7A7A);
    }
}
