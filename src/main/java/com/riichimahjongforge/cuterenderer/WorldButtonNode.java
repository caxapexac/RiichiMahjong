package com.riichimahjongforge.cuterenderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * World-space button — text on a 3D extruded backplate (prism). Action-chip
 * look: top face holds the bg color, four darkened side faces give it depth,
 * hovering brightens both bg + text and scales the whole plate up.
 *
 * <p>Coordinate convention (node-local, before this node's transform): the
 * plate's visible top face lies on the X-Y plane at Z=0, with depth extruding
 * along +Z (away from the viewer). Text sits a hair in front at z = -textLift.
 * Use the node's transform to orient the plate — typically a yaw so its +Z
 * axis points away from the seat occupant.
 *
 * <p>Hover Y-lift comes from the framework via {@link Interactive#button}; this
 * node only handles the visual scale + colour shift on hover.
 */
public final class WorldButtonNode extends CuteNode {

    /** Default text scale matches legacy "nice" action chip. */
    private static final float DEFAULT_TEXT_SCALE = 0.0078f;
    /** Plate padding around text, in font px (pre-scale). */
    private static final float DEFAULT_PAD_X = 3.0f;
    private static final float DEFAULT_PAD_Y = 1.2f;
    /** Plate depth in font px (pre-scale). */
    private static final float DEFAULT_DEPTH = 2.1f;
    /** Hover-brighten delta added to each RGB channel of the bg. */
    private static final int DEFAULT_HOVER_BRIGHTEN = 90;
    /** Side-face darkening delta vs top color. */
    private static final int DEFAULT_SIDE_DARKEN = 52;
    /** Plate scale-up factor on hover. 1.0 = no scale change; framework
     *  hover-lift carries the visual feedback on its own. */
    private static final float DEFAULT_HOVER_SCALE = 1.0f;
    /** Tiny offset to draw text in front of the plate so it isn't z-fighting. */
    private static final float TEXT_LIFT_PX = 0.6f;

    private Component label;
    private int textColor = 0xFFE8E8E8;
    private int hoverTextColor = 0xFFFFFFFF;
    private int bgColor = 0xB0444A58;
    private int sideColor = darken(0xB0444A58, DEFAULT_SIDE_DARKEN);
    private boolean sideColorExplicit = false;
    private float textScale = DEFAULT_TEXT_SCALE;
    private float padX = DEFAULT_PAD_X;
    private float padY = DEFAULT_PAD_Y;
    private float depth = DEFAULT_DEPTH;
    private float hoverScale = DEFAULT_HOVER_SCALE;
    private int hoverBrighten = DEFAULT_HOVER_BRIGHTEN;
    /** Pulsing buttons (RON/TSUMO style): sine-modulated brighten + scale. */
    private boolean pulsing = false;
    /** Extra plate width on the right side of the text (in font-px before
     *  scale). Lets owners reserve inline space for adjacent content (e.g.
     *  ghost-tile previews on a "CHI [tile][tile][tile]" button). 0 = symmetric
     *  plate. */
    private float extraRightPad = 0f;

    public WorldButtonNode(Component label) {
        this.label = label;
        // Buttons render at very small textScale, so the renderer's default
        // hover-lift amount over-translates them. Scale lift down so it reads
        // as a small "press up" rather than a jump.
        setHoverLiftScale(0.25f);
    }

    public WorldButtonNode setLabel(Component c) { this.label = c; return this; }
    public WorldButtonNode setTextColor(int argb) { this.textColor = argb; return this; }
    public WorldButtonNode setHoverTextColor(int argb) { this.hoverTextColor = argb; return this; }
    public WorldButtonNode setBgColor(int argb) {
        this.bgColor = argb;
        if (!sideColorExplicit) this.sideColor = darken(argb, DEFAULT_SIDE_DARKEN);
        return this;
    }
    public WorldButtonNode setSideColor(int argb) {
        this.sideColor = argb;
        this.sideColorExplicit = true;
        return this;
    }
    public WorldButtonNode setTextScale(float s) { this.textScale = s; return this; }
    public WorldButtonNode setPlatePadding(float x, float y) { this.padX = x; this.padY = y; return this; }
    public WorldButtonNode setDepth(float d) { this.depth = d; return this; }
    public WorldButtonNode setHoverScale(float s) { this.hoverScale = s; return this; }
    public WorldButtonNode setHoverBrighten(int delta) { this.hoverBrighten = delta; return this; }
    public WorldButtonNode setPulsing(boolean p) { this.pulsing = p; return this; }
    /** Reserve extra empty plate space on the right side of the text. Units
     *  are font-px (pre-textScale). Useful for inlining adjacent content
     *  (e.g. ghost-tile previews) inside the button's plate. */
    public WorldButtonNode setExtraRightPad(float fontPx) { this.extraRightPad = Math.max(0f, fontPx); return this; }

    /** Half-width of the plate's text+padding region in world units (the
     *  symmetric portion, before {@link #extraRightPad}). Owners use this to
     *  position content right-aligned to the text inside the extra-pad area. */
    public float plateHalfWidthWorld() {
        Font font = Minecraft.getInstance().font;
        return (font.width(label) * 0.5f + padX) * textScale;
    }

    public float textScaleWorld() { return textScale; }

    /**
     * Make this button clickable using its current text plate as the hit target.
     * Hover lift comes from {@link Interactive.HoverPolicy#LIFT_AND_CLICK}.
     */
    public WorldButtonNode makeClickable(InteractKey key) {
        setInteractive(Interactive.button(key, naturalLocalAabb()));
        return this;
    }

    public Component label() { return label; }
    public float textScale() { return textScale; }

    /**
     * Node-local AABB sized to the rendered plate (text + padding). Matches the
     * un-hovered footprint so the hit test stays still while the visual scales.
     */
    @Override
    protected AABB naturalLocalAabbOrNull() { return naturalLocalAabb(); }

    public AABB naturalLocalAabb() {
        Font font = Minecraft.getInstance().font;
        float halfW = (font.width(label) * 0.5f + padX) * textScale;
        float extraRight = extraRightPad * textScale;
        float halfH = (font.lineHeight * 0.5f + padY) * textScale;
        // Plate extrudes along +Z; include the depth so a ray hitting the side
        // counts. Small clearance in front for the lifted text.
        float depthLocal = depth * textScale;
        float frontPad   = TEXT_LIFT_PX * textScale;
        return new AABB(-halfW, -halfH, -frontPad, halfW + extraRight, halfH, depthLocal);
    }

    @Override
    protected void drawSelf(PoseStack pose, MultiBufferSource buffers, int packedLight,
                            int packedOverlay, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int width = font.width(label);
        float halfW = width * 0.5f + padX;
        float halfH = font.lineHeight * 0.5f + padY;
        float plateLeft  = -halfW;
        float plateRight = halfW + extraRightPad;
        float plateZTop    = 0f;
        float plateZBottom = depth;
        float textY0 = -font.lineHeight * 0.34f;

        float pulseT = pulsing ? sinePulse() : 0f;
        float scale = textScale
                * (isHovered ? hoverScale : 1f)
                * (1f + 0.10f * pulseT);
        int brighten = (isHovered ? hoverBrighten : 0) + Math.round(50f * pulseT);
        int topArgb  = brighten > 0 ? brighten(bgColor, brighten) : bgColor;
        int sideArgb = brighten > 0 ? brighten(sideColor, brighten) : sideColor;
        int textArgb = isHovered ? hoverTextColor : textColor;
        float depthScale = 1f + 0.18f * pulseT;

        pose.pushPose();
        pose.scale(scale, scale, scale);
        Matrix4f m = pose.last().pose();

        // 3D prism plate: top face + 4 darkened side faces. Bottom face omitted —
        // it would only show if viewed from behind, and skipping it saves a draw.
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        float zb = plateZBottom * depthScale;
        emitQuad(quads, m, plateLeft, -halfH, plateZTop,  plateRight, -halfH, plateZTop,
                            plateRight,  halfH, plateZTop, plateLeft,  halfH, plateZTop, topArgb);
        // Top edge.
        emitQuad(quads, m, plateLeft,  halfH, plateZTop, plateRight,  halfH, plateZTop,
                            plateRight,  halfH, zb,        plateLeft,  halfH, zb, sideArgb);
        // Bottom edge.
        emitQuad(quads, m, plateLeft, -halfH, zb,         plateRight, -halfH, zb,
                            plateRight, -halfH, plateZTop, plateLeft, -halfH, plateZTop, sideArgb);
        // Left edge.
        emitQuad(quads, m, plateLeft, -halfH, plateZTop, plateLeft,  halfH, plateZTop,
                            plateLeft,  halfH, zb,        plateLeft, -halfH, zb, sideArgb);
        // Right edge.
        emitQuad(quads, m,  plateRight, -halfH, zb,        plateRight,  halfH, zb,
                            plateRight,  halfH, plateZTop, plateRight, -halfH, plateZTop, sideArgb);

        // Text — drawn just in front of the plate top so it's not coplanar.
        Matrix4f textPose = new Matrix4f(m).translate(0f, 0f, -TEXT_LIFT_PX);
        font.drawInBatch(
                label,
                -width * 0.5f,
                textY0,
                textArgb,
                false,
                textPose,
                buffers,
                Font.DisplayMode.NORMAL,
                0,
                packedLight);
        pose.popPose();
    }

    private static void emitQuad(
            VertexConsumer vc, Matrix4f m,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>>  8) & 0xFF;
        int b =  argb         & 0xFF;
        vc.vertex(m, x1, y1, z1).color(r, g, b, a).endVertex();
        vc.vertex(m, x2, y2, z2).color(r, g, b, a).endVertex();
        vc.vertex(m, x3, y3, z3).color(r, g, b, a).endVertex();
        vc.vertex(m, x4, y4, z4).color(r, g, b, a).endVertex();
    }

    /** 0..1 sine pulse driven by wall-clock so all clients pulse in sync per node. */
    private static float sinePulse() {
        double t = (System.nanoTime() / 1_000_000_000.0) * 2.4; // ~0.38 rad/tick equivalent
        return (float) ((Math.sin(t) + 1.0) * 0.5);
    }

    private static int brighten(int color, int delta) {
        int a = (color >>> 24) & 0xFF;
        int r = Math.min(255, ((color >>> 16) & 0xFF) + delta);
        int g = Math.min(255, ((color >>>  8) & 0xFF) + delta);
        int b = Math.min(255, ( color         & 0xFF) + delta);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int darken(int color, int delta) {
        int a = (color >>> 24) & 0xFF;
        int r = Math.max(0, ((color >>> 16) & 0xFF) - delta);
        int g = Math.max(0, ((color >>>  8) & 0xFF) - delta);
        int b = Math.max(0, ( color         & 0xFF) - delta);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
