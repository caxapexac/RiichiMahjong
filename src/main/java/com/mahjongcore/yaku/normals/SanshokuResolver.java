package com.mahjongcore.yaku.normals;

import com.mahjongcore.tile.TileType;

import static com.mahjongcore.tile.TileType.*;

public abstract class SanshokuResolver implements NormalYakuResolver {
    protected boolean manzu = false;
    protected boolean pinzu = false;
    protected boolean sohzu = false;

    protected void detectType(TileType shuntsuType) {
        if (shuntsuType == MANZU) {
            manzu = true;
        } else if (shuntsuType == PINZU) {
            pinzu = true;
        } else if (shuntsuType == SOHZU) {
            sohzu = true;
        }
    }
}
