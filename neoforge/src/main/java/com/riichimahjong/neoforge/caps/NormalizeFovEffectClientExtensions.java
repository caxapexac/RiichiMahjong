package com.riichimahjong.neoforge.caps;

import com.riichimahjong.registry.ModMobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Hides the {@code normalize_fov} marker effect from the HUD and inventory effect
 * list. The effect is purely a server-side trigger for the client-side FOV blend
 * (see {@code NormalizeFovBlend}); showing it as a status icon would be noise.
 *
 * <p>NeoForge equivalent of the 1.20.1 Forge {@code IClientMobEffectExtensions}.
 * Fabric handles this via {@code MobEffectVisibilityMixin}.
 */
public final class NormalizeFovEffectClientExtensions implements IClientMobEffectExtensions {

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterClientExtensionsEvent event) ->
                event.registerMobEffect(
                        new NormalizeFovEffectClientExtensions(),
                        ModMobEffects.NORMALIZE_FOV_EFFECT.get()));
    }

    @Override
    public boolean isVisibleInInventory(MobEffectInstance instance) {
        return false;
    }

    @Override
    public boolean isVisibleInGui(MobEffectInstance instance) {
        return false;
    }
}
