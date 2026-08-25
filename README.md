# Physical Structures API

Мод для **Minecraft 1.21.1 (NeoForge 21.1.227)**, который берёт обычный `.nbt`-шаблон
структуры (такой же, как создаётся Structure Block'ом) и превращает его в **физический
объект** — размещает блоки в мире и «собирает» их в подвижный под-уровень (sub-level)
мода [**Sable**](https://github.com/) (`dev.ryanhcode.sable`), давая структуре
самостоятельную физику и возможность перемещаться — как корабли в Create: Aeronautics.

Мод спроектирован в первую очередь как **библиотека / API для других модов**: он даёт
Java-программисту (или автору датапака) один вызов, чтобы заспавнить готовую физическую
структуру, и набор событий/крючков, чтобы встроить это в свою логику.

---

## Содержание

1. [Что делает мод и как это устроено](#1-что-делает-мод-и-как-это-устроено)
2. [Установка и зависимости](#2-установка-и-зависимости)
3. [Быстрый старт](#3-быстрый-старт)
4. [Способы задать структуру](#4-способы-задать-структуру)
   - [4.1. JSON-датапак (одиночная структура)](#41-json-датапак-одиночная-структура)
   - [4.2. JSON-датапак (составная структура — несколько NBT-частей)](#42-json-датапак-составная-структура--несколько-nbt-частей)
   - [4.3. Runtime-регистрация из Java](#43-runtime-регистрация-из-java)
5. [Java API подробно](#5-java-api-подробно)
   - [5.1. `PhysicalStructures` — главный фасад](#51-physicalstructures--главный-фасад)
   - [5.2. `PlacementOptions` — тонкая настройка размещения](#52-placementoptions--тонкая-настройка-размещения)
   - [5.3. `PlacementResult` — что вернул спавн](#53-placementresult--что-вернул-спавн)
   - [5.4. `StructureMetadata` — узнать размер без спавна](#54-structuremetadata--узнать-размер-без-спавна)
   - [5.5. `canPlace` — проверка «влезет ли» без изменения мира](#55-canplace--проверка-влезет-ли-без-изменения-мира)
6. [События (NeoForge Event Bus)](#6-события-neoforge-event-bus)
   - [6.1. `PhysicalStructurePlacingEvent` (до размещения, отменяемое)](#61-physicalstructureplacingevent-до-размещения-отменяемое)
   - [6.2. `PhysicalStructurePlacedEvent` (после размещения)](#62-physicalstructureplacedevent-после-размещения)
   - [6.3. `PhysicalStructureDespawnedEvent` (после удаления)](#63-physicalstructuredespawnedevent-после-удаления)
7. [Игровые механики: блок и предмет](#7-игровые-механики-блок-и-предмет)
   - [7.1. `StructureSpawnerBlock` — блок-спаунер](#71-structurespawnerblock--блок-спаунер)
   - [7.2. `StructureSpawnerItem` — предмет с привязанной структурой](#72-structurespawneritem--предмет-с-привязанной-структурой)
8. [Генерация в мире (Worldgen Feature)](#8-генерация-в-мире-worldgen-feature)
9. [Расширение: свои источники структур (`StructureSourceProvider`)](#9-расширение-свои-источники-структур-structuresourceprovider)
10. [Совместимость с Create Aeronautics Toolgun (`excraft:`)](#10-совместимость-с-create-aeronautics-toolgun-excraft)
11. [Практические сценарии использования](#11-практические-сценарии-использования)
12. [Частые вопросы / отладка](#12-частые-вопросы--отладка)
13. [Сборка из исходников](#13-сборка-из-исходников)
14. [Лицензия](#14-лицензия)

---

## 1. Что делает мод и как это устроено

Поток данных — от файла на диске до физического объекта в мире:

```
.nbt (ресурс мода/датапака ИЛИ произвольный файл на диске)
        │
        ▼
PhysicalStructureDefinition   — id, путь к NBT, поворот по умолчанию, задержка сборки
        │  регистрируется в...
        ▼
PhysicalStructureRegistry     — статический реестр id → Definition / Set
        │  читается через...
        ▼
PhysicalStructures.spawn(...) (публичный API) ──> внутренний движок StructurePlacer
        │
        ├─ 1. загружает StructureTemplate из NBT
        ├─ 2. template.placeInWorld(...)             — реальные блоки появляются в мире
        ├─ 3. собирает список непустых BlockPos
        ├─ 4. если задержка сборки == 0 → сразу собирает в sub-level
        │      иначе → ставит в очередь, досборка идёт по server-тику
        └─ 5. SubLevelAssemblyHelper.assembleBlocks(...) (Sable)
               → создаёт ServerSubLevel, присваивает UUID,
                 шлёт событие PhysicalStructurePlacedEvent
```

Ключевая идея: **блоки — это лишь промежуточный шаг**. Финальный результат — не набор
обычных блоков, а отдельный под-уровень Sable с этими блоками внутри, который может
двигаться, вращаться и физически взаимодействовать с миром, как корабль или транспорт.

---

## 2. Установка и зависимости

| Параметр | Значение |
|---|---|
| Minecraft | `1.21.1` |
| Мод-лоадер | NeoForge `21.1.227`+ |
| Java | 21 |

**Обязательные зависимости** (без них мод не запустится — указаны как `required` в
`neoforge.mods.toml`):

- `neoforge`
- `minecraft`
- `sable` (`dev.ryanhcode.sable`) — обеспечивает под-уровни (sub-levels) и физику структур
- `cbc_autotarget` — обязательная зависимость сборки (используется транзитивно)

**Опциональная зависимость** (определяется в рантайме, отсутствие не ломает загрузку):

- `create_aeronautics_toolgun` (Excraft Toolgun) — если установлен, мод автоматически
  подключает совместимость с форматом блупринтов `.excraft` (см. [раздел 10](#10-совместимость-с-create-aeronautics-toolgun-excraft)).

Из репозитория также видно, что при сборке используются (как `compileOnly`, то есть
только для компиляции — в рантайме нужны сами jar-файлы этих модов, если задействован
соответствующий функционал): Sable, `simulated`, Create, Flywheel, Ponder, Registrate.

### Установка как обычного мода (для игрока/сервера)

1. Установите NeoForge `21.1.227`+ для Minecraft `1.21.1`.
2. Положите `sable` и `cbc_autotarget` в папку `mods/` — без них игра не запустится.
3. Положите jar `physical_structures` в `mods/`.
4. (Опционально) Установите `create_aeronautics_toolgun`, если хотите использовать
   `.excraft`-блупринты через этот мод.

### Подключение как библиотеки (для разработчика другого мода)

Добавьте `physical_structures` в зависимости своей `build.gradle` (как `compileOnly` +
runtime-зависимость через NeoForge-мод-менеджмент) и укажите его как `required`/`optional`
зависимость в своём `neoforge.mods.toml`, в зависимости от того, обязателен ли он для
вашего мода.

---

## 3. Быстрый старт

Минимальный вызов из любого другого мода (например, из обработчика команды, ивента или
логики блока):

```java
import net.minecraft.resources.ResourceLocation;
import org.exampl.physical_structures.api.PhysicalStructures;

// level — ServerLevel, pos — BlockPos, где будет origin структуры
boolean ok = PhysicalStructures.spawnStructure(
        level, pos,
        ResourceLocation.fromNamespaceAndPath("mymod", "my_cannon"));
```

Это заспавнит структуру с id `mymod:my_cannon` (должна быть предварительно
зарегистрирована — см. [раздел 4](#4-способы-задать-структуру)), поставит блоки и
немедленно соберёт их в физический sub-level.

Более современный и подробный вариант (рекомендуется для новых интеграций):

```java
import net.minecraft.world.level.block.Rotation;
import org.exampl.physical_structures.api.PhysicalStructures;
import org.exampl.physical_structures.api.PlacementOptions;
import org.exampl.physical_structures.api.PlacementResult;

PlacementResult result = PhysicalStructures.spawn(level, pos,
        ResourceLocation.fromNamespaceAndPath("mymod", "my_cannon"),
        PlacementOptions.withRotation(Rotation.CLOCKWISE_90));

if (result.status() == PlacementResult.Status.SUCCESS_ASSEMBLED) {
    UUID handle = result.handle(); // сохраните, чтобы потом despawn-нуть
}
```

---

## 4. Способы задать структуру

Есть три способа зарегистрировать структуру под своим `id` (`ResourceLocation`), чтобы
её можно было заспавнить через API.

### 4.1. JSON-датапак (одиночная структура)

Положите файл по пути `data/<namespace>/physical_structures/<name>.json` — в датапак или
в `resources` собственного мода. Загрузчик (`PhysicalStructureJsonLoader`) — это обычный
`SimpleJsonResourceReloadListener`, он переживает команду `/reload`.

**Пример** (`data/mymod/physical_structures/my_cannon.json`):

```json
{
  "id":           "mymod:my_cannon",
  "nbt_location": "mymod:structures/my_cannon.nbt",
  "rotation":     "none",
  "assemble_delay_ticks": 0
}
```

| Поле | Обязательное | Описание |
|---|---|---|
| `id` | да | `ResourceLocation` структуры — то, что вы передаёте в API |
| `nbt_location` | да | `ResourceLocation` пути к `.nbt`-файлу в ресурсах (обычная `structure`-структура) |
| `rotation` | нет | `"none"` \| `"clockwise_90"` \| `"counterclockwise_90"` \| `"clockwise_180"` (по умолчанию `"none"`) |
| `assemble_delay_ticks` | нет | задержка в тиках перед сборкой в Sable sub-level после постановки блоков (по умолчанию `0` — сборка немедленно) |

Сам `.nbt` файл кладите как обычную структуру: `data/<namespace>/structures/<name>.nbt`
(это стандартный путь Minecraft для `StructureTemplate`, создаётся через Structure Block
в игре или сторонними редакторами).

### 4.2. JSON-датапак (составная структура — несколько NBT-частей)

Если структура собирается из нескольких отдельных `.nbt`-файлов со своими смещениями
(например, корпус + башня + пушка), используйте поле `parts` вместо `nbt_location`:

```json
{
  "id": "mymod:big_ship",
  "rotation": "none",
  "assemble_delay_ticks": 0,
  "parts": [
    { "nbt_location": "mymod:structures/big_ship_hull.nbt",  "offset": [0, 0, 0] },
    { "nbt_location": "mymod:structures/big_ship_tower.nbt", "offset": [4, 3, 2] }
  ]
}
```

Каждая часть в `parts` — объект с полями:

- `nbt_location` — путь к `.nbt`-файлу этой части;
- `offset` — массив из трёх целых чисел `[x, y, z]`, смещение части относительно общего
  `origin` при размещении (до применения поворота).

Спавнится составная структура через `spawnStructureSet(...)` (см.
[раздел 5.1](#51-physicalstructures--главный-фасад)) — все части ставятся и собираются
в один общий sub-level.

### 4.3. Runtime-регистрация из Java

Если структуры генерируются программно, лежат вне ресурсов мода (например, в отдельной
папке на диске сервера) или зависят от логики другого мода — регистрируйте их напрямую
в коде, без датапака:

```java
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import org.exampl.physical_structures.api.PhysicalStructures;

import java.nio.file.Path;

// Простой вариант — поворот NONE, без задержки:
PhysicalStructures.registerStructureFromFile(
        ResourceLocation.fromNamespaceAndPath("mymod", "generated_base"),
        Path.of("config/mymod/generated_structures/base_42.nbt"));

// Полный вариант — с поворотом и задержкой сборки:
PhysicalStructures.registerStructureFromFile(
        ResourceLocation.fromNamespaceAndPath("mymod", "generated_base"),
        Path.of("config/mymod/generated_structures/base_42.nbt"),
        Rotation.CLOCKWISE_180,
        20 // тиков задержки перед физической сборкой
);
```

Или через объект `PhysicalStructureDefinition` напрямую (например, если путь к NBT — это
ресурс, а не файл на диске):

```java
import org.exampl.physical_structures.api.PhysicalStructureDefinition;
import org.exampl.physical_structures.api.PhysicalStructures;

PhysicalStructures.registerStructure(new PhysicalStructureDefinition(
        ResourceLocation.fromNamespaceAndPath("mymod", "my_turret"),
        ResourceLocation.fromNamespaceAndPath("mymod", "structures/my_turret.nbt")
));
```

Runtime-зарегистрированные id **защищены от затирания** при `/reload` датапаков (в
отличие от id, пришедших из JSON, которые перезагружаются вместе с датапаком).

Убрать runtime-регистрацию:

```java
PhysicalStructures.unregisterStructure(ResourceLocation.fromNamespaceAndPath("mymod", "my_turret"));
```

---

## 5. Java API подробно

Все классы находятся в пакете `org.exampl.physical_structures.api`.

### 5.1. `PhysicalStructures` — главный фасад

Единственная точка входа, которую стоит использовать из другого мода.

#### Простой спавн

```java
// Спавн с настройками по умолчанию (поворот NONE, немедленная сборка)
PlacementResult spawn(ServerLevel level, BlockPos origin, ResourceLocation structureId);

// Спавн с полным контролем через PlacementOptions
PlacementResult spawn(ServerLevel level, BlockPos origin,
                       ResourceLocation structureId, PlacementOptions opts);
```

**Пример:**

```java
PlacementResult r = PhysicalStructures.spawn(level, pos,
        ResourceLocation.fromNamespaceAndPath("mymod", "my_cannon"),
        PlacementOptions.builder()
                .rotation(Rotation.CLOCKWISE_90)
                .postPlaceHook((blockPos, blockEntity) -> {
                    // например, рандомизировать лут в сундуке структуры
                    randomizeLoot(blockEntity);
                })
                .build());

if (r.status() == PlacementResult.Status.SUCCESS_ASSEMBLED) {
    myTracker.put(r.placementId(), r.handle());
}
```

#### `canPlace` — dry-run без изменения мира

```java
boolean canPlace(ServerLevel level, BlockPos origin, ResourceLocation structureId, Rotation rotation);
```

Проверяет наличие NBT, загруженность чанков и границы мира по Y, **не изменяя мир**:

```java
if (PhysicalStructures.canPlace(level, pos, id, Rotation.NONE)) {
    PhysicalStructures.spawn(level, pos, id);
}
```

#### `getMetadata` — метаданные без размещения

```java
Optional<StructureMetadata> getMetadata(ServerLevel level, ResourceLocation structureId);
```

```java
PhysicalStructures.getMetadata(level, id).ifPresent(meta -> {
    LOGGER.info("{}: {}x{}x{}", id, meta.sizeX(), meta.sizeY(), meta.sizeZ());
});
```

#### Составные структуры (несколько частей)

```java
boolean spawnStructureSet(ServerLevel level, BlockPos origin, ResourceLocation setId);
boolean spawnStructureSet(ServerLevel level, BlockPos origin, ResourceLocation setId, Rotation rotation);

PhysicalStructurePlacer.PlaceResultHandle spawnStructureSetWithHandle(
        ServerLevel level, BlockPos origin, ResourceLocation setId);
PhysicalStructurePlacer.PlaceResultHandle spawnStructureSetWithHandle(
        ServerLevel level, BlockPos origin, ResourceLocation setId, Rotation rotation);
```

#### Удаление структуры

```java
boolean despawnStructure(ServerLevel level, UUID handle);
```

`handle` — UUID, полученный из `PlacementResult.handle()` или из `handle()` события
`PhysicalStructurePlacedEvent`. Удаляет sub-level в Sable и шлёт
`PhysicalStructureDespawnedEvent`.

```java
boolean removed = PhysicalStructures.despawnStructure(level, savedHandle);
```

#### Реестр / инспекция

```java
Set<ResourceLocation> availableStructures();       // все зарегистрированные id
boolean isRegistered(ResourceLocation structureId); // есть ли конкретный id
```

```java
for (ResourceLocation id : PhysicalStructures.availableStructures()) {
    LOGGER.info("Известна структура: {}", id);
}
```

#### Runtime-регистрация (см. также [раздел 4.3](#43-runtime-регистрация-из-java))

```java
void registerStructure(PhysicalStructureDefinition def);
void registerStructureFromFile(ResourceLocation id, Path nbtFile, Rotation defaultRotation, int assembleDelayTicks);
void registerStructureFromFile(ResourceLocation id, Path nbtFile); // rotation=NONE, delay=0
boolean unregisterStructure(ResourceLocation structureId);
```

#### Устаревшие (legacy) методы

Оставлены для обратной совместимости, но помечены `@Deprecated` — новый код должен
использовать `spawn(...)` с `PlacementOptions`:

```java
@Deprecated boolean spawnStructure(ServerLevel level, BlockPos origin, ResourceLocation structureId);
@Deprecated boolean spawnStructure(ServerLevel level, BlockPos origin, ResourceLocation structureId, Rotation rotation);
@Deprecated PhysicalStructurePlacer.PlaceResult spawnStructureResult(ServerLevel level, BlockPos origin, ResourceLocation structureId);
```

---

### 5.2. `PlacementOptions` — тонкая настройка размещения

Единый объект-конфигурация вместо разрастающегося списка перегрузок. Собирается через
builder или готовые фабрики.

```java
// Только поворот, всё остальное по умолчанию
PlacementOptions opts = PlacementOptions.withRotation(Rotation.CLOCKWISE_90);

// Полная настройка
PlacementOptions opts = PlacementOptions.builder()
        .rotation(Rotation.CLOCKWISE_90)
        .assembleDelayTicksOverride(20)                  // переопределить задержку сборки
        .snapToHeightmap(Heightmap.Types.WORLD_SURFACE_WG) // "прилипание" к поверхности
        .postPlaceHook((pos, blockEntity) -> randomizeLoot(blockEntity))
        .spawnerPos(spawnerBlockPos)                      // позиция инициировавшего блока (для события)
        .blocksOnlyNoAssemble(true)                        // только блоки, без физической сборки
        .build();
```

| Опция | По умолчанию | Назначение |
|---|---|---|
| `rotation(Rotation)` | `NONE` | поворот структуры при размещении |
| `assembleDelayTicksOverride(int)` | `-1` (не переопределять) | переопределяет задержку сборки, заданную в JSON/Definition |
| `spawnerPos(BlockPos)` | `null` | позиция инициировавшего блока-спаунера — попадёт в `PhysicalStructurePlacedEvent.spawnerPos()` |
| `snapToHeightmap(Heightmap.Types)` | `null` | если задано, Y координата origin автоматически поднимается до указанной heightmap перед размещением |
| `postPlaceHook(BiConsumer<BlockPos, BlockEntity>)` | `null` | вызывается для каждой `BlockEntity` сразу после расстановки блоков, **до** сборки в Sable — удобно для рандомизации лута, установки NBT и т.п. |
| `blocksOnlyNoAssemble(boolean)` | `false` | если `true` — ставит только блоки без физической Sable-сборки (для статичных декораций) |
| `deferAssemblyToServerThread(boolean)` | `false` | откладывает сборку на ближайший server-тик через очередь, даже при нулевой задержке — нужно для безопасного вызова из ворлдгена |

**Готовый пресет для ворлдгена:**

```java
PlacementOptions.forWorldgen(Rotation rotation)
```

Ставит `snapToHeightmap = WORLD_SURFACE_WG` и `deferAssemblyToServerThread = true` — блоки
ставятся сразу, а Sable-сборка откладывается на server-тик, когда чанк гарантированно
загружен (прямой вызов Sable вне server-thread небезопасен).

```java
PhysicalStructures.spawn(level, origin, id, PlacementOptions.forWorldgen(Rotation.NONE));
```

---

### 5.3. `PlacementResult` — что вернул спавн

Детальный результат попытки размещения, приходит на замену грубому трёхзначному
`PlaceResult`.

```java
public enum Status {
    SUCCESS_ASSEMBLED,   // структура размещена и немедленно собрана — handle() доступен
    SUCCESS_PENDING,     // блоки поставлены, сборка Sable отложена (задержка > 0)
    SUCCESS_BLOCKS_ONLY, // только блоки без физики (blocksOnlyNoAssemble)
    UNKNOWN_ID,          // id не найден в реестре
    LOAD_FAILED,         // NBT не найден или повреждён
    ASSEMBLY_FAILED,     // блоки поставлены, но Sable не смог собрать sub-level
    CANCELLED            // PhysicalStructurePlacingEvent отменил размещение
}
```

Пример обработки всех веток:

```java
PlacementResult r = PhysicalStructures.spawn(level, origin, id, opts);
switch (r.status()) {
    case SUCCESS_ASSEMBLED   -> { UUID handle = r.handle(); /* уже в мире */ }
    case SUCCESS_PENDING     -> { /* блоки есть, sub-level соберётся позже —
                                       ловите PhysicalStructurePlacedEvent по r.placementId() */ }
    case SUCCESS_BLOCKS_ONLY -> { /* декорация без физики */ }
    case UNKNOWN_ID           -> LOGGER.warn("Неизвестный id структуры: {}", id);
    case LOAD_FAILED          -> LOGGER.error("Не удалось загрузить NBT: {}", r.errorMessage());
    case ASSEMBLY_FAILED      -> LOGGER.error("Sable не смог собрать: {}", r.errorMessage());
    case CANCELLED            -> { /* отменено слушателем PhysicalStructurePlacingEvent */ }
}
```

Полезные методы:

```java
r.isSuccess();     // true для любого из трёх SUCCESS_*
r.handle();         // UUID sub-level (только при SUCCESS_ASSEMBLED), иначе null
r.placementId();    // стабильный UUID этой попытки — совпадает с id в событиях
r.errorMessage();   // причина при LOAD_FAILED / ASSEMBLY_FAILED
r.toLegacy();        // конвертация в старый трёхзначный PlaceResult
```

`placementId()` особенно полезен при `SUCCESS_PENDING`: он генерируется **до** постановки
в очередь и придёт в `PhysicalStructurePlacedEvent.placementId()` после фактической
сборки — так можно однозначно связать конкретный вызов `spawn(...)` с результатом даже
при задержке (без гонки по совпадению позиции/времени).

---

### 5.4. `StructureMetadata` — узнать размер без спавна

```java
public record StructureMetadata(
        ResourceLocation id,
        int sizeX, int sizeY, int sizeZ,
        Rotation defaultRotation,
        int assembleDelayTicks,
        boolean isComposite
) {}
```

Загружает только NBT-заголовок (размер), не изменяя мир:

```java
PhysicalStructures.getMetadata(level, id).ifPresent(m -> {
    LOGGER.info("{} размер: {}×{}×{}, поворот по умолч.: {}",
            id, m.sizeX(), m.sizeY(), m.sizeZ(), m.defaultRotation());
});
```

Полезно, например, для генератора подземелий: сначала узнать габариты структуры, затем
решить, влезет ли она на выбранное место, прежде чем реально размещать.

---

### 5.5. `canPlace` — проверка «влезет ли» без изменения мира

См. [раздел 5.1](#51-physicalstructures--главный-фасад) выше — `canPlace(level, origin, id, rotation)`
проверяет наличие NBT, загруженность чанков и границы мира по высоте.

---

## 6. События (NeoForge Event Bus)

Все события находятся в пакете `org.exampl.physical_structures.api.event` и публикуются
на **обычном game event bus** (`NeoForge.EVENT_BUS`), а не на mod event bus:

```java
NeoForge.EVENT_BUS.register(MyEventListener.class);
```

### 6.1. `PhysicalStructurePlacingEvent` (до размещения, отменяемое)

Файрится **до** того, как структура будет поставлена. Позволяет:

- **отменить** размещение (например, в защищённой зоне мода claims);
- **изменить** позицию или поворот в последний момент;
- **прочитать** `placementId()`, который придёт и в `PhysicalStructurePlacedEvent`.

```java
@SubscribeEvent
public static void beforePlace(PhysicalStructurePlacingEvent event) {
    if (ClaimsAPI.isProtected(event.level(), event.origin())) {
        event.setCanceled(true);
        return;
    }
    // например, принудительно запретить любой поворот, кроме NONE
    event.setRotation(Rotation.NONE);
}
```

Доступные методы:

```java
ServerLevel level();
BlockPos origin();                          // можно изменить через setOrigin(pos)
Rotation rotation();                        // можно изменить через setRotation(rotation)
PhysicalStructureDefinition definition();
PlacementOptions options();
UUID placementId();
void setOrigin(BlockPos pos);
void setRotation(Rotation rotation);
void setCanceled(boolean cancel);           // унаследовано от ICancellableEvent
```

> Используется NeoForge 21.1 / EventBus 7.x: отменяемость реализуется через
> `ICancellableEvent`, а не через устаревшую аннотацию `@Cancelable`.

### 6.2. `PhysicalStructurePlacedEvent` (после размещения)

Файрится после успешного размещения **и** сборки (или постановки в очередь, в зависимости
от статуса — см. `PlacementResult`).

```java
@SubscribeEvent
public static void onPlaced(PhysicalStructurePlacedEvent e) {
    if (e.structureId().equals(MY_ID)) {
        e.level().playSound(null, e.origin(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1f, 1f);
    }
}
```

Доступные методы:

```java
ServerLevel level();
BlockPos origin();
PhysicalStructureDefinition definition();
ResourceLocation structureId();
int blockCount();
@Nullable BlockPos spawnerPos();   // позиция инициировавшего блока-спаунера, если был
@Nullable UUID handle();           // UUID sub-level для despawnStructure(...); null при blocksOnly
UUID placementId();                // совпадает с placementId() из PhysicalStructurePlacingEvent
```

### 6.3. `PhysicalStructureDespawnedEvent` (после удаления)

Симметричное событие к `PhysicalStructurePlacedEvent`, файрится при вызове
`PhysicalStructures.despawnStructure(...)`.

```java
@SubscribeEvent
public static void onDespawn(PhysicalStructureDespawnedEvent e) {
    MapMarkers.remove(e.handle());
    e.level().playSound(null, e.origin(), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1f, 1f);
}
```

```java
ServerLevel level();
UUID handle();
ResourceLocation structureId();
@Nullable BlockPos origin();  // может быть null, если запись не сохранила позицию
```

---

## 7. Игровые механики: блок и предмет

Мод поставляет два готовых игровых способа триггерить размещение — оба используют тот же
публичный API, что доступен и стороннему коду.

### 7.1. `StructureSpawnerBlock` — блок-спаунер

`BlockEntity` этого блока хранит `structure_id` (сохраняется в NBT блока, переживает
`/reload` и перезагрузку чанка). При правом клике игроком или программном вызове блок:

1. проверяет, есть ли зарегистрированный `StructureSourceProvider` для данного id
   (например, `excraft:` для Toolgun) — если да, делегирует ему;
2. иначе ищет id в собственном реестре `PhysicalStructureRegistry`;
3. при успехе — размещает структуру над собой и удаляет сам блок-спаунер.

**Программное размещение блока + структуры «в один вызов»:**

```java
StructureSpawnerBlock.placeAndTrigger(serverLevel, pos,
        ResourceLocation.fromNamespaceAndPath("mymod", "my_cannon"));
```

**Ручной триггер уже существующего блока-спаунера** (например, из редстоун-логики или
своей команды):

```java
if (level.getBlockState(pos).getBlock() instanceof StructureSpawnerBlock spawner) {
    spawner.trigger(serverLevel, pos, playerOrNull);
}
```

### 7.2. `StructureSpawnerItem` — предмет с привязанной структурой

`BlockItem`, который при установке в мир создаёт `StructureSpawnerBlock` с уже
предустановленным `structure_id` (хранится в data component). Тултип предмета
показывает привязанный id.

**Выдать игроку предмет, который заспавнит конкретную структуру:**

```java
ItemStack stack = StructureSpawnerItem.forStructure(
        ResourceLocation.fromNamespaceAndPath("mymod", "my_cannon"));
player.getInventory().add(stack);
```

Игрок ставит этот предмет как обычный блок — при установке автоматически создаётся
блок-спаунер с этим id, готовый к активации правым кликом.

> В репозитории также присутствует более ранний/альтернативный класс
> `org.exampl.physical_structures.init.SpawnStructureItem` — обычный `Item` (не
> `BlockItem`), который при клике по грани блока **сразу** ставит структуру через
> `PhysicalStructurePlacer.place(...)`, без промежуточного блока-спаунера. Он жёстко
> привязан к одному `structureId`, заданному в конструкторе — удобен, если вы
> регистрируете отдельный предмет на каждую структуру вручную.

---

## 8. Генерация в мире (Worldgen Feature)

Мод регистрирует собственный тип `Feature` — `physical_structures:physical_structure` —
который можно использовать в стандартном датапак-ворлдгене (`configured_feature` +
`placed_feature`), чтобы структуры физически появлялись при генерации чанков.

**`configured_feature`** (`data/<ns>/worldgen/configured_feature/<name>.json`):

```json
{
  "type": "physical_structures:physical_structure",
  "config": {
    "structure_id": "physical_structures:gun6",
    "rotation": "none",
    "snap_to_surface": true,
    "assemble_delay_ticks": 5
  }
}
```

| Поле конфига | Обязательное | По умолчанию | Описание |
|---|---|---|---|
| `structure_id` | да | — | id структуры (должна быть зарегистрирована) |
| `rotation` | нет | `NONE` | поворот при генерации |
| `snap_to_surface` | нет | `false` | привязать Y к поверхности мира |
| `assemble_delay_ticks` | нет | `1` | задержка перед Sable-сборкой (важно для ворлдгена — прямой вызов Sable вне server-thread небезопасен) |

**`placed_feature`** (`data/<ns>/worldgen/placed_feature/<name>.json`) — стандартный
Minecraft-механизм размещения с фильтрами (частота, привязка к биому, heightmap и т.д.):

```json
{
  "feature": "physical_structures:gun6_worldgen",
  "placement": [
    { "type": "minecraft:rarity_filter", "chance": 200 },
    { "type": "minecraft:in_square" },
    { "type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG" },
    { "type": "minecraft:biome" }
  ]
}
```

Дальше подключите `placed_feature` к нужным биомам через тег
`data/<ns>/worldgen/biome_modifier/*.json` или напрямую в биомах, как для любой другой
ванильной или модовой фичи.

**Программный аналог из Java** (если вызываете размещение не через ChunkGenerator/Feature,
а вручную из своего кода генерации) — используйте готовый пресет:

```java
PhysicalStructures.spawn(level, origin, id, PlacementOptions.forWorldgen(Rotation.NONE));
```

---

## 9. Расширение: свои источники структур (`StructureSourceProvider`)

Если вы хотите, чтобы `StructureSpawnerBlock` и в целом инфраструктура мода умели
работать с вашим собственным форматом «блупринтов» (не `.nbt` из `PhysicalStructureRegistry`,
а что-то своё) — зарегистрируйте провайдера, не трогая ядро мода:

```java
public class MyBlueprintProvider implements StructureSourceProvider {

    @Override
    public String providerId() { return "mymod:blueprint_provider"; }

    @Override
    public boolean supports(ResourceLocation id) {
        // например, отдаём все id из своего namespace
        return "myblueprints".equals(id.getNamespace());
    }

    @Override
    public boolean place(ServerLevel level, BlockPos origin, ResourceLocation id, @Nullable Player player) {
        return MyBlueprintSystem.spawn(level, origin, id.getPath());
    }
}
```

Регистрация (например, в конструкторе своего мода или на `FMLCommonSetupEvent`):

```java
StructureSourceProviderRegistry.register(new MyBlueprintProvider());
```

После этого `id` вида `myblueprints:whatever` будет автоматически перехватываться вашим
провайдером везде, где мод проверяет источник структуры — в первую очередь в
`StructureSpawnerBlock.trigger(...)`. Провайдеры проверяются **в порядке регистрации**,
побеждает первый, чей `supports(id)` вернёт `true`.

Полезные статические методы реестра:

```java
StructureSourceProviderRegistry.isHandled(id);              // есть ли провайдер для id
StructureSourceProviderRegistry.place(level, origin, id, player); // разместить через найденный провайдер
StructureSourceProviderRegistry.registeredProviderIds();    // список id всех провайдеров (для отладки)
```

---

## 10. Совместимость с Create Aeronautics Toolgun (`excraft:`)

Если в сборке присутствует мод `create_aeronautics_toolgun`, `physical_structures`
автоматически регистрирует `ExcraftCompat` как `StructureSourceProvider` для namespace
`excraft:`. Это позволяет использовать в `StructureSpawnerBlock` id вида
`excraft:my_ship`, которые указывают на файлы `<gamedir>/blueprints/*.excraft`.

Технически мост делегирует размещение встроенной команде самого Toolgun:

```
/aerotoolgun print_blueprint <file> <pos>
```

выполняемой от имени `CommandSourceStack` сервера (или игрока, если он известен —
это важно для физических Create-схем, которым нужен игровой контекст).

**Практический момент для интеграторов:** если вы вызываете размещение структуры с
namespace `excraft:` программно и у вас есть реальный `ServerPlayer` (а не `null`) —
передавайте его. Это включает поддержку Create-физических схем (`sub_levels` формата
`CreatePhysicalSchematicSupport`), которые **не могут** быть размещены без игрового
контекста — таково ограничение самого Toolgun, а не моста. Обычные `.excraft`-блупринты
(формат `SubLevelFileStore`) работают и без игрока, полностью на стороне сервера.

> Подробный технический разбор (декомпиляция, ограничения байткода Toolgun 0.2.0,
> альтернативы через рефлексию) — см. `src/API_INTEGRATION_NOTES.md`, раздел 7, внутри
> репозитория. Это внутренняя инженерная заметка, а не часть публичного API.

---

## 11. Практические сценарии использования

### Сценарий A: «Спавни структуру по команде»

```java
@SubscribeEvent
public static void onCommand(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("spawncannon")
        .requires(src -> src.hasPermission(2))
        .executes(ctx -> {
            ServerLevel level = ctx.getSource().getLevel();
            BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
            PlacementResult r = PhysicalStructures.spawn(level, pos,
                    ResourceLocation.fromNamespaceAndPath("mymod", "my_cannon"));
            ctx.getSource().sendSuccess(() -> Component.literal("Результат: " + r), true);
            return r.isSuccess() ? 1 : 0;
        }));
}
```

### Сценарий B: «Проверить размер и место перед спавном в подземелье»

```java
ResourceLocation id = ResourceLocation.fromNamespaceAndPath("dungeonmod", "boss_room");
Optional<StructureMetadata> meta = PhysicalStructures.getMetadata(level, id);

meta.ifPresentOrElse(m -> {
    if (roomFits(m.sizeX(), m.sizeY(), m.sizeZ())
            && PhysicalStructures.canPlace(level, candidatePos, id, Rotation.NONE)) {
        PhysicalStructures.spawn(level, candidatePos, id);
    } else {
        // выбрать другое место или пропустить
    }
}, () -> LOGGER.warn("Структура {} не зарегистрирована", id));
```

### Сценарий C: «Защищённые territory-зоны отменяют спавн»

```java
@SubscribeEvent
public static void onPlacing(PhysicalStructurePlacingEvent e) {
    if (MyClaimsMod.isProtected(e.level(), e.origin())) {
        e.setCanceled(true);
    }
}
```

### Сценарий D: «Рандомизировать лут сразу после расстановки блоков»

```java
PhysicalStructures.spawn(level, pos, structureId,
    PlacementOptions.builder()
        .postPlaceHook((blockPos, be) -> {
            if (be instanceof ChestBlockEntity chest) {
                LootTable table = level.getServer().reloadableRegistries()
                        .getLootTable(MyLootTables.STRUCTURE_LOOT);
                chest.setLootTable(MyLootTables.STRUCTURE_LOOT_KEY, level.random.nextLong());
            }
        })
        .build());
```

### Сценарий E: «Отслеживать структуру и уметь удалить её позже»

```java
UUID handle;

void spawnAndTrack(ServerLevel level, BlockPos pos) {
    PlacementResult r = PhysicalStructures.spawn(level, pos, MY_STRUCTURE_ID);
    if (r.status() == PlacementResult.Status.SUCCESS_ASSEMBLED) {
        this.handle = r.handle();
    }
}

void removeIfTracked(ServerLevel level) {
    if (handle != null && PhysicalStructures.despawnStructure(level, handle)) {
        handle = null;
    }
}
```

Если сборка может быть отложенной (`SUCCESS_PENDING`), ловите `handle` из события:

```java
@SubscribeEvent
public static void onPlaced(PhysicalStructurePlacedEvent e) {
    if (e.placementId().equals(myPendingPlacementId) && e.handle() != null) {
        myTracker.put(e.placementId(), e.handle());
    }
}
```

### Сценарий F: «Выдать игроку предмет-«чертёж», ставящий структуру»

```java
ItemStack blueprintItem = StructureSpawnerItem.forStructure(
        ResourceLocation.fromNamespaceAndPath("mymod", "watchtower"));
player.getInventory().add(blueprintItem);
```

### Сценарий G: «Структуры в мировой генерации»

См. [раздел 8](#8-генерация-в-мире-worldgen-feature) — датапак с `configured_feature` +
`placed_feature`, привязанный к нужным биомам через `biome_modifier`.

---

## 12. Частые вопросы / отладка

**Структура не спавнится, `UNKNOWN_ID`.**
Проверьте, что id действительно зарегистрирован: `PhysicalStructures.isRegistered(id)`
или `PhysicalStructures.availableStructures()`. Частые причины: опечатка в namespace,
JSON не подхватился (проверьте путь `data/<ns>/physical_structures/<name>.json` и логи
на предмет `[PhysicalStructures] Bad JSON`), либо runtime-регистрация не была вызвана до
попытки спавна.

**`LOAD_FAILED`.**
NBT-файл не найден по указанному `nbt_location`/пути или повреждён. Проверьте, что файл
реально лежит по пути `data/<ns>/structures/<name>.nbt` внутри ресурсов/датапака (для
JSON-регистрации) либо что переданный `Path` действительно существует на диске (для
`registerStructureFromFile`).

**`ASSEMBLY_FAILED`.**
Блоки поставлены, но Sable не смог собрать sub-level. Проверьте `errorMessage()` в
`PlacementResult`, а также что `sable` действительно загружен и не выдаёт ошибок в логе.

**`CANCELLED`.**
Какой-то слушатель `PhysicalStructurePlacingEvent` вызвал `setCanceled(true)`. Обычно это
делает свой же код защиты территории — ищите обработчики этого события в загруженных модах.

**Спавн из ворлдгена «зависает» или падает.**
Используйте `PlacementOptions.forWorldgen(rotation)` — он откладывает Sable-сборку на
server-тик, потому что прямой вызов Sable вне server-thread небезопасен во время
генерации чанков.

**Create-физические схемы через `excraft:` не размещаются программно.**
Это ограничение самого Toolgun: безыгроковое размещение `CreatePhysicalSchematicSupport`
всегда бросает `IOException`. Передайте реального `ServerPlayer`, если он у вас есть —
см. [раздел 10](#10-совместимость-с-create-aeronautics-toolgun-excraft).

---

## 13. Сборка из исходников

```bash
git clone https://github.com/IYourOverlord/Physical_structures-API.git
cd Physical_structures-API
./gradlew build
```

Собранный jar появится в `build/libs/`. Для запуска тестового клиента/сервера в среде
разработки (после `./gradlew build` и импорта проекта в IDE с поддержкой NeoForge
ModDev):

```bash
./gradlew runClient
./gradlew runServer
```

Обратите внимание: `build.gradle` использует `compileOnly fileTree(dir: 'libs', ...)`
для зависимостей Sable/Create/Flywheel/Ponder/Registrate — соответствующие jar-файлы
нужно вручную положить в папку `libs/` перед сборкой, если их там ещё нет.

---

## 14. Лицензия

См. файл [`LICENSE`](LICENSE) в корне репозитория.
