package com.mahjongcore;

import com.mahjongcore.tile.Tile;

import static com.mahjongcore.tile.Tile.TON;

public class MahjongPersonalSituation {
    private boolean isParent;
    private boolean isTsumo;
    private boolean isIppatsu;
    private boolean isRiichi;
    private boolean isDoubleRiichi;
    private boolean isChankan;
    private boolean isRinshankaihou;
    private Tile jikaze;

    public MahjongPersonalSituation() {
    }

    public MahjongPersonalSituation(boolean isTsumo, boolean isIppatsu, boolean isRiichi, boolean isDoubleRiichi, boolean isChankan, boolean isRinshankaihou, Tile jikaze) {
        this.isTsumo = isTsumo;
        this.isIppatsu = isIppatsu;
        this.isRiichi = isRiichi;
        this.isDoubleRiichi = isDoubleRiichi;
        this.isChankan = isChankan;
        this.isRinshankaihou = isRinshankaihou;
        this.jikaze = jikaze;
        isParent = (jikaze == TON);
    }

    public boolean isParent() {
        return isParent;
    }

    public boolean isTsumo() {
        return isTsumo;
    }

    public void setTsumo(boolean tsumo) {
        isTsumo = tsumo;
    }

    public boolean isIppatsu() {
        return isIppatsu;
    }

    public void setIppatsu(boolean ippatsu) {
        isIppatsu = ippatsu;
    }

    public boolean isRiichi() {
        return isRiichi;
    }

    public void setRiichi(boolean riichi) {
        this.isRiichi = riichi;
    }

    public boolean isDoubleRiichi() {
        return isDoubleRiichi;
    }

    public void setDoubleRiichi(boolean doubleRiichi) {
        isDoubleRiichi = doubleRiichi;
    }

    public boolean isChankan() {
        return isChankan;
    }

    public void setChankan(boolean chankan) {
        isChankan = chankan;
    }

    public boolean isRinshankaihou() {
        return isRinshankaihou;
    }

    public void setRinshankaihou(boolean rinshankaihou) {
        isRinshankaihou = rinshankaihou;
    }

    public Tile getJikaze() {
        return jikaze;
    }

    public void setJikaze(Tile jikaze) {
        this.jikaze = jikaze;
        isParent = (jikaze == TON);
    }
}
