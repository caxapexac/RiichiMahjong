package com.riichimahjong.fabric;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.fabric.storage.SolitaireFluidStorage;
import com.riichimahjong.fabric.storage.SolitaireItemStorage;
import com.riichimahjong.fabric.storage.YakuGenEnergyStorage;
import com.riichimahjong.registry.ModBlockEntities;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import team.reborn.energy.api.EnergyStorage;

public final class RiichiMahjongFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        RiichiMahjong.init();

        // Solitaire BE exposes its hopper-input + Liquid XP output via Fabric Storages.
        // Side-agnostic — same storage on every face (that's what Carry On / hoppers expect).
        ItemStorage.SIDED.registerForBlockEntity(
                (be, side) -> new SolitaireItemStorage(be),
                ModBlockEntities.MAHJONG_SOLITAIRE_BLOCK_ENTITY.get());
        FluidStorage.SIDED.registerForBlockEntity(
                (be, side) -> new SolitaireFluidStorage(be),
                ModBlockEntities.MAHJONG_SOLITAIRE_BLOCK_ENTITY.get());

        // Yaku generator: Team Reborn Energy storage for RF output. Plays nicely with
        // Fabric tech mods (Industrial Revolution, Modern Industrialization, etc.).
        EnergyStorage.SIDED.registerForBlockEntity(
                (be, side) -> new YakuGenEnergyStorage(be),
                ModBlockEntities.YAKU_GENERATOR_BLOCK_ENTITY.get());
    }
}
