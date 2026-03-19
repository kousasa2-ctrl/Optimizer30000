package dev.optimizer30000.runtime;

import dev.optimizer30000.config.OptimizationProfile;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Ограничитель фонового параллелизма для 2C/4T Sandy Bridge.
 *
 * На i5-2520M Minecraft быстро уходит в троттлинг, если chunk rebuild и worldgen
 * одновременно занимают все логические потоки. Поэтому мы оборачиваем экзекуторы
 * семафорами и жестко держим их в диапазоне 1..2 permit'а.
 */
public final class ThreadBudgetGovernor {
    private final Semaphore chunkRebuildPermits;
    private final Semaphore worldgenPermits;
    private final int chunkRebuildLimit;
    private final int worldgenLimit;

    private volatile ExecutorService limitedMainWorkerExecutor;
    private volatile Executor limitedBackendExecutor;

    public ThreadBudgetGovernor(OptimizationProfile profile) {
        this.chunkRebuildLimit = Math.max(1, Math.min(2, profile.maxChunkRebuildThreads()));
        this.worldgenLimit = Math.max(1, Math.min(2, profile.maxWorldgenThreads()));
        this.chunkRebuildPermits = new Semaphore(this.chunkRebuildLimit);
        this.worldgenPermits = new Semaphore(this.worldgenLimit);
    }

    public int maxChunkRebuildThreads() {
        return this.chunkRebuildLimit;
    }

    public int maxWorldgenThreads() {
        return this.worldgenLimit;
    }

    /**
     * Ограничивает main worker executor для worldgen/IO работы.
     * Это снижает CPU contention и помогает удержать server/client thread отзывчивыми.
     */
    public ExecutorService limitMainWorkerExecutor(ExecutorService delegate) {
        ExecutorService existing = this.limitedMainWorkerExecutor;
        if (existing != null) {
            return existing;
        }

        ExecutorService wrapped = new PermittedExecutorService(delegate, this.worldgenPermits);
        this.limitedMainWorkerExecutor = wrapped;
        return wrapped;
    }

    /**
     * Ограничивает backend/render executor.
     * Для Intel HD 3000 это снижает пики по подготовке render work и уменьшает шанс
     * получить долгие frame-time spikes на фоне rebuild чанков.
     */
    public Executor limitBackendExecutor(Executor delegate) {
        Executor existing = this.limitedBackendExecutor;
        if (existing != null) {
            return existing;
        }

        Executor wrapped = command -> delegate.execute(() -> {
            this.chunkRebuildPermits.acquireUninterruptibly();
            try {
                command.run();
            } finally {
                this.chunkRebuildPermits.release();
            }
        });
        this.limitedBackendExecutor = wrapped;
        return wrapped;
    }

    private static final class PermittedExecutorService extends AbstractExecutorService {
        private final ExecutorService delegate;
        private final Semaphore permits;

        private PermittedExecutorService(ExecutorService delegate, Semaphore permits) {
            this.delegate = delegate;
            this.permits = permits;
        }

        @Override
        public void shutdown() {
            this.delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return this.delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return this.delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return this.delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return this.delegate.awaitTermination(timeout, unit);
        }

        @Override
        public void execute(Runnable command) {
            this.delegate.execute(() -> {
                this.permits.acquireUninterruptibly();
                try {
                    command.run();
                } finally {
                    this.permits.release();
                }
            });
        }
    }
}
