package com.riichimahjong.fabric.mixin;

import com.riichimahjong.registry.ModMobEffects;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Hides the {@code normalize_fov} marker effect from the inventory effect list.
 * Fabric counterpart of NeoForge's {@link com.riichimahjong.neoforge.caps.NormalizeFovEffectClientExtensions}.
 *
 * <p>Redirects every {@code LivingEntity.getActiveEffects()} call inside
 * {@link EffectRenderingInventoryScreen} to a filtered collection — the class
 * iterates effects in multiple methods (background panel sizing, icon list,
 * text labels), so the redirect is method-wildcarded.
 */
@Mixin(EffectRenderingInventoryScreen.class)
public abstract class InventoryHideNormalizeFovMixin {

    @Redirect(
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getActiveEffects()Ljava/util/Collection;"))
    private Collection<MobEffectInstance> riichi_mahjong$filterNormalizeFov(LocalPlayer player) {
        return player.getActiveEffects().stream()
                .filter(inst -> inst.getEffect().value() != ModMobEffects.NORMALIZE_FOV_EFFECT.get())
                .collect(Collectors.toList());
    }
}
