package com.riichimahjongforge.fovnormalizer;

import net.minecraft.client.gui.Gui;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;

import java.util.function.Consumer;

/** Marker effect used to drive client-side smooth FOV normalization near a table. */
public final class NormalizeFovMobEffect extends MobEffect {

    public NormalizeFovMobEffect() {
        super(MobEffectCategory.NEUTRAL, 0x7A7A7A);
    }

    @Override
    public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
        consumer.accept(new IClientMobEffectExtensions() {
            @Override
            public boolean isVisibleInInventory(MobEffectInstance instance) {
                return false;
            }

            @Override
            public boolean isVisibleInGui(MobEffectInstance instance) {
                return false;
            }

            @Override
            public boolean renderInventoryIcon(
                    MobEffectInstance instance,
                    EffectRenderingInventoryScreen<?> screen,
                    GuiGraphics guiGraphics,
                    int x,
                    int y,
                    int blitOffset) {
                return true;
            }

            @Override
            public boolean renderInventoryText(
                    MobEffectInstance instance,
                    EffectRenderingInventoryScreen<?> screen,
                    GuiGraphics guiGraphics,
                    int x,
                    int y,
                    int blitOffset) {
                return true;
            }

            @Override
            public boolean renderGuiIcon(
                    MobEffectInstance instance,
                    Gui gui,
                    GuiGraphics guiGraphics,
                    int x,
                    int y,
                    float z,
                    float alpha) {
                return true;
            }
        });
    }
}
