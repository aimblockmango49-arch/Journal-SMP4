package dev.journalsmp.ability;

import dev.journalsmp.JournalItems;
import dev.journalsmp.JournalKind;
import dev.journalsmp.PokemonMemoirMode;
import dev.journalsmp.data.JournalSavedData;
import dev.journalsmp.network.CooldownSyncPayload;
import dev.journalsmp.network.JumpscarePayload;
import dev.journalsmp.network.PokemonMemoirMusicPayload;
import dev.journalsmp.particle.JournalParticles;
import dev.journalsmp.sound.JournalSounds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class AbilityManager {
    private static final long SECOND = 1_000L;
    private static final int NILLI_ULTIMATE_TICKS = 10 * 20;
    private static final int NILLI_FIRE_INTERVAL_TICKS = 10;
    private static final int NILLI_MAX_PROJECTILES = 20;
    private static final double NILLI_PROJECTILE_SPEED = 0.75;
    private static final double NILLI_PROJECTILE_RANGE = 10.0;
    private static final int POKEMON_MEMOIR_DURATION_TICKS = 20 * 20;
    private static final int POKEMON_METEOR_DURATION_TICKS = 10 * 20;
    private static final Map<UUID, PlayerState> PLAYER_STATES = new HashMap<>();
    private static final Map<UUID, TimedUltimate> ACTIVE_ULTIMATES = new HashMap<>();
    private static final Map<UUID, JumpscareState> ACTIVE_JUMPSCARES = new HashMap<>();
    private static final Map<UUID, Long> ACTION_LOCKS = new HashMap<>();
    private static long serverTick;

    private AbilityManager() {
    }

    public static void activateHeldUltimate(ServerPlayer player) {
        JournalKind kind = JournalItems.kindOf(player.getOffhandItem());
        if (kind == null) {
            tell(player, "Hold a journal in your off-hand to activate its ultimate.", ChatFormatting.RED);
            return;
        }
        activate(player, kind);
    }

    public static void activate(ServerPlayer player, JournalKind kind) {
        if (player.getOffhandItem().isEmpty() || JournalItems.kindOf(player.getOffhandItem()) != kind) {
            tell(player, "That journal must be in your off-hand.", ChatFormatting.RED);
            return;
        }
        if (isActionLocked(player)) {
            tell(player, "You cannot act while Bankruptcy is affecting you.", ChatFormatting.RED);
            return;
        }
        if (!player.isAlive() || player.isSpectator()) {
            tell(player, "You cannot activate this ultimate right now.", ChatFormatting.RED);
            return;
        }
        long remaining = cooldownRemaining(player, kind);
        if (remaining > 0L) {
            tell(player, kind.ultimateName() + " is on cooldown for " + formatDuration(remaining) + ".", ChatFormatting.RED);
            return;
        }

        if (kind == JournalKind.EINO) {
            PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
            if (state.bankruptcyUntilMillis > System.currentTimeMillis()) {
                tell(player, "Bankruptcy is already armed.", ChatFormatting.YELLOW);
                return;
            }
            state.bankruptcyUntilMillis = System.currentTimeMillis() + 10L * SECOND;
            tell(player, "Bankruptcy armed: your next unblocked melee hit against a player disables their actions for 10 seconds.", ChatFormatting.GOLD);
            return;
        }

        ServerPlayer jumpscareTarget = null;
        if (kind == JournalKind.VANILTE) {
            jumpscareTarget = findJumpscareTarget(player);
            if (jumpscareTarget == null) {
                tell(player, "Look directly at an opposing player within 32 blocks to use Jumpscare.", ChatFormatting.RED);
                return;
            }
        }

        long now = System.currentTimeMillis();
        PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
        if (kind == JournalKind.POKEMON) {
            activatePokemonMemoir(player, state, now);
            JournalSavedData.get(player.level().getServer()).setCooldown(player.getUUID(), kind,
                    now + kind.cooldownMillis());
            sendMemoirMusic(player, true);
            announce(player, kind);
            tell(player, state.pokemonMode.displayName() + " activated.", ChatFormatting.GREEN);
            return;
        }
        switch (kind) {
            case ARBY -> {
                addEffect(player, MobEffects.HEALTH_BOOST, 60 * 20, 2);
                addEffect(player, MobEffects.REGENERATION, 10 * 20, 2);
                ACTIVE_ULTIMATES.put(player.getUUID(), new TimedUltimate(kind, now + 60L * SECOND, now + 10L * SECOND));
                spawn(player, JournalParticles.ARBY, 22);
                playSound(player, JournalSounds.POKEMON_CENTER, 0.8F);
            }
            case JOURNAL -> {
                addEffect(player, MobEffects.SPEED, 60 * 20, 3);
                ACTIVE_ULTIMATES.put(player.getUUID(), new TimedUltimate(kind, now + 60L * SECOND, 0L));
                spawn(player, JournalParticles.JOURNAL, 18);
            }
            case DEXZYN -> {
                MobEffectInstance currentStrength = player.getEffect(MobEffects.STRENGTH);
                boolean grantStrength = currentStrength == null || currentStrength.getAmplifier() < 3;
                if (grantStrength) {
                    addEffect(player, MobEffects.STRENGTH, 20 * 20, 3);
                }
                ACTIVE_ULTIMATES.put(player.getUUID(),
                        new TimedUltimate(kind, now + 20L * SECOND, 0L, grantStrength));
                spawnBerserkerBurst(player);
                playSound(player, JournalSounds.SWORDS_DANCE, 0.8F);
            }
            case ACE -> {
                addEffect(player, MobEffects.RESISTANCE, 10 * 20, 4);
                ACTIVE_ULTIMATES.put(player.getUUID(), new TimedUltimate(kind, now + 10L * SECOND, 0L));
                spawn(player, JournalParticles.ACE, 18);
                playSound(player, JournalSounds.IRON_DEFENSE, 0.8F);
            }
            case VANILTE -> {
                ServerPlayNetworking.send(jumpscareTarget, new JumpscarePayload(20_000));
                ACTIVE_JUMPSCARES.put(player.getUUID(),
                        new JumpscareState(jumpscareTarget.getUUID(), now + 20L * SECOND));
                spawn(player, JournalParticles.VANILTE, 18);
                playSound(jumpscareTarget, JournalSounds.LAVENDER_TOWN, 0.8F);
            }
            case NILLI -> {
                state.nilliProjectiles.clear();
                state.nilliShotsFired = 0;
                state.nilliStartTick = serverTick + 1;
                state.nilliNextFireTick = serverTick + 1;
                ACTIVE_ULTIMATES.put(player.getUUID(),
                        new TimedUltimate(kind, now + 10L * SECOND, 0L));
                spawn(player, JournalParticles.NILLI, 20);
                playSound(player, JournalSounds.HYPER_BEAM, 0.8F);
            }
            case POKEMON -> throw new IllegalStateException("Pokémon's Memoir is handled above.");
            default -> throw new IllegalStateException("Unhandled journal: " + kind);
        }
        JournalSavedData.get(player.level().getServer()).setCooldown(player.getUUID(), kind,
                now + kind.cooldownMillis());
        player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.8F, 1.15F);
        announce(player, kind);
        tell(player, kind.ultimateName() + " activated.", ChatFormatting.GREEN);
    }

    public static void tickServer(MinecraftServer server) {
        serverTick++;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player);
        }
    }

    private static void tickPlayer(ServerPlayer player) {
        if (!player.isAlive()) {
            cleanup(player);
            syncCooldownHud(player, JournalItems.kindOf(player.getOffhandItem()));
            return;
        }
        UUID id = player.getUUID();
        PlayerState state = PLAYER_STATES.computeIfAbsent(id, ignored -> new PlayerState());
        JournalKind held = JournalItems.kindOf(player.getOffhandItem());

        if (state.lastHeld != held) {
            if (state.lastHeld != null) {
                clearPassiveEffects(player, state, state.lastHeld);
            }
            if (held != null) {
                cancelUltimateIfJournalChanged(player, held);
            } else {
                cancelUltimateIfJournalChanged(player, null);
            }
            state.lastHeld = held;
            state.villageResistanceApplied = false;
            state.idleResistanceGranted = false;
            state.einoHeroActivated = false;
            state.pokemonWasOnFire = false;
            state.stillSinceTick = serverTick;
            if (held == JournalKind.VANILTE) {
                clearHostileTargets(player);
            }
        }
        tickJumpscare(player, held);

        if (held == JournalKind.EINO) {
            ensureEffect(player, MobEffects.HERO_OF_THE_VILLAGE, 2, 45);
            if (!state.einoHeroActivated) {
                state.einoHeroActivated = true;
                playEinoPassiveSound(player, state);
            }
            if (serverTick % 20 == 0) {
                boolean nearbyVillager = !player.level().getEntitiesOfClass(
                        Villager.class,
                        player.getBoundingBox().inflate(5.0),
                        villager -> villager.distanceToSqr(player) <= 25.0
                ).isEmpty();
                if (nearbyVillager) {
                    ensureEffect(player, MobEffects.RESISTANCE, 1, 45);
                    if (!state.villageResistanceApplied) {
                        playEinoPassiveSound(player, state);
                    }
                    state.villageResistanceApplied = true;
                } else if (state.villageResistanceApplied) {
                    removeIfAmplifier(player, MobEffects.RESISTANCE, 1);
                    state.villageResistanceApplied = false;
                }
            }
        } else if (held == JournalKind.JOURNAL) {
            ensureEffect(player, MobEffects.SPEED, 0, 45);
        } else if (held == JournalKind.ACE) {
            ensureEffect(player, MobEffects.HASTE, 1, 45);
        } else if (held == JournalKind.VANILTE) {
            tickDreadwalker(player, state);
        }

        tickIdleResistance(player, state, held);
        tickTimedUltimate(player);
        tickPokemonMemoir(player);
        tickWilliNilli(player, state, held);
        tickPokemonFireResistance(player, state, held);
        tickBerserkerAura(player);
        if (held != null && !ACTIVE_ULTIMATES.containsKey(id)
                && serverTick - state.lastAmbientTick >= 30) {
            spawn(player, particleFor(held), 2);
            state.lastAmbientTick = serverTick;
        }

        if (state.bankruptcyUntilMillis > 0L && System.currentTimeMillis() >= state.bankruptcyUntilMillis) {
            state.bankruptcyUntilMillis = 0L;
            tell(player, "Bankruptcy expired without a successful hit.", ChatFormatting.GRAY);
        }
        Long lockUntil = ACTION_LOCKS.get(id);
        if (lockUntil != null && System.currentTimeMillis() >= lockUntil) {
            ACTION_LOCKS.remove(id);
            tell(player, "Bankruptcy has ended. You can act again.", ChatFormatting.GREEN);
        }
        syncCooldownHud(player, held);
    }

    public static void selectPokemonMode(ServerPlayer player, int number) {
        if (JournalItems.kindOf(player.getOffhandItem()) != JournalKind.POKEMON) {
            tell(player, "Hold Pokémon's Memoir in your off-hand to select a mode.", ChatFormatting.RED);
            return;
        }
        if (ACTIVE_ULTIMATES.containsKey(player.getUUID())) {
            tell(player, "You cannot change modes while Pokémon's Memoir is active.", ChatFormatting.RED);
            return;
        }
        PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
        state.pokemonMode = PokemonMemoirMode.fromNumber(number);
        tell(player, "Selected mode " + number + ": " + state.pokemonMode.displayName() + ".",
                ChatFormatting.LIGHT_PURPLE);
    }

    private static void activatePokemonMemoir(ServerPlayer player, PlayerState state, long now) {
        PokemonMemoirMode mode = state.pokemonMode;
        if (mode == null) {
            mode = PokemonMemoirMode.ORIGIN_PULSE;
            state.pokemonMode = mode;
        }
        switch (mode) {
            case ORIGIN_PULSE -> {
                ACTIVE_ULTIMATES.put(player.getUUID(),
                        new TimedUltimate(JournalKind.POKEMON, now + 20L * SECOND, 0L, mode, serverTick));
            }
            case VERDANT_OBLIVION -> {
                ACTIVE_ULTIMATES.put(player.getUUID(),
                        new TimedUltimate(JournalKind.POKEMON, now + 20L * SECOND, 0L, mode, serverTick));
            }
            case PROTECT_DRACO_METEOR -> {
                addEffect(player, MobEffects.RESISTANCE, POKEMON_MEMOIR_DURATION_TICKS, 2);
                ACTIVE_ULTIMATES.put(player.getUUID(),
                        new TimedUltimate(JournalKind.POKEMON, now + 20L * SECOND, 0L, mode, serverTick));
            }
        }
        spawn(player, JournalParticles.POKEMON, 24);
    }

    private static void playSound(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, float volume) {
        player.playSound(sound, volume, 1.0F);
    }

    private static void sendMemoirMusic(ServerPlayer player, boolean playing) {
        ServerPlayNetworking.send(player, new PokemonMemoirMusicPayload(playing));
    }

    private static void tickIdleResistance(ServerPlayer player, PlayerState state, JournalKind held) {
        Vec3 current = player.position();
        boolean dimensionChanged = state.lastDimension != player.level().dimension();
        if (dimensionChanged || state.lastPosition == null || current.distanceToSqr(state.lastPosition) > 0.0004) {
            state.lastPosition = current;
            state.stillSinceTick = serverTick;
            state.idleResistanceGranted = false;
            state.lastDimension = player.level().dimension();
            if (dimensionChanged) {
                cancelForDimensionChange(player, state);
            }
            return;
        }

        if (held == JournalKind.DEXZYN && !state.idleResistanceGranted
                && serverTick - state.stillSinceTick >= 100) {
            addEffect(player, MobEffects.RESISTANCE, 3 * 20, 0);
            state.idleResistanceGranted = true;
            spawn(player, JournalParticles.DEXZYN, 10);
            playSound(player, JournalSounds.PROTECT, 0.8F);
        }
    }

    private static void playEinoPassiveSound(ServerPlayer player, PlayerState state) {
        if (state.lastEinoPassiveSoundTick != serverTick) {
            playSound(player, JournalSounds.LEVEL_UP, 0.75F);
            state.lastEinoPassiveSoundTick = serverTick;
        }
    }

    private static void tickPokemonFireResistance(ServerPlayer player, PlayerState state, JournalKind held) {
        boolean onFire = held == JournalKind.POKEMON && player.isOnFire();
        if (onFire && !state.pokemonWasOnFire) {
            addEffect(player, MobEffects.FIRE_RESISTANCE, 5 * 20, 0);
        }
        state.pokemonWasOnFire = onFire;
    }

    private static void tickDreadwalker(ServerPlayer player, PlayerState state) {
        long timeOfDay = Math.floorMod(player.level().getDayTime(), 24_000L);
        boolean nighttime = timeOfDay >= 13_000L && timeOfDay < 23_000L;
        MobEffectInstance current = player.getEffect(MobEffects.SPEED);

        if (!nighttime) {
            if (state.dreadwalkerApplied) {
                removeIfAmplifier(player, MobEffects.SPEED, 1);
                state.dreadwalkerApplied = false;
            }
            return;
        }

        if (current != null && current.getAmplifier() > 1) {
            state.dreadwalkerApplied = false;
            return;
        }
        if (current == null || current.getAmplifier() < 1) {
            addEffect(player, MobEffects.SPEED, 45, 1);
            if (!state.dreadwalkerApplied) {
                playSound(player, JournalSounds.DREADWALKER_NIGHT, 0.75F);
            }
            state.dreadwalkerApplied = true;
        } else if (state.dreadwalkerApplied && current.getDuration() < 25) {
            addEffect(player, MobEffects.SPEED, 45, 1);
        }
    }

    private static void clearHostileTargets(ServerPlayer protectedPlayer) {
        AABB nearby = protectedPlayer.getBoundingBox().inflate(64.0);
        for (Mob mob : protectedPlayer.level().getEntitiesOfClass(Mob.class, nearby,
                entity -> entity instanceof Enemy && entity.getTarget() == protectedPlayer)) {
            mob.setTarget(null);
        }
    }

    private static ServerPlayer findJumpscareTarget(ServerPlayer caster) {
        Vec3 start = caster.getEyePosition();
        Vec3 end = start.add(caster.getLookAngle().scale(32.0));
        AABB searchArea = caster.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0);
        return caster.level().getEntitiesOfClass(ServerPlayer.class, searchArea,
                        candidate -> candidate != caster && candidate.isAlive() && !candidate.isSpectator()
                                && !caster.isAlliedTo(candidate) && caster.hasLineOfSight(candidate)
                                && candidate.getBoundingBox().inflate(0.3).clip(start, end).isPresent())
                .stream()
                .min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(caster)))
                .orElse(null);
    }

    private static void spawnBerserkerBurst(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY() + 1.0,
                    player.getZ(), 12, 0.45, 0.55, 0.45, 0.0);
            level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0,
                    player.getZ(), 18, 0.4, 0.5, 0.4, 0.025);
        }
    }

    private static void tickWilliNilli(ServerPlayer player, PlayerState state, JournalKind held) {
        TimedUltimate active = ACTIVE_ULTIMATES.get(player.getUUID());
        if (held == JournalKind.NILLI && active != null && active.kind == JournalKind.NILLI
                && state.nilliShotsFired < NILLI_MAX_PROJECTILES
                && serverTick - state.nilliStartTick < NILLI_ULTIMATE_TICKS
                && serverTick >= state.nilliNextFireTick) {
            Vec3 direction = player.getLookAngle().normalize();
            state.nilliProjectiles.add(new StarProjectile(
                    player.getEyePosition().add(direction.scale(0.4)), direction, 0.4));
            state.nilliShotsFired++;
            state.nilliNextFireTick += NILLI_FIRE_INTERVAL_TICKS;
            playSound(player, JournalSounds.SWIFT, 0.2F);
        }

        if (state.nilliProjectiles.isEmpty() || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        Iterator<StarProjectile> iterator = state.nilliProjectiles.iterator();
        while (iterator.hasNext()) {
            StarProjectile projectile = iterator.next();
            double step = Math.min(NILLI_PROJECTILE_SPEED,
                    NILLI_PROJECTILE_RANGE - projectile.distanceTravelled);
            if (step <= 0.0) {
                iterator.remove();
                continue;
            }

            Vec3 start = projectile.position;
            Vec3 end = start.add(projectile.direction.scale(step));
            BlockHitResult blockHit = level.clip(new ClipContext(start, end,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            boolean hitBlock = blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS;
            Vec3 collisionEnd = hitBlock ? blockHit.getLocation() : end;
            double blockDistanceSquared = start.distanceToSqr(collisionEnd);

            LivingEntity firstTarget = null;
            Vec3 targetHitPosition = null;
            double targetDistanceSquared = Double.MAX_VALUE;
            AABB sweep = new AABB(start, collisionEnd).inflate(0.3);
            for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, sweep,
                    entity -> entity != player && entity.isAlive()
                            && (!(entity instanceof ServerPlayer other) || player.canHarmPlayer(other)))) {
                var hitPosition = candidate.getBoundingBox().inflate(0.3).clip(start, collisionEnd);
                if (hitPosition.isPresent()) {
                    double distanceSquared = start.distanceToSqr(hitPosition.get());
                    if (distanceSquared <= blockDistanceSquared && distanceSquared < targetDistanceSquared) {
                        firstTarget = candidate;
                        targetHitPosition = hitPosition.get();
                        targetDistanceSquared = distanceSquared;
                    }
                }
            }

            if (firstTarget != null) {
                firstTarget.hurtServer(level, player.damageSources().magic(), 2.0F);
                level.sendParticles(JournalParticles.NILLI, targetHitPosition.x, targetHitPosition.y,
                        targetHitPosition.z, 8, 0.16, 0.16, 0.16, 0.02);
                iterator.remove();
                continue;
            }
            if (hitBlock) {
                level.sendParticles(JournalParticles.NILLI, collisionEnd.x, collisionEnd.y, collisionEnd.z,
                        4, 0.1, 0.1, 0.1, 0.015);
                iterator.remove();
                continue;
            }

            projectile.position = end;
            projectile.distanceTravelled += step;
            level.sendParticles(JournalParticles.NILLI, end.x, end.y, end.z,
                    2, 0.035, 0.035, 0.035, 0.01);
            if (projectile.distanceTravelled >= NILLI_PROJECTILE_RANGE) {
                iterator.remove();
            }
        }
    }

    private static void tickPokemonMemoir(ServerPlayer player) {
        TimedUltimate active = ACTIVE_ULTIMATES.get(player.getUUID());
        if (active == null || active.kind != JournalKind.POKEMON || active.pokemonMode == null
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        long elapsed = serverTick - active.startedTick;
        switch (active.pokemonMode) {
            case ORIGIN_PULSE -> {
                if (serverTick % 5 == 0) {
                    for (int point = 0; point < 16; point++) {
                        double angle = point * (Math.PI * 2.0 / 16.0);
                        level.sendParticles(JournalParticles.ORIGIN_PULSE,
                                player.getX() + Math.cos(angle) * 10.0, player.getY() + 0.15,
                                player.getZ() + Math.sin(angle) * 10.0, 1, 0, 0.04, 0, 0);
                    }
                }
                if (serverTick % 20 == 0) {
                    for (LivingEntity target : memoirTargets(player, level, 10.0)) {
                        target.hurtServer(level, player.damageSources().magic(), 1.0F);
                        level.sendParticles(JournalParticles.THUNDER, target.getX(),
                                target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                                2, 0.2, 0.3, 0.2, 0.02);
                    }
                }
                if (elapsed > 0 && elapsed % 40 == 0) {
                    LivingEntity target = memoirTargets(player, level, 10.0).stream()
                            .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)))
                            .orElse(null);
                    if (target != null) {
                        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
                        if (lightning != null) {
                            lightning.setPos(target.getX(), target.getY(), target.getZ());
                            lightning.setCause(player);
                            if (level.addFreshEntity(lightning)) {
                                playSound(player, JournalSounds.THUNDER, 0.65F);
                            }
                        }
                    }
                }
            }
            case VERDANT_OBLIVION -> {
                for (LivingEntity target : memoirTargets(player, level, 5.0)) {
                    Vec3 inward = player.position().subtract(target.position()).normalize();
                    target.setDeltaMovement(target.getDeltaMovement().scale(0.3)
                            .add(inward.x * 0.12, 0.04, inward.z * 0.12));
                    target.hurtMarked = true;
                }
                if (serverTick % 4 == 0) {
                    for (int point = 0; point < 10; point++) {
                        double angle = point * (Math.PI * 2.0 / 10.0) + elapsed * 0.08;
                        level.sendParticles(JournalParticles.VORTEX,
                                player.getX() + Math.cos(angle) * 5.0, player.getY() + 0.7,
                                player.getZ() + Math.sin(angle) * 5.0, 1, 0, 0.02, 0, 0);
                    }
                }
                if (serverTick % 20 == 0) {
                    for (LivingEntity target : memoirTargets(player, level, 5.0)) {
                        float before = target.getHealth();
                        if (target.hurtServer(level, player.damageSources().magic(), 1.0F)) {
                            float dealt = Math.max(0.0F, before - target.getHealth());
                            if (dealt > 0.0F) {
                                player.heal(dealt);
                            }
                        }
                    }
                }
            }
            case PROTECT_DRACO_METEOR -> {
                if (elapsed >= POKEMON_METEOR_DURATION_TICKS) {
                    return;
                }
                Vec3 start = player.getEyePosition();
                Vec3 direction = player.getLookAngle().normalize();
                Vec3 end = start.add(direction.scale(16.0));
                BlockHitResult blockHit = level.clip(new ClipContext(start, end,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                if (blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                    end = blockHit.getLocation();
                }
                if (serverTick % 5 == 0) {
                    for (double distance = 2.0; distance < start.distanceTo(end); distance += 3.0) {
                        Vec3 point = start.add(direction.scale(distance));
                        level.sendParticles(JournalParticles.DRACO_METEOR, point.x, point.y, point.z,
                                2, 0.3, 0.3, 0.3, 0.015);
                    }
                }
Vec3 attackEnd = end;
if (serverTick % 20 == 0) {
    AABB attackArea = new AABB(start, end).inflate(0.8);
                    for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, attackArea,
                            candidate -> candidate != player && candidate.isAlive()
                                    && (!(candidate instanceof ServerPlayer other)
                                    || player.canHarmPlayer(other))
&& candidate.getBoundingBox().inflate(0.7).clip(start, attackEnd).isPresent())) {
                        target.hurtServer(level, player.damageSources().playerAttack(player), 4.0F);
                    }
                }
            }
        }
    }

    private static java.util.List<LivingEntity> memoirTargets(ServerPlayer player, ServerLevel level, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                entity -> entity != player && entity.isAlive()
                        && entity.distanceToSqr(player) <= radius * radius
                        && (!(entity instanceof ServerPlayer other) || player.canHarmPlayer(other)));
    }

    private static void syncCooldownHud(ServerPlayer player, JournalKind held) {
        int ordinal = held == null ? -1 : held.ordinal();
        int remaining = held == null ? 0 : (int) Math.min(Integer.MAX_VALUE,
                cooldownRemaining(player, held));
        ServerPlayNetworking.send(player, new CooldownSyncPayload(ordinal, remaining));
    }

    private static void tickBerserkerAura(ServerPlayer player) {
        TimedUltimate active = ACTIVE_ULTIMATES.get(player.getUUID());
        if (active == null || active.kind != JournalKind.DEXZYN || serverTick % 12 != 0
                || JournalItems.kindOf(player.getOffhandItem()) != JournalKind.DEXZYN) {
            return;
        }
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY() + 1.0,
                    player.getZ(), 1, 0.22, 0.35, 0.22, 0.0);
            level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.6,
                    player.getZ(), 1, 0.24, 0.4, 0.24, 0.006);
        }
    }

    private static SimpleParticleType particleFor(JournalKind kind) {
        return switch (kind) {
            case EINO -> JournalParticles.EINO;
            case ARBY -> JournalParticles.ARBY;
            case JOURNAL -> JournalParticles.JOURNAL;
            case DEXZYN -> JournalParticles.DEXZYN;
            case ACE -> JournalParticles.ACE;
            case VANILTE -> JournalParticles.VANILTE;
            case NILLI -> JournalParticles.NILLI;
            case POKEMON -> JournalParticles.POKEMON;
        };
    }

    private static void tickTimedUltimate(ServerPlayer player) {
        TimedUltimate active = ACTIVE_ULTIMATES.get(player.getUUID());
        if (active == null) {
            return;
        }
        long now = System.currentTimeMillis();
        PlayerState state = PLAYER_STATES.get(player.getUUID());
        boolean nilliExpired = active.kind == JournalKind.NILLI && state != null
                && serverTick - state.nilliStartTick >= NILLI_ULTIMATE_TICKS;
        if (!player.isAlive() || JournalItems.kindOf(player.getOffhandItem()) != active.kind
                || (active.kind == JournalKind.NILLI ? nilliExpired : now >= active.expiresAtMillis)) {
            removeUltimateEffects(player, active);
            ACTIVE_ULTIMATES.remove(player.getUUID());
            stopMemoirMusic(player, active);
            if (now < active.expiresAtMillis) {
                tell(player, active.kind.ultimateName() + " ended because the journal left your off-hand.", ChatFormatting.GRAY);
            }
            return;
        }
        if (active.kind == JournalKind.ARBY && active.secondaryExpiresAtMillis > 0L
                && now >= active.secondaryExpiresAtMillis) {
            removeIfAmplifier(player, MobEffects.REGENERATION, 2);
            active.secondaryExpiresAtMillis = 0L;
        }
    }

    private static void cancelUltimateIfJournalChanged(ServerPlayer player, JournalKind held) {
        TimedUltimate active = ACTIVE_ULTIMATES.get(player.getUUID());
        if (active != null && active.kind != held) {
            removeUltimateEffects(player, active);
            ACTIVE_ULTIMATES.remove(player.getUUID());
            stopMemoirMusic(player, active);
        }
        PlayerState state = PLAYER_STATES.get(player.getUUID());
        if (state != null && state.bankruptcyUntilMillis > 0L && held != JournalKind.EINO) {
            state.bankruptcyUntilMillis = 0L;
            tell(player, "Bankruptcy was cancelled because Eino's Playbook left your off-hand.", ChatFormatting.GRAY);
        }
    }

    private static void cancelForDimensionChange(ServerPlayer player, PlayerState state) {
        TimedUltimate active = ACTIVE_ULTIMATES.remove(player.getUUID());
        if (active != null) {
            removeUltimateEffects(player, active);
            stopMemoirMusic(player, active);
            tell(player, active.kind.ultimateName() + " ended because you changed dimensions.", ChatFormatting.GRAY);
        }
        if (state.bankruptcyUntilMillis > 0L) {
            state.bankruptcyUntilMillis = 0L;
            tell(player, "Bankruptcy was cancelled because you changed dimensions.", ChatFormatting.GRAY);
        }
        cancelJumpscare(player);
    }

    private static void tickJumpscare(ServerPlayer caster, JournalKind held) {
        JumpscareState jumpscare = ACTIVE_JUMPSCARES.get(caster.getUUID());
        if (jumpscare == null) {
            return;
        }
        if (held != JournalKind.VANILTE || !caster.isAlive()) {
            cancelJumpscare(caster);
            return;
        }
        if (System.currentTimeMillis() >= jumpscare.expiresAtMillis) {
            ACTIVE_JUMPSCARES.remove(caster.getUUID());
            return;
        }
        if (caster.level().getServer().getPlayerList().getPlayer(jumpscare.targetId) == null) {
            ACTIVE_JUMPSCARES.remove(caster.getUUID());
        }
    }

    private static void cancelJumpscare(ServerPlayer caster) {
        JumpscareState jumpscare = ACTIVE_JUMPSCARES.remove(caster.getUUID());
        if (jumpscare == null || caster.level().getServer() == null) {
            return;
        }
        ServerPlayer target = caster.level().getServer().getPlayerList().getPlayer(jumpscare.targetId);
        if (target != null) {
            ServerPlayNetworking.send(target, new JumpscarePayload(0));
        }
    }

    private static void stopMemoirMusic(ServerPlayer player, TimedUltimate active) {
        if (active.kind == JournalKind.POKEMON) {
            sendMemoirMusic(player, false);
        }
    }

    private static void removeUltimateEffects(ServerPlayer player, TimedUltimate active) {
        switch (active.kind) {
            case ARBY -> {
                removeIfAmplifier(player, MobEffects.HEALTH_BOOST, 2);
                removeIfAmplifier(player, MobEffects.REGENERATION, 2);
            }
            case JOURNAL -> removeIfAmplifier(player, MobEffects.SPEED, 3);
            case DEXZYN -> {
                if (active.grantedStrength) {
                    removeIfAmplifier(player, MobEffects.STRENGTH, 3);
                }
            }
            case ACE -> removeIfAmplifier(player, MobEffects.RESISTANCE, 4);
            case NILLI -> {
                PlayerState state = PLAYER_STATES.get(player.getUUID());
                if (state != null) {
                    state.nilliProjectiles.clear();
                    state.nilliShotsFired = NILLI_MAX_PROJECTILES;
                }
            }
            case POKEMON -> {
                if (active.pokemonMode == PokemonMemoirMode.PROTECT_DRACO_METEOR) {
                    removeIfAmplifier(player, MobEffects.RESISTANCE, 2);
                }
            }
            default -> {
            }
        }
    }

    private static void clearPassiveEffects(ServerPlayer player, PlayerState state, JournalKind kind) {
        switch (kind) {
            case EINO -> {
                removeIfAmplifier(player, MobEffects.HERO_OF_THE_VILLAGE, 2);
                removeIfAmplifier(player, MobEffects.RESISTANCE, 1);
            }
            case ARBY -> {
            }
            case JOURNAL -> removeIfAmplifier(player, MobEffects.SPEED, 0);
            case ACE -> removeIfAmplifier(player, MobEffects.HASTE, 1);
            case DEXZYN -> {
            }
            case VANILTE -> {
                if (state.dreadwalkerApplied) {
                    removeIfAmplifier(player, MobEffects.SPEED, 1);
                    state.dreadwalkerApplied = false;
                }
            }
            case NILLI -> {
            }
            case POKEMON -> {
            }
        }
    }

    private static void ensureEffect(ServerPlayer player, Holder<MobEffect> effect, int amplifier, int duration) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || current.getAmplifier() != amplifier || current.getDuration() < 25) {
            player.addEffect(new MobEffectInstance(effect, duration, amplifier, false, false, true));
        }
    }

    private static void addEffect(ServerPlayer player, Holder<MobEffect> effect, int duration, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, false, true, true));
    }

    private static void removeIfAmplifier(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.getAmplifier() == amplifier) {
            player.removeEffect(effect);
        }
    }

    public static boolean isActionLocked(ServerPlayer player) {
        Long until = ACTION_LOCKS.get(player.getUUID());
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            ACTION_LOCKS.remove(player.getUUID());
            return false;
        }
        return true;
    }

    public static void markAttack(ServerPlayer attacker, InteractionHand hand, Entity target) {
        PlayerState state = PLAYER_STATES.computeIfAbsent(attacker.getUUID(), ignored -> new PlayerState());
        state.attackTick = serverTick;
        state.attackTarget = target.getUUID();
        state.meleeAttack = hand == InteractionHand.MAIN_HAND && target instanceof LivingEntity;
        state.axeAttack = state.meleeAttack && attacker.getMainHandItem().is(ItemTags.AXES);
        state.featherAttack = hand == InteractionHand.MAIN_HAND
                && attacker.getMainHandItem().is(Items.FEATHER)
                && JournalItems.kindOf(attacker.getOffhandItem()) == JournalKind.NILLI;
        state.criticalAxeAttack = state.axeAttack
                && JournalItems.kindOf(attacker.getOffhandItem()) == JournalKind.NILLI
                && isCriticalStrikeCandidate(attacker, target);
    }

    private static boolean isCriticalStrikeCandidate(ServerPlayer attacker, Entity target) {
        return target instanceof LivingEntity living && living.isAlive()
                && attacker.getAttackStrengthScale(0.5F) > 0.9F
                && attacker.fallDistance > 0.0F
                && !attacker.onGround()
                && !attacker.onClimbable()
                && !attacker.isInWater()
                && !attacker.hasEffect(MobEffects.BLINDNESS)
                && !attacker.isPassenger()
                && !attacker.isSprinting();
    }

    public static void handleSuccessfulHit(ServerPlayer attacker, LivingEntity target, DamageSource source,
                                           boolean blocked, float damageTaken) {
        PlayerState state = PLAYER_STATES.get(attacker.getUUID());
        boolean featherAttack = false;
        boolean criticalAxeAttack = false;
        boolean trackedMeleeHit = false;
        boolean trackedAxeHit = false;
        if (state != null && state.attackTick == serverTick) {
            if (target.getUUID().equals(state.attackTarget)) {
                featherAttack = state.featherAttack;
                criticalAxeAttack = state.criticalAxeAttack;
                trackedMeleeHit = state.meleeAttack;
                trackedAxeHit = state.axeAttack;
            }
            state.attackTick = -1L;
            state.attackTarget = null;
            state.featherAttack = false;
            state.criticalAxeAttack = false;
            state.meleeAttack = false;
            state.axeAttack = false;
        }
        if (blocked || damageTaken <= 0.0F) {
            return;
        }
        if (target == attacker) {
            return;
        }
        JournalKind held = JournalItems.kindOf(attacker.getOffhandItem());
        if (held == JournalKind.ARBY) {
            boolean applied = target.addEffect(
                    new MobEffectInstance(MobEffects.POISON, 3 * 20, 0, false, true, true));
            if (applied) {
                spawn(target instanceof ServerPlayer victim ? victim : attacker, JournalParticles.ARBY, 5);
                playSound(target instanceof ServerPlayer victim ? victim : attacker, JournalSounds.POISON, 0.7F);
            }
        }
        if (held == JournalKind.NILLI && featherAttack && attacker.getMainHandItem().is(Items.FEATHER)) {
            target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10 * 20, 0, false, true, true));
            spawn(attacker, JournalParticles.NILLI, 4);
        }
        if (held == JournalKind.EINO && trackedMeleeHit && source.getDirectEntity() == attacker
                && target instanceof ServerPlayer victim && state != null
                && state.bankruptcyUntilMillis > System.currentTimeMillis()) {
            state.bankruptcyUntilMillis = 0L;
            long until = System.currentTimeMillis() + 10L * SECOND;
            ACTION_LOCKS.put(victim.getUUID(), until);
            JournalSavedData.get(attacker.level().getServer()).setCooldown(attacker.getUUID(), JournalKind.EINO,
                    System.currentTimeMillis() + JournalKind.EINO.cooldownMillis());
            victim.displayClientMessage(Component.literal("Bankruptcy: your actions are disabled for 10 seconds.")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
            tell(attacker, "Bankruptcy disabled " + victim.getName().getString() + " for 10 seconds.", ChatFormatting.GREEN);
            announce(attacker, JournalKind.EINO);
            spawn(attacker, JournalParticles.EINO, 18);
            playSound(attacker, JournalSounds.PAY_DAY, 0.8F);
        }

        if (held == JournalKind.JOURNAL && trackedAxeHit
                && attacker.getMainHandItem().is(ItemTags.AXES)) {
            boolean applied = target.addEffect(
                    new MobEffectInstance(MobEffects.BLINDNESS, 10 * 20, 0, false, true, true));
            if (applied) {
                spawn(target instanceof ServerPlayer victim ? victim : attacker, JournalParticles.JOURNAL, 10);
                playSound(target instanceof ServerPlayer victim ? victim : attacker,
                        JournalSounds.NIGHT_SHADE, 0.7F);
            }
        }

        if (held == JournalKind.POKEMON) {
            target.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 5 * 20, 0, false, true, true));
        }

        if (held == JournalKind.NILLI && criticalAxeAttack && source.getDirectEntity() == attacker) {
            applyCosmicSmite(attacker, target);
        }
    }

    private static void applyCosmicSmite(ServerPlayer attacker, LivingEntity target) {
        if (!target.isAlive() || target == attacker || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        if (target instanceof ServerPlayer victim && !attacker.canHarmPlayer(victim)) {
            return;
        }

        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (lightning != null) {
            lightning.setPos(target.getX(), target.getY(), target.getZ());
            lightning.setCause(attacker);
            if (level.addFreshEntity(lightning)) {
                playSound(attacker, JournalSounds.THUNDER, 0.65F);
            }
        }
    }

    public static long cooldownRemaining(ServerPlayer player, JournalKind kind) {
        long until = JournalSavedData.get(player.level().getServer()).cooldownUntil(player.getUUID(), kind);
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public static void showCooldown(ServerPlayer player, JournalKind kind) {
        long remaining = cooldownRemaining(player, kind);
        if (remaining <= 0) {
            tell(player, kind.ultimateName() + " is ready.", ChatFormatting.GREEN);
        } else {
            tell(player, kind.ultimateName() + " cooldown: " + formatDuration(remaining) + ".", ChatFormatting.YELLOW);
        }
    }

    public static boolean claimJournal(ServerPlayer player, JournalKind kind) {
        JournalSavedData data = JournalSavedData.get(player.level().getServer());
        if (!data.claim(kind, player.getUUID(), player.getName().getString())) {
            player.displayClientMessage(Component.translatable("ability.journalsmp.claimed")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    public static String passiveOne(JournalKind kind) {
        return switch (kind) {
            case EINO -> "Master Trader — Hero of the Village III";
            case ARBY -> "Food Poisoning — successful hits inflict Poison I for 3 seconds";
            case JOURNAL -> "Speedrunner — Speed I";
            case DEXZYN -> "Idle Protection — after 5 seconds still, gain Resistance I for 3 seconds once";
            case ACE -> "Master Builder — Haste II";
            case VANILTE -> "Dreadwalker — Speed II at night";
            case NILLI -> "Featherweight — feather hits inflict Slow Falling for 10 seconds";
            case POKEMON -> "EXP Share — doubles experience points while held; plays a level-up sound";
        };
    }

    public static String passiveTwo(JournalKind kind) {
        return switch (kind) {
            case EINO -> "Village Guardian — Resistance II while a villager is within 5 blocks";
            case ARBY -> "Extra Calories — food eaten grants 1.5× saturation, not extra hunger";
            case JOURNAL -> "Blindside — successful axe hits inflict Blindness for 10 seconds";
            case DEXZYN -> "Sword Looting — 50% chance for one extra item per mob-drop stack on sword kills; disabled when the sword has Looting";
            case ACE -> "Extended Reach — place blocks up to one block farther away; containers, mining, and entity reach are unchanged";
            case VANILTE -> "Predator's Instinct — hostile mobs ignore you";
            case NILLI -> "Cosmic Smite — axe critical hits summon real lightning on the target";
            case POKEMON -> "Type Advantage — Strength on hit, Haste on block break, Resistance on damage, Fire Resistance when burning, and Regeneration after eating";
        };
    }

    public static String ultimateDescription(JournalKind kind) {
        return switch (kind) {
            case EINO -> "Arm it, then land an unblocked melee hit on a player to disable actions for 10 seconds.";
            case ARBY -> "Health Boost III for 60 seconds and Regeneration III for 10 seconds.";
            case JOURNAL -> "Speed IV for 60 seconds; ends if the journal leaves your off-hand.";
            case DEXZYN -> "Strength IV for 20 seconds.";
            case ACE -> "Resistance V for 10 seconds.";
            case VANILTE -> "Show the supplied image to the targeted player for 20 seconds.";
            case NILLI -> "Fire up to 20 stars over 10 seconds: 2 damage within 10 blocks, ignores shields, stops at solid blocks.";
            case POKEMON -> "Activate the selected mode with the shared 3-minute cooldown. Select with /mode 1, /mode 2, or /mode 3.";
        };
    }

    public static String cooldownText(JournalKind kind) {
        return formatDuration(kind.cooldownMillis());
    }

    private static String formatDuration(long milliseconds) {
        long totalSeconds = Math.max(0L, (milliseconds + 999L) / 1_000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return minutes > 0 ? minutes + "m " + String.format("%02d", seconds) + "s" : seconds + "s";
    }

    private static void tell(ServerPlayer player, String message, ChatFormatting color) {
        player.displayClientMessage(Component.literal(message).withStyle(color), true);
    }

    private static void announce(ServerPlayer player, JournalKind kind) {
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal(player.getName().getString() + " activated " + kind.ultimateName() + "!")
                            .withStyle(ChatFormatting.GOLD),
                    false);
        }
    }

    private static void spawn(ServerPlayer player, SimpleParticleType particle, int count) {
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(particle, player.getX(), player.getY() + 1.0, player.getZ(),
                    count, 0.45, 0.55, 0.45, 0.025);
        }
    }

    public static void cleanup(ServerPlayer player) {
        PLAYER_STATES.remove(player.getUUID());
        ACTION_LOCKS.remove(player.getUUID());
        cancelJumpscare(player);
        ACTIVE_JUMPSCARES.entrySet().removeIf(entry -> entry.getValue().targetId.equals(player.getUUID()));
        TimedUltimate active = ACTIVE_ULTIMATES.remove(player.getUUID());
        if (active != null) {
            removeUltimateEffects(player, active);
            stopMemoirMusic(player, active);
        }
    }

    public static boolean isJournalItem(ItemStack stack, JournalKind kind) {
        return JournalItems.kindOf(stack) == kind;
    }

    private static final class PlayerState {
        private JournalKind lastHeld;
        private Vec3 lastPosition;
        private net.minecraft.resources.ResourceKey<Level> lastDimension;
        private long stillSinceTick;
        private long lastAmbientTick;
        private long nilliStartTick;
        private long nilliNextFireTick;
        private int nilliShotsFired;
        private long attackTick = -1L;
        private UUID attackTarget;
        private long bankruptcyUntilMillis;
        private boolean villageResistanceApplied;
        private boolean idleResistanceGranted;
        private boolean dreadwalkerApplied;
        private boolean einoHeroActivated;
        private long lastEinoPassiveSoundTick = Long.MIN_VALUE;
        private boolean pokemonWasOnFire;
        private PokemonMemoirMode pokemonMode = PokemonMemoirMode.ORIGIN_PULSE;
        private boolean meleeAttack;
        private boolean axeAttack;
        private boolean featherAttack;
        private boolean criticalAxeAttack;
        private final java.util.List<StarProjectile> nilliProjectiles = new java.util.ArrayList<>();
    }

    private static final class StarProjectile {
        private Vec3 position;
        private final Vec3 direction;
        private double distanceTravelled;

        private StarProjectile(Vec3 position, Vec3 direction, double initialDistance) {
            this.position = position;
            this.direction = direction;
            this.distanceTravelled = initialDistance;
        }
    }

    private static final class JumpscareState {
        private final UUID targetId;
        private final long expiresAtMillis;

        private JumpscareState(UUID targetId, long expiresAtMillis) {
            this.targetId = targetId;
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    private static final class TimedUltimate {
        private final JournalKind kind;
        private final long expiresAtMillis;
        private long secondaryExpiresAtMillis;
        private final boolean grantedStrength;
        private final PokemonMemoirMode pokemonMode;
        private final long startedTick;

        private TimedUltimate(JournalKind kind, long expiresAtMillis, long secondaryExpiresAtMillis) {
            this(kind, expiresAtMillis, secondaryExpiresAtMillis, false);
        }

        private TimedUltimate(JournalKind kind, long expiresAtMillis, long secondaryExpiresAtMillis,
                              boolean grantedStrength) {
            this(kind, expiresAtMillis, secondaryExpiresAtMillis, grantedStrength, null, serverTick);
        }

        private TimedUltimate(JournalKind kind, long expiresAtMillis, long secondaryExpiresAtMillis,
                              PokemonMemoirMode pokemonMode, long startedTick) {
            this(kind, expiresAtMillis, secondaryExpiresAtMillis, false, pokemonMode, startedTick);
        }

        private TimedUltimate(JournalKind kind, long expiresAtMillis, long secondaryExpiresAtMillis,
                              boolean grantedStrength, PokemonMemoirMode pokemonMode, long startedTick) {
            this.kind = kind;
            this.expiresAtMillis = expiresAtMillis;
            this.secondaryExpiresAtMillis = secondaryExpiresAtMillis;
            this.grantedStrength = grantedStrength;
            this.pokemonMode = pokemonMode;
            this.startedTick = startedTick;
        }
    }
}
