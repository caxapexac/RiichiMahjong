package com.mahjongcore.rules;

public final class SlotIndexRules {

    private SlotIndexRules() {}

    public static int playerZoneAbsolute(
            int seatCount, int playerZoneStart, int playerZoneSlotsPerSeat, int physicalSeat, int slotInSeat) {
        if (!isSeatValid(seatCount, physicalSeat) || !isSlotValid(playerZoneSlotsPerSeat, slotInSeat)) {
            return -1;
        }
        return playerZoneBase(seatCount, playerZoneStart, playerZoneSlotsPerSeat, physicalSeat) + slotInSeat;
    }

    public static int playerZoneBase(int seatCount, int playerZoneStart, int playerZoneSlotsPerSeat, int physicalSeat) {
        if (!isSeatValid(seatCount, physicalSeat)) {
            return -1;
        }
        return playerZoneStart + physicalSeat * playerZoneSlotsPerSeat;
    }

    public static int discardAbsolute(
            int seatCount, int discardsStart, int discardsSlotsPerSeat, int physicalSeat, int slotInSeat) {
        if (!isSeatValid(seatCount, physicalSeat) || !isSlotValid(discardsSlotsPerSeat, slotInSeat)) {
            return -1;
        }
        return discardBase(seatCount, discardsStart, discardsSlotsPerSeat, physicalSeat) + slotInSeat;
    }

    public static int discardBase(int seatCount, int discardsStart, int discardsSlotsPerSeat, int physicalSeat) {
        if (!isSeatValid(seatCount, physicalSeat)) {
            return -1;
        }
        return discardsStart + physicalSeat * discardsSlotsPerSeat;
    }

    public static int physicalSeatFromPlayerZoneAbsolute(
            int playerZoneStart, int playerZoneTotalSlots, int playerZoneSlotsPerSeat, int absolute) {
        int rel = absolute - playerZoneStart;
        if (rel < 0 || rel >= playerZoneTotalSlots) {
            return -1;
        }
        return rel / playerZoneSlotsPerSeat;
    }

    public static int slotInSeatFromPlayerZoneAbsolute(
            int playerZoneStart, int playerZoneTotalSlots, int playerZoneSlotsPerSeat, int absolute) {
        int rel = absolute - playerZoneStart;
        if (rel < 0 || rel >= playerZoneTotalSlots) {
            return -1;
        }
        return rel % playerZoneSlotsPerSeat;
    }

    private static boolean isSeatValid(int seatCount, int physicalSeat) {
        return physicalSeat >= 0 && physicalSeat < seatCount;
    }

    private static boolean isSlotValid(int slotsPerSeat, int slotInSeat) {
        return slotInSeat >= 0 && slotInSeat < slotsPerSeat;
    }
}
