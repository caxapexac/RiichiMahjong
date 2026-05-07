# WRC 2025 — Official Riichi Mahjong Rules

Condensed reference for engine implementation. Source: WRC_Rules_2025.md.
Differences from Tenhou are marked **[≠ Tenhou]**.

---

## Game structure

Players start with 30,000 points and return 30,000 (no oka). **[≠ Tenhou: 25k start]**

Uma: +15P / +5P / −5P / −15P. Tied players split combined uma for their positions. **[≠ Tenhou: 10-20]**

Game = East round + South round (hanchan). No West/North extra rounds. **[≠ Tenhou: sudden-death extension]**

No agari-yame (dealer cannot voluntarily stop the game in all-last even if winning). **[≠ Tenhou: tenpai-yame / agari-yame allowed]**

Negative scores are allowed; game continues. Referee lends 10,000-pt sticks. **[≠ Tenhou: busting ends game]**

Remaining riichi deposits at game end stay on the table and are not added to any score. **[≠ Tenhou: go to 1st place]**

Tie-breaking at game end: by final score, then by uma split.

---

## Dora

No red fives (aka dora). **[≠ Tenhou: 1 red five per suit]**

Regular dora + ura dora (riichi only) + kan dora / kan ura dora.

Kan dora revealed before the discarder's next discard for all quad types (ankan, minkan, kakan). **[≠ Tenhou: ankan = immediate, minkan/kakan = after discard]**

Kan dora is NOT revealed when the quad is robbed (chankan).

---

## Fu (minipoints)

Base: 20 fu (all wins). Closed ron adds +10 (menzen kafu), giving 30 base.

Tsumo bonus: +2 fu (not added for pinfu).

Double-wind pair (seat wind == round wind): **2 fu**. **[≠ Tenhou: 4 fu]**

Pinfu tsumo: fixed 20 fu (no tsumo bonus, no wait fu, no pair fu).

Seven pairs (chitoitsu): fixed 25 fu. No rounding.

Pair fu: dragons, seat wind, round wind = 2 fu each. Non-value pairs = 0 fu.

Wait fu: kanchan / penchan / tanki = 2 fu. Shanpon / ryanmen = 0 fu.

Group fu: see standard table (sequences 0, triplets 2/4/8, kans 8/16/32, open halves these).

Fu rounded up to nearest 10, minimum 30 (except chitoitsu fixed 25).

---

## Limit hands & kiriage mangan

Kiriage mangan: 4 han 30 fu and 3 han 60 fu are scored as mangan. **[≠ Tenhou: no kiriage]**

| Han  | Limit    | Basic pts |
|------|----------|-----------|
| 5    | Mangan   | 2,000     |
| 6–7  | Haneman  | 3,000     |
| 8–10 | Baiman   | 4,000     |
| 11–12| Sanbaiman| 6,000     |
| 13+  | Yakuman  | 8,000     |

Multiple genuine yakuman stack (each 8,000 basic). Counted yakuman (13+ han without yakuman yaku) = 1× yakuman only.

---

## Payment

Basic points: `min(fu × 2^(han+2), 2000)` before limit thresholds.

- Dealer ron: `basic × 6`, rounded up to 100.
- Non-dealer ron: `basic × 4`, rounded up to 100 (discarder pays all).
- Dealer tsumo: `basic × 2` from each opponent, rounded up to 100.
- Non-dealer tsumo: `basic × 2` from dealer, `basic × 1` from each other, rounded up to 100.
- Honba bonus: +100 per counter per payer (tsumo) / +300 per counter from discarder (ron).

---

## Honba (continuance counters)

Increment +1 when: East wins a hand OR hand ends in exhaustive draw.

Reset to 0 when: non-East player wins.

Honba does NOT apply to noten payments at exhaustive draw.

---

## Exhaustive draw

Noten payments total 3,000 pts split among noten players, paid to tenpai players.

Renchan (dealer stays): East tenpai at draw OR East wins a hand.

Wind rotates when: non-East wins OR East is noten at exhaustive draw.

