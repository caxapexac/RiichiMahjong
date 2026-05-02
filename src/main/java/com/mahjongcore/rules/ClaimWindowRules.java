package com.mahjongcore.rules;

import java.util.ArrayList;
import java.util.List;

public final class ClaimWindowRules {

    private ClaimWindowRules() {}

    public enum OutcomeKind {
        PASS_ALL,
        RON,
        CHANKAN,
        CHI,
        PON,
        DAIMIN_KAN
    }

    public enum ClaimIntent {
        NONE,
        PASS,
        RON,
        CHANKAN,
        PON,
        CHI,
        DAIMIN_KAN
    }

    public record SeatClaimView(ClaimIntent intent, boolean legalRon, boolean legalDaiminKan, boolean legalPon, boolean legalChi) {}

    public record ClaimWindowSnapshot(List<Integer> priorityOrder, boolean chankanWindow, List<SeatClaimView> seatViewsBySeat) {}

    public record ClaimResolution(OutcomeKind kind, int winnerSeat) {}

    public static List<Integer> eligibleClaimants(List<Integer> playOrder, int discarderSeat) {
        return claimPriorityOrder(playOrder, discarderSeat);
    }

    /** Turn priority from discarder's shimocha (next in {@code playOrder}). */
    public static List<Integer> claimPriorityOrder(List<Integer> playOrder, int discarderSeat) {
        int di = playOrder.indexOf(discarderSeat);
        if (di < 0) {
            return List.of();
        }
        int n = playOrder.size();
        ArrayList<Integer> out = new ArrayList<>(Math.max(0, n - 1));
        for (int k = 1; k < n; k++) {
            out.add(playOrder.get((di + k) % n));
        }
        return out;
    }

    public static boolean isReadyToResolve(
            boolean claimWindowActive, List<Integer> playOrder, int discarderSeat, List<ClaimIntent> seatIntentsBySeat) {
        if (!claimWindowActive) {
            return false;
        }
        List<Integer> elig = eligibleClaimants(playOrder, discarderSeat);
        if (elig.isEmpty()) {
            return true;
        }
        for (int seat : elig) {
            if (seat < 0 || seat >= seatIntentsBySeat.size()) {
                return false;
            }
            if (seatIntentsBySeat.get(seat) == ClaimIntent.NONE) {
                return false;
            }
        }
        return true;
    }

    /** True if any eligible seat (excluding {@code kakanSeat}) has legal chankan/ron. */
    public static boolean hasAnyChankanEligible(List<Integer> playOrder, int kakanSeat, boolean[] legalRonBySeat) {
        if (playOrder == null || legalRonBySeat == null) {
            return false;
        }
        for (int seat : eligibleClaimants(playOrder, kakanSeat)) {
            if (seat >= 0 && seat < legalRonBySeat.length && legalRonBySeat[seat]) {
                return true;
            }
        }
        return false;
    }

    public static ClaimResolution resolve(ClaimWindowSnapshot snapshot) {
        ClaimIntent winIntent = snapshot.chankanWindow() ? ClaimIntent.CHANKAN : ClaimIntent.RON;
        OutcomeKind winOutcome = snapshot.chankanWindow() ? OutcomeKind.CHANKAN : OutcomeKind.RON;
        for (int seat : snapshot.priorityOrder()) {
            SeatClaimView view = seatView(snapshot.seatViewsBySeat(), seat);
            if (view.intent() == winIntent && view.legalRon()) {
                return new ClaimResolution(winOutcome, seat);
            }
        }

        if (!snapshot.chankanWindow()) {
            for (int seat : snapshot.priorityOrder()) {
                SeatClaimView view = seatView(snapshot.seatViewsBySeat(), seat);
                if (view.intent() == ClaimIntent.DAIMIN_KAN && view.legalDaiminKan()) {
                    return new ClaimResolution(OutcomeKind.DAIMIN_KAN, seat);
                }
                if (view.intent() == ClaimIntent.PON && view.legalPon()) {
                    return new ClaimResolution(OutcomeKind.PON, seat);
                }
            }
            for (int seat : snapshot.priorityOrder()) {
                SeatClaimView view = seatView(snapshot.seatViewsBySeat(), seat);
                if (view.intent() == ClaimIntent.CHI && view.legalChi()) {
                    return new ClaimResolution(OutcomeKind.CHI, seat);
                }
            }
        }
        return new ClaimResolution(OutcomeKind.PASS_ALL, -1);
    }

    private static SeatClaimView seatView(List<SeatClaimView> bySeat, int seat) {
        if (seat < 0 || seat >= bySeat.size()) {
            return new SeatClaimView(ClaimIntent.NONE, false, false, false, false);
        }
        SeatClaimView view = bySeat.get(seat);
        if (view == null) {
            return new SeatClaimView(ClaimIntent.NONE, false, false, false, false);
        }
        return view;
    }

}
