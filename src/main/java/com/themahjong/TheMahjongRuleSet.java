package com.themahjong;

/**
 * All rule variants that differ between rulesets (WRC 2025 vs Tenhou, etc.).
 *
 * Scoring fields are consumed by FuCalculator and WinCalculator.
 * Game-flow fields are consumed by match/round logic (busting, abortive draws, etc.)
 * and are declared here even before that logic is implemented, so factories stay accurate.
 *
 * WRC source: WRC_Rules_2025.md. Tenhou source: tenhou_rules.md.
 * Differences are noted inline.
 */
public record TheMahjongRuleSet(

        // -------------------------------------------------------------------------
        // Scoring (per-hand calculation)
        // -------------------------------------------------------------------------

        /** 4 han 30 fu and 3 han 60 fu score as mangan. WRC: true. Tenhou: false. */
        boolean kiriageMangan,

        /** Double-wind pair (seat == round wind) = 4 fu; false = 2 fu. WRC: false. Tenhou: true. */
        boolean doubleWindPairFu4,

        /** Renhou (winning on first discard before drawing) is a valid yaku (5 han, mangan cap).
         *  WRC: true. Tenhou: false ("人和なし"). */
        boolean renhouAllowed,

        /** Pao (liability payment) applies to Suukantsu in addition to Daisangen/Daisuushi.
         *  WRC: true. Tenhou: false. */
        boolean paoOnSuukantsu,

        // -------------------------------------------------------------------------
        // Game flow (match / round logic)
        // -------------------------------------------------------------------------

        /** Abortive draws (nine terminals, four riichi, three ron, four kans, four same wind) are allowed.
         *  All count as renchan. WRC: false. Tenhou: true. */
        boolean abortiveDrawsAllowed,

        /** Nagashi mangan is allowed (all terminals/honours discarded, none called).
         *  Pays as mangan; dealer tenpai counts as renchan. WRC: false. Tenhou: true. */
        boolean nagashiManganAllowed,

        /** Double ron (two players win off the same discard) is allowed.
         *  Deposits go to closest player by turn order. WRC: false (single winner). Tenhou: true. */
        boolean doubleRonAllowed,

        /** A player reaching negative points immediately ends the game (busting / 飛び).
         *  WRC: false (play continues, referee lends sticks). Tenhou: true. */
        boolean bustingEndsGame,

        /** Riichi requires the player to have at least 1000 points before depositing.
         *  WRC: false (can go negative). Tenhou: true. */
        boolean riichiRequires1000Points,

        /** Unclaimed riichi deposits at game end go to 1st place.
         *  WRC: false (stay on table, not added to scores). Tenhou: true. */
        boolean depositsToFirstAtEnd,

        /** Dealer may voluntarily stop the game in all-last when winning (agari-yame)
         *  or when tenpai and leading (tenpai-yame). WRC: false. Tenhou: true. */
        boolean agariYameAllowed,

        /** Game can extend into extra wind rounds (West, North) via sudden-death if no player
         *  has reached target points by end of South round. WRC: false. Tenhou: true. */
        boolean suddenDeathRound,

        /** Kan dora for minkan and kakan is revealed after the replacement discard,
         *  not immediately on the kan declaration. Ankan is always immediate.
         *  WRC: false (all immediate). Tenhou: true. */
        boolean openKanDoraDelayedReveal,

        /** "Swap-call" kuikae is forbidden: after chi, the discarder cannot immediately
         *  discard the same tile they claimed (genbutsu) nor the tile that would have
         *  formed an alternate sequence with the same two hand tiles (suji). After pon,
         *  the discarder cannot discard the same kind. WRC, Tenhou, Mahjong Soul: all true. */
        boolean kuikaeForbidden,

        /** A riichi player declaring ankan must (a) use the just-drawn tile and (b) leave
         *  the wait set unchanged. WRC, Tenhou, Mahjong Soul: all true. */
        boolean strictRiichiAnkan,

        // -------------------------------------------------------------------------
        // 3-player (sanma) specific
        // -------------------------------------------------------------------------

        /** Chi calls are forbidden. WRC/Tenhou/MahjongSoul 4P: false. Sanma rulesets: true. */
        boolean chiDisabled,

        /** Each declared kita (north-removal) counts as +1 dora for the declarer.
         *  4P rulesets: false. Sanma rulesets: true. */
        boolean kitaCountsAsDora,

        /** On 3-player tsumo, the winner forgoes the share that the absent NORTH seat
         *  would have paid (Mahjong Soul ranked default). When false, the absent share is
         *  redistributed evenly to the remaining payers — "north-bisection".
         *  Has no effect in 4-player games. Mahjong Soul ranked sanma: true. */
        boolean tsumoLoss,

        /** Declining ron on a kita declaration adds the kita tile to the player's
         *  same-turn furiten (and sets riichi-permanent furiten if in riichi), matching
         *  the general "passing a ron causes furiten" rule. Mahjong Soul carves out an
         *  exception: kita-decline doesn't cause furiten ("North replacing does not cause
         *  furiten if waiting for North"). Tenhou sanma: true. Mahjong Soul sanma: false.
         *  Irrelevant in 4-player games. */
        boolean kitaDeclineCausesFuriten,

        /** Three players ronning the same discard (sanchahou) triggers an abortive draw.
         *  When false, all three rons are paid normally (driver collects via
         *  {@code beginRon + addRon + addRon + resolveRons}). When true, the driver
         *  should call {@link com.themahjong.TheMahjongRound#abortiveDraw} from
         *  CLAIM_WINDOW instead. Tenhou: true. Mahjong Soul: false. Irrelevant when
         *  {@code !doubleRonAllowed} (no multi-ron at all). */
        boolean sanchahouAbortive

) {

    /** WRC 2025 ruleset. */
    public static TheMahjongRuleSet wrc() {
        return new TheMahjongRuleSet(
                true,   // kiriageMangan
                false,  // doubleWindPairFu4 (2 fu)
                true,   // renhouAllowed (5 han mangan)
                true,   // paoOnSuukantsu
                false,  // abortiveDrawsAllowed
                false,  // nagashiManganAllowed
                false,  // doubleRonAllowed
                false,  // bustingEndsGame
                false,  // riichiRequires1000Points
                false,  // depositsToFirstAtEnd
                false,  // agariYameAllowed
                false,  // suddenDeathRound
                false,  // openKanDoraDelayedReveal
                true,   // kuikaeForbidden
                true,   // strictRiichiAnkan
                false,  // chiDisabled (4P)
                false,  // kitaCountsAsDora (4P)
                false,  // tsumoLoss (4P)
                true,   // kitaDeclineCausesFuriten (irrelevant in 4P)
                true    // sanchahouAbortive (irrelevant: doubleRonAllowed=false)
        );
    }

    /** Tenhou 4-player ruleset. */
    public static TheMahjongRuleSet tenhou() {
        return new TheMahjongRuleSet(
                false,  // kiriageMangan
                true,   // doubleWindPairFu4 (4 fu)
                false,  // renhouAllowed
                false,  // paoOnSuukantsu
                true,   // abortiveDrawsAllowed
                true,   // nagashiManganAllowed
                true,   // doubleRonAllowed
                true,   // bustingEndsGame
                true,   // riichiRequires1000Points
                true,   // depositsToFirstAtEnd
                true,   // agariYameAllowed
                true,   // suddenDeathRound
                true,   // openKanDoraDelayedReveal
                true,   // kuikaeForbidden
                true,   // strictRiichiAnkan
                false,  // chiDisabled (4P)
                false,  // kitaCountsAsDora (4P)
                false,  // tsumoLoss (4P)
                true,   // kitaDeclineCausesFuriten (irrelevant in 4P)
                true    // sanchahouAbortive
        );
    }

    /** Mahjong Soul 4-player ranked ruleset.
     *
     * For every flag modelled here this matches {@link #tenhou()}. Differences from Tenhou
     * that exist in the published rules but are not (yet) gated by a {@code TheMahjongRuleSet} flag:
     *   - Sanchahou (triple ron) is not an abortive draw — driver should resolve it via
     *     atamahane (closest winner to discarder) rather than calling {@code abortiveDraw()}.
     *   - Kuikae (swap calling) forbidden — claim-validation concern, not modelled.
     *   - Renhou is a "local yaku" disabled in ranked play (matches {@code renhouAllowed=false}).
     */
    public static TheMahjongRuleSet mahjongSoul() {
        return new TheMahjongRuleSet(
                false,  // kiriageMangan
                true,   // doubleWindPairFu4 (4 fu)
                false,  // renhouAllowed (local yaku, off in ranked)
                false,  // paoOnSuukantsu (only daisangen / daisuushi)
                true,   // abortiveDrawsAllowed (suufuu renda, suukan nagare, kyuushu, suucha riichi)
                true,   // nagashiManganAllowed
                true,   // doubleRonAllowed
                true,   // bustingEndsGame (negative points only)
                true,   // riichiRequires1000Points
                true,   // depositsToFirstAtEnd
                true,   // agariYameAllowed
                true,   // suddenDeathRound (enchousen)
                true,   // openKanDoraDelayedReveal (closed kan immediate, opened after discard)
                true,   // kuikaeForbidden
                true,   // strictRiichiAnkan
                false,  // chiDisabled (4P)
                false,  // kitaCountsAsDora (4P)
                false,  // tsumoLoss (4P)
                true,   // kitaDeclineCausesFuriten (irrelevant in 4P)
                false   // sanchahouAbortive (MS allows triple ron)
        );
    }

    /** Tenhou 3-player (sanma) ruleset.
     *  Rinshan = 8, M2-M8 removed, no chi, kita counts as dora, tsumo-loss applied. */
    public static TheMahjongRuleSet tenhouSanma() {
        return new TheMahjongRuleSet(
                false,  // kiriageMangan
                true,   // doubleWindPairFu4
                false,  // renhouAllowed
                false,  // paoOnSuukantsu
                true,   // abortiveDrawsAllowed (kyuushu, suukan-sanra; suufon-renda/suucha-riichi vacuously false)
                true,   // nagashiManganAllowed
                true,   // doubleRonAllowed
                true,   // bustingEndsGame
                true,   // riichiRequires1000Points
                true,   // depositsToFirstAtEnd
                true,   // agariYameAllowed
                true,   // suddenDeathRound
                true,   // openKanDoraDelayedReveal
                true,   // kuikaeForbidden
                true,   // strictRiichiAnkan
                true,   // chiDisabled
                true,   // kitaCountsAsDora
                true,   // tsumoLoss
                true,   // kitaDeclineCausesFuriten
                true    // sanchahouAbortive
        );
    }

    /** Mahjong Soul 3-player (sanma) ranked ruleset.
     *  Matches Tenhou sanma except: triple ron is paid out (not abortive), and declining
     *  ron on a kita doesn't cause furiten. Uma values (+15/0/-15 vs +20/0/-20) are not
     *  yet flagged. */
    public static TheMahjongRuleSet mahjongSoulSanma() {
        return new TheMahjongRuleSet(
                false,  // kiriageMangan
                true,   // doubleWindPairFu4
                false,  // renhouAllowed
                false,  // paoOnSuukantsu
                true,   // abortiveDrawsAllowed (kyuushu, suukan-sanra; suufon-renda/suucha-riichi vacuously false)
                true,   // nagashiManganAllowed
                true,   // doubleRonAllowed
                true,   // bustingEndsGame
                true,   // riichiRequires1000Points
                true,   // depositsToFirstAtEnd
                true,   // agariYameAllowed
                true,   // suddenDeathRound
                true,   // openKanDoraDelayedReveal
                true,   // kuikaeForbidden
                true,   // strictRiichiAnkan
                true,   // chiDisabled
                true,   // kitaCountsAsDora
                true,   // tsumoLoss
                false,  // kitaDeclineCausesFuriten (MS exception: kita-decline doesn't furiten)
                false   // sanchahouAbortive (MS pays out triple ron)
        );
    }
}
