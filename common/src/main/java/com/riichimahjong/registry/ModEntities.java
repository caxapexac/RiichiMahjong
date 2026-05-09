package com.riichimahjong.registry;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.mahjongtools.MahjongRiichiStickEntity;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(RiichiMahjong.MOD_ID, Registries.ENTITY_TYPE);

    public static final RegistrySupplier<EntityType<MahjongRiichiStickEntity>> MAHJONG_RIICHI_STICK_ENTITY =
            ENTITY_TYPES.register(
                    "mahjong_riichi_stick",
                    () -> EntityType.Builder.<MahjongRiichiStickEntity>of(MahjongRiichiStickEntity::new, MobCategory.MISC)
                            .sized(0.25f, 0.25f)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("mahjong_riichi_stick"));

    private ModEntities() {}
}
