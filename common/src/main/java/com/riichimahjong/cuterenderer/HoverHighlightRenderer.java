package com.riichimahjong.cuterenderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;

/**
 * Draws a wireframe outline of an AABB using {@link RenderType#lines()} — the same
 * pipeline vanilla uses for the block-targeting outline. Colour matches vanilla's
 * default outline (black with full alpha) by default.
 */
public final class HoverHighlightRenderer {

    private HoverHighlightRenderer() {}

    /**
     * Outline a {@link CuteNode}'s world AABB, shifted by its current visual lift so
     * the wireframe tracks the rendered position rather than the rest pose. No-op if
     * the node has no world bounds (e.g. it was just removed). Use this for hover and
     * selection highlights so they stay visually consistent.
     */
    public static void drawNodeOutline(PoseStack pose, MultiBufferSource buffers, CuteNode node,
                                       float r, float g, float b, float a) {
        AABB box = node.worldBoundsOrNull();
        if (box == null) return;
        float lift = node.totalVisualLift();
        if (lift != 0f) box = box.move(0, lift, 0);
        drawAabb(pose, buffers, box, r, g, b, a);
    }

    public static void drawAabb(PoseStack pose, MultiBufferSource buffers, AABB box,
                                float r, float g, float b, float a) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose entry = pose.last();
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;

        // 12 edges — bottom rectangle, top rectangle, four verticals.
        line(vc, entry, x0,y0,z0, x1,y0,z0, r,g,b,a);
        line(vc, entry, x1,y0,z0, x1,y0,z1, r,g,b,a);
        line(vc, entry, x1,y0,z1, x0,y0,z1, r,g,b,a);
        line(vc, entry, x0,y0,z1, x0,y0,z0, r,g,b,a);

        line(vc, entry, x0,y1,z0, x1,y1,z0, r,g,b,a);
        line(vc, entry, x1,y1,z0, x1,y1,z1, r,g,b,a);
        line(vc, entry, x1,y1,z1, x0,y1,z1, r,g,b,a);
        line(vc, entry, x0,y1,z1, x0,y1,z0, r,g,b,a);

        line(vc, entry, x0,y0,z0, x0,y1,z0, r,g,b,a);
        line(vc, entry, x1,y0,z0, x1,y1,z0, r,g,b,a);
        line(vc, entry, x1,y0,z1, x1,y1,z1, r,g,b,a);
        line(vc, entry, x0,y0,z1, x0,y1,z1, r,g,b,a);
    }

    /** Public so the editor can draw axis triads with the same pipeline. */
    public static void line(VertexConsumer vc, PoseStack.Pose entry,
                             float ax, float ay, float az,
                             float bx, float by, float bz,
                             float r, float g, float b, float a) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1e-6f) { dx /= len; dy /= len; dz /= len; }
        vc.addVertex(entry, ax, ay, az).setColor(r, g, b, a).setNormal(entry, dx, dy, dz);
        vc.addVertex(entry, bx, by, bz).setColor(r, g, b, a).setNormal(entry, dx, dy, dz);
    }
}
