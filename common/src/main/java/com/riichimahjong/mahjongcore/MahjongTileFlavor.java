package com.riichimahjong.mahjongcore;

import com.themahjong.TheMahjongTile;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Builds the per-tile {@link Item.Properties} (food, attack-damage, etc.) used
 * when registering tile {@code BlockItem}s. Keeps the food/effect/damage table
 * in one place instead of scattering it across the registration loop.
 *
 * <p>Tile gimmicks (added in 0.4.0):
 * <ul>
 *   <li>Eatable: hunger scales with rank for numbered suits; honors restore more.</li>
 *   <li>Effects on eat: man → Strength, pin → Resistance, sou → Speed, with
 *       higher ranks granting longer durations and amplifier II at 7–9.
 *       Honors give thematic effects (winds → environmental, dragons → buffs).</li>
 *   <li>Pin tiles deal attack damage equal to their rank (rank 1 = 1 dmg ... 9 = 9 dmg)
 *       at sword-paced attack speed (1.6/s). Pin-5-aka deals 6 dmg.</li>
 * </ul>
 *
 * <p>Sou fuel registration is in {@link MahjongTileFuels} (Architectury
 * {@code FuelRegistry} doesn't go through {@code Item.Properties}).
 */
public final class MahjongTileFlavor {
    private MahjongTileFlavor() {}

    private static final ResourceLocation PIN_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath("riichi_mahjong", "pin_attack_damage");
    private static final ResourceLocation PIN_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath("riichi_mahjong", "pin_attack_speed");
    private static final double SWORD_ATTACK_SPEED = 1.6;
    private static final double PLAYER_BASE_ATTACK_SPEED = 4.0;
    private static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0;

    private static final int SECOND_TICKS = 20;
    private static final int MINUTE_TICKS = 60 * SECOND_TICKS;

    /** Returns the {@link Item.Properties} for the given tile code. */
    public static Item.Properties propertiesForCode(int code) {
        Item.Properties properties = new Item.Properties().stacksTo(1);
        properties.food(foodForCode(code));
        if (isPinCode(code)) {
            int rank = pinRank(code);
            properties.attributes(pinAttackModifiers(rank));
        }
        return properties;
    }

    // ---- food / effects ---------------------------------------------------

    private static FoodProperties foodForCode(int code) {
        // alwaysEdible(): tiles can be eaten even at full hunger, like golden
        // apples — they're more about the effects than the nutrition.
        FoodProperties.Builder builder = new FoodProperties.Builder().alwaysEdible();
        if (code == MahjongTileItems.CODE_MAN_5_AKA) {
            applyNumberedFood(builder, TheMahjongTile.Suit.MANZU, 5);
            builder.effect(new MobEffectInstance(MobEffects.LUCK, MINUTE_TICKS, 0), 1.0f);
            return builder.build();
        }
        if (code == MahjongTileItems.CODE_PIN_5_AKA) {
            applyNumberedFood(builder, TheMahjongTile.Suit.PINZU, 5);
            builder.effect(new MobEffectInstance(MobEffects.LUCK, MINUTE_TICKS, 0), 1.0f);
            return builder.build();
        }
        if (code == MahjongTileItems.CODE_SOU_5_AKA) {
            applyNumberedFood(builder, TheMahjongTile.Suit.SOUZU, 5);
            builder.effect(new MobEffectInstance(MobEffects.LUCK, MINUTE_TICKS, 0), 1.0f);
            return builder.build();
        }

        // Standard 0..33: codes encode (suit, rank). See MahjongTileItems.codeForSuitRank.
        if (code >= 0 && code <= 8) {
            applyNumberedFood(builder, TheMahjongTile.Suit.MANZU, code + 1);
        } else if (code >= 9 && code <= 17) {
            applyNumberedFood(builder, TheMahjongTile.Suit.PINZU, code - 9 + 1);
        } else if (code >= 18 && code <= 26) {
            applyNumberedFood(builder, TheMahjongTile.Suit.SOUZU, code - 18 + 1);
        } else if (code >= 27 && code <= 30) {
            applyWindFood(builder, code - 27);
        } else if (code >= 31 && code <= 33) {
            applyDragonFood(builder, code - 31);
        } else {
            // Unknown code — ship a tiny food just so the item is well-formed.
            builder.nutrition(1).saturationModifier(0.1f);
        }
        return builder.build();
    }

    private static void applyNumberedFood(FoodProperties.Builder builder,
                                          TheMahjongTile.Suit suit, int rank) {
        int hunger = (rank + 1) / 2;            // 1,1,2,2,3,3,4,4,5
        float saturation = (float) (0.3 * rank); // 0.3 .. 2.7
        builder.nutrition(hunger).saturationModifier(saturation);

        Holder<MobEffect> effect = switch (suit) {
            case MANZU -> MobEffects.DAMAGE_BOOST;     // Strength
            case PINZU -> MobEffects.DAMAGE_RESISTANCE; // Resistance
            case SOUZU -> MobEffects.MOVEMENT_SPEED;    // Speed
            default -> null;
        };
        if (effect == null) return;

        int amplifier = rank >= 7 ? 1 : 0;        // II at 7–9, otherwise I
        int duration;
        if (rank <= 3) duration = 5 * SECOND_TICKS * rank;   // 5/10/15 s
        else if (rank <= 6) duration = 30 * SECOND_TICKS;    // 30 s
        else duration = MINUTE_TICKS;                        // 60 s
        builder.effect(new MobEffectInstance(effect, duration, amplifier), 1.0f);
    }

    private static void applyWindFood(FoodProperties.Builder builder, int windIdx) {
        builder.nutrition(5).saturationModifier(4.0f);
        Holder<MobEffect> effect = switch (windIdx) {
            case 0 -> MobEffects.SLOW_FALLING;     // East
            case 1 -> MobEffects.FIRE_RESISTANCE;  // South
            case 2 -> MobEffects.WATER_BREATHING;  // West
            case 3 -> MobEffects.CONDUIT_POWER;    // North
            default -> null;
        };
        if (effect != null) {
            builder.effect(new MobEffectInstance(effect, MINUTE_TICKS, 0), 1.0f);
        }
    }

    private static void applyDragonFood(FoodProperties.Builder builder, int dragonIdx) {
        switch (dragonIdx) {
            case 0 -> { // White (Haku)
                builder.nutrition(6).saturationModifier(5.0f);
                builder.effect(new MobEffectInstance(MobEffects.REGENERATION, MINUTE_TICKS, 0), 1.0f);
            }
            case 1 -> { // Green (Hatsu)
                builder.nutrition(7).saturationModifier(6.0f);
                builder.effect(new MobEffectInstance(MobEffects.LUCK, 2 * MINUTE_TICKS, 0), 1.0f);
            }
            case 2 -> { // Red (Chun)
                builder.nutrition(8).saturationModifier(7.0f);
                builder.effect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MINUTE_TICKS, 1), 1.0f);
                builder.effect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MINUTE_TICKS, 0), 1.0f);
            }
            default -> builder.nutrition(1).saturationModifier(0.1f);
        }
    }

    // ---- pin attack damage ------------------------------------------------

    private static boolean isPinCode(int code) {
        return (code >= 9 && code <= 17) || code == MahjongTileItems.CODE_PIN_5_AKA;
    }

    /** Pin rank 1..9 from the tile code (aka-5 collapses to rank 5). */
    private static int pinRank(int code) {
        if (code == MahjongTileItems.CODE_PIN_5_AKA) return 5;
        return code - 9 + 1;
    }

    private static ItemAttributeModifiers pinAttackModifiers(int rank) {
        // Total damage = rank, total attack speed = SWORD_ATTACK_SPEED.
        // Modifiers are ADD_VALUE on top of player base attributes.
        double damageBonus = rank - PLAYER_BASE_ATTACK_DAMAGE;
        double speedBonus = SWORD_ATTACK_SPEED - PLAYER_BASE_ATTACK_SPEED;
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(PIN_DAMAGE_ID, damageBonus,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(PIN_SPEED_ID, speedBonus,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }
}
