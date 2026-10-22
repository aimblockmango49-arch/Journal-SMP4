
package dev.journalsmp.mixin;

import dev.journalsmp.JournalItems;
import dev.journalsmp.JournalKind;
import dev.journalsmp.sound.JournalSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class PlayerExtraSaturationMixin {

    @Inject(method = "finishUsingItem", at = @At("TAIL"))
    private void journalsmp$grantExtraSaturation(
            ItemStack stack,
            Level level,
            LivingEntity user,
            CallbackInfoReturnable<ItemStack> callback
    ) {
        if (level.isClientSide() || !(user instanceof ServerPlayer player)) {
            return;
        }

        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) {
            return;
        }

        JournalKind held = JournalItems.kindOf(player.getOffhandItem());
        if (held == JournalKind.ARBY) {
            float extraSaturation = food.nutrition() * food.saturation();
            var foodData = player.getFoodData();

            foodData.setSaturation(
                    Math.min(
                            foodData.getFoodLevel(),
                            foodData.getSaturationLevel() + extraSaturation
                    )
            );
            player.playSound(JournalSounds.LEFTOVERS, 0.65F, 1.0F);
        } else if (held == JournalKind.POKEMON) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.REGENERATION, 5 * 20, 0, false, true, true));
        }
    }
}

