package com.riichimahjongforge.fovnormalizer.client;

import com.riichimahjongforge.RiichiMahjongForgeMod;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Smoothly normalizes local FOV while the normalize effect is active. */
@Mod.EventBusSubscriber(modid = RiichiMahjongForgeMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class NormalizeFovClientHandler {

    private static final int DEFAULT_FOV = 70;
    private static final float TRANSITION_FACTOR = 0.1f;
    private static float normalizeBlend = 0.0f;

    private NormalizeFovClientHandler() {}

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (event.getCamera().getEntity() == null) {
            normalizeBlend = 0.0f;
            return;
        }
        boolean hasEffect = event.getCamera().getEntity() instanceof LivingEntity living
                && living.hasEffect(RiichiMahjongForgeMod.NORMALIZE_FOV_EFFECT.get());
        float targetBlend = hasEffect ? 1.0f : 0.0f;
        normalizeBlend += (targetBlend - normalizeBlend) * TRANSITION_FACTOR;
        normalizeBlend = Mth.clamp(normalizeBlend, 0.0f, 1.0f);
        if (normalizeBlend < 0.001f && !hasEffect) {
            normalizeBlend = 0.0f;
            return;
        }
        double vanillaFov = event.getFOV();
        double blendedFov = vanillaFov + (DEFAULT_FOV - vanillaFov) * normalizeBlend;
        event.setFOV(blendedFov);
    }
}
