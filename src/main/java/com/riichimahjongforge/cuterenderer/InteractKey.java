package com.riichimahjongforge.cuterenderer;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Stable identifier for an interactive node, sent over the network on click.
 *
 * <p>Sealed: implementers are concrete record types. The wire format is
 * {@code byte tag + payload}. Owners (e.g. the mahjong table) define their own
 * keys and dispatch on {@code instanceof} server-side.
 */
public sealed interface InteractKey {

    /** Wire tag — unique within this sealed family. */
    byte wireTag();

    /** Encode payload (excluding wire tag). */
    void encode(FriendlyByteBuf buf);

    /** Free-form named tag (e.g. "PON", "RON"). */
    record Named(String name) implements InteractKey {
        public static final byte TAG = 1;
        @Override public byte wireTag() { return TAG; }
        @Override public void encode(FriendlyByteBuf buf) { buf.writeUtf(name, 64); }
        public static Named decode(FriendlyByteBuf buf) { return new Named(buf.readUtf(64)); }
    }

    /** Plain indexed slot (e.g. mahjong solitaire tile #42). Owner-defined index space. */
    record Slot(int index) implements InteractKey {
        public static final byte TAG = 3;
        @Override public byte wireTag() { return TAG; }
        @Override public void encode(FriendlyByteBuf buf) { buf.writeVarInt(index); }
        public static Slot decode(FriendlyByteBuf buf) { return new Slot(buf.readVarInt()); }
    }

    /** Slot in some indexed area belonging to a seat (e.g. tile in seat 0's hand). */
    record SeatSlot(byte seat, byte area, short index) implements InteractKey {
        public static final byte TAG = 2;
        public static final byte AREA_HAND = 0;
        public static final byte AREA_RIVER = 1;
        public static final byte AREA_MELD = 2;
        public static final byte AREA_WALL = 3;
        /** Action button at the seat's button grid; {@code index} is the
         *  position into the seat's current {@code legalActions(seat)} list,
         *  filtered to button-eligible actions (no plain discards/draws). */
        public static final byte AREA_BUTTON = 4;
        @Override public byte wireTag() { return TAG; }
        @Override public void encode(FriendlyByteBuf buf) {
            buf.writeByte(seat); buf.writeByte(area); buf.writeShort(index);
        }
        public static SeatSlot decode(FriendlyByteBuf buf) {
            return new SeatSlot(buf.readByte(), buf.readByte(), buf.readShort());
        }
    }

    static void write(FriendlyByteBuf buf, InteractKey key) {
        buf.writeByte(key.wireTag());
        key.encode(buf);
    }

    static InteractKey read(FriendlyByteBuf buf) {
        byte tag = buf.readByte();
        return switch (tag) {
            case Named.TAG -> Named.decode(buf);
            case SeatSlot.TAG -> SeatSlot.decode(buf);
            case Slot.TAG -> Slot.decode(buf);
            default -> throw new IllegalArgumentException("Unknown InteractKey tag: " + tag);
        };
    }
}
