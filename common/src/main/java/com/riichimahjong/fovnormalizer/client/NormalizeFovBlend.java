package com.riichimahjong.fovnormalizer.client;

import com.riichimahjong.registry.ModMobEffects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

/**
 * Smoothly normalizes local FOV while the normalize effect is active.
 *
 * <p>Stateless from the caller's perspective: pass in the camera entity and the
 * vanilla-computed FOV; we tick the internal blend toward target (1 if effect
 * present, 0 otherwise) and return the resulting FOV.
 *
 * <p>Hooked per-loader: NeoForge subscribes to {@code ViewportEvent.ComputeFov}
 * on the game bus; Fabric uses a mixin into {@code GameRenderer.getFov}.
 * Architectury has no built-in FOV-modify event.
 */
@Environment(EnvType.CLIENT)
public final class NormalizeFovBlend {

    private static final int DEFAULT_FOV = 70;
    private static final float TRANSITION_FACTOR = 0.1f;

    private static float normalizeBlend = 0.0f;

    private NormalizeFovBlend() {}

    /**
     * Tick the blend toward the target (1 with effect, 0 without) and return the FOV
     * to use this frame given the vanilla-computed value. Always-safe to call: returns
     * the input verbatim when no transition is in progress.
     */
    public static double tickAndCompute(LivingEntity entity, double vanillaFov) {
        // 1.21: hasEffect takes Holder<MobEffect>. Resolve it from the entity's level.
        Holder<MobEffect> holder = entity.level().registryAccess()
                .registryOrThrow(Registries.MOB_EFFECT)
                .wrapAsHolder(ModMobEffects.NORMALIZE_FOV_EFFECT.get());
        boolean hasEffect = entity.hasEffect(holder);
        float targetBlend = hasEffect ? 1.0f : 0.0f;
        normalizeBlend += (targetBlend - normalizeBlend) * TRANSITION_FACTOR;
        normalizeBlend = Mth.clamp(normalizeBlend, 0.0f, 1.0f);
        if (normalizeBlend < 0.001f && !hasEffect) {
            normalizeBlend = 0.0f;
            return vanillaFov;
        }
        return vanillaFov + (DEFAULT_FOV - vanillaFov) * normalizeBlend;
    }
}
