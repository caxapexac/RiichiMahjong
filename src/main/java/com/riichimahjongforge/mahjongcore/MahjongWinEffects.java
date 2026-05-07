package com.riichimahjongforge.mahjongcore;

import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

/** Shared win celebration effects for yaku generator and table gameplay wins. */
public final class MahjongWinEffects {

    private MahjongWinEffects() {}

    public static void playWinEffects(
            ServerLevel serverLevel,
            @Nullable ServerPlayer winner,
            String winnerName,
            int han,
            boolean yakuman,
            List<String> yakuNames,
            List<String> yakumanNames) {
        MahjongYakuAdvancements.awardForWin(winner, han, yakuman, yakuNames);
        if (yakuman) {
            MutableComponent namesComponent = Component.empty();
            if (yakumanNames == null || yakumanNames.isEmpty()) {
                namesComponent.append(Component.translatable("riichi_mahjong_forge.yaku.yakuman"));
            } else {
                for (int i = 0; i < yakumanNames.size(); i++) {
                    if (i > 0) {
                        namesComponent.append(Component.literal(", "));
                    }
                    namesComponent.append(localizedYakuName(yakumanNames.get(i)));
                }
            }
            Component msg =
                    Component.translatable(
                            "riichi_mahjong_forge.chat.game.yakuman_win",
                            Component.literal(winnerName),
                            namesComponent);
            serverLevel.getServer().getPlayerList().broadcastSystemMessage(msg, false);
            for (ServerPlayer p : serverLevel.getServer().getPlayerList().getPlayers()) {
                p.playNotifySound(SoundEvents.ENDER_DRAGON_DEATH, SoundSource.MASTER, 1.0f, 1.0f);
            }
            return;
        }
        if (winner == null || han <= 0) {
            return;
        }
        if (han <= 3) {
            playLocalSound(winner, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.45f, 1.18f);
            return;
        }
        if (han <= 5) {
            playLocalSound(winner, SoundEvents.PLAYER_LEVELUP, 0.80f, 1.00f);
            return;
        }
        if (han <= 7) {
            // "Level 30" feel: same level-up cue, deeper pitch and stronger presence.
            playLocalSound(winner, SoundEvents.PLAYER_LEVELUP, 1.00f, 0.62f);
            return;
        }
        if (han <= 10) {
            playLocalSound(winner, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.95f, 0.96f);
            return;
        }
        playLocalSound(winner, SoundEvents.WITHER_SPAWN, 0.80f, 1.16f);
    }

    public static String humanizeYakuName(Object yakuName) {
        if (yakuName == null) {
            return "Unknown";
        }
        String raw = yakuName.toString().trim();
        if (raw.isEmpty()) {
            return "Unknown";
        }
        String[] words = raw.replace('_', ' ').toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                sb.append(word.substring(1));
            }
        }
        return sb.isEmpty() ? "Unknown" : sb.toString();
    }

    private static Component localizedYakuName(String rawYakumanName) {
        String normalized = normalizeYakuKey(rawYakumanName);
        if (normalized == null || normalized.isEmpty()) {
            return Component.literal(humanizeYakuName(rawYakumanName));
        }
        String canonical = canonicalYakuKey(normalized);
        return Component.translatableWithFallback(
                "riichi_mahjong_forge.yaku." + canonical,
                humanizeYakuName(rawYakumanName));
    }

    public static String canonicalYakuKey(Object rawYakuName) {
        if (rawYakuName == null) {
            return "";
        }
        return canonicalYakuKey(normalizeYakuKey(rawYakuName.toString()));
    }

    private static String normalizeYakuKey(String rawYakuName) {
        if (rawYakuName == null) {
            return "";
        }
        String normalized = rawYakuName.trim();
        normalized = normalized.replaceAll("([a-z0-9])([A-Z])", "$1_$2");
        normalized = normalized.toLowerCase(Locale.ROOT);
        normalized = normalized.replace('-', '_').replace(' ', '_');
        while (normalized.contains("__")) {
            normalized = normalized.replace("__", "_");
        }
        return normalized;
    }

    private static String canonicalYakuKey(String key) {
        return switch (key) {
            // Core enum names.
            case "tsumo" -> "menzen_tsumo";
            case "haku" -> "yakuhai_haku";
            case "hatsu" -> "yakuhai_hatsu";
            case "chun" -> "yakuhai_chun";
            case "jikaze" -> "yakuhai_jikaze";
            case "bakaze" -> "yakuhai_bakaze";
            case "rinshankaihou" -> "rinshan_kaihou";
            case "ikkitsukan" -> "ikkitsuukan";
            case "honroutou" -> "honroutou";
            case "sanshokudohjun" -> "sanshoku_doujun";
            case "toitoihou" -> "toitoi";
            case "sanshokudouhkou" -> "sanshoku_doukou";
            case "sananko" -> "sanankou";
            case "kokushimusou" -> "kokushi_musou";
            case "suuankou" -> "suu_ankou";
            case "chuurenpoutou" -> "chuuren_poutou";
            case "tsuisou" -> "tsuuiisou";
            case "shousuushi" -> "shousuushii";
            case "daisuushi" -> "daisuushii";
            // Common alias handling from english-like names to preferred romaji-style keys.
            case "all_simples" -> "tanyao";
            case "ready_hand" -> "riichi";
            case "double_ready" -> "double_riichi";
            case "fully_concealed_hand" -> "menzen_tsumo";
            case "seven_pairs" -> "chiitoitsu";
            case "all_triplets" -> "toitoi";
            case "three_concealed_triplets" -> "sanankou";
            case "three_kans" -> "sankantsu";
            case "little_three_dragons" -> "shousangen";
            case "half_flush" -> "honitsu";
            case "full_flush" -> "chinitsu";
            case "mixed_triple_sequence" -> "sanshoku_doujun";
            case "mixed_triple_triplets" -> "sanshoku_doukou";
            case "pure_straight" -> "ikkitsuukan";
            case "terminal_or_honor_in_each_set" -> "chanta";
            case "terminal_in_each_set" -> "junchan";
            case "all_terminals_and_honors" -> "honroutou";
            case "thirteen_orphans" -> "kokushi_musou";
            case "thirteen_orphans_13_wait" -> "kokushi_musou_juusanmen_machi";
            case "four_concealed_triplets" -> "suu_ankou";
            case "four_concealed_triplets_single_wait" -> "suu_ankou_tanki";
            case "big_three_dragons" -> "daisangen";
            case "little_four_winds" -> "shousuushii";
            case "big_four_winds" -> "daisuushii";
            case "all_honors" -> "tsuuiisou";
            case "all_terminals" -> "chinroutou";
            case "all_green" -> "ryuuiisou";
            case "nine_gates" -> "chuuren_poutou";
            case "pure_nine_gates" -> "junsei_chuuren_poutou";
            case "four_kans" -> "suukantsu";
            case "heavenly_hand" -> "tenhou";
            case "earthly_hand" -> "chiihou";
            case "humanly_hand" -> "renhou";
            default -> key;
        };
    }

    private static void playLocalSound(ServerPlayer winner, SoundEvent sound, float volume, float pitch) {
        winner.playNotifySound(sound, SoundSource.PLAYERS, volume, pitch);
    }
}
