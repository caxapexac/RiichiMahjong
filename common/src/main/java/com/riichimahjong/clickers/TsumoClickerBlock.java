package com.riichimahjong.clickers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.riichimahjong.yakugenerator.YakuGeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class TsumoClickerBlock extends ClickerBlock {

    public static final MapCodec<TsumoClickerBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    propertiesCodec()
            ).apply(instance, TsumoClickerBlock::new));

    public TsumoClickerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<TsumoClickerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void performClick(ServerLevel level, BlockPos pos, YakuGeneratorBlockEntity target) {
        target.tsumoAutomated();
    }
}
