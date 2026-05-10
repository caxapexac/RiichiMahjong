package com.riichimahjong.mahjongcore;

import dev.architectury.registry.fuel.FuelRegistry;
import net.minecraft.world.item.Item;

/**
 * Registers sou tiles as furnace fuel. Burn time scales linearly with rank —
 * 1-sou = 1 coal worth (1600 ticks), 9-sou = 9 coal. Aka-5-sou matches sou-5.
 *
 * <p>Architectury's {@link FuelRegistry} fans out to NeoForge's
 * {@code IFuel}-style hook and Fabric's {@code FuelRegistry}, so calling once
 * from common init covers both loaders.
 *
 * <p>Uses {@code RegistrySupplier.listen(...)} to defer the actual
 * {@code FuelRegistry.register} call until the tile items are populated —
 * NeoForge populates the item registry after the {@code @Mod} constructor, so
 * resolving via {@code .get()} during {@code init()} would NPE.
 */
public final class MahjongTileFuels {
    private MahjongTileFuels() {}

    private static final int COAL_BURN_TICKS = 1600;

    /** Call once from common init, after the tile items have been
     *  {@link MahjongTileItems#register registered} (suppliers exist). */
    public static void register() {
        for (int rank = 1; rank <= 9; rank++) {
            int code = 18 + rank - 1; // SOUZU base = 18 in MahjongTileItems.codeForSuitRank
            int burnTime = rank * COAL_BURN_TICKS;
            MahjongTileItems.itemSupplierForCode(code)
                    .ifPresent(s -> s.listen(item -> FuelRegistry.register(burnTime, item)));
        }
        int akaBurnTime = 5 * COAL_BURN_TICKS;
        MahjongTileItems.itemSupplierForCode(MahjongTileItems.CODE_SOU_5_AKA)
                .ifPresent(s -> s.listen(item -> FuelRegistry.register(akaBurnTime, item)));
    }
}
