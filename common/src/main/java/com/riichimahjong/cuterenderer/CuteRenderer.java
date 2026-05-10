package com.riichimahjong.cuterenderer;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Per-block-entity scene root. The owning {@link BlockEntityRenderer} creates one
 * instance per BE in its constructor and calls {@link #frame} from {@code render(...)}.
 *
 * <p>Hover detection and animation are driven from {@link #frame} — no global tick
 * subscription. A static client-side map ({@link #LIVE}) tracks live renderers by
 * block position so the click input handler can look one up when forwarding clicks.
 *
 * <p>Owners build the scene declaratively via {@link #root()} and the node API.
 * Re-running scene-building after state changes is fine; targeting transforms
 * triggers smooth glide instead of teleport.
 */
public final class CuteRenderer {

    /** All renderers currently attached, keyed by (dimension, master pos). */
    private static final Map<Key, CuteRenderer> LIVE = new HashMap<>();

    private final Key key;
    private final GroupNode root = new GroupNode();
    @Nullable private CuteNode hovered;
    @Nullable private CuteNode lastHovered;
    /** Identity for hover-enter detection survives scene rebuilds. Comparing
     *  {@code hovered != lastHovered} by object identity false-fires every frame
     *  in modes that rebuild the scene per tick (editor preview, Dealing). */
    @Nullable private InteractKey lastHoveredKey;
    private long lastFrameNanos = -1L;
    private float hoverLiftAmount = 0.06f;
    @Nullable private SoundEvent hoverSound;
    private float hoverSoundVolume = 0.55f;
    private float hoverSoundPitch = 1.0f;

    /** Owner identity for the click packet. */
    public record Key(ResourceKey<Level> dim, BlockPos masterPos) {}

    public CuteRenderer(ResourceKey<Level> dim, BlockPos masterPos) {
        this.key = new Key(dim, masterPos);
    }

    /**
     * Register this renderer under the master pos. Call once when the BE binds.
     * Multipart owners don't need to register every cell — the click handler
     * resolves any clicked cell to its master via {@code BaseMultipartBlock.masterPos}.
     */
    public void attach() {
        LIVE.put(key, this);
    }

    /** Unregister. */
    public void detach() {
        LIVE.remove(key, this);
    }

    public Key key() { return key; }
    public GroupNode root() { return root; }

    /**
     * Rotate the scene root to match a {@link Direction} {@code FACING} property,
     * pivoting on the master cell centre at master-local (0.5, 0, 0.5).
     *
     * <p>Minecraft's blockstate {@code "y": N} rotates clockwise viewed from above;
     * Joml's {@code rotateY(+θ)} is counter-clockwise. The mapping is therefore
     * {@code MC y° = -θ°}. The pose offset {@code (I − R)·center} keeps the
     * rotation centred on the master cell.
     */
    public void setFacing(Direction facing) {
        float yawDeg = switch (facing) {
            case EAST  -> -90f;
            case SOUTH -> -180f;
            case WEST  -> 90f;
            default    -> 0f; // NORTH (and any non-horizontal)
        };
        Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(yawDeg));
        Vector3f rotatedCenter = rot.transform(new Vector3f(0.5f, 0f, 0.5f));
        Vector3f offset = new Vector3f(0.5f, 0f, 0.5f).sub(rotatedCenter);
        root.transform.setPos(offset.x, offset.y, offset.z);
        root.transform.setRotation(rot);
    }

    /** Vertical lift in world units when hovered with LIFT_AND_CLICK. Default 0.06. */
    public CuteRenderer setHoverLift(float worldUnits) { this.hoverLiftAmount = worldUnits; return this; }

    /** Sound played on hover-enter for LIFT_AND_CLICK nodes. Null = no sound. */
    public CuteRenderer setHoverSound(@Nullable SoundEvent s, float volume, float pitch) {
        this.hoverSound = s; this.hoverSoundVolume = volume; this.hoverSoundPitch = pitch;
        return this;
    }

    /** Currently hovered node, or null. Used by the click handler. */
    @Nullable public CuteNode hovered() { return hovered; }

    /**
     * Drive one frame: animate, raycast hover, then draw. Called from the BE renderer.
     *
     * <p>The pose stack arrives positioned at the master block origin (the BE's
     * {@code render(...)} pushes block-space already). All scene coordinates are
     * therefore relative to the master block.
     */
    public void frame(PoseStack pose, MultiBufferSource buffers, int packedLight,
                      int packedOverlay, float partialTick) {
        long now = System.nanoTime();
        float dt = lastFrameNanos < 0 ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;

        List<CuteNode> interactives = new ArrayList<>();
        root.collectInteractive(interactives);
        updateHover(interactives);
        applyHoverEffects(interactives);
        root.advance(dt);
        root.draw(pose, buffers, packedLight, packedOverlay, partialTick);

        if (hovered != null) {
            // Match vanilla's block-targeting outline (black, ~0.4 alpha).
            // Underlying hit-test AABB stays at rest; only the drawn outline
            // tracks the visual lift so the lift can't drag the cursor target.
            HoverHighlightRenderer.drawNodeOutline(pose, buffers, hovered, 0f, 0f, 0f, 0.4f);
        }

        InteractKey hoveredKey = hovered != null && hovered.interactive() != null
                ? hovered.interactive().key() : null;
        if (hovered != null && !java.util.Objects.equals(hoveredKey, lastHoveredKey)) {
            playHoverEnter(hovered);
        }
        lastHovered = hovered;
        lastHoveredKey = hoveredKey;
    }

    /**
     * Find the hovered interactive node, if any, by ray-AABB testing against the
     * camera's current pick ray. We only consider nodes whose AABB ray-distance is
     * &le; the vanilla pick distance, so the player aiming past the table still
     * hovers whatever's behind us.
     */
    private void updateHover(List<CuteNode> candidates) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        if (mc.player == null || mc.level == null) { hovered = null; return; }
        if (!mc.level.dimension().equals(key.dim)) { hovered = null; return; }

        Vec3 origin = cam.getPosition();
        Vec3 dir = new Vec3(cam.getLookVector());
        // Translate ray into block-relative space (root is at master block origin).
        Vec3 localOrigin = origin.subtract(
                key.masterPos.getX(), key.masterPos.getY(), key.masterPos.getZ());
        // Cap ray length at a generous reach (vanilla creative reach is 5 blocks);
        // the player can only interact with what's within reach anyway, and the
        // ray length doesn't otherwise matter for AABB hit tests.
        Vec3 endpoint = localOrigin.add(dir.scale(8.0));

        CuteNode best = null;
        double bestT = Double.POSITIVE_INFINITY;
        for (CuteNode n : candidates) {
            AABB world = n.worldBoundsOrNull();
            if (world == null) continue;
            var hit = world.clip(localOrigin, endpoint);
            if (hit.isEmpty()) continue;
            double t = hit.get().distanceTo(localOrigin);
            if (t < bestT) {
                bestT = t;
                best = n;
            }
        }
        hovered = best;
    }

    private void applyHoverEffects(List<CuteNode> all) {
        // Walk all interactive nodes and set hovered flag + lift target.
        for (CuteNode n : all) {
            boolean h = (n == hovered);
            n.isHovered = h;
            Interactive i = n.interactive();
            if (i != null && i.policy() == Interactive.HoverPolicy.LIFT_AND_CLICK) {
                n.hoverLiftTarget = h ? hoverLiftAmount * n.hoverLiftScale() : 0f;
            } else {
                n.hoverLiftTarget = 0f;
            }
        }
    }

    private void playHoverEnter(CuteNode n) {
        Interactive i = n.interactive();
        if (i == null || i.policy() != Interactive.HoverPolicy.LIFT_AND_CLICK) return;
        if (hoverSound == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            org.slf4j.LoggerFactory.getLogger("TableSound").info(
                    "[CuteHover] key={} sound={}", i.key(), hoverSound.getLocation());
            mc.player.playSound(hoverSound, hoverSoundVolume, hoverSoundPitch);
        }
    }

    /** Look up the live renderer for a given (dim, master pos). Null if none. */
    @Nullable
    public static CuteRenderer find(ResourceKey<Level> dim, BlockPos masterPos) {
        return LIVE.get(new Key(dim, masterPos));
    }
}
