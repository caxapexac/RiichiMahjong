package com.riichimahjongforge.cuterenderer;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Renders a baked block model in scaled local space. The model is resolved on
 * first use and cached on the node; if you need a different model, make a new node.
 *
 * <p>Renders all sides + face quads in the model's natural render type (translucent /
 * cutout / solid). Cull faces are ignored — these are floating tabletop objects, no
 * neighbouring block to occlude.
 */
public final class BlockModelNode extends CuteNode {

    /** Vertex stride in ints for {@code DefaultVertexFormat.BLOCK} (3 pos + 1 col + 2 uv + 1 light + 1 normal). */
    private static final int VERTEX_STRIDE_INTS = 8;

    private final BlockState state;
    @Nullable private BakedModel cachedModel;
    @Nullable private AABB cachedNaturalAabb;
    private final RandomSource rng = RandomSource.create();

    public BlockModelNode(BlockState state) {
        this.state = state;
    }

    public BlockState state() {
        return state;
    }

    /**
     * Make this model clickable using its baked-quad footprint as the hit target.
     * Equivalent to {@code setInteractive(Interactive.button(key, naturalLocalAabb()))}
     * but reads cleanly in a fluent chain. Mirrors {@code WorldButtonNode.makeClickable}.
     */
    public BlockModelNode makeClickable(InteractKey key) {
        setInteractive(Interactive.button(key, naturalLocalAabb()));
        return this;
    }

    /**
     * Node-local AABB tightly enclosing the baked model's geometry, with the same
     * {@code (-0.5, -0.5, -0.5)} recentering {@link #drawSelf} applies. Use this as
     * an {@link Interactive#localBounds} so click + hover hit-tests match the
     * rendered footprint without per-model magic numbers.
     *
     * <p>Resolved lazily: the BakedModel is fetched on first call (must be on the
     * client render thread), the union of all quad vertex positions is computed
     * once, and the result is cached for the node's lifetime. Falls back to the
     * unit cube if the model has no quads at all.
     */
    @Override
    protected AABB naturalLocalAabbOrNull() { return naturalLocalAabb(); }

    public AABB naturalLocalAabb() {
        if (cachedNaturalAabb != null) return cachedNaturalAabb;
        float[] bounds = {
                Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY
        };
        boolean[] any = {false};
        forEachQuad(q -> {
            int[] v = q.getVertices();
            for (int i = 0; i < 4; i++) {
                int o = i * VERTEX_STRIDE_INTS;
                float x = Float.intBitsToFloat(v[o]);
                float y = Float.intBitsToFloat(v[o + 1]);
                float z = Float.intBitsToFloat(v[o + 2]);
                if (x < bounds[0]) bounds[0] = x; if (x > bounds[3]) bounds[3] = x;
                if (y < bounds[1]) bounds[1] = y; if (y > bounds[4]) bounds[4] = y;
                if (z < bounds[2]) bounds[2] = z; if (z > bounds[5]) bounds[5] = z;
                any[0] = true;
            }
        });
        cachedNaturalAabb = any[0]
                ? new AABB(bounds[0] - 0.5, bounds[1] - 0.5, bounds[2] - 0.5,
                           bounds[3] - 0.5, bounds[4] - 0.5, bounds[5] - 0.5)
                : new AABB(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5);
        return cachedNaturalAabb;
    }

    /**
     * Iterate every quad of the cached baked model — the six face-culled directions
     * plus the direction-less "general" bucket — re-seeding the shared RNG before
     * each {@code getQuads} call so quad randomization stays deterministic.
     */
    private void forEachQuad(Consumer<BakedQuad> consumer) {
        BakedModel model = ensureModel();
        for (Direction dir : Direction.values()) {
            rng.setSeed(state.getSeed(BlockPos.ZERO));
            for (BakedQuad q : model.getQuads(state, dir, rng, ModelData.EMPTY, null)) consumer.accept(q);
        }
        rng.setSeed(state.getSeed(BlockPos.ZERO));
        for (BakedQuad q : model.getQuads(state, null, rng, ModelData.EMPTY, null)) consumer.accept(q);
    }

    private BakedModel ensureModel() {
        if (cachedModel == null) {
            BlockRenderDispatcher dispatch = Minecraft.getInstance().getBlockRenderer();
            cachedModel = dispatch.getBlockModel(state);
        }
        return cachedModel;
    }

    @Override
    protected void drawSelf(PoseStack pose, MultiBufferSource buffers, int packedLight,
                            int packedOverlay, float partialTick) {
        // Centre the block model around its own origin so transforms feel natural.
        pose.pushPose();
        pose.translate(-0.5, -0.5, -0.5);
        var entry = pose.last();
        var renderType = ItemBlockRenderTypes.getRenderType(state, false);
        forEachQuad(quad -> buffers.getBuffer(renderType)
                .putBulkData(entry, quad, 1f, 1f, 1f, packedLight, packedOverlay));
        pose.popPose();
    }
}
