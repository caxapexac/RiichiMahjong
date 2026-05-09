package com.riichimahjong.mahjongcore;

import com.riichimahjong.RiichiMahjong;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** Server-side yaku advancement awarding from canonical yaku keys. */
public final class MahjongYakuAdvancements {

    private static final String CRITERION = "earned";
    private static final ResourceLocation ROOT_ID =
            ResourceLocation.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "yaku/root");
    private static final Set<String> KNOWN_YAKU_KEYS = Set.of(
            "yakuman",
            "riichi",
            "double_riichi",
            "ippatsu",
            "menzen_tsumo",
            "tanyao",
            "pinfu",
            "iipeikou",
            "ryanpeikou",
            "yakuhai_haku",
            "yakuhai_hatsu",
            "yakuhai_chun",
            "yakuhai_jikaze",
            "yakuhai_bakaze",
            "chanta",
            "junchan",
            "ikkitsuukan",
            "sanshoku_doujun",
            "sanshoku_doukou",
            "toitoi",
            "sanankou",
            "sankantsu",
            "shousangen",
            "honroutou",
            "chiitoitsu",
            "honitsu",
            "chinitsu",
            "dora",
            "uradora",
            "akadora",
            "haitei",
            "houtei",
            "rinshan_kaihou",
            "chankan",
            "nagashi_mangan",
            "kokushi_musou",
            "kokushi_musou_juusanmen_machi",
            "suu_ankou",
            "suu_ankou_tanki",
            "daisangen",
            "shousuushii",
            "daisuushii",
            "tsuuiisou",
            "chinroutou",
            "ryuuiisou",
            "chuuren_poutou",
            "junsei_chuuren_poutou",
            "suukantsu",
            "tenhou",
            "chiihou",
            "renhou",
            "kazoe_yakuman");

    private MahjongYakuAdvancements() {}

    public static void awardForWin(
            @Nullable ServerPlayer winner,
            int han,
            boolean yakuman,
            @Nullable List<String> rawYakuNames) {
        if (winner == null) {
            return;
        }
        LinkedHashSet<String> canonicalKeys = new LinkedHashSet<>();
        if (rawYakuNames != null) {
            for (String rawYakuName : rawYakuNames) {
                String key = MahjongWinEffects.canonicalYakuKey(rawYakuName);
                if (!key.isEmpty()) {
                    canonicalKeys.add(key);
                }
            }
        }
        if (yakuman) {
            canonicalKeys.add("yakuman");
        } else if (han >= 13) {
            canonicalKeys.add("kazoe_yakuman");
        }
        canonicalKeys.removeIf(key -> !KNOWN_YAKU_KEYS.contains(key));
        if (canonicalKeys.isEmpty()) {
            return;
        }

        grant(winner, ROOT_ID);
        for (String key : canonicalKeys) {
            grant(winner, ResourceLocation.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "yaku/" + key));
        }
    }

    private static void grant(ServerPlayer player, ResourceLocation id) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        // 1.21: ServerAdvancementManager.getAdvancement(ResourceLocation) → get()
        // returning AdvancementHolder. PlayerAdvancements methods now take the holder.
        AdvancementHolder advancement = server.getAdvancements().get(id);
        if (advancement == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) {
            return;
        }
        player.getAdvancements().award(advancement, CRITERION);
    }
}
