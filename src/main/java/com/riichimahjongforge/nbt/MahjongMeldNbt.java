package com.riichimahjongforge.nbt;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class MahjongMeldNbt {

    private MahjongMeldNbt() {}

    public static void save(CompoundTag tag, com.mahjongcore.MahjongMeld meld) {
        tag.putByte("K", (byte) meld.kind().ordinal());
        tag.putInt("F", meld.fromSeat());
        ListTag list = new ListTag();
        for (int c : meld.tileCodes()) {
            CompoundTag e = new CompoundTag();
            e.putByte("C", (byte) c);
            list.add(e);
        }
        tag.put("T", list);
    }

    public static com.mahjongcore.MahjongMeld load(CompoundTag tag) {
        int k = tag.getByte("K") & 0xFF;
        com.mahjongcore.MahjongMeld.Kind[] kinds = com.mahjongcore.MahjongMeld.Kind.values();
        com.mahjongcore.MahjongMeld.Kind kind = k < kinds.length ? kinds[k] : com.mahjongcore.MahjongMeld.Kind.PON;
        int from = tag.contains("F", Tag.TAG_INT) ? tag.getInt("F") : -1;
        ListTag list = tag.getList("T", Tag.TAG_COMPOUND);
        int[] tiles = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            tiles[i] = list.getCompound(i).getByte("C") & 0xFF;
        }
        return new com.mahjongcore.MahjongMeld(kind, tiles, from);
    }

    public static void saveMeldsList(CompoundTag tag, String key, List<com.mahjongcore.MahjongMeld> melds) {
        ListTag list = new ListTag();
        for (com.mahjongcore.MahjongMeld m : melds) {
            CompoundTag e = new CompoundTag();
            save(e, m);
            list.add(e);
        }
        tag.put(key, list);
    }

    public static void loadMeldsList(CompoundTag tag, String key, List<com.mahjongcore.MahjongMeld> out) {
        out.clear();
        if (!tag.contains(key, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            out.add(MahjongMeldNbt.load(list.getCompound(i)));
        }
    }
}

