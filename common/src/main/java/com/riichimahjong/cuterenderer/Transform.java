package com.riichimahjong.cuterenderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Mutable local-space TRS plus a "target" copy for animation.
 *
 * <p>Each node owns a current transform and a target transform; per frame the renderer
 * exponentially eases current toward target. Setting a target equal to current makes
 * a node static; calling {@link #snapToTarget()} skips the animation. All values are
 * in the parent's local space (parent of root nodes is the master block-relative origin).
 */
public final class Transform {

    /** Position in parent-local pixels (1.0 = one minecraft block). */
    public final Vector3f pos = new Vector3f();
    /** Rotation as a quaternion. */
    public final Quaternionf rot = new Quaternionf();
    /** Per-axis scale (1.0 = unscaled). */
    public final Vector3f scale = new Vector3f(1f, 1f, 1f);

    private final Vector3f targetPos = new Vector3f();
    private final Quaternionf targetRot = new Quaternionf();
    private final Vector3f targetScale = new Vector3f(1f, 1f, 1f);

    /** 1.0 = snap, lower = laggier. Reasonable range 4..30 (per-second easing rate). */
    public float easeRate = 14f;

    public Transform() {}

    public Transform setPos(double x, double y, double z) {
        pos.set((float) x, (float) y, (float) z);
        targetPos.set(pos);
        return this;
    }

    public Transform setRotY(float yawDegrees) {
        rot.identity().rotateY((float) Math.toRadians(yawDegrees));
        targetRot.set(rot);
        return this;
    }

    /** Set rotation directly. Both current and target are set (no animation). */
    public Transform setRotation(Quaternionf q) {
        rot.set(q);
        targetRot.set(q);
        return this;
    }

    /**
     * Lay a node flat (face up) on a horizontal surface, oriented so its native
     * +Y axis (top of text / front of model) points away from a viewer standing
     * on the given side of the block.
     *
     * <p>Built on {@link #setRotation}: applies {@code rotateX(+90°)} to tip the
     * node face-up, then a world-space Y rotation chosen so the natural reading
     * direction is correct for {@code viewer}. Non-horizontal directions are
     * treated as {@link Direction#SOUTH} (the unrotated baseline).
     */
    public Transform layFlatReadableFrom(Direction viewer) {
        float yawDeg = switch (viewer) {
            case NORTH -> 180f;
            case EAST  ->  90f;
            case WEST  -> -90f;
            default    ->   0f; // SOUTH (and any non-horizontal)
        };
        return setRotation(new Quaternionf()
                .rotateX((float) Math.toRadians(90))
                .rotateLocalY((float) Math.toRadians(yawDeg)));
    }

    public Transform setScale(float s) {
        scale.set(s, s, s);
        targetScale.set(scale);
        return this;
    }

    public Transform setScale(float sx, float sy, float sz) {
        scale.set(sx, sy, sz);
        targetScale.set(scale);
        return this;
    }

    public Transform targetPos(double x, double y, double z) {
        targetPos.set((float) x, (float) y, (float) z);
        return this;
    }

    public Transform targetRotY(float yawDegrees) {
        targetRot.identity().rotateY((float) Math.toRadians(yawDegrees));
        return this;
    }

    /** Animate toward an arbitrary rotation. Mirrors {@link #setRotation} (which snaps both). */
    public Transform targetRotation(Quaternionf q) {
        targetRot.set(q);
        return this;
    }

    public Transform targetScale(float s) {
        targetScale.set(s, s, s);
        return this;
    }

    public void snapToTarget() {
        pos.set(targetPos);
        rot.set(targetRot);
        scale.set(targetScale);
    }

    /** Exponential ease toward target. dtSeconds is real time since last frame. */
    public void advance(float dtSeconds) {
        if (easeRate <= 0f) {
            snapToTarget();
            return;
        }
        float t = 1f - (float) Math.exp(-easeRate * dtSeconds);
        pos.lerp(targetPos, t);
        rot.slerp(targetRot, t);
        scale.lerp(targetScale, t);
    }

    /** Apply the current TRS to the active matrix on a PoseStack. */
    public void applyTo(PoseStack poseStack) {
        poseStack.translate(pos.x, pos.y, pos.z);
        poseStack.mulPose(rot);
        poseStack.scale(scale.x, scale.y, scale.z);
    }

    /** Apply the current TRS to a Matrix4f (right-multiply). */
    public void applyTo(Matrix4f m) {
        m.translate(pos);
        m.rotate(rot);
        m.scale(scale);
    }

    public Vector3f targetPos() {
        return targetPos;
    }
}
