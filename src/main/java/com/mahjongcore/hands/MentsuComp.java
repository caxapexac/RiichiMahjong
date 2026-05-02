package com.mahjongcore.hands;

import com.mahjongcore.MahjongIllegalMentsuSizeException;
import com.mahjongcore.tile.Tile;

import java.util.ArrayList;
import java.util.List;

/**
 * Organizes the complete set of winning melds for a hand.
 */
public class MentsuComp {

    private List<Toitsu> toitsuList = new ArrayList<>(7);
    private List<Shuntsu> shuntsuList = new ArrayList<>(4);
    private List<Kotsu> kotsuList = new ArrayList<>(4);
    private List<Kantsu> kantsuList = new ArrayList<>(4);
    private Tile last;

    /**
     * @param mentsuList the winning meld list
     * @param last
     * @throws MahjongIllegalMentsuSizeException if the meld count does not form a valid winning pattern
     */
    public MentsuComp(List<Mentsu> mentsuList, Tile last) throws MahjongIllegalMentsuSizeException {
        this.last = last;
        for (Mentsu mentsu : mentsuList) {
            setMentsu(mentsu);
        }

        int checkSum = shuntsuList.size() + kotsuList.size() + kantsuList.size();
        boolean isNormal = checkSum == 4 && toitsuList.size() == 1;
        boolean isChitoitsu = toitsuList.size() == 7 && checkSum == 0;
        if (!isNormal && !isChitoitsu) {
            throw new MahjongIllegalMentsuSizeException(mentsuList);
        }
    }

    private void setMentsu(Mentsu mentsu) {
        if (mentsu instanceof Toitsu) {
            toitsuList.add((Toitsu) mentsu);
        } else if (mentsu instanceof Shuntsu) {
            shuntsuList.add((Shuntsu) mentsu);
        } else if (mentsu instanceof Kotsu) {
            kotsuList.add((Kotsu) mentsu);
        } else if (mentsu instanceof Kantsu) {
            kantsuList.add((Kantsu) mentsu);
        }
    }

    /**
     * Returns null for chitoitsu (seven pairs).
     *
     * @return the jantou (pair head)
     */
    public Toitsu getJanto() {
        if (getToitsuCount() == 1) {
            return toitsuList.get(0);
        }
        return null;
    }

    public List<Toitsu> getToitsuList() {
        return toitsuList;
    }

    public int getToitsuCount() {
        return toitsuList.size();
    }

    public List<Shuntsu> getShuntsuList() {
        return shuntsuList;
    }

    public int getShuntsuCount() {
        return shuntsuList.size();
    }

    public List<Kotsu> getKotsuList() {
        return kotsuList;
    }

    /**
     * Returns kotsu and kantsu merged into a single list, for yaku checks that treat them identically.
     * TODO: good name
     */
    public List<Kotsu> getKotsuKantsu() {
        List<Kotsu> kotsuList = new ArrayList<>(this.kotsuList);
        for (Kantsu kantsu : kantsuList) {
            kotsuList.add(new Kotsu(kantsu.isOpen(), kantsu.getTile()));
        }
        return kotsuList;
    }

    public int getKotsuCount() {
        return kotsuList.size();
    }

    public List<Kantsu> getKantsuList() {
        return kantsuList;
    }

    public int getKantsuCount() {
        return kantsuList.size();
    }

    public List<Mentsu> getAllMentsu() {
        List<Mentsu> allMentsu = new ArrayList<>(7);
        allMentsu.addAll(getToitsuList());
        allMentsu.addAll(getShuntsuList());
        allMentsu.addAll(getKotsuList());
        allMentsu.addAll(getKantsuList());

        return allMentsu;
    }

    public Tile getLast() {
        return last;
    }

    public boolean isRyanmen(Tile last) {
        for (Shuntsu shuntsu : shuntsuList) {
            if (shuntsu.isOpen()) {
                continue;
            }
            if (shuntsu.getTile().getType() != last.getType()) {
                continue;
            }

            int number = shuntsu.getTile().getNumber();
            if (number == 8 || number == 2) {
                continue;
            }

            if (number - 1 == last.getNumber() || number + 1 == last.getNumber()) {
                return true;
            }
        }

        return false;
    }

    public boolean isTanki(Tile last) {
        return getJanto().getTile() == last;
    }

    public boolean isKanchan(Tile last) {
        if (isRyanmen(last)) {
            return false;
        }
        for (Shuntsu shuntsu : shuntsuList) {
            if (shuntsu.isOpen() || shuntsu.getTile().getType() != last.getType()) {
                continue;
            }
            if (shuntsu.getTile().getNumber() == last.getNumber()) {
                return true;
            }
        }
        return false;
    }

    public boolean isPenchan(Tile last) {
        if (isRyanmen(last)) {
            return false;
        }
        for (Shuntsu shuntsu : shuntsuList) {
            if (shuntsu.isOpen() || shuntsu.getTile().getType() != last.getType()) {
                continue;
            }
            int number = shuntsu.getTile().getNumber();
            if (number == 8 && last.getNumber() == 7) {
                return true;
            }
            if (number == 2 && last.getNumber() == 3) {
                return true;
            }
        }
        return false;
    }

    /**
     * Order-independent equality: returns true even if melds are in a different order.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MentsuComp)) return false;

        MentsuComp that = (MentsuComp) o;

        if (last != that.last) return false;
        if (toitsuList.size() != that.toitsuList.size()) return false;
        if (shuntsuList.size() != that.shuntsuList.size()) return false;
        if (kotsuList.size() != that.kotsuList.size()) return false;
        if (kantsuList.size() != that.kantsuList.size()) return false;
        for (Toitsu toitsu : toitsuList) {
            if (!that.toitsuList.contains(toitsu)) return false;
        }
        for (Shuntsu shuntsu : shuntsuList) {
            if (!that.shuntsuList.contains(shuntsu)) return false;
        }
        for (Kotsu kotsu : kotsuList) {
            if (!that.kotsuList.contains(kotsu)) return false;
        }
        for (Kantsu kantsu : kantsuList) {
            if (!that.kantsuList.contains(kantsu)) return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int tmp = 0;
        int result;

        result = last.hashCode();

        if (toitsuList != null) {
            for (Toitsu toitsu : toitsuList) {
                tmp += toitsu.hashCode();
            }
        }
        result = 31 * result + tmp;

        tmp = 0;
        if (shuntsuList != null) {
            for (Shuntsu shuntsu : shuntsuList) {
                tmp += shuntsu.hashCode();
            }
        }

        result = 31 * result + tmp;

        tmp = 0;
        if (kotsuList != null) {
            for (Kotsu kotsu : kotsuList) {
                tmp += kotsu.hashCode();
            }
        }

        result = 31 * result + tmp;

        tmp = 0;
        if (kantsuList != null) {
            for (Kantsu kantsu : kantsuList) {
                tmp += kantsu.hashCode();
            }
        }

        return 31 * result + tmp;
    }
}