Riichi deposits stay on the table after an exhaustive draw (carry to next hand's winner).

---

## Riichi

Riichi deposit: 1,000 pts. Allowed even if score goes negative. **[≠ Tenhou: requires 1,000+ pts]**

Riichi requires at least 1 tile remaining in the live wall to draw.

If the riichi tile is won off by ron: deposit is NOT collected (returned). **[≠ Tenhou: deposit lost]**

Furiten riichi allowed.

Closed kan after riichi: allowed only if it does not change the wait AND the four tiles were not all in hand before the draw. Hand shape and yaku changes are irrelevant. **[≠ Tenhou: same rule, different phrasing]**

---

## Abortive draws

None. **[≠ Tenhou: 5 types allowed]**

---

## Nagashi mangan

Not allowed. **[≠ Tenhou: allowed]**

---

## Double ron

Not allowed — single winner by turn priority (closest to discarder wins, others forfeit). **[≠ Tenhou: double ron allowed]**

---

## Renhou (Blessing of Man)

Allowed. Worth 5 han (mangan value), not a yakuman. Not cumulative with other yaku — use the higher value if other yaku + dora score more. **[≠ Tenhou: not allowed]**

---

## Kuitan (open tanyao)

Allowed. Open tan'yao is a valid 1-han yaku.

---

## Swap-calling (kuikae)

Forbidden. Cannot call a tile and discard the same tile, or call a sequence and discard from its other end.

---

## Last tile

Last discard cannot be called for sequences, triplets, or quads. Can still win by ron.

---

## Pao (liability payment)

Applies to: Big Dragons (daisangen), Big Winds (daisuushi), Four Quads (suukantsu). **[≠ Tenhou: no pao on suukantsu]**

Condition: player fed the completing meld (visible triplet/quad that finishes the yakuman set).

On tsumo: liable player pays the full pao-yakuman value alone.

On ron: liable player and discarder split equally; discarder additionally pays honba.

If hand has multiple yakuman, liability only covers the pao-triggered one; other yakuman paid normally.

---

## Yaku list (han values — closed / open)

| Yaku                   | Closed | Open |
|------------------------|--------|------|
| Riichi                 | 1      | —    |
| Ippatsu                | 1      | —    |
| Menzen tsumo           | 1      | —    |
| Pinfu                  | 1      | —    |
| Iipeiko                | 1      | —    |
| Tanyao                 | 1      | 1    |
| Yakuhai (per honor)    | 1      | 1    |
| Chankan                | 1      | 1    |
| Rinshan kaihou         | 1      | 1    |
| Haitei                 | 1      | 1    |
| Hotei                  | 1      | 1    |
| Double riichi          | 2      | —    |
| Chitoitsu              | 2      | —    |
| Ittsu                  | 2      | 1    |
| Sanshoku dojun         | 2      | 1    |
| Sanshoku doko          | 2      | 2    |
| Toitoi                 | 2      | 2    |
| San ankou              | 2      | 2    |
| Sankantsu              | 2      | 2    |
| Chanta                 | 2      | 1    |
| Honroto                | 2      | 2    |
| Shosangen              | 2      | 2    |
| Ryanpeikou             | 3      | —    |
| Honitsu                | 3      | 2    |
| Junchan                | 3      | 2    |
| Renhou                 | 5      | —    |
| Chinitsu               | 6      | 5    |

Subsumptions: ryanpeikou removes iipeiko; junchan removes chanta; chinitsu removes honitsu.

---

## Yakuman list

| Yakuman        | Notes                                        |
|----------------|----------------------------------------------|
| Tenhou         | Dealer tsumo on initial draw                 |
| Chihou         | Non-dealer tsumo before any call             |
| Kokushimusou   | 13 orphans (wins on any completing tile)     |
| Chuurenpoutou  | 9-sided wait counts as standard; no quad     |
| Ryuuiisou      | Green tiles only; dragon not required        |
| Suuankou       | Tsumo OR tanki ron                           |
| Suukantsu      | 4 quads; pao applies                         |
| Chinroutou     | All terminals                                |
| Tsuisou        | All honours                                  |
| Daisangen      | 3 dragon sets; pao applies                   |
| Shousuushi     | 3 wind sets + wind pair                      |
| Daisuushi      | 4 wind sets; pao applies                     |

Multiple genuine yakuman stack. No double-yakuman variants.

---

## Key numbers

| Item                  | Value        |
|-----------------------|--------------|
| Starting points       | 30,000       |
| Return / target       | 30,000       |
| Uma                   | +15/+5/−5/−15|
| Riichi deposit        | 1,000        |
| Noten payment total   | 3,000        |
| Mangan basic          | 2,000        |
| Haneman basic         | 3,000        |
| Baiman basic          | 4,000        |
| Sanbaiman basic       | 6,000        |
| Yakuman basic         | 8,000        |
| Honba (ron)           | +300/counter |
| Honba (tsumo/payer)   | +100/counter |
| Double-wind pair      | 2 fu         |
| Red fives             | none         |
