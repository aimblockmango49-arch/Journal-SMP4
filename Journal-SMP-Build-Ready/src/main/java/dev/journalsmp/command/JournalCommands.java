package dev.journalsmp.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.journalsmp.JournalKind;
import dev.journalsmp.ability.AbilityManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.level.ServerPlayer;

public final class JournalCommands {
    private JournalCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ability")
                .executes(context -> showOverview(context.getSource()))
                .then(Commands.literal("eino").executes(context -> showJournal(context.getSource(), JournalKind.EINO)))
                .then(Commands.literal("einos").executes(context -> showJournal(context.getSource(), JournalKind.EINO)))
                .then(Commands.literal("arby").executes(context -> showJournal(context.getSource(), JournalKind.ARBY)))
                .then(Commands.literal("arbys").executes(context -> showJournal(context.getSource(), JournalKind.ARBY)))
                .then(Commands.literal("journal").executes(context -> showJournal(context.getSource(), JournalKind.JOURNAL)))
                .then(Commands.literal("dexzyn").executes(context -> showJournal(context.getSource(), JournalKind.DEXZYN)))
                .then(Commands.literal("dexzyns").executes(context -> showJournal(context.getSource(), JournalKind.DEXZYN)))
                .then(Commands.literal("ace").executes(context -> showJournal(context.getSource(), JournalKind.ACE)))
                .then(Commands.literal("vanilte").executes(context -> showJournal(context.getSource(), JournalKind.VANILTE)))
                .then(Commands.literal("nilli").executes(context -> showJournal(context.getSource(), JournalKind.NILLI))));
        dispatcher.register(Commands.literal("mode")
                .then(Commands.argument("mode", IntegerArgumentType.integer(1, 3))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            AbilityManager.selectPokemonMode(player,
                                    IntegerArgumentType.getInteger(context, "mode"));
                            return 1;
                        })));
    }

    private static int showOverview(CommandSourceStack source) {
        line(source, "JOURNAL SMP — ABILITIES", ChatFormatting.GOLD);
        line(source, "Eight unique journals grant powers only while held in the off-hand.", ChatFormatting.GRAY);
        line(source, "/ability eino — Eino's Playbook (trading and villagers)", ChatFormatting.GREEN);
        line(source, "/ability arby — Arby's Menu (food and survival)", ChatFormatting.GOLD);
        line(source, "/ability journal — Journal's Journal (speed and movement)", ChatFormatting.AQUA);
        line(source, "/ability dexzyn — Dexzyn's AFK Log (idleness and impact)", ChatFormatting.BLUE);
        line(source, "/ability ace — Ace's Blueprint (building and defense)", ChatFormatting.DARK_AQUA);
        line(source, "/ability vanilte — Vanilte's Horror Game (night and mob avoidance)", ChatFormatting.DARK_RED);
        line(source, "/ability nilli — Nilli's Space Book (feathers, lightning, and star projectiles)", ChatFormatting.LIGHT_PURPLE);
        line(source, "/ability pokemon — Pokémon's Memoir (experience and three ultimate modes)", ChatFormatting.LIGHT_PURPLE);
        return 1;
    }

    private static int showJournal(CommandSourceStack source, JournalKind kind) {
        switch (kind) {
            case EINO -> {
                line(source, "EINO'S PLAYBOOK", ChatFormatting.GREEN);
                line(source, "PASSIVE 1 — MASTER TRADER", ChatFormatting.GOLD);
                line(source, "Permanent Hero of the Village III.", ChatFormatting.GRAY);
                line(source, "PASSIVE 2 — VILLAGE GUARDIAN", ChatFormatting.GOLD);
                line(source, "Gain Resistance II while a villager is within 5 blocks.", ChatFormatting.GRAY);
                line(source, "ULTIMATE — BANKRUPTCY", ChatFormatting.RED);
                line(source, "Arm it, then land an unblocked melee hit on a player to disable their actions for 10 seconds.", ChatFormatting.GRAY);
                line(source, "A shield-blocked hit does not consume the ultimate. The arm expires after 10 seconds; cooldown begins on a successful hit.", ChatFormatting.GRAY);
                line(source, "COOLDOWN: 4 MINUTES after a successful hit. " + offhandText(), ChatFormatting.YELLOW);
            }
            case ARBY -> {
                line(source, "ARBY'S MENU", ChatFormatting.GOLD);
                line(source, "PASSIVE 1 — FOOD POISONING", ChatFormatting.GOLD);
                line(source, "Successful hits inflict Poison I on the target for 3 seconds.", ChatFormatting.GRAY);
                line(source, "PASSIVE 2 — EXTRA CALORIES", ChatFormatting.GOLD);
                line(source, "Food eaten grants 1.5 times normal saturation; hunger points are unchanged.", ChatFormatting.GRAY);
                line(source, "ULTIMATE — ALL YOU CAN EAT", ChatFormatting.RED);
                line(source, "Health Boost III for 60 seconds and Regeneration III for 10 seconds.", ChatFormatting.GRAY);
                line(source, "COOLDOWN: 3 MINUTES. " + offhandText(), ChatFormatting.YELLOW);
            }
            case JOURNAL -> {
                line(source, "JOURNAL'S JOURNAL", ChatFormatting.AQUA);
                line(source, "PASSIVE 1 — SPEEDRUNNER", ChatFormatting.GOLD);
                line(source, "Permanent Speed I while held in the off-hand.", ChatFormatting.GRAY);
                line(source, "PASSIVE 2 — BLINDSIDE", ChatFormatting.GOLD);
                line(source, "Successful axe hits on eligible entities inflict Blindness for 10 seconds.", ChatFormatting.GRAY);
                line(source, "ULTIMATE — WORLD RECORD", ChatFormatting.RED);
                line(source, "Speed IV for 60 seconds. Ends when the journal leaves your off-hand.", ChatFormatting.GRAY);
                line(source, "COOLDOWN: 3 MINUTES. " + offhandText(), ChatFormatting.YELLOW);
            }
            case DEXZYN -> {
                line(source, "DEXZYN'S AFK LOG", ChatFormatting.BLUE);
                line(source, "PASSIVE 1 — IDLE PROTECTION", ChatFormatting.GOLD);
                line(source, "After 5 seconds of stillness, gain Resistance I for 3 seconds once; moving resets the timer.", ChatFormatting.GRAY);
                line(source, "PASSIVE 2 — SWORD LOOTING", ChatFormatting.GOLD);
                line(source, "On sword kills, each mob-drop stack has a 50% chance to grant one extra item. This bonus is disabled if the sword has Looting.", ChatFormatting.GRAY);
                line(source, "ULTIMATE — BERSERKER MODE", ChatFormatting.RED);
                line(source, "Strength IV for 20 seconds.", ChatFormatting.GRAY);
                line(source, "COOLDOWN: 3 MINUTES. " + offhandText(), ChatFormatting.YELLOW);
            }
            case ACE -> {
                line(source, "ACE'S BLUEPRINT", ChatFormatting.DARK_AQUA);
                line(source, "PASSIVE 1 — MASTER BUILDER", ChatFormatting.GOLD);
                line(source, "Permanent Haste II while held in the off-hand.", ChatFormatting.GRAY);
                line(source, "PASSIVE 2 — EXTENDED REACH", ChatFormatting.GOLD);
                line(source, "Place blocks up to one block farther away. Mining and entity range are unchanged; container blocks cannot be reached remotely.", ChatFormatting.GRAY);
                line(source, "ULTIMATE — MASTERPIECE", ChatFormatting.RED);
                line(source, "Resistance V for 10 seconds; normal void-damage rules still apply.", ChatFormatting.GRAY);
                line(source, "COOLDOWN: 3 MINUTES. " + offhandText(), ChatFormatting.YELLOW);
            }
            case VANILTE -> {
                line(source, "VANILTE'S HORROR GAME", ChatFormatting.DARK_RED);
                line(source, "PASSIVE 1 — DREADWALKER", ChatFormatting.GOLD);
                line(source, "Gain Speed II during nighttime.", ChatFormatting.GRAY);
                line(source, "PASSIVE 2 — PREDATOR'S INSTINCT", ChatFormatting.GOLD);
                line(source, "Hostile mobs ignore you while the journal is in your off-hand.", ChatFormatting.GRAY);
                line(source, "ULTIMATE — JUMPSCARE", ChatFormatting.RED);
                line(source, "Look at an opposing player within 32 blocks to show them the supplied image for 20 seconds.", ChatFormatting.GRAY);
                line(source, "COOLDOWN: 3 MINUTES. " + offhandText(), ChatFormatting.YELLOW);
            }
            case NILLI -> {
                line(source, "NILLI'S SPACE BOOK", ChatFormatting.LIGHT_PURPLE);
                line(source, "PASSIVE 1 — FEATHERWEIGHT", ChatFormatting.GOLD);
                line(source, "A feather hit applies Slow Falling for 10 seconds.", ChatFormatting.GRAY);
                line(source, "PASSIVE 2 — COSMIC SMITE", ChatFormatting.GOLD);
                line(source, "A genuine critical axe hit summons a real lightning strike on the entity hit.", ChatFormatting.GRAY);
                line(source, "ULTIMATE — WILLI NILLI", ChatFormatting.RED);
                line(source, "Fire up to 20 star projectiles over 10 seconds (one every 10 ticks). Each deals 2 damage within 10 blocks, ignores shields, and stops at solid blocks.", ChatFormatting.GRAY);
                line(source, "COOLDOWN: 3 MINUTES. " + offhandText(), ChatFormatting.YELLOW);
            }
            case POKEMON -> {
                line(source, "POKÉMON'S MEMOIR", ChatFormatting.LIGHT_PURPLE);
                line(source, "PASSIVE 1 — EXP SHARE", ChatFormatting.GOLD);
                line(source, "Doubles experience points gained while this journal is in the off-hand; level-up sound plays only when your level increases.", ChatFormatting.GRAY);
                line(source, "PASSIVE 2 — TYPE ADVANTAGE", ChatFormatting.GOLD);
                line(source, "Strength after a hit, Haste after breaking a block, Resistance after damage, Fire Resistance when you catch fire, and Regeneration after eating.", ChatFormatting.GRAY);
                line(source, "ULTIMATE — THREE MODES", ChatFormatting.RED);
                line(source, "/mode 1 Origin Pulse + Thunderbolt; /mode 2 Verdant Oblivion; /mode 3 Protect + Draco Meteor.", ChatFormatting.GRAY);
                line(source, "Select a mode while holding the Memoir, then activate it with the journal key or off-hand use.", ChatFormatting.GRAY);
                line(source, "COOLDOWN: 3 MINUTES shared by all modes. " + offhandText(), ChatFormatting.YELLOW);
            }
        }
        return 1;
    }

    private static String offhandText() {
        return "Hold the journal in your off-hand.";
    }

    private static void line(CommandSourceStack source, String text, ChatFormatting color) {
        source.sendSuccess(() -> Component.literal(text).withStyle(color), false);
    }
}
