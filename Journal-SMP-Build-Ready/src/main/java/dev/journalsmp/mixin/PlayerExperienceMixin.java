package dev.journalsmp.mixin;

import dev.journalsmp.JournalItems;
import dev.journalsmp.JournalKind;
import dev.journalsmp.sound.JournalSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerExperienceMixin {
    @Unique
    private int journalsmp$levelBeforeExperience;

    @ModifyVariable(method = "giveExperiencePoints", at = @At("HEAD"), argsOnly = true)
    private int journalsmp$doubleExperienceWithMemoir(int amount) {
        Player player = (Player) (Object) this;
        return JournalItems.kindOf(player.getOffhandItem()) == JournalKind.POKEMON ? amount * 2 : amount;
    }

    @Inject(method = "giveExperiencePoints", at = @At("HEAD"))
    private void journalsmp$rememberExperienceLevel(int amount, CallbackInfo ci) {
        journalsmp$levelBeforeExperience = ((Player) (Object) this).experienceLevel;
    }

    @Inject(method = "giveExperiencePoints", at = @At("TAIL"))
    private void journalsmp$playSoundOnLevelUp(int amount, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (player instanceof ServerPlayer serverPlayer
                && JournalItems.kindOf(player.getOffhandItem()) == JournalKind.POKEMON
                && player.experienceLevel > journalsmp$levelBeforeExperience) {
serverPlayer.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    JournalSounds.LEVEL_UP, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }
}
