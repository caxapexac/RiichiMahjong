package com.mahjongcore.yaku.normals;


import com.mahjongcore.hands.Mentsu;
import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.tile.TileType;

import java.util.List;

import static com.mahjongcore.yaku.normals.NormalYaku.HONITSU;

/**
 * (half flush): all tiles are from one suit plus honor tiles.
 */
public class HonitsuResolver implements NormalYakuResolver {
    private final NormalYaku yakuEnum = HONITSU;

    private List<Mentsu> allMentsu;

    private boolean hasJihai = false;
    private TileType type = null;

    public HonitsuResolver(MentsuComp comp) {
        allMentsu = comp.getAllMentsu();
    }

    public NormalYaku getNormalYaku() {
        return yakuEnum;
    }

    public boolean isMatch() {
        for (Mentsu mentsu : allMentsu) {
            if (!hasOnlyOneType(mentsu)) {
                return false;
            }
        }

        return hasJihai;
    }

    private boolean hasOnlyOneType(Mentsu mentsu) {
        if (mentsu.getTile().getNumber() == 0) {
            hasJihai = true;
        } else if (type == null) {
            type = mentsu.getTile().getType();
        } else if (type != mentsu.getTile().getType()) {
            return false;
        }
        return true;
    }
}
