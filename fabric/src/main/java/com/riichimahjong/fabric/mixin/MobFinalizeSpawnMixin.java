package com.riichimahjong.fabric.mixin;

import com.riichimahjong.gimmicks.EndermanCarriesMahjongTile;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric-side hook for {@link Mob#finalizeSpawn}. Fabric API has no
 * finalize-spawn callback (only the chunk-load-aware {@code ServerEntityEvents.ENTITY_LOAD}),
 * so a mixin is the only clean way to match Forge's {@code FinalizeSpawnEvent}
 * semantics — fires once per genuine spawn, never on chunk-load.
 *
 * <p>The actual logic lives in common {@link EndermanCarriesMahjongTile}.
 */
@Mixin(Mob.class)
public abstract class MobFinalizeSpawnMixin {
    @Inject(method = "finalizeSpawn", at = @At("RETURN"))
    private void riichi_mahjong$onFinalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType spawnType,
            @Nullable SpawnGroupData spawnData,
            CallbackInfoReturnable<SpawnGroupData> cir) {
        EndermanCarriesMahjongTile.onFinalizeSpawn((Mob) (Object) this);
    }
}
