package com.themahjong;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheMahjongTileSetTest {

    @Test
    void standardRiichiWithRedFivesBuildsExpectedWall() {
        TheMahjongTileSet tileSet = TheMahjongTileSet.standardRiichi(true);
        TheMahjongTile manFiveRed = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 5, true);
        TheMahjongTile manFive = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 5, false);
        TheMahjongTile pinFiveRed = new TheMahjongTile(TheMahjongTile.Suit.PINZU, 5, true);
        TheMahjongTile pinFive = new TheMahjongTile(TheMahjongTile.Suit.PINZU, 5, false);
        TheMahjongTile souFiveRed = new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 5, true);
        TheMahjongTile souFive = new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 5, false);

        List<TheMahjongTile> wall = tileSet.createOrderedWall();

        assertEquals(136, wall.size());
        assertEquals(37, tileSet.tiles().size());
        assertEquals(1, tileSet.copiesPerTile().get(manFiveRed));
        assertEquals(3, tileSet.copiesPerTile().get(manFive));
        assertEquals(1, tileSet.copiesPerTile().get(pinFiveRed));
        assertEquals(3, tileSet.copiesPerTile().get(pinFive));
        assertEquals(1, tileSet.copiesPerTile().get(souFiveRed));
        assertEquals(3, tileSet.copiesPerTile().get(souFive));
    }

    @Test
    void standardRiichiWithoutRedFivesKeepsThirtyFourTileDefinitions() {
        TheMahjongTileSet tileSet = TheMahjongTileSet.standardRiichi(false);

        assertEquals(34, tileSet.tiles().size());
        assertEquals(136, tileSet.createOrderedWall().size());
    }

    @Test
    void orderedWallReusesTileDefinitionsForCopies() {
        TheMahjongTileSet tileSet = TheMahjongTileSet.standardRiichi(false);

        List<TheMahjongTile> wall = tileSet.createOrderedWall();
        TheMahjongTile firstTileDefinition = tileSet.tiles().get(0);

        assertSame(firstTileDefinition, wall.get(0));
        assertSame(firstTileDefinition, wall.get(1));
        assertSame(firstTileDefinition, wall.get(2));
        assertSame(firstTileDefinition, wall.get(3));
    }

    @Test
    void shuffledWallUsesExplicitRandomSource() {
        TheMahjongTileSet tileSet = TheMahjongTileSet.standardRiichi(false);

        List<TheMahjongTile> orderedWall = tileSet.createOrderedWall();
        List<TheMahjongTile> shuffledWall = tileSet.createShuffledWall(new Random(1234L));

        assertEquals(orderedWall.size(), shuffledWall.size());
        assertNotEquals(orderedWall, shuffledWall);
    }

    @Test
    void shuffledWallRejectsNullRandom() {
        TheMahjongTileSet tileSet = TheMahjongTileSet.standardRiichi(false);

        assertThrows(IllegalArgumentException.class, () -> tileSet.createShuffledWall(null));
    }

    @Test
    void redDoraTileIsRepresentedAsDistinctTileDefinition() {
        TheMahjongTileSet tileSet = TheMahjongTileSet.standardRiichi(true);
        TheMahjongTile expectedRedFive = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 5, true);

        TheMahjongTile redFive = tileSet.tiles().stream()
                .filter(tile -> tile.equals(expectedRedFive))
                .findFirst()
                .orElseThrow();

        assertTrue(redFive.redDora());
        assertFalse(redFive.honor());
        assertFalse(redFive.terminal());
        assertEquals(TheMahjongTile.Suit.MANZU, redFive.suit());
        assertEquals(5, redFive.rank());
    }

    @Test
    void honorTilesUseSeparateWindAndDragonSuits() {
        TheMahjongTileSet tileSet = TheMahjongTileSet.standardRiichi(false);
        TheMahjongTile eastTile = new TheMahjongTile(TheMahjongTile.Suit.WIND, 1, false);
        TheMahjongTile whiteTile = new TheMahjongTile(TheMahjongTile.Suit.DRAGON, 1, false);

        TheMahjongTile east = tileSet.tiles().stream()
                .filter(tile -> tile.equals(eastTile))
                .findFirst()
                .orElseThrow();
        TheMahjongTile white = tileSet.tiles().stream()
                .filter(tile -> tile.equals(whiteTile))
                .findFirst()
                .orElseThrow();

        assertEquals(TheMahjongTile.Suit.WIND, east.suit());
        assertEquals(TheMahjongTile.Suit.DRAGON, white.suit());
        assertTrue(east.honor());
        assertTrue(white.honor());
        assertFalse(east.terminal());
        assertFalse(white.terminal());
    }

    @Test
    void equalsAndHashCode() {
        TheMahjongTileSet a = TheMahjongTileSet.standardRiichi(false);
        TheMahjongTileSet b = TheMahjongTileSet.standardRiichi(false);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void withRedFivesNotEqualToWithout() {
        assertNotEquals(
                TheMahjongTileSet.standardRiichi(true),
                TheMahjongTileSet.standardRiichi(false));
    }

    @Test
    void toStringContainsTileKindCount() {
        assertTrue(TheMahjongTileSet.standardRiichi(false).toString().contains("34"));
    }

    @Test
    void constructorRejectsDuplicateTiles() {
        TheMahjongTile tile = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);
        TheMahjongTile other = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 2, false);
        // tiles=[A,A] with copies={A:4, B:2} — sizes match but A is a duplicate and B is an orphan
        assertThrows(IllegalArgumentException.class, () ->
                new TheMahjongTileSet(java.util.Arrays.asList(tile, tile), java.util.Map.of(tile, 4, other, 4)));
    }

    @Test
    void constructorRejectsOrphanMapEntries() {
        TheMahjongTile inList = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);
        TheMahjongTile orphan = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 2, false);
        assertThrows(IllegalArgumentException.class, () ->
                new TheMahjongTileSet(List.of(inList), Map.of(inList, 4, orphan, 4)));
    }

    @Test
    void suitedOneAndNineTilesAreTerminal() {
        TheMahjongTileSet tileSet = TheMahjongTileSet.standardRiichi(false);
        TheMahjongTile oneManTile = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);
        TheMahjongTile nineSouTile = new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 9, false);

        TheMahjongTile oneMan = tileSet.tiles().stream()
                .filter(tile -> tile.equals(oneManTile))
                .findFirst()
                .orElseThrow();
        TheMahjongTile nineSou = tileSet.tiles().stream()
                .filter(tile -> tile.equals(nineSouTile))
                .findFirst()
                .orElseThrow();

        assertTrue(oneMan.terminal());
        assertTrue(nineSou.terminal());
    }
}
