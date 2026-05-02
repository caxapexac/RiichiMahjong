package com.riichimahjongforge.nbt;

import com.mahjongcore.MahjongMatchDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Minecraft serialization helpers for {@link MahjongMatchDefinition}.
 */
public final class MahjongMatchDefinitionNbt {

    private static final int SCHEMA = 3;

    private MahjongMatchDefinitionNbt() {}

    public static void save(CompoundTag tag, MahjongMatchDefinition rules) {
        CompoundTag r = new CompoundTag();
        r.putInt("V", SCHEMA);
        r.putBoolean("Han", rules.hanchan());
        r.putBoolean("Aka", rules.akaDora());
        r.putBoolean("OpenTan", rules.openTanyao());
        r.putInt("StartPts", rules.startingPoints());
        net.minecraft.nbt.ListTag seats = new net.minecraft.nbt.ListTag();
        for (MahjongMatchDefinition.SeatDefinition seat : rules.seats()) {
            CompoundTag seatTag = new CompoundTag();
            seatTag.putBoolean("En", seat.enabled());
            if (seat.occupant() != null) {
                seatTag.putUUID("O", seat.occupant());
            }
            seats.add(seatTag);
        }
        r.put("Seats", seats);
        int[] seatPoints = rules.seatPoints();
        net.minecraft.nbt.ListTag seatPointsTag = new net.minecraft.nbt.ListTag();
        for (int points : seatPoints) {
            seatPointsTag.add(net.minecraft.nbt.IntTag.valueOf(points));
        }
        r.put("SeatPts", seatPointsTag);
        tag.put("MatchRules", r);
    }

    public static MahjongMatchDefinition load(CompoundTag tag, MahjongMatchDefinition fallback) {
        if (!tag.contains("MatchRules")) {
            return fallback;
        }
        CompoundTag r = tag.getCompound("MatchRules");
        int v = r.contains("V") ? r.getInt("V") : SCHEMA;
        if (v != 1 && v != SCHEMA) {
            return fallback;
        }
        MahjongMatchDefinition.SeatDefinition[] seats = loadSeats(tag, r);
        int[] seatPoints = loadSeatPoints(r, seats);
        return new MahjongMatchDefinition(
                r.getBoolean("Han"),
                r.getBoolean("Aka"),
                r.getBoolean("OpenTan"),
                r.getInt("StartPts"),
                seats,
                seatPoints);
    }

    public static boolean hasSeatDefinitions(CompoundTag tag) {
        if (!tag.contains("MatchRules", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag rulesTag = tag.getCompound("MatchRules");
        return rulesTag.contains("Seats", net.minecraft.nbt.Tag.TAG_LIST)
                || tag.contains("LobbySeats", net.minecraft.nbt.Tag.TAG_LIST);
    }

    public static void write(FriendlyByteBuf buf, MahjongMatchDefinition rules) {
        buf.writeInt(SCHEMA);
        buf.writeBoolean(rules.hanchan());
        buf.writeBoolean(rules.akaDora());
        buf.writeBoolean(rules.openTanyao());
        buf.writeInt(rules.startingPoints());
        buf.writeVarInt(rules.seats().length);
        for (MahjongMatchDefinition.SeatDefinition seat : rules.seats()) {
            buf.writeBoolean(seat.enabled());
            boolean hasOccupant = seat.occupant() != null;
            buf.writeBoolean(hasOccupant);
            if (hasOccupant) {
                buf.writeUUID(seat.occupant());
            }
        }
        buf.writeVarInt(rules.seatPoints().length);
        for (int points : rules.seatPoints()) {
            buf.writeInt(points);
        }
    }

    public static MahjongMatchDefinition read(FriendlyByteBuf buf) {
        int schema = buf.readInt();
        if (schema != 1 && schema != 2 && schema != SCHEMA) {
            return MahjongMatchDefinition.DEFAULT;
        }
        boolean hanchan = buf.readBoolean();
        boolean akaDora = buf.readBoolean();
        boolean openTanyao = buf.readBoolean();
        int startingPoints = buf.readInt();
        if (schema == 1) {
            return new MahjongMatchDefinition(hanchan, akaDora, openTanyao, startingPoints);
        }
        int seatsCount = Math.max(0, buf.readVarInt());
        MahjongMatchDefinition.SeatDefinition[] seats = new MahjongMatchDefinition.SeatDefinition[seatsCount];
        for (int i = 0; i < seatsCount; i++) {
            boolean enabled = buf.readBoolean();
            java.util.UUID occupant = buf.readBoolean() ? buf.readUUID() : null;
            seats[i] = new MahjongMatchDefinition.SeatDefinition(enabled, occupant);
        }
        int[] seatPoints;
        if (schema >= 3) {
            int pointsCount = Math.max(0, buf.readVarInt());
            seatPoints = new int[pointsCount];
            for (int i = 0; i < pointsCount; i++) {
                seatPoints[i] = buf.readInt();
            }
        } else {
            seatPoints = MahjongMatchDefinition.createDefaultSeatPoints(seats, startingPoints);
        }
        return new MahjongMatchDefinition(
                hanchan,
                akaDora,
                openTanyao,
                startingPoints,
                seats,
                seatPoints);
    }

    private static int[] loadSeatPoints(CompoundTag rulesTag, MahjongMatchDefinition.SeatDefinition[] seats) {
        if (!rulesTag.contains("SeatPts", net.minecraft.nbt.Tag.TAG_LIST)) {
            return MahjongMatchDefinition.createDefaultSeatPoints(seats, rulesTag.getInt("StartPts"));
        }
        net.minecraft.nbt.ListTag pointsTag = rulesTag.getList("SeatPts", net.minecraft.nbt.Tag.TAG_INT);
        int[] seatPoints = new int[pointsTag.size()];
        for (int i = 0; i < pointsTag.size(); i++) {
            seatPoints[i] = pointsTag.getInt(i);
        }
        return seatPoints;
    }

    private static MahjongMatchDefinition.SeatDefinition[] loadSeats(CompoundTag fullTag, CompoundTag rulesTag) {
        net.minecraft.nbt.ListTag seatsTag;
        if (rulesTag.contains("Seats", net.minecraft.nbt.Tag.TAG_LIST)) {
            seatsTag = rulesTag.getList("Seats", net.minecraft.nbt.Tag.TAG_COMPOUND);
        } else if (fullTag.contains("LobbySeats", net.minecraft.nbt.Tag.TAG_LIST)) {
            seatsTag = fullTag.getList("LobbySeats", net.minecraft.nbt.Tag.TAG_COMPOUND);
        } else {
            return MahjongMatchDefinition.createDefaultSeats();
        }

        int size = seatsTag.size();
        MahjongMatchDefinition.SeatDefinition[] seats = new MahjongMatchDefinition.SeatDefinition[size];
        for (int i = 0; i < size; i++) {
            CompoundTag seatTag = seatsTag.getCompound(i);
            java.util.UUID occupant = seatTag.hasUUID("O") ? seatTag.getUUID("O") : null;
            boolean enabled = !seatTag.contains("En") || seatTag.getBoolean("En");
            seats[i] = new MahjongMatchDefinition.SeatDefinition(enabled, occupant);
        }
        return seats;
    }
}
