package com.themahjong.replay;

import com.themahjong.TheMahjongTile;
import com.themahjong.TheMahjongTile.Suit;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parses a decoded Tenhou mjlog XML stream into a {@link TenhouGame}.
 *
 * <p>Tile encoding: index/4 = tile type (0-8 = 1-9m, 9-17 = 1-9p, 18-26 = 1-9s,
 * 27-30 = East-North winds, 31-33 = Haku/Hatsu/Chun), index%4 = copy number.
 * Red fives: index 16 (5m), 52 (5p), 88 (5s).
 *
 * <p>Meld m-field decoding ported from mthrok/tenhou-log-utils (MIT licence).
 */
public final class TenhouLogParser {

    private TenhouLogParser() {}

    /**
     * Per-parse flag for whether red 5s (akadora) are present in this game.
     * Tenhou game-type lobby code bit 1 set ⇒ no aka. Single-threaded tests, so
     * a static is safe; reset at the start of every {@link #parse} call.
     */
    private static boolean akaEnabled = true;

    /**
     * Returns null if the stream is not a valid mjlog XML (e.g. an HTTP error page).
     * All other exceptions propagate normally.
     */
    public static TenhouGame parse(String gameId, InputStream xml) throws Exception {
        akaEnabled = isAkaEnabledFromGameId(gameId);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
        Document doc;
        try {
            doc = builder.parse(xml);
        } catch (org.xml.sax.SAXParseException e) {
            return null;
        }

        NodeList children = doc.getDocumentElement().getChildNodes();
        List<TenhouRound> rounds = new ArrayList<>();
        RoundBuilder current = null;

        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element el)) continue;
            String tag = el.getTagName();

            switch (tag) {
                case "INIT" -> {
                    if (current != null) rounds.add(current.build());
                    current = parseInit(el);
                }
                case "N" -> {
                    if (current != null) current.add(parseClaim(el));
                }
                case "REACH" -> {
                    if (current != null) current.add(parseReach(el));
                }
                case "AGARI" -> {
                    if (current != null) current.add(parseAgari(el));
                }
                case "DORA" -> {
                    if (current != null) current.add(new TenhouAction.DoraReveal(
                            tileFromIndex(Integer.parseInt(el.getAttribute("hai")))));
                }
                case "RYUUKYOKU" -> {
                    if (current != null) current.add(new TenhouAction.ExhaustiveDraw());
                }
                default -> {
                    if (current == null || tag.isEmpty()) continue;
                    char first = tag.charAt(0);
                    String num = tag.substring(1);
                    if (num.isEmpty() || !isAllDigits(num)) continue;
                    int tileIdx = Integer.parseInt(num);
                    if (first >= 'T' && first <= 'W') {
                        current.add(new TenhouAction.Draw(first - 'T', tileFromIndex(tileIdx)));
                    } else if (first >= 'D' && first <= 'G') {
                        current.add(new TenhouAction.Discard(first - 'D', tileFromIndex(tileIdx)));
                    }
                }
            }
        }
        if (current != null) rounds.add(current.build());
        return new TenhouGame(gameId, List.copyOf(rounds));
    }

    // -------------------------------------------------------------------------

    private static RoundBuilder parseInit(Element el) {
        int[] seed = parseIntCSV(el.getAttribute("seed"));
        int[] scores = parseIntCSV(el.getAttribute("ten"));
        int dealer = Integer.parseInt(el.getAttribute("oya"));
        TheMahjongTile doraIndicator = tileFromIndex(seed[5]);

        List<List<TheMahjongTile>> hands = new ArrayList<>();
        for (int s = 0; s < 4; s++) {
            String attr = el.getAttribute("hai" + s);
            hands.add(attr.isEmpty() ? List.of() : tilesFromCSV(attr));
        }
        return new RoundBuilder(seed[0], seed[1], seed[2], dealer, doraIndicator, scores, hands);
    }

    private static TenhouAction parseClaim(Element el) {
        int seat = Integer.parseInt(el.getAttribute("who"));
        int m = Integer.parseInt(el.getAttribute("m"));
        return decodeMeld(seat, m);
    }

    private static TenhouAction parseReach(Element el) {
        int seat = Integer.parseInt(el.getAttribute("who"));
        int step = Integer.parseInt(el.getAttribute("step"));
        if (step == 1) return new TenhouAction.RiichiStep1(seat);
        String tenAttr = el.getAttribute("ten");
        int[] scores = tenAttr.isEmpty() ? new int[0] : parseIntCSV(tenAttr);
        return new TenhouAction.RiichiStep2(seat, scores);
    }

    private static TenhouAction parseAgari(Element el) {
        int winner = Integer.parseInt(el.getAttribute("who"));
        int fromWho = Integer.parseInt(el.getAttribute("fromWho"));

        List<TheMahjongTile> hand = tilesFromCSV(el.getAttribute("hai"));
        List<TheMahjongTile> dora = tilesFromCSV(el.getAttribute("doraHai"));
        String uraAttr = el.getAttribute("doraHaiUra");
        List<TheMahjongTile> ura = uraAttr.isEmpty() ? List.of() : tilesFromCSV(uraAttr);
        boolean isLast = !el.getAttribute("owari").isEmpty();

        // sc: alternating [score0, delta0, score1, delta1, ...] in units of 100
        String scAttr = el.getAttribute("sc");
        int[] scoreDeltas;
        if (scAttr.isEmpty()) {
            scoreDeltas = new int[0];
        } else {
            int[] sc = parseIntCSV(scAttr);
            scoreDeltas = new int[sc.length / 2];
            for (int i = 0; i < scoreDeltas.length; i++) {
                scoreDeltas[i] = sc[2 * i + 1] * 100;
            }
        }
        return new TenhouAction.Win(winner, fromWho, hand, dora, ura, isLast, scoreDeltas);
    }

    // -------------------------------------------------------------------------
    // Meld m-field decoding — ported from mthrok/tenhou-log-utils (MIT licence)
    // https://github.com/mthrok/tenhou-log-utils/blob/master/tenhou_log_utils/parser.py

    private static TenhouAction decodeMeld(int seat, int m) {
        int kui = m & 0x3;
        if ((m & (1 << 2)) != 0) {
            return new TenhouAction.Claim(seat, TenhouAction.ClaimType.CHI, tiles(parseChi(m)));
        } else if ((m & (1 << 3)) != 0) {
            return new TenhouAction.Claim(seat, TenhouAction.ClaimType.PON, tiles(parsePon(m)));
        } else if ((m & (1 << 4)) != 0) {
            return new TenhouAction.Claim(seat, TenhouAction.ClaimType.KAKAN, tiles(parseKakan(m)));
        } else if ((m & (1 << 5)) != 0) {
            // Kita (北抜き): sanma North-tile removal; hai0 is the specific North copy
            int hai0 = (m & 0xff00) >> 8;
            return new TenhouAction.Claim(seat, TenhouAction.ClaimType.KITA,
                    List.of(tileFromIndex(hai0)));
        } else if (kui == 0) {
            return new TenhouAction.Claim(seat, TenhouAction.ClaimType.ANKAN, tiles(parseKan(m, false)));
        } else {
            return new TenhouAction.Claim(seat, TenhouAction.ClaimType.DAIMINKAN, tiles(parseKan(m, true)));
        }
    }

    private static int[] parseChi(int m) {
        int t = (m & 0xfc00) >> 10;
        int r = t % 3;
        t = t / 3;
        t = 9 * (t / 7) + (t % 7);
        t *= 4;
        int[] h = {
            t + 4 * 0 + ((m & 0x0018) >> 3),
            t + 4 * 1 + ((m & 0x0060) >> 5),
            t + 4 * 2 + ((m & 0x0180) >> 7)
        };
        if (r == 1) {
            int tmp = h[0]; h[0] = h[1]; h[1] = tmp;
        } else if (r == 2) {
            int tmp = h[0]; h[0] = h[2]; h[2] = h[1]; h[1] = tmp;
        }
        return h;
    }

    private static int[] parsePon(int m) {
        int unused = (m & 0x0060) >> 5;
        int t = (m & 0xfe00) >> 9;
        int r = t % 3;
        t = (t / 3) * 4;
        int[] h = {t, t, t};
        if (unused == 0) { h[0] += 1; h[1] += 2; h[2] += 3; }
        else if (unused == 1) { h[1] += 2; h[2] += 3; }
        else if (unused == 2) { h[1] += 1; h[2] += 3; }
        else { h[1] += 1; h[2] += 2; }

        if (r == 1) { int tmp = h[0]; h[0] = h[1]; h[1] = tmp; }
        else if (r == 2) { int tmp = h[0]; h[0] = h[2]; h[2] = h[1]; h[1] = tmp; }

        int kui = m & 0x3;
        if (kui < 3) { int tmp = h[0]; h[0] = h[2]; h[2] = h[1]; h[1] = tmp; }
        if (kui < 2) { int tmp = h[0]; h[0] = h[2]; h[2] = h[1]; h[1] = tmp; }
        return h;
    }

    private static int[] parseKakan(int m) {
        int added = (m & 0x0060) >> 5;
        int t = (m & 0xfe00) >> 9;
        int r = t % 3;
        t = (t / 3) * 4;
        int[] base = {t, t, t};
        if (added == 0) { base[0] += 1; base[1] += 2; base[2] += 3; }
        else if (added == 1) { base[1] += 2; base[2] += 3; }
        else if (added == 2) { base[1] += 1; base[2] += 3; }
        else { base[1] += 1; base[2] += 2; }

        if (r == 1) { int tmp = base[0]; base[0] = base[1]; base[1] = tmp; }
        else if (r == 2) { int tmp = base[0]; base[0] = base[2]; base[2] = base[1]; base[1] = tmp; }

        int kui = m & 0x3;
        int addedTile = t + added;
        int[] h;
        if (kui == 3) {
            h = new int[]{addedTile, base[0], base[1], base[2]};
        } else if (kui == 2) {
            h = new int[]{base[0], addedTile, base[1], base[2]};
        } else {
            h = new int[]{base[1], base[0], addedTile, base[2]};
        }
        return h;
    }

    private static int[] parseKan(int m, boolean open) {
        int hai0 = (m & 0xff00) >> 8;
        int kui = m & 0x3;
        if (!open) {
            hai0 = (hai0 & ~3) + 3;
        }
        int t = (hai0 / 4) * 4;
        int[] h = {t, t, t};
        int rem = hai0 % 4;
        if (rem == 0) { h[0] += 1; h[1] += 2; h[2] += 3; }
        else if (rem == 1) { h[1] += 2; h[2] += 3; }
        else if (rem == 2) { h[1] += 1; h[2] += 3; }
        else { h[1] += 1; h[2] += 2; }

        if (kui == 1) { int tmp = hai0; hai0 = h[2]; h[2] = tmp; }
        else if (kui == 2) { int tmp = hai0; hai0 = h[0]; h[0] = tmp; }

        if (open) {
            return new int[]{hai0, h[0], h[1], h[2]};
        } else {
            // ankan: return all 4 copies from the base tile
            int base = (hai0 / 4) * 4;
            return new int[]{base, base + 1, base + 2, base + 3};
        }
    }

    // -------------------------------------------------------------------------

    /**
     * Tenhou game id lobby/rule code is the first hex group after "gm-" in the
     * filename. Bit 1 of that code indicates "no aka dora" — when set, the
     * specific tile ids that would otherwise be red 5s are regular tiles.
     */
    private static boolean isAkaEnabledFromGameId(String gameId) {
        if (gameId == null) return true;
        int dash = gameId.indexOf("gm-");
        if (dash < 0) return true;
        int start = dash + 3;
        int end = gameId.indexOf('-', start);
        if (end < 0 || end - start < 1) return true;
        try {
            int code = Integer.parseInt(gameId.substring(start, end), 16);
            return (code & 0x02) == 0;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    public static TheMahjongTile tileFromIndex(int index) {
        int type = index / 4;
        boolean red = akaEnabled && (index == 16 || index == 52 || index == 88);
        if (type <= 8) return new TheMahjongTile(Suit.MANZU, type + 1, red);
        if (type <= 17) return new TheMahjongTile(Suit.PINZU, type - 8, red);
        if (type <= 26) return new TheMahjongTile(Suit.SOUZU, type - 17, red);
        if (type <= 30) return new TheMahjongTile(Suit.WIND, type - 26, false);
        return new TheMahjongTile(Suit.DRAGON, type - 30, false);
    }

    private static List<TheMahjongTile> tiles(int[] indices) {
        List<TheMahjongTile> result = new ArrayList<>(indices.length);
        for (int idx : indices) result.add(tileFromIndex(idx));
        return List.copyOf(result);
    }

    private static List<TheMahjongTile> tilesFromCSV(String csv) {
        return tiles(parseIntCSV(csv));
    }

    private static boolean isAllDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    private static int[] parseIntCSV(String csv) {
        if (csv == null || csv.isEmpty()) return new int[0];
        String[] parts = csv.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) result[i] = Integer.parseInt(parts[i].trim());
        return result;
    }

    // -------------------------------------------------------------------------

    private static final class RoundBuilder {
        private final int roundNumber, honba, riichiSticks, dealer;
        private final TheMahjongTile doraIndicator;
        private final int[] startingScores;
        private final List<List<TheMahjongTile>> initialHands;
        private final List<TenhouAction> actions = new ArrayList<>();

        RoundBuilder(int roundNumber, int honba, int riichiSticks, int dealer,
                     TheMahjongTile doraIndicator, int[] startingScores,
                     List<List<TheMahjongTile>> initialHands) {
            this.roundNumber = roundNumber;
            this.honba = honba;
            this.riichiSticks = riichiSticks;
            this.dealer = dealer;
            this.doraIndicator = doraIndicator;
            this.startingScores = startingScores;
            this.initialHands = initialHands;
        }

        void add(TenhouAction action) { actions.add(action); }

        TenhouRound build() {
            return new TenhouRound(roundNumber, honba, riichiSticks, dealer, doraIndicator,
                    startingScores, List.copyOf(initialHands), List.copyOf(actions));
        }
    }
}
