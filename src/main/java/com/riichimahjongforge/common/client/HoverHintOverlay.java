package com.riichimahjongforge.common.client;

import com.riichimahjongforge.RiichiMahjongForgeMod;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
public final class HoverHintOverlay {

    private static final List<Function<Minecraft, String>> RESOLVERS = new CopyOnWriteArrayList<>();
    @Nullable
    private static String activeKey;

    private HoverHintOverlay() {}

    public static void register(Function<Minecraft, String> resolver) {
        RESOLVERS.add(resolver);
    }

    @Mod.EventBusSubscriber(modid = RiichiMahjongForgeMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class TickSubscriber {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            tick();
        }
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
