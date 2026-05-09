package com.riichimahjong.common.client;

import dev.architectury.event.events.client.ClientTickEvent;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Action-bar hint dispatcher. Owns the only client-tick subscriber; callers just
 * register a resolver function via {@link #register(Function)} and return either a
 * translation key or {@code null}.
 *
 * <p>Resolvers are polled in registration order; the first non-null key wins. The
 * overlay is only re-set when the winning key changes, so the on-screen fade timer
 * isn't reset every tick.
 *
 * <p>The {@link Minecraft} instance passed to a resolver is guaranteed non-null with
 * a non-null player and level — null-checks live here, not in the resolver.
 */
@Environment(EnvType.CLIENT)
public final class HoverHintOverlay {

    private static final List<Function<Minecraft, String>> RESOLVERS = new CopyOnWriteArrayList<>();
    @Nullable
    private static String activeKey;

    private HoverHintOverlay() {}

    public static void register(Function<Minecraft, String> resolver) {
        RESOLVERS.add(resolver);
    }

    /** Call once from CLIENT init to wire the per-tick poll. */
    public static void registerTickHandler() {
        ClientTickEvent.CLIENT_POST.register(mc -> tick());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        String next = (mc.player == null || mc.level == null) ? null : firstNonNullKey(mc);
        if (Objects.equals(activeKey, next)) return;
        mc.gui.setOverlayMessage(
                next == null ? Component.empty() : Component.translatable(next),
                false);
        activeKey = next;
    }

    @Nullable
    private static String firstNonNullKey(Minecraft mc) {
        for (Function<Minecraft, String> resolver : RESOLVERS) {
            String key = resolver.apply(mc);
            if (key != null) return key;
        }
        return null;
    }
}
