package dev.journalsmp.client;

import dev.journalsmp.particle.JournalParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;

public final class JournalSmpClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ParticleFactoryRegistry.getInstance().register(JournalParticles.EINO, JournalParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(JournalParticles.ARBY, JournalParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(JournalParticles.JOURNAL, JournalParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(JournalParticles.DEXZYN, JournalParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(JournalParticles.ACE, JournalParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(JournalParticles.VANILTE, JournalParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(JournalParticles.NILLI,
                sprites -> new JournalParticle.Provider(sprites, 0.48F));
        ParticleFactoryRegistry.getInstance().register(JournalParticles.POKEMON,
                sprites -> new JournalParticle.Provider(sprites, 0.42F));
        ParticleFactoryRegistry.getInstance().register(JournalParticles.VORTEX,
                sprites -> new JournalParticle.Provider(sprites, 0.75F));
        ParticleFactoryRegistry.getInstance().register(JournalParticles.ORIGIN_PULSE,
                sprites -> new JournalParticle.Provider(sprites, 0.7F));
        ParticleFactoryRegistry.getInstance().register(JournalParticles.THUNDER,
                sprites -> new JournalParticle.Provider(sprites, 0.55F));
        ParticleFactoryRegistry.getInstance().register(JournalParticles.DRACO_METEOR,
                sprites -> new JournalParticle.Provider(sprites, 0.62F));
        JumpscareOverlay.register();
        JournalNetworkingClient.register();
        UltimateKeybind.register();
        CooldownHud.register();
    }
}
