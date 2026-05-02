package com.mahjongcore;

import com.mahjongcore.hands.Kantsu;
import com.mahjongcore.hands.Kotsu;
import com.mahjongcore.hands.Mentsu;
import com.mahjongcore.hands.Shuntsu;
import com.mahjongcore.tile.Tile;
import java.util.ArrayList;
import java.util.List;

/**
 * Meld structure (open or closed). Used as table-authoritative state; tile zones are only a materialized view.
 *
 * <p>Open melds have {@code fromSeat >= 0}. Closed melds use {@code fromSeat = -1}.
 */
public class MahjongMeld {

    public enum Kind {
        CHI,
        PON,
        DAIMIN_KAN,
        ANKAN
    }

    protected final Kind kind;
    /** Tile codes 0–33; length 3 (chi, pon) or 4 (kan). */
    protected final int[] tileCodes;
    /** Physical seat index of the discarder for open melds; -1 for concealed. */
    protected final int fromSeat;

    public MahjongMeld(Kind kind, int[] tileCodes, int fromSeat) {
        this.kind = kind;
        this.tileCodes = tileCodes.clone();
        this.fromSeat = fromSeat;
    }

    public Kind kind() {
        return kind;
    }

    public int[] tileCodes() {
        return tileCodes.clone();
    }

    public int fromSeat() {
        return fromSeat;
    }

    public boolean isOpen() {
        return fromSeat >= 0;
    }

    public List<Mentsu> toMentsuList() {
        ArrayList<Mentsu> list = new ArrayList<>(1);
        list.add(toSingleMentsu());
        return list;
    }

    public Mentsu toSingleMentsu() {
        return switch (kind) {
            case CHI -> {
                if (tileCodes.length != 3) {
                    throw new IllegalStateException("chi");
                }
                yield new Shuntsu(
                        true,
                        Tile.valueOf(tileCodes[0]),
                        Tile.valueOf(tileCodes[1]),
                        Tile.valueOf(tileCodes[2]));
            }
            case PON -> {
                if (tileCodes.length != 3) {
                    throw new IllegalStateException("pon");
                }
                Tile t = Tile.valueOf(tileCodes[0]);
                yield new Kotsu(true, t, t, t);
            }
            case DAIMIN_KAN -> {
                if (tileCodes.length != 4) {
                    throw new IllegalStateException("daiminkan");
                }
                Tile t = Tile.valueOf(tileCodes[0]);
                yield new Kantsu(true, t, t, t, t);
            }
            case ANKAN -> {
                if (tileCodes.length != 4) {
                    throw new IllegalStateException("ankan");
                }
                Tile t = Tile.valueOf(tileCodes[0]);
                yield new Kantsu(false, t, t, t, t);
            }
        };
    }
}