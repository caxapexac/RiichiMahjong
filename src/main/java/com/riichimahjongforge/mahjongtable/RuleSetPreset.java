package com.riichimahjongforge.mahjongtable;

import com.themahjong.TheMahjongMatch;

import java.util.function.Supplier;

/**
 * A named ruleset / player-count combination the table can be configured to play.
 * Each preset bundles:
 * <ul>
 *   <li>The library factory that produces the {@link TheMahjongMatch}.</li>
 *   <li>The player count, which dictates the canonical seat layout
 *       (seats {@code 0..playerCount-1} open, rest closed).</li>
 *   <li>A translation key for UI display.</li>
 * </ul>
 *
 * <p>The default preset is {@link #MAHJONG_SOUL_4P}; the table also has a soft
 * fallback from MS-4P to {@link #MAHJONG_SOUL_SANMA_3P} when the player closes the
 * North seat (handled in the BE), so a freshly placed table can be played as either
 * 4-player or sanma without opening settings.
 *
 * <p><b>TODO — library refactor opportunity.</b> This enum currently lives mod-side
 * and pulls together three things that ought to be one in the library:
 * <ul>
 *   <li>The 5 hardcoded factories on {@link TheMahjongMatch} ({@code defaults},
 *       {@code defaultTenhou}, {@code defaultMahjongSoul}, {@code defaultTenhouSanma},
 *       {@code defaultMahjongSoulSanma}). Each spells out starting points, target
 *       points, tile set, and rule flags inline.</li>
 *   <li>The {@code (key, playerCount)} bundle here, duplicating the player count
 *       that's already inside the produced {@link TheMahjongMatch}.</li>
 *   <li>The implicit "this is a canonical, named configuration" concept that has no
 *       first-class home today.</li>
 * </ul>
 *
 * TODO:
 *   <p>The cleaner shape is to move starting points, target points, tile set, and
 *   player count <i>into</i> {@link com.themahjong.TheMahjongRuleSet} so a ruleset is
 *   the complete match configuration, then expose a {@code TheMahjongRuleSet.Preset}
 *   (or {@code TheMahjongMatch.Preset}) enum in the library that lists the named
 *   canonical rulesets. The 5 standalone factories collapse to a single
 *   {@code TheMahjongMatch.startFromPreset(Preset, Random)}. This mod-side enum then
 *   deletes; the BE stores {@code TheMahjongMatch.Preset} directly. Lang keys derive
 *   from {@code preset.name().toLowerCase()} so no string field is needed.
 *
 * <p>Defer because: the library refactor touches every test that constructs a match
 * via the existing factories (~40+ files) and forces a decision on backward-compat
 * for the 5 existing public factory methods. Worth doing in a dedicated pass.
 */
public enum RuleSetPreset {
    MAHJONG_SOUL_4P("mahjong_soul_4p", 4, TheMahjongMatch::defaultMahjongSoul),
    TENHOU_4P("tenhou_4p", 4, TheMahjongMatch::defaultTenhou),
    WRC_4P("wrc_4p", 4, TheMahjongMatch::defaults),
    MAHJONG_SOUL_SANMA_3P("mahjong_soul_sanma_3p", 3, TheMahjongMatch::defaultMahjongSoulSanma),
    TENHOU_SANMA_3P("tenhou_sanma_3p", 3, TheMahjongMatch::defaultTenhouSanma);

    private final String key;
    private final int playerCount;
    private final Supplier<TheMahjongMatch> factory;

    RuleSetPreset(String key, int playerCount, Supplier<TheMahjongMatch> factory) {
        this.key = key;
        this.playerCount = playerCount;
        this.factory = factory;
    }

    public int playerCount() { return playerCount; }

    public TheMahjongMatch newMatch() { return factory.get(); }

    public String langKey() { return "riichi_mahjong_forge.preset." + key; }
}
