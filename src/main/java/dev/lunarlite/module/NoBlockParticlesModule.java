package dev.lunarlite.module;

/**
 * Tắt hạt vỡ block (khi đào và khi block bị phá). Phần chặn hạt nằm ở {@code mixin.ParticleEngineMixin}.
 * Hiệu ứng rung block khi đào của bản 1.8.9 chưa port (cần vẽ lại block bằng renderer mới).
 */
public class NoBlockParticlesModule extends Module {
    public NoBlockParticlesModule() {
        super("no_block_particles", "No Block Particles", "Tắt hạt vỡ block khi đào / phá block.",
                Category.VISUAL, false);
    }
}
