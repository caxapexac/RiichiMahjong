package com.themahjong.yaku;

import java.util.List;

/**
 * Outcome of a single win event: yaku, scoring components, and per-seat point changes.
 *
 * @param yaku        non-yakuman yaku awarded (empty for yakuman hands)
 * @param yakuman     yakuman awarded (empty for normal hands)
 * @param han         total han (yaku + dora; for yakuman, yakumanCount × 13)
 * @param fu          fu value (25 for chitoitsu, 30 for kokushimusou, standard otherwise)
 * @param doraCount   total dora tiles counted (regular + red + ura)
 * @param pointDeltas per-seat point change; winner's entry is positive, payers' are negative
 */
public record WinResult(
        List<NonYakuman> yaku,
        List<Yakuman> yakuman,
        int han,
        int fu,
        int doraCount,
        List<Integer> pointDeltas
) {}
