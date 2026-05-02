package com.riichimahjongforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.riichimahjongforge.MahjongAltarBlockEntity;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class MahjongAltarBlockEntityRenderer implements BlockEntityRenderer<MahjongAltarBlockEntity> {
    public MahjongAltarBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            MahjongAltarBlockEntity altar,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay) {
        List<ItemStack> stacks = altar.renderStacks();
        if (stacks.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = altar.getLevel() != null ? altar.getLevel().getGameTime() : 0L;
        float time = gameTime + partialTick;
        int count = stacks.size();
        for (int i = 0; i < count; i++) {
            ItemStack stack = stacks.get(i);
            poseStack.pushPose();
            float angle = count == 1 ? 0.0f : ((float) (Math.PI * 2.0) * i / (float) count);
            float radius = count == 1 ? 0.0f : 0.22f;
            double x = 0.5 + (Math.cos(angle) * radius);
            double z = 0.5 + (Math.sin(angle) * radius);
            double bob = Math.sin((time + (i * 13.0f)) / 8.0f) * 0.03;
            poseStack.translate(x, 1.03 + bob, z);
            float scale = stack.getItem() instanceof BlockItem ? 0.48f : 0.38f;
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(Axis.YP.rotationDegrees((time * 3.0f) + (i * 45.0f)));
            minecraft.getItemRenderer()
                    .renderStatic(
                            stack,
                            ItemDisplayContext.GROUND,
                            packedLight,
                            packedOverlay,
                            poseStack,
                            buffer,
                            altar.getLevel(),
                            i);
            poseStack.popPose();
        }
    }
}
