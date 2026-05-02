package com.mahjongcore.yaku.yakuman;

/**
 * Yakuman enum. Kept separate from NormalYaku to prevent dora from inflating yakuman to double yakuman.
 */
public enum Yakuman {
    KOKUSHIMUSOU("国士無双"),
    SUUANKOU("四暗刻"),
    CHUURENPOUTOU("九蓮宝燈"),
    DAISANGEN("大三元"),
    TSUISOU("字一色"),
    SHOUSUUSHI("小四喜"),
    DAISUUSHI("大四喜"),
    RYUUIISOU("緑一色"),
    CHINROUTOU("清老頭"),
    SUUKANTSU("四槓子"),
    RENHOU("人和"),
    CHIHOU("地和"),
    TENHOU("天和"),;

    private final String japanese;

    Yakuman(String japanese) {
        this.japanese = japanese;
    }

    public String getJapanese() {
        return japanese;
    }
}
