package com.themahjong;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheMahjongPlayerTest {

    @Test
    void handIsClosedWhenPlayerHasNoOpenMelds() {
        TheMahjongPlayer player = TheMahjongPlayer.initial(25000);

        assertFalse(player.handOpen());
    }

    @Test
    void handIsOpenWhenPlayerHasAnyOpenMeld() {
        TheMahjongTile chiA = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 2, false);
        TheMahjongTile chiB = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 3, false);
        TheMahjongTile chiC = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 4, false);

        TheMahjongPlayer player = new TheMahjongPlayer(
                25000,
                TheMahjongPlayer.RiichiState.NONE,
                false,
                List.of(),
                List.of(new TheMahjongMeld.Chi(List.of(chiA, chiB, chiC), 0, 1, 0)),
                List.of(),
                List.of());

        assertTrue(player.handOpen());
    }

    @Test
    void equalsAndHashCodeForEqualPlayers() {
        TheMahjongPlayer a = TheMahjongPlayer.initial(25000);
        TheMahjongPlayer b = TheMahjongPlayer.initial(25000);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equalityDistinguishesByPoints() {
        assertNotEquals(TheMahjongPlayer.initial(25000), TheMahjongPlayer.initial(30000));
    }

    @Test
    void equalityDistinguishesByRiichiState() {
        TheMahjongPlayer none = TheMahjongPlayer.initial(25000);
        TheMahjongPlayer riichi = new TheMahjongPlayer(
                25000,
                TheMahjongPlayer.RiichiState.RIICHI,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of());
        assertNotEquals(none, riichi);
    }

    @Test
    void toStringContainsPoints() {
        assertTrue(TheMahjongPlayer.initial(25000).toString().contains("25000"));
    }

    @Test
    void playerCopiesCurrentHandInput() {
        TheMahjongTile tile = new TheMahjongTile(TheMahjongTile.Suit.PINZU, 7, false);
        List<TheMahjongTile> hand = new java.util.ArrayList<>(List.of(tile));

        TheMahjongPlayer player = new TheMahjongPlayer(
                25000,
                TheMahjongPlayer.RiichiState.NONE,
                false,
                hand,
                List.of(),
                List.of(),
                List.of());

        hand.clear();

        assertEquals(1, player.currentHand().size());
        assertEquals(tile, player.currentHand().get(0));
    }
}
