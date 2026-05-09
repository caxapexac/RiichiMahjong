package com.riichimahjong.registry;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.mahjongtable.MahjongTableMenu;
import com.riichimahjong.mahjongtable.MahjongTableSettingsMenu;
import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(RiichiMahjong.MOD_ID, Registries.MENU);

    public static final RegistrySupplier<MenuType<MahjongTableMenu>> MAHJONG_TABLE_MENU =
            MENU_TYPES.register(
                    "mahjong_table_new",
                    () -> MenuRegistry.ofExtended(MahjongTableMenu::fromNetwork));

    public static final RegistrySupplier<MenuType<MahjongTableSettingsMenu>> MAHJONG_TABLE_SETTINGS_MENU =
            MENU_TYPES.register(
                    "mahjong_table_new_settings",
                    () -> MenuRegistry.ofExtended(MahjongTableSettingsMenu::fromNetwork));

    public static final RegistrySupplier<MenuType<com.riichimahjong.yakugenerator.YakuGeneratorMenu>> YAKU_GENERATOR_MENU =
            MENU_TYPES.register(
                    "yaku_generator",
                    () -> MenuRegistry.ofExtended((id, inv, buf) ->
                            new com.riichimahjong.yakugenerator.YakuGeneratorMenu(id, inv, buf.readBlockPos())));

    private ModMenus() {}
}
