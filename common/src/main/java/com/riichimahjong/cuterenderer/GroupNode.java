package com.riichimahjong.cuterenderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/** Pure transform container — draws nothing of its own. */
public final class GroupNode extends CuteNode {
    @Override
    protected void drawSelf(PoseStack pose, MultiBufferSource buffers, int packedLight,
                            int packedOverlay, float partialTick) {
        // no-op
    }
}
