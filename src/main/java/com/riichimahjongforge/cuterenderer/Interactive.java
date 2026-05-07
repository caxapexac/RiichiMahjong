package com.riichimahjongforge.cuterenderer;

import net.minecraft.world.phys.AABB;

/**
 * Interaction profile attached to a {@link CuteNode}. Null = non-interactive.
 *
 * @param key node identity sent on click
 * @param localBounds AABB in the node's <b>local</b> space (before its own transform);
 *                    intersected against the camera ray after the full parent chain
 *                    is applied
 * @param policy hover behaviour
 */
public record Interactive(InteractKey key, AABB localBounds, HoverPolicy policy) {

    public enum HoverPolicy {
        /** Render outline highlight only. No motion / sound. */
        HIGHLIGHT_ONLY,
        /** Outline + lift cute offset + click sound on hover-enter. */
        LIFT_AND_CLICK
    }

    public static Interactive button(InteractKey key, AABB bounds) {
        return new Interactive(key, bounds, HoverPolicy.LIFT_AND_CLICK);
    }

    public static Interactive highlight(InteractKey key, AABB bounds) {
        return new Interactive(key, bounds, HoverPolicy.HIGHLIGHT_ONLY);
    }
}
