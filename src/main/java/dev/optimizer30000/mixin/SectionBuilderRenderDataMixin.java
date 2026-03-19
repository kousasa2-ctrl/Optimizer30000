package dev.optimizer30000.mixin;

import dev.optimizer30000.runtime.RenderDataSortStateHolder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.chunk.SectionBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Хранилище кэша translucent sorting прямо внутри RenderData.
 *
 * Это важно для слабой DDR3-памяти: мы переиспользуем уже построенный SortState,
 * вместо создания новых временных структур при каждом мелком движении камеры.
 */
@Mixin(SectionBuilder.RenderData.class)
public abstract class SectionBuilderRenderDataMixin implements RenderDataSortStateHolder {
    @Unique private BuiltBuffer.SortState optimizer30000$cachedSortState;
    @Unique private long optimizer30000$cachedGeometryFingerprint;
    @Unique private double optimizer30000$lastSortX;
    @Unique private double optimizer30000$lastSortY;
    @Unique private double optimizer30000$lastSortZ;

    @Override
    public BuiltBuffer.SortState optimizer30000$getCachedSortState() {
        return this.optimizer30000$cachedSortState;
    }

    @Override
    public void optimizer30000$setCachedSortState(BuiltBuffer.SortState state) {
        this.optimizer30000$cachedSortState = state;
    }

    @Override
    public long optimizer30000$getCachedGeometryFingerprint() {
        return this.optimizer30000$cachedGeometryFingerprint;
    }

    @Override
    public void optimizer30000$setCachedGeometryFingerprint(long fingerprint) {
        this.optimizer30000$cachedGeometryFingerprint = fingerprint;
    }

    @Override
    public double optimizer30000$getLastSortX() {
        return this.optimizer30000$lastSortX;
    }

    @Override
    public double optimizer30000$getLastSortY() {
        return this.optimizer30000$lastSortY;
    }

    @Override
    public double optimizer30000$getLastSortZ() {
        return this.optimizer30000$lastSortZ;
    }

    @Override
    public void optimizer30000$setLastSortPos(double x, double y, double z) {
        this.optimizer30000$lastSortX = x;
        this.optimizer30000$lastSortY = y;
        this.optimizer30000$lastSortZ = z;
    }
}
