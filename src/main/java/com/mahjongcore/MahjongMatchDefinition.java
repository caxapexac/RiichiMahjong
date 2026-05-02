package com.mahjongcore;

import java.util.UUID;

/**
 * Match definition for a table session (server-authoritative).
 */
public final class MahjongMatchDefinition {

    public record SeatDefinition(boolean enabled, UUID occupant) {}

    private static final int DEFAULT_SEAT_COUNT = 4;

    public static final MahjongMatchDefinition DEFAULT =
            new MahjongMatchDefinition(false, true, true, 25_000, createDefaultSeats());

    /** {@code false}: east round only (tonpuu); {@code true}: full game (hanchan). */
    private boolean hanchan;
    private boolean akaDora;
    private boolean openTanyao;
    /** Typical riichi: 25000. */
    private int startingPoints;
    private SeatDefinition[] seats;
    private int[] seatPoints;

    public MahjongMatchDefinition(boolean hanchan, boolean akaDora, boolean openTanyao, int startingPoints, SeatDefinition[] seats) {
        this.hanchan = hanchan;
        this.akaDora = akaDora;
        this.openTanyao = openTanyao;
        this.startingPoints = startingPoints;
        this.seats = seats == null ? createDefaultSeats() : seats.clone();
        this.seatPoints = createDefaultSeatPoints(this.seats, this.startingPoints);
    }

    public MahjongMatchDefinition(
            boolean hanchan,
            boolean akaDora,
            boolean openTanyao,
            int startingPoints,
            SeatDefinition[] seats,
            int[] seatPoints) {
        this.hanchan = hanchan;
        this.akaDora = akaDora;
        this.openTanyao = openTanyao;
        this.startingPoints = startingPoints;
        this.seats = seats == null ? createDefaultSeats() : seats.clone();
        this.seatPoints = normalizeSeatPoints(this.seats, this.startingPoints, seatPoints);
    }

    public MahjongMatchDefinition(boolean hanchan, boolean akaDora, boolean openTanyao, int startingPoints) {
        this(hanchan, akaDora, openTanyao, startingPoints, createDefaultSeats());
    }

    public static SeatDefinition[] createDefaultSeats() {
        SeatDefinition[] defaultSeats = new SeatDefinition[DEFAULT_SEAT_COUNT];
        for (int seat = 0; seat < defaultSeats.length; seat++) {
            defaultSeats[seat] = new SeatDefinition(true, null);
        }
        return defaultSeats;
    }

    public static int[] createDefaultSeatPoints(SeatDefinition[] seats, int startingPoints) {
        int[] points = new int[seats == null ? 0 : seats.length];
        for (int seat = 0; seat < points.length; seat++) {
            SeatDefinition seatDef = seats[seat];
            if (seatDef != null && seatDef.enabled() && seatDef.occupant() != null) {
                points[seat] = startingPoints;
            }
        }
        return points;
    }

    private static int[] normalizeSeatPoints(SeatDefinition[] seats, int startingPoints, int[] seatPoints) {
        if (seatPoints == null || seatPoints.length != seats.length) {
            return createDefaultSeatPoints(seats, startingPoints);
        }
        return seatPoints.clone();
    }

    public boolean hanchan() {
        return hanchan;
    }

    public boolean akaDora() {
        return akaDora;
    }

    public boolean openTanyao() {
        return openTanyao;
    }

    public int startingPoints() {
        return startingPoints;
    }

    public SeatDefinition[] seats() {
        return seats;
    }

    public int seatPoints(int seat) {
        if (seat < 0 || seat >= seatPoints.length) {
            return 0;
        }
        return seatPoints[seat];
    }

    public int[] seatPoints() {
        return seatPoints;
    }

    public MahjongMatchDefinition withHanchan(boolean v) {
        this.hanchan = v;
        return this;
    }

    public MahjongMatchDefinition withAkaDora(boolean v) {
        this.akaDora = v;
        return this;
    }

    public MahjongMatchDefinition withOpenTanyao(boolean v) {
        this.openTanyao = v;
        return this;
    }

    public MahjongMatchDefinition withStartingPoints(int v) {
        this.startingPoints = v;
        return this;
    }

    public MahjongMatchDefinition withSeats(SeatDefinition[] v) {
        this.seats = v == null ? createDefaultSeats() : v.clone();
        this.seatPoints = normalizeSeatPoints(this.seats, this.startingPoints, this.seatPoints);
        return this;
    }

    public MahjongMatchDefinition withSeatPoints(int[] v) {
        this.seatPoints = normalizeSeatPoints(this.seats, this.startingPoints, v);
        return this;
    }

    public MahjongMatchDefinition setSeatPoints(int seat, int value) {
        if (seat < 0 || seat >= seatPoints.length) {
            return this;
        }
        seatPoints[seat] = value;
        return this;
    }

    public MahjongMatchDefinition addSeatPoints(int seat, int delta) {
        if (seat < 0 || seat >= seatPoints.length) {
            return this;
        }
        seatPoints[seat] += delta;
        return this;
    }
}
