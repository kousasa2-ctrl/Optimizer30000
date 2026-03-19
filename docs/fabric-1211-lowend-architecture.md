# Optimizer30000: архитектура low-end мода для Minecraft 1.21.1 (Fabric)

## Цель

Профиль железа:

- Intel i5-2520M (2C/4T)
- Intel HD 3000
- 4-8 GB DDR3
- Java 21

Мод проектируется не как «ускоритель для всех», а как **жесткий low-end профиль**, который жертвует качеством ради:

1. стабильного frame time;
2. уменьшения количества аллокаций на клиенте и сервере;
3. снижения пиков по CPU и GC;
4. минимизации нагрузки на устаревший OpenGL-драйвер.

## Архитектурные модули

### 1. Bootstrap / Main Mod

Главный класс инициализирует:

- профиль оптимизации `OptimizationProfile`;
- ограничитель фоновых задач `ThreadBudgetGovernor`;
- кэш/пул для временных объектов секций и сортировки геометрии;
- low-spec флаги рендера;
- lazy/optional обход DFU.

### 2. Render Compatibility Layer

Отвечает за устаревшие GPU и Intel HD 3000:

- отключает post-processing;
- запрещает дорогие прозрачные/outline эффекты;
- переводит rebuild чанков в режим, близкий к `ChunkBuilderMode.NEARBY`;
- упрощает повторную сортировку прозрачной геометрии;
- уменьшает частоту обновления lightmap / smooth-lighting путей там, где возможно.

### 3. Memory Layer

Цель — уменьшить мусор на Java 21:

- не создавать временные массивы на каждый rebuild секции;
- использовать thread-local / ring-buffer пулы для сортировки индексов и временных ключей;
- переиспользовать контейнеры для `ChunkSection`/`RenderData` metadata;
- ограничить рост очередей `CompletableFuture` для чанков.

### 4. Thread Budget Layer

Для Sandy Bridge важна не абсолютная загрузка, а отсутствие длительного 100% использования всех 4 логических потоков.

Стратегия:

- 1 поток: render/client main;
- 1 поток: server/world logic;
- 1 поток: chunk meshing/generation;
- 1 поток: IO + bursts, но не постоянная heavy load.

Практически это означает:

- лимитировать executor для chunk rebuild/generation до `1..2` потоков;
- вводить budget по времени на кадр/тик;
- при перегреве/низком FPS снижать расстояние рендера сущностей и rebuild priority.

### 5. Entity / UI Optimizer

- агрессивное скрытие сущностей дальше 16-24 блоков;
- отдельный лимит для табличек, nameplates, bossbars, tooltip и текста;
- пропуск части HUD-рендера при low FPS;
- batching/short-circuit для text renderer, где это безопасно.

### 6. DFU Strategy

DFU полностью отключать опасно для старых миров, поэтому безопаснее 3 режима:

1. `SAFE` — обычное поведение;
2. `LAZY` — откладывать bootstrap DFU до первого реально нужного обращения;
3. `OFF_FOR_NEW_WORLDS` — отключать для новых инстансов/pack profiles, но не для legacy-сохранений.

## Предлагаемые Mixin-точки

### Клиент

- `GameRenderer` — отключение post-processing, снижение тяжелых графических путей.
- `WorldRenderer` — ограничение rebuild distance и entity render distance.
- `SectionBuilder` — оптимизация build/sort pipeline для эквивалента старого RenderChunk.
- `TextRenderer` / `InGameHud` — упрощение UI/text render.
- `MinecraftClient` — lazy bootstrap для DFU и применение low-spec профиля.

### Сервер / общая часть

- `MinecraftServer` — ограничение executor budget.
- `ServerChunkLoadingManager` / chunk task executors — ограничение фоновых задач.
- `ChunkSection` / связанные фабрики секций — переиспользование временных буферов и metadata.

## Практические параметры для целевого ноутбука

### Графика

