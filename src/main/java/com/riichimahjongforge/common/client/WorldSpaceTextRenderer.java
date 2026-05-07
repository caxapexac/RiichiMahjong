package com.riichimahjongforge.common.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * Reusable BE-renderer helpers for placing tabletop text labels in world space.
 * No per-game logic — callers build the text and supply layout parameters.
 */
public final class WorldSpaceTextRenderer {

    /** Visual style for a stack of horizontal lines. */
    public record Style(float scale, float lineGapRatio, int textColor, int bgColor) {}

    private WorldSpaceTextRenderer() {}

    /**
     * Draws one or more centred lines at {@code (localX, localY, localZ)} (BE-relative
     * coordinates), oriented so the text reads from the {@code outward} direction
     * (i.e. someone standing on that side of the block sees it upright).
     *
     * <p>Lines are stacked downward in screen-space using {@code style.lineGapRatio()
     * × font.lineHeight} as the row pitch.
     */
    public static void drawOutwardLines(
            PoseStack pose,
            MultiBufferSource buffers,
            int packedLight,
            Font font,
            double localX,
            double localY,
            double localZ,
            Direction outward,
            Style style,
            List<Component> lines) {
        if (lines.isEmpty()) return;

        pose.pushPose();
        pose.translate(localX, localY, localZ);
        pose.mulPose(Axis.YP.rotationDegrees(yawForOutwardFacing(outward)));
        pose.scale(-style.scale(), -style.scale(), style.scale());

        float rowPitch = font.lineHeight * style.lineGapRatio();
        for (int i = 0; i < lines.size(); i++) {
            drawCentered(pose, buffers, packedLight, font, lines.get(i), i * rowPitch, style);
        }
        pose.popPose();
    }

    private static void drawCentered(
            PoseStack pose,
            MultiBufferSource buffers,
            int packedLight,
            Font font,
            Component text,
            float yOffset,
            Style style) {
        int width = font.width(text);
        font.drawInBatch(
                text,
                -width / 2.0f,
                yOffset,
                style.textColor(),
                false,
                pose.last().pose(),
                buffers,
                Font.DisplayMode.NORMAL,
                style.bgColor(),
                packedLight);
    }

    /** Yaw such that text drawn at the origin reads correctly when viewed from {@code outward}. */
    public static float yawForOutwardFacing(Direction outward) {
        return switch (outward) {
            case NORTH -> 0.0f;
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        };
    }
}
