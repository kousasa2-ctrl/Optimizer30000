package dev.optimizer30000;

import dev.optimizer30000.config.OptimizationProfile;
import dev.optimizer30000.runtime.RenderBudgetState;
import dev.optimizer30000.runtime.ThreadBudgetGovernor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Главный bootstrap low-end профиля.
 */
public final class Optimizer30000Mod implements ClientModInitializer {
    public static final String MOD_ID = "optimizer30000";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final OptimizationProfile PROFILE = OptimizationProfile.lowEndLaptop2011();
    private static final ThreadBudgetGovernor THREAD_BUDGET_GOVERNOR = new ThreadBudgetGovernor(PROFILE);

    @Override
    public void onInitializeClient() {
        RenderBudgetState.configure(
                PROFILE.legacyGpuMode(),
                PROFILE.disablePostProcessing(),
                PROFILE.aggressiveEntityCulling(),
                PROFILE.disableTextShadows(),
                PROFILE.simplifyLightmapUpdates(),
                PROFILE.lazyDfu(),
                PROFILE.entityDistanceBlocks(),
                PROFILE.blockEntityDistanceBlocks(),
                PROFILE.translucentResortDistance()
        );

        LOGGER.info(
                "[{}] Enabled low-end profile for Fabric {}: rebuildThreads={}, worldgenThreads={}, entityDistance={}, legacyGpuMode={}, disableTextShadows={}",
                MOD_ID,
                FabricLoader.getInstance()
                        .getModContainer("fabricloader")
                        .map(container -> container.getMetadata().getVersion().getFriendlyString())
                        .orElse("unknown"),
                THREAD_BUDGET_GOVERNOR.maxChunkRebuildThreads(),
                THREAD_BUDGET_GOVERNOR.maxWorldgenThreads(),
                PROFILE.entityDistanceBlocks(),
                PROFILE.legacyGpuMode(),
                PROFILE.disableTextShadows()
        );
    }

    public static OptimizationProfile profile() {
        return PROFILE;
    }

    public static ThreadBudgetGovernor threadBudgetGovernor() {
        return THREAD_BUDGET_GOVERNOR;
    }
}