- Post effects: OFF
- Entity distance cap: 16 блоков по умолчанию
- Block entity distance cap: 16 блоков
- Particles: MINIMAL
- Chunk rebuild threads: 1
- Transparency resort cooldown: 250-500 мс
- Clouds / vignette / nausea / distortion: OFF

### Память

- Java heap: 2048-3072 MB
- Цель мода: уменьшить short-lived allocations, а не просто увеличивать Xmx
- Fixed-size object pools вместо unbounded caching

### Потоки

- Chunk generation workers: 1
- Meshing workers: 1
- IO: 1 shared
- Никаких «занять все cores» режимов на постоянной основе

## Почему для 1.21.1 лучше миксинить не старый `RenderChunk`, а `SectionBuilder`

В старых версиях многие оптимизации делались через `RenderChunk`, но в актуальном Yarn/Fabric 1.21.1 цепочка chunk rendering смещена в сторону `ChunkBuilder` / `SectionBuilder`. Поэтому «сложный mixin для RenderChunk» в современной кодовой базе удобнее реализовывать как mixin в `SectionBuilder`, который:

- управляет сортировкой прозрачной геометрии;
- переиспользует временные массивы;
- пропускает resort, если камера не сдвинулась существенно;
- сокращает rebuild churn.



## Примеры дополнительных mixin-стратегий

### Aggressive Entity Rendering Distance

```java
@Mixin(WorldRenderer.class)
abstract class WorldRendererEntityDistanceMixin {
    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
    private void optimizer30000$cullFarEntities(Entity entity, double cameraX, double cameraY, double cameraZ,
                                                float tickProgress, MatrixStack matrices, VertexConsumerProvider consumers,
                                                CallbackInfo ci) {
        int limit = RenderBudgetState.entityDistanceLimit();
        if (entity.squaredDistanceTo(cameraX, cameraY, cameraZ) > (limit * limit)) {
            ci.cancel();
        }
    }
}
```

### UI/Text Optimizer

```java
@Mixin(TextRenderer.class)
abstract class TextRendererLowSpecMixin {
    @ModifyVariable(method = "draw", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private int optimizer30000$forceSimpleTextColor(int color) {
        return RenderBudgetState.legacyGpuMode() ? 0xFFE0E0E0 : color;
    }
}
```

Идея не в самом цвете, а в том, чтобы:

- убирать лишние shadow/outline path;
- отключать анимированные alpha/gradient эффекты;
- держать text path максимально линейным и без дополнительных проходов.

### Lazy DFU Startup

```java
@Mixin(MinecraftClient.class)
abstract class MinecraftClientDfuMixin {
    @WrapOperation(
        method = "<init>",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/datafixer/DataFixTypes;values()[Lnet/minecraft/datafixer/DataFixTypes;")
    )
    private DataFixTypes[] optimizer30000$lazyDfu(Operation<DataFixTypes[]> original) {
        return Optimizer30000Mod.profile().lazyDfu() ? new DataFixTypes[0] : original.call();
    }
}
```

Для production-версии безопаснее не «убивать» DFU целиком, а:

- разрешать его для старых миров;
- делать ленивый bootstrap по требованию;
- хранить флаг совместимости мира в отдельном metadata-файле.

### ChunkSection Memory Strategy

Вместо постоянного выделения временных контейнеров на rebuild:

```java
public final class ChunkSectionScratch {
    private static final ThreadLocal<ChunkSectionScratch> LOCAL = ThreadLocal.withInitial(ChunkSectionScratch::new);

    private final short[] visitedBlocks = new short[4096];
    private final int[] lightSamples = new int[4096];

    public static ChunkSectionScratch local() {
        return LOCAL.get();
    }
}
```

На Java 21 это дает лучший эффект, чем слепая попытка «победить GC» увеличением `-Xmx`: для DDR3-ноутбука критичнее сократить скорость появления short-lived мусора.
