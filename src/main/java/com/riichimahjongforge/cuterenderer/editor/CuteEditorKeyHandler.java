package com.riichimahjongforge.cuterenderer.editor;

import com.mojang.blaze3d.platform.InputConstants;
import com.riichimahjongforge.RiichiMahjongForgeMod;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Registers the F8 keymapping that toggles the {@link CuteEditor} overlay.
 * Dev-only — strip with the rest of the editor package before shipping.
 */
public final class CuteEditorKeyHandler {

    public static final KeyMapping TOGGLE = new KeyMapping(
            "key.riichi_mahjong_forge.cute_editor",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "key.categories.riichi_mahjong_forge");

    private CuteEditorKeyHandler() {}

    @Mod.EventBusSubscriber(modid = RiichiMahjongForgeMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE);
        }
    }

    @Mod.EventBusSubscriber(modid = RiichiMahjongForgeMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {}

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            // consumeClick() drains queued presses; toggling on every drained press
            // matches vanilla key-binding semantics.
            while (TOGGLE.consumeClick()) {
                CuteEditor.toggle();
            }
        }
    }
}
