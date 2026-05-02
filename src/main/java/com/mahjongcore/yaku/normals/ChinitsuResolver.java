package com.mahjongcore.yaku.normals;


import com.mahjongcore.hands.Mentsu;
import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.tile.TileType;

import java.util.List;

import static com.mahjongcore.tile.TileType.FONPAI;
import static com.mahjongcore.tile.TileType.SANGEN;
import static com.mahjongcore.yaku.normals.NormalYaku.CHINITSU;

/**
 * (pure one suit): all tiles belong to the same suit.
 */
public class ChinitsuResolver implements NormalYakuResolver {
    private final NormalYaku yakuEnum = CHINITSU;
    private final MentsuComp comp;

    public ChinitsuResolver(MentsuComp comp) {
        this.comp = comp;
    }

    public NormalYaku getNormalYaku() {
        return yakuEnum;
    }

    public boolean isMatch() {
        List<Mentsu> allMentsu = comp.getAllMentsu();
        TileType firstType = allMentsu.get(0).getTile().getType();

        if (firstType == FONPAI || firstType == SANGEN) {
            return false;
        }

        for (Mentsu mentsu : allMentsu) {
            TileType checkType = mentsu.getTile().getType();
            if (firstType != checkType) {
                return false;
            }
        }

        return true;
    }
}
