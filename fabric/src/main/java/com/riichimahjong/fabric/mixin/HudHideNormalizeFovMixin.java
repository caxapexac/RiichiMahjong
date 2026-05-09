package com.riichimahjong.fabric.mixin;

import com.riichimahjong.registry.ModMobEffects;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Hides the {@code normalize_fov} marker effect from the in-game HUD effect list.
 * Fabric counterpart of NeoForge's {@link com.riichimahjong.neoforge.caps.NormalizeFovEffectClientExtensions}.
 *
 * <p>Redirects the {@code LivingEntity.getActiveEffects()} call inside
 * {@code Gui.renderEffects} to a filtered collection that drops our marker effect.
 * Other effects are unaffected.
 */
@Mixin(Gui.class)
public abstract class HudHideNormalizeFovMixin {

    @Redirect(
            method = "renderEffects",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getActiveEffects()Ljava/util/Collection;"))
    private Collection<MobEffectInstance> riichi_mahjong$filterNormalizeFov(LocalPlayer player) {
        return player.getActiveEffects().stream()
                .filter(inst -> inst.getEffect().value() != ModMobEffects.NORMALIZE_FOV_EFFECT.get())
                .collect(Collectors.toList());
    }
}
