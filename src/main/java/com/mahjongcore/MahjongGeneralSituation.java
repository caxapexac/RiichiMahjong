package com.mahjongcore;

import com.mahjongcore.tile.Tile;

import java.util.List;

public class MahjongGeneralSituation {
    private boolean isFirstRound;
    private boolean isHoutei;
    private Tile bakaze;
    private List<Tile> dora;
    private List<Tile> uradora;

    public MahjongGeneralSituation(boolean isFirstRound, boolean isHoutei, Tile bakaze, List<Tile> dora, List<Tile> uradora) {
        this.isFirstRound = isFirstRound;
        this.isHoutei = isHoutei;
        this.bakaze = bakaze;
        this.dora = dora;
        this.uradora = uradora;
    }

    public MahjongGeneralSituation() {
    }

    public boolean isFirstRound() {

        return isFirstRound;
    }

    public void setFirstRound(boolean firstRound) {
        isFirstRound = firstRound;
    }

    public boolean isHoutei() {
        return isHoutei;
    }

    public void setHoutei(boolean houtei) {
        isHoutei = houtei;
    }

    public Tile getBakaze() {
        return bakaze;
    }

    public void setBakaze(Tile bakaze) {
        this.bakaze = bakaze;
    }

    public List<Tile> getDora() {
        return dora;
    }

    public void setDora(List<Tile> dora) {
        this.dora = dora;
    }

    public List<Tile> getUradora() {
        return uradora;
    }

    public void setUradora(List<Tile> uradora) {
        this.uradora = uradora;
    }
}
