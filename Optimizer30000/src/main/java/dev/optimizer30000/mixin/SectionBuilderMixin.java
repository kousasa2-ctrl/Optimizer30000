package dev.optimizer30000.mixin;

import com.mojang.blaze3d.systems.VertexSorter;
import dev.optimizer30000.runtime.RenderBudgetState;
import dev.optimizer30000.runtime.RenderDataSortStateHolder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Low-end оптимизация translucent sorting.
 *
 * Для i5-2520M дорог не только сам sort, но и постоянные short-lived аллокации.
 * Поэтому:
 * - сохраняем SortState в RenderData;
 * - пересортировываем только если камера ушла дальше 1.5 блока;
 * - считаем fingerprint геометрии через ThreadLocal<float[]>, не создавая новый
 *   мусор в куче на каждом build().
 */
@Mixin(SectionBuilder.class)
public abstract class SectionBuilderMixin {
    @Unique private static final double RESORT_DISTANCE_SQUARED = 2.25D;
    @Unique private static final ThreadLocal<float[]> FINGERPRINT_BUFFER = ThreadLocal.withInitial(() -> new float[256]);

    @Inject(
            method = "build(Lnet/minecraft/util/math/ChunkSectionPos;Lnet/minecraft/client/render/chunk/ChunkRendererRegion;Lcom/mojang/blaze3d/systems/VertexSorter;Lnet/minecraft/client/render/chunk/BlockBufferAllocatorStorage;)Lnet/minecraft/client/render/chunk/SectionBuilder$RenderData;",
            at = @At("RETURN")
    )
    private void optimizer30000$cacheTranslucencySortState(ChunkSectionPos sectionPos,
                                                           ChunkRendererRegion region,
                                                           VertexSorter vertexSorter,
                                                           BlockBufferAllocatorStorage allocatorStorage,
                                                           CallbackInfoReturnable<SectionBuilder.RenderData> cir) {
        if (!RenderBudgetState.legacyGpuMode()) {
            return;
        }

        SectionBuilder.RenderData renderData = cir.getReturnValue();
        BuiltBuffer.SortState currentSortState = renderData.translucencySortingData;
        if (currentSortState == null) {
            return;
        }

        RenderDataSortStateHolder cache = (RenderDataSortStateHolder) (Object) renderData;
        Vec3d cameraPos = new Vec3d(RenderBudgetState.cameraX(), RenderBudgetState.cameraY(), RenderBudgetState.cameraZ());
        long geometryFingerprint = this.optimizer30000$computeGeometryFingerprint(currentSortState, cameraPos);

        BuiltBuffer.SortState cachedSortState = cache.optimizer30000$getCachedSortState();
        if (cachedSortState != null
                && geometryFingerprint == cache.optimizer30000$getCachedGeometryFingerprint()
                && optimizer30000$cameraMovedSquared(cache, cameraPos) <= RESORT_DISTANCE_SQUARED) {
            // Мелкое движение камеры не оправдывает новую сортировку: переиспользуем
            // старый SortState и уменьшаем CPU/GC давление на слабом ноутбуке.
            renderData.translucencySortingData = cachedSortState;
            return;
        }

        cache.optimizer30000$setCachedSortState(currentSortState);
        cache.optimizer30000$setCachedGeometryFingerprint(geometryFingerprint);
        cache.optimizer30000$setLastSortPos(cameraPos.x, cameraPos.y, cameraPos.z);
    }

    @Unique
    private static double optimizer30000$cameraMovedSquared(RenderDataSortStateHolder cache, Vec3d cameraPos) {
        double dx = cameraPos.x - cache.optimizer30000$getLastSortX();
        double dy = cameraPos.y - cache.optimizer30000$getLastSortY();
        double dz = cameraPos.z - cache.optimizer30000$getLastSortZ();
        return dx * dx + dy * dy + dz * dz;
    }

    @Unique
    private long optimizer30000$computeGeometryFingerprint(BuiltBuffer.SortState sortState, Vec3d cameraPos) {
        Vector3f[] centroids = sortState.centroids();
        float[] scratch = optimizer30000$getScratchBuffer(centroids.length);

        float cameraX = (float) cameraPos.x;
        float cameraY = (float) cameraPos.y;
        float cameraZ = (float) cameraPos.z;

        long hash = 1469598103934665603L;
        for (int index = 0; index < centroids.length; index++) {
            Vector3f centroid = centroids[index];
            float dx = centroid.x - cameraX;
            float dy = centroid.y - cameraY;
            float dz = centroid.z - cameraZ;
            scratch[index] = dx * dx + dy * dy + dz * dz;
            hash ^= Float.floatToIntBits(scratch[index]);
            hash *= 1099511628211L;
        }

        hash ^= centroids.length;
        hash *= 1099511628211L;
        hash ^= sortState.indexType().ordinal();
        return hash;
    }

    @Unique
    private static float[] optimizer30000$getScratchBuffer(int requiredLength) {
        float[] current = FINGERPRINT_BUFFER.get();
        if (current.length >= requiredLength) {
            return current;
        }

        int capacity = Math.max(256, Integer.highestOneBit(requiredLength - 1) << 1);
        float[] resized = new float[capacity];
        FINGERPRINT_BUFFER.set(resized);
        return resized;
    }
}
