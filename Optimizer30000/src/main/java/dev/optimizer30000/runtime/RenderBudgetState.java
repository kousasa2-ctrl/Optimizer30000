package dev.optimizer30000.runtime;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Глобальное low-spec состояние, доступное из mixin-кода без лишних аллокаций.
 */
public final class RenderBudgetState {
    private static final AtomicInteger FRAME_INDEX = new AtomicInteger();

    private static volatile boolean legacyGpuMode;
    private static volatile boolean postProcessingDisabled;
    private static volatile boolean aggressiveEntityCulling;
    private static volatile boolean disableTextShadows;
    private static volatile boolean simplifyLightmapUpdates;
    private static volatile boolean lazyDfu;
    private static volatile int entityDistanceLimit = 16;
    private static volatile int blockEntityDistanceLimit = 12;
    private static volatile double translucentResortDistance = 1.5D;
    private static volatile double translucentResortDistanceSquared = 2.25D;

    private static volatile double cameraX;
    private static volatile double cameraY;
    private static volatile double cameraZ;
    private static volatile double previousCameraX;
    private static volatile double previousCameraY;
    private static volatile double previousCameraZ;

    private RenderBudgetState() {
    }

    public static void configure(boolean legacyGpuMode,
                                 boolean disablePostProcessing,
                                 boolean aggressiveEntityCulling,
                                 boolean disableTextShadows,
                                 boolean simplifyLightmapUpdates,
                                 boolean lazyDfu,
                                 int entityDistanceLimit,
                                 int blockEntityDistanceLimit,
                                 double translucentResortDistance) {
        RenderBudgetState.legacyGpuMode = legacyGpuMode;
        RenderBudgetState.postProcessingDisabled = disablePostProcessing;
        RenderBudgetState.aggressiveEntityCulling = aggressiveEntityCulling;
        RenderBudgetState.disableTextShadows = disableTextShadows;
        RenderBudgetState.simplifyLightmapUpdates = simplifyLightmapUpdates;
        RenderBudgetState.lazyDfu = lazyDfu;
        RenderBudgetState.entityDistanceLimit = entityDistanceLimit;
        RenderBudgetState.blockEntityDistanceLimit = blockEntityDistanceLimit;
        RenderBudgetState.translucentResortDistance = translucentResortDistance;
        RenderBudgetState.translucentResortDistanceSquared = translucentResortDistance * translucentResortDistance;
    }

    public static void beginFrame() {
        FRAME_INDEX.incrementAndGet();
    }

    public static int frameIndex() {
        return FRAME_INDEX.get();
    }

    public static void updateCamera(double x, double y, double z) {
        previousCameraX = cameraX;
        previousCameraY = cameraY;
        previousCameraZ = cameraZ;
        cameraX = x;
        cameraY = y;
        cameraZ = z;
    }

    public static double cameraX() {
        return cameraX;
    }

    public static double cameraY() {
        return cameraY;
    }

    public static double cameraZ() {
        return cameraZ;
    }

    public static boolean cameraMovedSinceLastFrame(double distanceSquared) {
        double dx = cameraX - previousCameraX;
        double dy = cameraY - previousCameraY;
        double dz = cameraZ - previousCameraZ;
        return dx * dx + dy * dy + dz * dz >= distanceSquared;
    }

    public static boolean shouldTickLightmapThisFrame() {
        if (!simplifyLightmapUpdates) {
            return true;
        }

        return (FRAME_INDEX.get() & 1) == 0 || cameraMovedSinceLastFrame(0.0625D);
    }

    public static boolean legacyGpuMode() {
        return legacyGpuMode;
    }

    public static boolean postProcessingDisabled() {
        return postProcessingDisabled;
    }

    public static boolean aggressiveEntityCulling() {
        return aggressiveEntityCulling;
    }

    public static boolean disableTextShadows() {
        return disableTextShadows;
    }

    public static boolean simplifyLightmapUpdates() {
        return simplifyLightmapUpdates;
    }

    public static boolean lazyDfu() {
        return lazyDfu;
    }

    public static int entityDistanceLimit() {
        return entityDistanceLimit;
    }

    public static int blockEntityDistanceLimit() {
        return blockEntityDistanceLimit;
    }

    public static double translucentResortDistance() {
        return translucentResortDistance;
    }

    public static double translucentResortDistanceSquared() {
        return translucentResortDistanceSquared;
    }
}
