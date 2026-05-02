package com.mahjongcore.rules;

import java.util.List;

public final class ClaimIntentRules {

    private ClaimIntentRules() {}

    public enum ValidationCode {
        OK,
        NOT_IN_CLAIM_WINDOW,
        NOT_ELIGIBLE_SEAT,
        INTENT_NOT_ALLOWED_IN_WINDOW,
        RIICHI_RESTRICTED_CALL,
        RON_NOT_LEGAL,
        PON_NOT_LEGAL,
        DAIMIN_KAN_NOT_LEGAL,
        CHI_NOT_KAMICHA,
        CHI_PAIR_REQUIRED,
        CHI_PAIR_NOT_LEGAL,
        CHANKAN_NOT_LEGAL
    }

    public record ClaimIntentValidation(
            ValidationCode code, boolean accepted, int resolvedChiTileA, int resolvedChiTileB) {}

    public static boolean isSeatEligibleForClaim(List<Integer> playOrder, int discarderSeat, int seat) {
        return seat != discarderSeat && playOrder.contains(seat);
    }

    /** True if {@code discarderSeat} is the immediate previous seat (kamicha) of {@code claimantSeat}. */
    public static boolean isKamicha(List<Integer> playOrder, int discarderSeat, int claimantSeat) {
        int n = playOrder.size();
        int ci = playOrder.indexOf(claimantSeat);
        if (ci < 0) {
            return false;
        }
        int prev = playOrder.get((ci - 1 + n) % n);
        return prev == discarderSeat;
    }

    public static boolean hasAnyLegalClaim(
            boolean chankanWindow,
            boolean legalRon,
            boolean inRiichi,
            boolean legalDaiminKan,
            boolean legalPon,
            boolean legalChiFromKamicha) {
        if (chankanWindow) {
            return legalRon;
        }
        if (legalRon) {
            return true;
        }
        if (inRiichi) {
            return false;
        }
        return legalDaiminKan || legalPon || legalChiFromKamicha;
    }

    public static ClaimIntentValidation validate(
            boolean claimWindowActive,
            boolean chankanWindow,
            boolean seatEligible,
            boolean inRiichi,
            ClaimWindowRules.ClaimIntent intent,
            boolean legalRon,
            boolean legalPon,
            boolean legalDaiminKan,
            boolean legalChi,
            boolean isKamicha,
            int chiTileA,
            int chiTileB,
            List<ClaimLegalityRules.ChiPair> chiPairs) {
        if (!claimWindowActive) {
            return rejected(ValidationCode.NOT_IN_CLAIM_WINDOW, chiTileA, chiTileB);
        }
        if (!seatEligible) {
            return rejected(ValidationCode.NOT_ELIGIBLE_SEAT, chiTileA, chiTileB);
        }
        if (chankanWindow) {
            if (intent == ClaimWindowRules.ClaimIntent.PASS) {
                return accepted(chiTileA, chiTileB);
            }
            if (intent != ClaimWindowRules.ClaimIntent.CHANKAN) {
                return rejected(ValidationCode.INTENT_NOT_ALLOWED_IN_WINDOW, chiTileA, chiTileB);
            }
            if (!legalRon) {
                return rejected(ValidationCode.CHANKAN_NOT_LEGAL, chiTileA, chiTileB);
            }
            return accepted(chiTileA, chiTileB);
        }

        if (intent == ClaimWindowRules.ClaimIntent.PASS) {
            return accepted(chiTileA, chiTileB);
        }
        if (intent == ClaimWindowRules.ClaimIntent.RON) {
            return legalRon ? accepted(chiTileA, chiTileB) : rejected(ValidationCode.RON_NOT_LEGAL, chiTileA, chiTileB);
        }
        if (inRiichi) {
            return rejected(ValidationCode.RIICHI_RESTRICTED_CALL, chiTileA, chiTileB);
        }
        if (intent == ClaimWindowRules.ClaimIntent.PON) {
            return legalPon ? accepted(chiTileA, chiTileB) : rejected(ValidationCode.PON_NOT_LEGAL, chiTileA, chiTileB);
        }
        if (intent == ClaimWindowRules.ClaimIntent.DAIMIN_KAN) {
            return legalDaiminKan
                    ? accepted(chiTileA, chiTileB)
                    : rejected(ValidationCode.DAIMIN_KAN_NOT_LEGAL, chiTileA, chiTileB);
        }
        if (intent == ClaimWindowRules.ClaimIntent.CHI) {
            if (!isKamicha) {
                return rejected(ValidationCode.CHI_NOT_KAMICHA, chiTileA, chiTileB);
            }
            if (!legalChi || chiPairs == null || chiPairs.isEmpty()) {
                return rejected(ValidationCode.CHI_PAIR_REQUIRED, chiTileA, chiTileB);
            }
            int resolvedA = chiTileA;
            int resolvedB = chiTileB;
            if (resolvedA < 0 || resolvedB < 0) {
                ClaimLegalityRules.ChiPair p = chiPairs.get(0);
                resolvedA = p.tileA();
                resolvedB = p.tileB();
            }
            for (ClaimLegalityRules.ChiPair p : chiPairs) {
                if ((p.tileA() == resolvedA && p.tileB() == resolvedB)
                        || (p.tileA() == resolvedB && p.tileB() == resolvedA)) {
                    return accepted(resolvedA, resolvedB);
                }
            }
            return rejected(ValidationCode.CHI_PAIR_NOT_LEGAL, resolvedA, resolvedB);
        }
        return rejected(ValidationCode.INTENT_NOT_ALLOWED_IN_WINDOW, chiTileA, chiTileB);
    }

    private static ClaimIntentValidation accepted(int chiTileA, int chiTileB) {
        return new ClaimIntentValidation(ValidationCode.OK, true, chiTileA, chiTileB);
    }

    private static ClaimIntentValidation rejected(ValidationCode code, int chiTileA, int chiTileB) {
        return new ClaimIntentValidation(code, false, chiTileA, chiTileB);
    }
}
