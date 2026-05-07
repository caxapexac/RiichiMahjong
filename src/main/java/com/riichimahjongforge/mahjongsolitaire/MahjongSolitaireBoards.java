package com.riichimahjongforge.mahjongsolitaire;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;

/**
 * Lazy classpath loader for the boards JSON shipped at
 * {@code /data/riichi_mahjong_forge/solitaire/boards.json}. The first call parses
 * and caches; subsequent calls return the cached list.
 *
 * <p>The on-disk schema is a single JSON array of board objects. Each board:
 * <pre>
 * { "id": "...", "name": "...", "cat": "...",
 *   "map": [ [layer, [ [rowZ, items], ... ]], ... ] }
 * </pre>
 *
 * <p>{@code items} is a permissive grammar:
 * <ul>
 *   <li>An int <i>n</i> — single tile at X=<i>n</i>.</li>
 *   <li>An array of items — each element is itself an int (single) or a 2-int
 *       array {@code [startX, length]} representing a run of {@code length} tiles
 *       starting at {@code X=startX}, stepping by {@link MahjongSolitaireBoard#TILE_GRID}.</li>
 *   <li>The {@code items} value itself may be a bare int (shorthand for a single
 *       tile in the row) — observed in some boards.</li>
 * </ul>
 *
 * <p>Tile codes (which suit / rank goes in each slot) are <b>not</b> in the file;
 * those are assigned at runtime by {@link MahjongSolitaireBlockEntity} when a
 * board is loaded into a table.
 */
public final class MahjongSolitaireBoards {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESOURCE_PATH =
            "/data/riichi_mahjong_forge/solitaire/boards.json";

    private static List<MahjongSolitaireBoard> cached;

    private MahjongSolitaireBoards() {}

    /** All boards, parsed once. Empty list if the resource is missing or malformed. */
    public static synchronized List<MahjongSolitaireBoard> all() {
        if (cached == null) {
            cached = loadFromClasspath();
        }
        return cached;
    }

    private static List<MahjongSolitaireBoard> loadFromClasspath() {
        try (InputStream in = MahjongSolitaireBoards.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                LOGGER.warn("solitaire boards resource not found at {}", RESOURCE_PATH);
                return Collections.emptyList();
            }
            JsonElement root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            if (!root.isJsonArray()) {
                LOGGER.warn("solitaire boards root is not a JSON array");
                return Collections.emptyList();
            }
            ArrayList<MahjongSolitaireBoard> out = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray()) {
                MahjongSolitaireBoard board = parseBoard(e.getAsJsonObject());
                if (board != null) {
                    out.add(board);
                }
            }
            LOGGER.info("loaded {} solitaire boards", out.size());
            return Collections.unmodifiableList(out);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("failed to load solitaire boards", e);
            return Collections.emptyList();
        }
    }

    private static MahjongSolitaireBoard parseBoard(JsonObject obj) {
        String id = optString(obj, "id", "");
        String name = optString(obj, "name", id);
        String cat = optString(obj, "cat", "");
        if (!obj.has("map") || !obj.get("map").isJsonArray()) {
            return null;
        }
        ArrayList<MahjongSolitaireBoard.Slot> slots = new ArrayList<>();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int maxLayer = 0;
        for (JsonElement layerElem : obj.getAsJsonArray("map")) {
            JsonArray layer = layerElem.getAsJsonArray();
            int y = layer.get(0).getAsInt();
            maxLayer = Math.max(maxLayer, y);
            JsonElement rowsElem = layer.get(1);
            if (!rowsElem.isJsonArray()) {
                continue;
            }
            for (JsonElement rowElem : rowsElem.getAsJsonArray()) {
                JsonArray row = rowElem.getAsJsonArray();
                int z = row.get(0).getAsInt();
                JsonElement items = row.get(1);
                parseRowItems(items, y, z, slots);
            }
        }
        for (var s : slots) {
            if (s.x() < minX) minX = s.x();
            if (s.x() > maxX) maxX = s.x();
            if (s.z() < minZ) minZ = s.z();
            if (s.z() > maxZ) maxZ = s.z();
        }
        if (slots.isEmpty()) {
            return null;
        }
        return new MahjongSolitaireBoard(
                id, name, cat, List.copyOf(slots), minX, maxX, minZ, maxZ, maxLayer);
    }

    private static void parseRowItems(
            JsonElement items, int y, int z, List<MahjongSolitaireBoard.Slot> sink) {
        // Shorthand: items is itself a single integer.
        if (items.isJsonPrimitive()) {
            sink.add(new MahjongSolitaireBoard.Slot(items.getAsInt(), y, z));
            return;
        }
        if (!items.isJsonArray()) {
            return;
        }
        for (JsonElement item : items.getAsJsonArray()) {
            if (item.isJsonPrimitive()) {
                sink.add(new MahjongSolitaireBoard.Slot(item.getAsInt(), y, z));
            } else if (item.isJsonArray()) {
                JsonArray run = item.getAsJsonArray();
                if (run.size() < 2) continue;
                int startX = run.get(0).getAsInt();
                int length = run.get(1).getAsInt();
                for (int i = 0; i < length; i++) {
                    sink.add(new MahjongSolitaireBoard.Slot(
                            startX + i * MahjongSolitaireBoard.TILE_GRID, y, z));
                }
            }
        }
    }

    private static String optString(JsonObject obj, String key, String dflt) {
        return obj.has(key) && obj.get(key).isJsonPrimitive()
                ? obj.get(key).getAsString() : dflt;
    }
}
