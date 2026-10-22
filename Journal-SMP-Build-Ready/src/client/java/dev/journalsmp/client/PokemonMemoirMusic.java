package dev.journalsmp.client;

import dev.journalsmp.sound.JournalSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public final class PokemonMemoirMusic {
    private static MemoirLoop active;

    private PokemonMemoirMusic() {
    }

    public static void setPlaying(boolean playing) {
        Minecraft client = Minecraft.getInstance();
        if (playing) {
            if (active != null && client.getSoundManager().isActive(active)) {
                return;
            }
            stop();
            active = new MemoirLoop();
            client.getSoundManager().play(active);
        } else {
            stop();
        }
    }

    public static void stop() {
        if (active == null) {
            return;
        }
Minecraft.getInstance().getSoundManager().stop(active);
        Minecraft.getInstance().getSoundManager().stop(active);
        active = null;
    }

    private static final class MemoirLoop extends AbstractTickableSoundInstance {
        private MemoirLoop() {
            super(JournalSounds.POKEMON_MEMOIR_ELITE_FOUR, SoundSource.MUSIC, RandomSource.create());
            this.looping = true;
            this.relative = true;
            this.volume = 0.65F;
            this.pitch = 1.0F;
        }

        @Override
        public void tick() {
        }
    }
}
