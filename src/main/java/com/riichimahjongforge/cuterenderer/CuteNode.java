package com.riichimahjongforge.cuterenderer;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Base node in the cute scene graph. Holds a local {@link Transform}, optional
 * {@link Interactive} payload, optional children, and a per-frame "hover lift"
 * offset that the renderer animates when this node is hovered.
 *
 * <p>Concrete subclasses: {@link GroupNode}, {@link LayoutNode}, {@link BlockModelNode},
 * {@link WorldButtonNode}. Add new shapes by subclassing — the base intentionally
 * isn't sealed so owners can introduce custom node kinds.
 */
public abstract class CuteNode {

    public final Transform transform = new Transform();
    @Nullable CuteNode parent;
    private final List<CuteNode> children = new ArrayList<>(0);
    private boolean visible = true;

    @Nullable private Interactive interactive;

    /** Current animated hover lift (parent-local Y). Updated by the renderer when hovered. */
    float hoverLift = 0f;
    /** Target hover lift — renderer eases hoverLift toward it. */
    float hoverLiftTarget = 0f;
    /** Per-node multiplier on the renderer's hover-lift amount. 1.0 = full
     *  lift, smaller values for nodes that look exaggerated when lifted by
     *  the default amount (e.g. small text plates). */
    private float hoverLiftScale = 1.0f;
    public CuteNode setHoverLiftScale(float s) { this.hoverLiftScale = s; return this; }
    public float hoverLiftScale() { return hoverLiftScale; }
    /** Set by the renderer each frame: is this node currently the hovered one? */
    boolean isHovered;
    /**
     * Owner-controlled extra visual offset, parent-local Y. Purely visual: does
     * not move the AABB used for hover hit-testing. Use for things like
     * "selected tile rises slightly" where the lift must persist beyond the
     * hover lifecycle. Set directly via {@link #setExtraLift}.
     */
    private float extraLift = 0f;

    public CuteNode setExtraLift(float v) { this.extraLift = v; return this; }
    public float extraLift() { return extraLift; }
    public float totalVisualLift() { return hoverLift + extraLift; }

    public CuteNode setVisible(boolean v) { this.visible = v; return this; }
    public boolean visible() { return visible; }

    public CuteNode setInteractive(@Nullable Interactive i) { this.interactive = i; return this; }
    @Nullable public Interactive interactive() { return interactive; }

    public List<CuteNode> children() { return children; }
    @Nullable public CuteNode parent() { return parent; }

    public <T extends CuteNode> T addChild(T child) {
        if (child.parent != null) {
            child.parent.children.remove(child);
        }
        child.parent = this;
        children.add(child);
        onChildAdded(child);
        return child;
    }

    public void removeChild(CuteNode child) {
        if (children.remove(child)) {
            child.parent = null;
        }
    }

    protected void onChildAdded(CuteNode child) {}

    /** Per-frame animation step. Subclasses override to extend; always call super. */
    public void advance(float dtSeconds) {
        transform.advance(dtSeconds);
        // Cute lift ease — uses a fixed snappy rate, independent of transform.easeRate.
        float t = 1f - (float) Math.exp(-22f * dtSeconds);
        hoverLift = hoverLift + (hoverLiftTarget - hoverLift) * t;
        for (CuteNode c : children) {
            c.advance(dtSeconds);
        }
    }

    /**
     * Draw self + children. The pose stack arrives positioned at the parent's local
     * origin; this method pushes its own transform, draws, recurses, then pops.
     */
    public final void draw(PoseStack pose, MultiBufferSource buffers, int packedLight,
                           int packedOverlay, float partialTick) {
        if (!visible) return;
        pose.pushPose();
        // Visual lift (hover + owner-set extra) is applied BEFORE this node's
        // own transform so it's expressed in parent-local Y (= world Y for an
        // unrotated parent chain). Applying after transform.applyTo would route
        // the lift through this node's rotation+scale — e.g. a tile rotated
        // +90° around X would receive a Y-lift that becomes a Z-push, and the
        // magnitude would shrink by the node's scale. Lifts are purely visual;
        // the AABB used for hover detection is unaffected (composeInto omits
        // them).
        float lift = hoverLift + extraLift;
        if (lift != 0f) {
            pose.translate(0f, lift, 0f);
        }
        transform.applyTo(pose);
        drawSelf(pose, buffers, packedLight, packedOverlay, partialTick);
        for (CuteNode c : children) {
            c.draw(pose, buffers, packedLight, packedOverlay, partialTick);
        }
        pose.popPose();
    }

    /** Subclasses render their own content. Children are drawn by the base after this. */
    protected abstract void drawSelf(PoseStack pose, MultiBufferSource buffers,
                                     int packedLight, int packedOverlay, float partialTick);

    /**
     * Compose this node's full local→world matrix by walking up to the root and
     * left-multiplying parent transforms. The root's parent is null, so the root's
     * local space is treated as the renderer-relative origin (block-relative).
     */
    final Matrix4f composeWorldMatrix(Matrix4f out) {
        out.identity();
        composeInto(out);
        return out;
    }

    private void composeInto(Matrix4f out) {
        if (parent != null) parent.composeInto(out);
        // hoverLift is intentionally omitted here: the world AABB used for hover
        // hit-testing must stay at the node's rest position, otherwise the lift
        // animation would drag the AABB out from under the cursor and unhover
        // the node. Lift is purely a visual effect, applied in draw().
        transform.applyTo(out);
    }

    /**
     * Local-space AABB for non-interactive bounds queries (e.g. drawing an
     * outline around a rendered node that isn't clickable). Subclasses with
     * a meaningful natural footprint override (see {@link BlockModelNode} and
     * {@link WorldButtonNode}). Default: no bounds.
     */
    @Nullable
    protected AABB naturalLocalAabbOrNull() { return null; }

    /**
     * Transform this node's local bounds (interactive's bounds when set,
     * otherwise the natural model bounds) by the full parent chain and
     * return the resulting world-space AABB. Null if invisible or there are
     * no usable bounds.
     */
    @Nullable
    public AABB worldBoundsOrNull() {
        if (!visible) return null;
        AABB lb = interactive != null ? interactive.localBounds() : naturalLocalAabbOrNull();
        if (lb == null) return null;
        Matrix4f m = composeWorldMatrix(new Matrix4f());
        // Transform the 8 corners and take min/max.
        Vector4f c = new Vector4f();
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            float x = ((i & 1) == 0) ? (float) lb.minX : (float) lb.maxX;
            float y = ((i & 2) == 0) ? (float) lb.minY : (float) lb.maxY;
            float z = ((i & 4) == 0) ? (float) lb.minZ : (float) lb.maxZ;
            c.set(x, y, z, 1f);
            m.transform(c);
            if (c.x < minX) minX = c.x; if (c.x > maxX) maxX = c.x;
            if (c.y < minY) minY = c.y; if (c.y > maxY) maxY = c.y;
            if (c.z < minZ) minZ = c.z; if (c.z > maxZ) maxZ = c.z;
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Walk subtree (depth-first) collecting nodes whose {@link Interactive} is non-null. */
    final void collectInteractive(List<CuteNode> sink) {
        if (!visible) return;
        if (interactive != null) {
            sink.add(this);
        }
        for (CuteNode c : children) c.collectInteractive(sink);
    }

    /** For LayoutNode — get a world-space anchor at child slot {@code i}. */
    public final Vector3f worldAnchor(Vector3f out) {
        Matrix4f m = composeWorldMatrix(new Matrix4f());
        Vector4f v = new Vector4f(0, 0, 0, 1);
        m.transform(v);
        return out.set(v.x, v.y, v.z);
    }
}
