package com.riichimahjong.neoforge.caps;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.registry.ModFluids;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * NeoForge 1.21 client-extensions registration for the Liquid Experience fluid.
 *
 * <p>Workaround for an Architectury 13.0.8 limitation: {@code ArchitecturyFluidAttributesForge}
 * exposes its client extensions through the deprecated {@code FluidType.initializeClient}
 * hook (1.20.1-style). NeoForge 1.21 routes {@code IClientFluidTypeExtensions.of(FluidState)}
 * through {@link RegisterClientExtensionsEvent} instead, so the deprecated hook never
 * fires — leading to {@code FluidSpriteCache.getFluidSprites} NPE when the fluid
 * renders in-world.
 *
 * <p>13.0.8 is the final Architectury release for the 1.21.1 line; no upstream fix is
 * coming. We register here directly. Same textures and tint as the {@code SimpleArchitecturyFluidAttributes}
 * already configured in common.
 */
public final class LiquidExperienceClientExtensions implements IClientFluidTypeExtensions {

    private static final ResourceLocation STILL =
            ResourceLocation.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "block/liquid_experience_still");
    private static final ResourceLocation FLOW =
            ResourceLocation.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "block/liquid_experience_flow");
    private static final int TINT = 0xFFC8FF40;

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterClientExtensionsEvent event) ->
                event.registerFluidType(
                        new LiquidExperienceClientExtensions(),
                        ModFluids.LIQUID_EXPERIENCE.get().getFluidType()));
    }

    @Override public ResourceLocation getStillTexture()   { return STILL; }
    @Override public ResourceLocation getFlowingTexture() { return FLOW; }
    @Override public int getTintColor()                   { return TINT; }
}
