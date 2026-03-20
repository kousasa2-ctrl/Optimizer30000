package dev.optimizer30000.runtime;

import net.minecraft.client.render.BuiltBuffer;

public interface RenderDataSortStateHolder {
    BuiltBuffer.SortState optimizer30000$getCachedSortState();

    void optimizer30000$setCachedSortState(BuiltBuffer.SortState state);

    long optimizer30000$getCachedGeometryFingerprint();

    void optimizer30000$setCachedGeometryFingerprint(long fingerprint);

    double optimizer30000$getLastSortX();

    double optimizer30000$getLastSortY();

    double optimizer30000$getLastSortZ();

    void optimizer30000$setLastSortPos(double x, double y, double z);
}
