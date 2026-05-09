package com.riichimahjong.registry;

import com.riichimahjong.RiichiMahjong;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(RiichiMahjong.MOD_ID, Registries.SOUND_EVENT);

    public static final RegistrySupplier<SoundEvent> TILE_PLACE_SOUND =
            SOUND_EVENTS.register("tile.place",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "tile.place")));

    public static final RegistrySupplier<SoundEvent> TILE_HOVER_TILE_SOUND =
            SOUND_EVENTS.register("tile.hover.tile",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "tile.hover.tile")));

    public static final RegistrySupplier<SoundEvent> TILE_HOVER_ACTION_SOUND =
            SOUND_EVENTS.register("tile.hover.action",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "tile.hover.action")));

    private ModSounds() {}
}
