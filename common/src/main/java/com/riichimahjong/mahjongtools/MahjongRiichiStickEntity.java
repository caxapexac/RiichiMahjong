package com.riichimahjong.mahjongtools;

import com.riichimahjong.registry.ModEntities;
import com.riichimahjong.registry.ModItems;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class MahjongRiichiStickEntity extends ThrowableProjectile implements ItemSupplier {

    private static final float KNOCKBACK_RADIUS = 3.0f;
    private static final double KNOCKBACK_STRENGTH = 1.2;

    public MahjongRiichiStickEntity(EntityType<? extends MahjongRiichiStickEntity> type, Level level) {
        super(type, level);
    }

    public MahjongRiichiStickEntity(Level level, LivingEntity thrower) {
        super(ModEntities.MAHJONG_RIICHI_STICK_ENTITY.get(), thrower, level);
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(ModItems.MAHJONG_RIICHI_STICK.get());
    }

    // 1.21: defineSynchedData(SynchedEntityData.Builder) — was no-arg in 1.20.1.
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    // 1.21: Entity.getGravity() is final (applies fluid modifiers). Override
    // getDefaultGravity() instead — returns the natural fall acceleration.
    @Override
    protected double getDefaultGravity() {
        return 0.15;
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            applyKnockback();
            dropItem();
            this.discard();
        }
    }

    private void applyKnockback() {
        Vec3 center = this.position();
        AABB area = new AABB(
                center.x - KNOCKBACK_RADIUS, center.y - KNOCKBACK_RADIUS, center.z - KNOCKBACK_RADIUS,
                center.x + KNOCKBACK_RADIUS, center.y + KNOCKBACK_RADIUS, center.z + KNOCKBACK_RADIUS);
        List<LivingEntity> nearby = this.level().getEntitiesOfClass(LivingEntity.class, area);
        for (LivingEntity entity : nearby) {
            Vec3 diff = entity.position().subtract(center);
            double dist = diff.length();
            if (dist < 0.01) continue;
            double factor = (1.0 - dist / KNOCKBACK_RADIUS) * KNOCKBACK_STRENGTH;
            if (factor <= 0) continue;
            Vec3 push = diff.normalize().scale(factor);
            entity.setDeltaMovement(entity.getDeltaMovement().add(push.x, push.y + 0.2, push.z));
            entity.hurtMarked = true;
        }
    }

    private void dropItem() {
        Vec3 pos = this.position();
        ItemEntity itemEntity = new ItemEntity(
                this.level(),
                pos.x, pos.y, pos.z,
                new ItemStack(ModItems.MAHJONG_RIICHI_STICK.get()));
        itemEntity.setDefaultPickUpDelay();
        this.level().addFreshEntity(itemEntity);
    }
}
