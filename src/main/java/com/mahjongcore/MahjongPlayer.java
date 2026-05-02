package com.mahjongcore;

import com.mahjongcore.hands.Hands;
import com.mahjongcore.hands.Mentsu;
import com.mahjongcore.hands.MentsuComp;
import com.mahjongcore.tile.Tile;
import com.mahjongcore.yaku.normals.NormalYaku;
import com.mahjongcore.yaku.normals.NormalYakuResolver;
import com.mahjongcore.yaku.yakuman.Yakuman;
import com.mahjongcore.yaku.yakuman.YakumanResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.mahjongcore.MahjongScore.SCORE0;
import static com.mahjongcore.tile.TileType.SANGEN;
import static com.mahjongcore.yaku.normals.NormalYaku.*;
import static com.mahjongcore.yaku.yakuman.Yakuman.KOKUSHIMUSOU;

/**
 * Handles win detection and score calculation; delegates yaku checking to resolver classes.
 */
public class MahjongPlayer {

    private List<Yakuman> yakumanList = new ArrayList<>(1);
    private List<NormalYaku> normalYakuList = new ArrayList<>(0);
    private MentsuComp comp;

    private int han = 0;
    private int fu = 0;
    private MahjongScore score = SCORE0;

    private Hands hands;
    private MahjongGeneralSituation generalSituation;
    private MahjongPersonalSituation personalSituation;


    public MahjongPlayer(Hands hands) {
        this.hands = hands;
    }

    public MahjongPlayer(Hands hands, MahjongGeneralSituation generalSituation, MahjongPersonalSituation personalSituation) {
        this.hands = hands;
        this.generalSituation = generalSituation;
        this.personalSituation = personalSituation;
    }


    public List<Yakuman> getYakumanList() {
        return yakumanList;
    }

    public List<NormalYaku> getNormalYakuList() {
        return normalYakuList;
    }

    public int getFu() {
        return fu;
    }

    public MahjongScore getScore() {
        return score;
    }

    public int getHan() {
        return han;
    }

    public void calculate() {
        if (!hands.getCanWin()) return;

        // kokushi musou: save as sole yakuman and exit early
        if (hands.getIsKokushimuso()) {
            yakumanList.add(KOKUSHIMUSOU);
            if (personalSituation != null) {
                score = MahjongScore.calculateYakumanScore(personalSituation.isParent(), yakumanList.size());
            } else {
                score = SCORE0;
            }
            return;
        }

        if (findYakuman()) {
            if (personalSituation == null) {
                score = SCORE0;
                return;
            }
            score = MahjongScore.calculateYakumanScore(personalSituation.isParent(), yakumanList.size());
            return;
        }

        findNormalYaku();
    }

    /**
     * @return true if any yakuman was found
     */
    private boolean findYakuman() {
        List<Yakuman> yakumanStock = new ArrayList<>(4);

        for (MentsuComp comp : hands.getMentsuCompSet()) {
            Set<YakumanResolver> yakumanResolverSet
                = MahjongYakuConfig.getYakumanResolverSet(comp, generalSituation, personalSituation);

            for (YakumanResolver resolver : yakumanResolverSet) {
                if (resolver.isMatch()) {
                    yakumanStock.add(resolver.getYakuman());
                }
            }

            if (yakumanList.size() < yakumanStock.size()) {
                yakumanList = yakumanStock;
                this.comp = comp;
            }
        }

        return yakumanList.size() > 0;
    }

    private void findNormalYaku() {
        for (MentsuComp comp : hands.getMentsuCompSet()) {
            List<NormalYaku> yakuStock = new ArrayList<>(7);
            Set<NormalYakuResolver> resolverSet
                = MahjongYakuConfig.getNormalYakuResolverSet(comp, generalSituation, personalSituation);
            for (NormalYakuResolver resolver : resolverSet) {
                if (resolver.isMatch()) {
                    yakuStock.add(resolver.getNormalYaku());
                }
            }

            int hanSum = calcHanSum(yakuStock);
            if (hanSum > this.han) {
                han = hanSum;
                normalYakuList = yakuStock;
                this.comp = comp;
            }
        }

        if (han > 0) {
            calcDora(hands.getHandsComp(), generalSituation, normalYakuList.contains(RIICHI));
        }
        calcScore();
    }

    private void calcScore() {
        fu = calcFu();
        if (personalSituation == null) {
            return;
        }
        score = MahjongScore.calculateScore(personalSituation.isParent(), han, fu);
    }

    /**
     * Returns 0 if no yaku; 20 if situation is unknown.
     */
    private int calcFu() {
        if (normalYakuList.size() == 0) {
            return 0;
        }
        if (personalSituation == null || generalSituation == null) {
            return 20;
        }
        // pinfu tsumo and chitoitsu are fixed-fu special cases
        if (normalYakuList.contains(PINFU) && normalYakuList.contains(TSUMO)) {
            return 20;
        }
        if (normalYakuList.contains(CHITOITSU)) {
            return 25;
        }

        int tmpFu = 20;
        // closed ron adds 10 fu
        tmpFu += calcFuByAgari();

        for (Mentsu mentsu : comp.getAllMentsu()) {
            tmpFu += mentsu.getFu();
        }

        tmpFu += calcFuByWait(comp, hands.getLast());
        tmpFu += calcFuByJanto();

        return tmpFu;
    }

    /**
     * Fu bonus for the pair head. Double-wind tile adds +4.
     */
    private int calcFuByJanto() {
        Tile jantoTile = comp.getJanto().getTile();
        int tmp = 0;
        if (jantoTile == generalSituation.getBakaze()) {
            tmp += 2;
        }
        if (jantoTile == personalSituation.getJikaze()) {
            tmp += 2;
        }
        if (jantoTile.getType() == SANGEN) {
            tmp += 2;
        }
        return tmp;
    }

    private int calcFuByAgari() {
        if (personalSituation.isTsumo()) {
            return 2;
        }
        if (!hands.isOpen()) {
            return 10;
        }
        return 0;
    }

    /** Fu bonus for wait type: kanchan, penchan, or tanki add 2. */
    private int calcFuByWait(MentsuComp comp, Tile last) {
        if (comp.isKanchan(last) || comp.isPenchan(last) || comp.isTanki(last)) {
            return 2;
        }

        return 0;
    }

    private void calcDora(int[] handsComp, MahjongGeneralSituation generalSituation, boolean isRiichi) {
        if (generalSituation == null) {
            return;
        }
        int dora = 0;
        for (Tile tile : generalSituation.getDora()) {
            dora += handsComp[tile.getCode()];
        }
        for (int i = 0; i < dora; i++) {
            normalYakuList.add(DORA);
            han += DORA.getHan();
        }

        if (isRiichi) {
            int uradora = 0;
            for (Tile tile : generalSituation.getUradora()) {
                uradora += handsComp[tile.getCode()];
            }
            for (int i = 0; i < uradora; i++) {
                normalYakuList.add(URADORA);
                han += URADORA.getHan();
            }
        }
    }

    /**
     * Calculates total han, applying kuisagari (han reduction) if the hand is open.
     *
     * @param yakuStock list of yaku scored
     * @return total han count
     */
    private int calcHanSum(List<NormalYaku> yakuStock) {
        int hanSum = 0;
        if (hands.isOpen()) {
            for (NormalYaku yaku : yakuStock) {
                hanSum += yaku.getKuisagari();
            }
        } else {
            for (NormalYaku yaku : yakuStock) {
                hanSum += yaku.getHan();
            }
        }
        return hanSum;
    }
}
