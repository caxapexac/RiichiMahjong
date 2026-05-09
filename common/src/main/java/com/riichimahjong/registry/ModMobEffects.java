package com.riichimahjong.registry;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.fovnormalizer.NormalizeFovMobEffect;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;

public final class ModMobEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(RiichiMahjong.MOD_ID, Registries.MOB_EFFECT);

    public static final RegistrySupplier<MobEffect> NORMALIZE_FOV_EFFECT =
            MOB_EFFECTS.register("normalize_fov", NormalizeFovMobEffect::new);

    private ModMobEffects() {}
}
