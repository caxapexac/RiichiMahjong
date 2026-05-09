package com.riichimahjong.fabric.mixin;

import com.riichimahjong.fovnormalizer.client.NormalizeFovBlend;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric-side hook for {@link GameRenderer#getFov}. Architectury and Fabric API have
 * no built-in FOV-modify event; this mixin lets common's {@link NormalizeFovBlend}
 * adjust the per-frame FOV the same way NeoForge does via {@code ViewportEvent.ComputeFov}.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererFovMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void riichi_mahjong$onGetFov(
            Camera camera,
            float partialTick,
            boolean useFOVSetting,
            CallbackInfoReturnable<Double> cir) {
        if (camera.getEntity() instanceof LivingEntity living) {
            double original = cir.getReturnValueD();
            double blended = NormalizeFovBlend.tickAndCompute(living, original);
            if (blended != original) cir.setReturnValue(blended);
        }
    }
}
