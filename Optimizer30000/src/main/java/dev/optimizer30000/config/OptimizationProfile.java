package dev.optimizer30000.config;

import net.minecraft.client.option.GraphicsMode;

/**
 * Неизменяемый профиль для крайне слабого ноутбука.
 *
 * Профиль специально настроен под Sandy Bridge + Intel HD 3000:
 * минимум фоновых потоков, минимум post-processing и агрессивное урезание
 * дальности сущностей/текста ради стабильного frame time.
 */
public record OptimizationProfile(
        boolean legacyGpuMode,
        boolean disablePostProcessing,
        boolean aggressiveEntityCulling,
        boolean disableTextShadows,
        boolean simplifyLightmapUpdates,
        boolean lazyDfu,
        int entityDistanceBlocks,
        int blockEntityDistanceBlocks,
        int maxChunkRebuildThreads,
        int maxWorldgenThreads,
        double translucentResortDistance,
        GraphicsMode forcedGraphicsMode
) {
    public static OptimizationProfile lowEndLaptop2011() {
        return new OptimizationProfile(
                true,
                true,
                true,
                true,
                true,
                true,
                16,
                12,
                1,
                1,
                1.5D,
                GraphicsMode.FAST
        );
    }
}
