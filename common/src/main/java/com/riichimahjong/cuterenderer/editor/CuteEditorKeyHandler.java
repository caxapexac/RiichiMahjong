package com.riichimahjong.cuterenderer.editor;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Registers the F8 keymapping that toggles the {@link CuteEditor} overlay.
 * Dev-only — the editor is layout-tuning helper UI.
 *
 * <p>Cross-loader via Architectury's {@link KeyMappingRegistry} (replaces Forge
 * {@code RegisterKeyMappingsEvent}) and {@link ClientTickEvent#CLIENT_POST}
 * (replaces Forge {@code TickEvent.ClientTickEvent}). The 1.20.1 Forge port used
 * {@code KeyConflictContext.IN_GAME} to suppress the binding while a screen was
 * open; since vanilla {@code KeyMapping} has no equivalent, we replicate the
 * gate manually by ignoring presses while {@code Minecraft.screen != null}.
 */
@Environment(EnvType.CLIENT)
public final class CuteEditorKeyHandler {

    public static final KeyMapping TOGGLE = new KeyMapping(
            "key.riichi_mahjong.cute_editor",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "key.categories.riichi_mahjong");

    private CuteEditorKeyHandler() {}

    /** Call once from CLIENT init. */
    public static void register() {
        KeyMappingRegistry.register(TOGGLE);
        ClientTickEvent.CLIENT_POST.register(mc -> {
            // Drain queued presses. Suppress while a screen is up so typing in chat
            // / inventory etc. doesn't accidentally toggle the editor overlay.
            // CuteEditor itself opens its own screen — that's exempt from the gate
            // since we want to be able to close the editor with the same key.
            while (TOGGLE.consumeClick()) {
                if (Minecraft.getInstance().screen != null
                        && !(Minecraft.getInstance().screen instanceof CuteEditorScreen)) {
                    continue;
                }
                CuteEditor.toggle();
            }
        });
    }
}
