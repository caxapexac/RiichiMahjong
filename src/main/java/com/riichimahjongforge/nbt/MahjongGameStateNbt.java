package com.riichimahjongforge.nbt;

import com.mahjongcore.MahjongGameState;
import com.mahjongcore.rules.ClaimWindowRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class MahjongGameStateNbt {

    private MahjongGameStateNbt() {
    }

    public static void save(MahjongGameState gameState, CompoundTag tag) {
        tag.putInt("HandNo", gameState.handNumber);
        tag.putInt("Honba", gameState.honba);
        tag.putInt("RiichiPot", gameState.riichiPot);
        tag.putInt("TurnSeat", gameState.currentTurnSeat);
        tag.putByte("TPhase", (byte) gameState.phase.ordinal());
        tag.putInt("LastDraw", gameState.lastDrawnCode);
        tag.putBoolean("LastDrawRinshan", gameState.lastDrawWasRinshan);
        tag.putBoolean("ScoreAsNotFirstRound", gameState.scoreAsNotFirstRound);
        tag.putInt("ClDisc", gameState.claimDiscarderSeat);
        tag.putInt("ClTile", gameState.claimTileCode);
        tag.putInt("ClNext", gameState.claimNextDrawerSeat);
        tag.putBoolean("ClIsChankan", gameState.claimIsChankanWindow);
        for (int s = 0; s < 4; s++) {
            tag.putByte("ClIn" + s, (byte) gameState.claimIntent[s].ordinal());
            tag.putInt("ClCxA" + s, gameState.claimChiTileA[s]);
            tag.putInt("ClCxB" + s, gameState.claimChiTileB[s]);
            tag.putBoolean("Riichi" + s, gameState.riichiDeclared[s]);
            tag.putBoolean("RiichiPending" + s, gameState.riichiPending[s]);
            tag.putBoolean("Ippatsu" + s, gameState.ippatsuEligible[s]);
            tag.putBoolean("TmpFuriten" + s, gameState.temporaryFuriten[s]);
            tag.putBoolean("RiichiFuriten" + s, gameState.riichiPermanentFuriten[s]);
            CompoundTag seen = new CompoundTag();
            for (int code = 0; code < 34; code++) {
                if (gameState.seenDiscardsBySeatAndTile[s][code]) {
                    seen.putBoolean("T" + code, true);
                }
            }
            tag.put("SeenDisc" + s, seen);
        }
    }

    public static MahjongGameState load(CompoundTag tag) {
        MahjongGameState gameState = new MahjongGameState();
        gameState.handNumber = tag.getInt("HandNo");
        gameState.honba = tag.getInt("Honba");
        gameState.riichiPot = tag.getInt("RiichiPot");
        gameState.currentTurnSeat = tag.getInt("TurnSeat");
        int tp = tag.getByte("TPhase") & 0xFF;
        MahjongGameState.TurnPhase[] vals = MahjongGameState.TurnPhase.values();
        gameState.phase = tp < vals.length ? vals[tp] : MahjongGameState.TurnPhase.MUST_DRAW;
        gameState.lastDrawnCode = tag.getInt("LastDraw");
        gameState.lastDrawWasRinshan = tag.getBoolean("LastDrawRinshan");
        gameState.scoreAsNotFirstRound = tag.getBoolean("ScoreAsNotFirstRound");
        gameState.claimDiscarderSeat = tag.contains("ClDisc") ? tag.getInt("ClDisc") : -1;
        gameState.claimTileCode = tag.contains("ClTile") ? tag.getInt("ClTile") : -1;
        gameState.claimNextDrawerSeat = tag.contains("ClNext") ? tag.getInt("ClNext") : -1;
        gameState.claimIsChankanWindow = tag.getBoolean("ClIsChankan");
        for (int s = 0; s < 4; s++) {
            if (tag.contains("ClIn" + s, Tag.TAG_BYTE)) {
                int o = tag.getByte("ClIn" + s) & 0xFF;
                ClaimWindowRules.ClaimIntent[] claimVals = ClaimWindowRules.ClaimIntent.values();
                gameState.claimIntent[s] = o < claimVals.length ? claimVals[o] : ClaimWindowRules.ClaimIntent.NONE;
            }
            gameState.claimChiTileA[s] = tag.getInt("ClCxA" + s);
            gameState.claimChiTileB[s] = tag.getInt("ClCxB" + s);
            gameState.riichiDeclared[s] = tag.getBoolean("Riichi" + s);
            gameState.riichiPending[s] = tag.getBoolean("RiichiPending" + s);
            gameState.ippatsuEligible[s] = tag.getBoolean("Ippatsu" + s);
            gameState.temporaryFuriten[s] = tag.getBoolean("TmpFuriten" + s);
            gameState.riichiPermanentFuriten[s] = tag.getBoolean("RiichiFuriten" + s);
            if (tag.contains("SeenDisc" + s, Tag.TAG_COMPOUND)) {
                CompoundTag seen = tag.getCompound("SeenDisc" + s);
                for (int code = 0; code < 34; code++) {
                    gameState.seenDiscardsBySeatAndTile[s][code] = seen.getBoolean("T" + code);
                }
            }
        }
        return gameState;
    }
}
