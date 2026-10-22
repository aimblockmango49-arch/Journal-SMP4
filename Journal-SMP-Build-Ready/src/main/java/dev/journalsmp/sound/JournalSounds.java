package dev.journalsmp.sound;

import dev.journalsmp.JournalSmpMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class JournalSounds {
    public static final SoundEvent LAVENDER_TOWN = register("lavender_town_snippet");
    public static final SoundEvent DREADWALKER_NIGHT = register("dreadwalker_night");
    public static final SoundEvent POISON = register("poison");
    public static final SoundEvent LEFTOVERS = register("leftovers_healing");
    public static final SoundEvent POKEMON_CENTER = register("pokemon_center_healing");
    public static final SoundEvent HYPER_BEAM = register("hyper_beam");
    public static final SoundEvent SWIFT = register("swift");
    public static final SoundEvent SWORDS_DANCE = register("swords_dance");
    public static final SoundEvent PROTECT = register("protect");
    public static final SoundEvent CRITICAL_HIT = register("critical_hit");
    public static final SoundEvent NIGHT_SHADE = register("night_shade");
    public static final SoundEvent PAY_DAY = register("pay_day");
    public static final SoundEvent LEVEL_UP = register("level_up");
    public static final SoundEvent IRON_DEFENSE = register("iron_defense");
    public static final SoundEvent THUNDER = register("thunder");
    public static final SoundEvent POKEMON_MEMOIR_ELITE_FOUR = register("pokemon_memoir_elite_four");

    private JournalSounds() {
    }

    public static void register() {
        // Static initialization registers every sound event.
    }

    private static SoundEvent register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(JournalSmpMod.MOD_ID, name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }
}
