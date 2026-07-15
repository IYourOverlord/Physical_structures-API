# Physical Structures — как устроен мод и как с ним взаимодействовать

> Внутренняя заметка по архитектуре `physical_structures` (NeoForge), составленная по
> исходникам из `src/main/java/org/exampl/physical_structures`. Цель: объяснить, как
> мод работает изнутри, что доступно сторонним модам уже сейчас, и что стоит улучшить,
> чтобы интеграция была проще.

---

## 1. Что вообще делает мод

`physical_structures` берёт `.nbt`-шаблон (структуру Minecraft, как в Structure Block),
ставит его блоки в мире и затем «собирает» их в **под-уровень (sub-level)** стороннего
мода **Sable** (`dev.ryanhcode.sable`) — это и даёт структурам физику/перемещение
(подвижные конструкции, как корабли Create: Aeronautics). После сборки летит ивент
`PhysicalStructurePlacedEvent`.

Дополнительно мод опционально умеет понимать `.excraft`-блупринты мода
`create_aeronautics_toolgun`, делегируя им физическое размещение через рефлексию.

### Жёсткие зависимости (`neoforge.mods.toml`)
- `neoforge`, `minecraft` — естественно;
- `cbc_autotarget` — обязательная зависимость (назначение не описано в коде, нужно сверить с другим модом);
- `sable` — обязательная, мод без неё не запустится.

### Опциональная зависимость
- `create_aeronautics_toolgun` — определяется в рантайме через `ModList.get().isLoaded(...)`,
  весь мост к нему сделан через **рефлексию** (`ToolgunPlacementBridge`), поэтому отсутствие
  Toolgun не ломает загрузку.

---

## 2. Поток данных: от файла до блоков в мире

```
.nbt (resource ИЛИ файл на диске)
        │
        ▼
PhysicalStructureDefinition  (id, путь к NBT, поворот по умолчанию, assembleDelayTicks)
        │   регистрируется в...
        ▼
PhysicalStructureRegistry    (статический реестр id -> Definition / Set)
        │   читается через...
        ▼
PhysicalStructurePlacer (API) ──> StructurePlacer (внутренний движок)
        │
        ├─ 1. загружает StructureTemplate из NBT (loadTemplate)
        ├─ 2. template.placeInWorld(...)            — реальные блоки в мире
        ├─ 3. собирает список не-воздушных BlockPos
        ├─ 4. если assembleDelayTicks == 0 → performAssembly() сразу
        │      иначе → кладёт в PendingAssemblyQueue, тикается ServerTickListener'ом
        └─ 5. performAssembly(): SubLevelAssemblyHelper.assembleBlocks(...) (Sable)
               → создаёт ServerSubLevel, регистрирует UUID в SpawnedStructureRegistry,
                 шлёт PhysicalStructurePlacedEvent на game-bus
```

### Откуда берутся определения структур
1. **JSON datapack** — `data/<namespace>/physical_structures/*.json`, грузится
   `PhysicalStructureJsonLoader` (обычный `SimpleJsonResourceReloadListener`, переживает `/reload`).
   Поддерживает одиночные структуры (`nbt_location`) и составные (`parts`).
2. **Хардкод в конструкторе мода** — `PhysicalStructures.java` регистрирует `gun6` напрямую
   (`gun4` — только через JSON, что несимметрично, см. раздел "Проблемы").
3. **Runtime-регистрация через публичный API** — `PhysicalStructures.registerStructure(...)`
   / `registerStructureFromFile(...)`, id защищены от затирания при `/reload` (`RUNTIME_IDS`).

### Два способа триггера в мире
- **`StructureSpawnerBlock`** — блок с `BlockEntity`, хранящей `structure_id`. ПКМ или
  программный вызов `trigger(...)` ставит структуру над блоком и удаляет сам спавнер.
  Поддерживает namespace `excraft:` для делегирования в Toolgun.
- **`SpawnStructureItem`** — предмет с зашитым `structureId`, ставит структуру по клику
  на грань блока. Использует тот же `PhysicalStructurePlacer`, что и любой другой мод.

---

## 3. Что уже можно вызывать из другого мода (текущий публичный API)

Главная точка входа — `org.exampl.physical_structures.api.PhysicalStructures` (фасад):

| Метод | Назначение |
|---|---|
| `spawnStructure(level, pos, id[, rotation])` | Спавн одиночной структуры, `boolean` |
| `spawnStructureResult(...)` | То же, но возвращает `PlaceResult` (enum: `SUCCESS / UNKNOWN_ID / LOAD_FAILED`) |
| `spawnStructureWithHandle(...)` | + UUID Sable sub-level для последующего удаления |
| `spawnStructureSet(...)` / `...WithHandle` | Составные структуры из нескольких NBT-частей |
| `registerStructure(def)` / `registerStructureFromFile(...)` | Runtime-регистрация без датапака |
| `unregisterStructure(id)` | Удаление runtime-структуры |
| `despawnStructure(level, handle)` | Удаление ранее собранной структуры по UUID |
| `availableStructures()` | `Set<ResourceLocation>` всех id |
| `isRegistered(id)` | Проверка наличия |

Плюс ивент `PhysicalStructurePlacedEvent` (game bus) — даёт `level`, `origin`,
`definition`, `blockCount`, `spawnerPos` (nullable).

Это уже даёт стороннему моду «спавни структуру одной строкой», что хорошо. Но при
ближайшем рассмотрении заметны дыры — см. ниже.

---

## 4. Анализ: что мешает лёгкой интеграции и что стоит улучшить

### 4.1. Нет способа узнать metadata структуры, не размещая её
`availableStructures()` отдаёт только `Set<ResourceLocation>` — голые id. Чтобы узнать
размер структуры, её поворот по умолчанию, есть ли delay сборки — стороннему моду
**негде** это посмотреть без копания во внутренних классах (`PhysicalStructureRegistry`
не публичный per se, но `allDefinitions()` есть; а вот размер (Vec3i) NBT нигде не кэшируется
и не отдаётся — нужно грузить и парсить NBT самому).

**Предложение:** добавить `PhysicalStructures.getMetadata(id)` → record с
`id, size (Vec3i), defaultRotation, assembleDelayTicks, isComposite`. Размер можно
закэшировать при первой загрузке шаблона в `StructurePlacer.loadTemplate`, чтобы не
гонять файл туда-сюда.

### 4.2. Нет «сухого прогона» (dry-run / валидации) перед размещением
Сторонний мод (например, генератор подземелий или строительный плагин) часто хочет
**проверить**, влезет ли структура в место (collision-check), не трогая мир. Сейчас
единственный способ узнать результат — реально вызвать `place(...)`.

**Предложение:** `PhysicalStructurePlacer.canPlace(level, origin, id, rotation)` —
загружает шаблон, считает bounding box, проверяет на пересечение с защищёнными зонами/
существующими структурами, не модифицируя мир.

### 4.3. `PlaceResult` слишком грубый
Три состояния (`SUCCESS / UNKNOWN_ID / LOAD_FAILED`) не различают:
- NBT не найден на диске vs повреждён;
- сборка Sable не удалась (sub-level == null) — это сейчас тихо проглатывается
  (`handle` просто `null`, но `PlaceResult` всё равно `SUCCESS`);
- структура успешно поставлена, но `assembleDelayTicks > 0` — тоже `SUCCESS`, хотя
  физической сборки ещё не произошло.

**Предложение:** разделить `PlaceResult` на `SUCCESS_ASSEMBLED`, `SUCCESS_PENDING`,
`UNKNOWN_ID`, `LOAD_FAILED`, `ASSEMBLY_FAILED` — чтобы интегратор мог осмысленно
реагировать (например, не пытаться `despawnStructure` сразу для pending-сборки).

### 4.4. Нет колбэка/Future для отложенной сборки
`spawnStructureWithHandle` возвращает `handle == null`, если `assembleDelayTicks() > 0`,
и стороннему моду **негде узнать**, когда сборка всё же произойдёт — нужно либо ловить
`PhysicalStructurePlacedEvent` и сравнивать `origin`/`structureId` (ненадёжно при двух
одинаковых структурах рядом), либо городить свой поллинг.

**Предложение:** генерировать собственный коррелирующий `UUID` (placement id) **до**
постановки в очередь, отдавать его сразу из `spawnStructureWithHandle`, и прокидывать тот
же id в `PhysicalStructurePlacedEvent` как `placementId()`. Это даёт стабильный ключ
для сопоставления независимо от тайминга.

### 4.5. Нет события "перед размещением" (pre-place hook)
Сейчас есть только `PhysicalStructurePlacedEvent` (после факта). Сторонний мод не может:
- отменить размещение (например, в защищённой зоне claims-мода);
- подменить позицию/поворот в последний момент;
- обогатить событие собственными данными.

**Предложение:** добавить `Cancelable` `PhysicalStructurePlacingEvent` (до
`template.placeInWorld`), с `setCanceled(true)` для блокировки, и возможностью
переопределить `origin`/`rotation` через сеттеры.

### 4.6. Реестр Set'ов (`PhysicalStructureSet`) не имеет publicly-friendly билдера
`StructurePart`/`PhysicalStructureSet` — голые record'ы, что нормально, но собрать составную
структуру программно из другого мода неудобно: нет билдера, нет валидации пересечения частей,
нет способа добавить часть в уже зарегистрированный сет без пересборки всего списка.

**Предложение:** `PhysicalStructureSet.Builder` с `.addPart(def, offset)`,
`.build()`, плюс `PhysicalStructures.registerStructureSet(set)` в фасаде (сейчас сеты
регистрируются только напрямую через `PhysicalStructureRegistry.registerRuntimeSet`,
минуя фасад `PhysicalStructures` — несогласованность API).

### 4.7. compat-слой жёстко завязан на конкретный мод (`create_aeronautics_toolgun`)
`StructureSpawnerBlock.trigger()` напрямую импортирует `ExcraftCompat` и проверяет
namespace `"excraft"`. Если завтра появится третий мод с похожей схемой (свой формат
блупринтов), его придётся встраивать так же — через ветвление `if/else` внутри блока,
что плохо масштабируется.

**Предложение:** ввести интерфейс-провайдер, например:

```java
public interface StructureSourceProvider {
    boolean supports(ResourceLocation id);
    boolean place(ServerLevel level, BlockPos origin, ResourceLocation id, @Nullable Player player);
}
```

и реестр `StructureSourceProviderRegistry` (по аналогии с `CapabilityRegistry` в Forge/NeoForge),
куда `ExcraftCompat` регистрируется как один из провайдеров, а `StructureSpawnerBlock.trigger()`
просто обходит зарегистрированные провайдеры вместо хардкода. Это открывает мод для
сторонних плагинов-интеграций без правки ядра.

### 4.8. Нет batch/массового API
Для генерации (например, процедурного города из структур) часто нужно поставить N
структур за раз с проверкой производительности (растянуть по тикам, не блокировать
сервер). Сейчас каждый вызов `spawnStructure` синхронный и сразу блочный.

**Предложение:** `PhysicalStructures.spawnStructuresBatched(List<PlacementRequest>, int perTick)`
— очередь, аналогичная уже существующей `PendingAssemblyQueue`, но для самого
*размещения*, не только сборки.

### 4.9. Нет программного API для чтения/изменения NBT-данных структуры (тегов блоков)
Структуры могут содержать блок-сущности с данными (сундуки с лутом, таблички и т.д.).
Сейчас `template.placeInWorld(..., settings, ..., 3)` ставит их as-is. Сторонний мод,
который хочет, например, рандомизировать лут при спавне, не имеет хука.

**Предложение:** добавить опциональный `BiConsumer<BlockPos, BlockEntity> postPlaceHook`
параметр в `PlacementOptions` (см. ниже про builder) — вызывается для каждого
блок-энтити сразу после `placeInWorld`, до сборки в Sable sub-level.

### 4.10. Слишком много перегрузок методов вместо одного builder/options-объекта
`PhysicalStructurePlacer` и фасад `PhysicalStructures` уже сейчас держат **~16
публичных методов** для размещения (`place`, `placeWithHandle`, `placeSet`,
`placeSetWithHandle`, с поворотом/без, с handle/без...). Каждая новая опция (см. 4.7–4.9)
удвоит количество перегрузок.

**Предложение:** ввести `PlacementOptions` (builder):

```java
PlacementOptions opts = PlacementOptions.builder()
        .rotation(Rotation.CLOCKWISE_90)
        .assembleDelayTicksOverride(20)
        .spawnerPos(pos)
        .postPlaceHook(this::randomizeLoot)
        .build();

PlaceResultHandle result = PhysicalStructures.spawn(level, origin, id, opts);
```

Это не ломает обратную совместимость (старые методы можно оставить как deprecated
тонкие обёртки над новым) и резко упрощает добавление новых параметров в будущем.

### 4.11. Несогласованность: `gun4` только в JSON, `gun6` — хардкод в конструкторе
Мелочь, но прямо сейчас в `PhysicalStructures.java` (главный класс мода, НЕ API-фасад)
регистрируется только `gun6` через `PhysicalStructureRegistry.register(...)`, а `gun4`
существует только как JSON datapack-файл. Для целостности либо обе встроенные структуры
должны идти одним путём, либо это стоит явно прокомментировать (что одна — пример
JSON-формата, а другая — пример программной регистрации).

### 4.12. `despawnStructure` завязан на Sable API напрямую внутри фасада
`PhysicalStructures.despawnStructure(...)` обращается напрямую к
`dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer` — это нормально для текущей
функциональности, но означает, что **вся** логика "удаления собранной структуры" не имеет
ивента (`PhysicalStructureDespawnedEvent` отсутствует). Сторонние моды, которые слушали
`PhysicalStructurePlacedEvent` для эффектов (звук, партиклы), не могут симметрично
среагировать на удаление.

**Предложение:** добавить `PhysicalStructureDespawnedEvent` рядом с
`PhysicalStructurePlacedEvent`, файрить его в `despawnStructure(...)` сразу после
`subLevel.markRemoved()`.

---

## 5. Сводная таблица приоритетов

| # | Улучшение | Сложность | Выгода для интеграторов |
|---|---|---|---|
| 4.3 | Детализировать `PlaceResult` | низкая | высокая |
| 4.1 | Метаданные структур без размещения | низкая | высокая |
| 4.4 | Стабильный `placementId` для pending-сборки | средняя | высокая |
| 4.12 | `PhysicalStructureDespawnedEvent` | низкая | средняя |
| 4.5 | Pre-place cancelable event | средняя | высокая (для protection-модов) |
| 4.7 | `StructureSourceProvider` реестр вместо хардкода Excraft | средняя | высокая (масштабируемость compat) |
| 4.10 | `PlacementOptions` builder | средняя | высокая (долгосрочно) |
| 4.2 | `canPlace` dry-run | средняя | средняя |
| 4.6 | Builder для `PhysicalStructureSet` + унификация регистрации через фасад | низкая | средняя |
| 4.8 | Batched spawn API | высокая | низкая-средняя (нишевый юзкейс) |
| 4.9 | post-place hook для блок-энтити | средняя | средняя |
| 4.11 | Причесать `gun4`/`gun6` регистрацию | тривиальная | косметика |

Рекомендуемый порядок реализации: **4.3 → 4.1 → 4.12 → 4.4 → 4.6 → 4.10 → 4.7 → 4.5 →
4.2 → 4.9 → 4.8**, потому что первые пункты — это в основном расширение существующих
record'ов/enum'ов без слома API, а `PlacementOptions` (4.10) стоит ввести **до** 4.5/4.7,
так как новые опции (cancel, hook, provider) удобнее вешать сразу на builder, а не плодить
ещё одно поколение перегруженных методов.

---

## 6. С чем конкретно может взаимодействовать сторонний мод уже сегодня

Коротко, что реально доступно без правок ядра прямо сейчас:

1. **Спавнить структуры** (`PhysicalStructures.spawnStructure*`) — по `ResourceLocation`,
   с поворотом, с/без handle для удаления.
2. **Регистрировать свои структуры в рантайме** — из собственных resources (другой
   namespace) или с произвольного файлового пути на диске.
3. **Слушать факт размещения** — `PhysicalStructurePlacedEvent` на `NeoForge.EVENT_BUS`
   (game bus), включая позицию спавнера-инициатора, если был блок.
4. **Узнавать список всех id** — `availableStructures()` / `isRegistered(id)`.
5. **Использовать составные структуры (Set)** — несколько NBT-частей с офсетами,
   собираемые в один sub-level.
6. **Удалять собранные структуры** — по UUID handle, полученному при спавне.
7. **Выдавать игроку/спавнить предмет с привязанной структурой** —
   `StructureSpawnerItem.forStructure(id)`.
8. **Программно ставить и сразу триггерить блок-спавнер** —
   `StructureSpawnerBlock.placeAndTrigger(level, pos, id)`.
9. **Подключать свой `.excraft`-подобный формат** — технически возможно скопировать
   паттерн `ExcraftCompat`, но пока без официального provider-интерфейса (см. 4.7) это
   требует правки `StructureSpawnerBlock` напрямую — что и есть главная боль на сегодня.

---

*Документ актуален на момент анализа исходников из архива `14Physical_structures.zip`.
При изменении API (`api/` пакет) — обновить таблицу в разделе 3 и проверить актуальность
пунктов 4.x.*

---

## 7. Приложение: интеграция с create_aeronautics_toolgun 0.2.0 (по декомпилированным исходникам)

По запросу был проанализирован архив `create_aeronautics_toolgun-0_2_0_jar_src.zip` —
**декомпилированный `.jar`** (не оригинальные исходники: видны footer'ы
`Location: ...class`, `JD-Core Version`, и ~20% классов декомпилятор не смог
восстановить — `// INTERNAL ERROR //`, включая ключевой `SubLevelFileStore.java`
и `PlacementSnapMode.java`).

### 7.1. Главный вывод: у Toolgun 0.2.0 нет публичного API
Поиск по всему исходнику не нашёл **ни одного** `public interface`, NeoForge-события
(`extends Event`) или capability, предназначенных для сторонних модов. Весь стек
размещения физических структур —
`CreatePhysicalSchematicSupport`, `PortableStructurePreviewData`, `SubLevelFileStore` —
объявлен package-private. Мод не проектировался для внешней интеграции.

### 7.2. Старая реализация моста (рефлексия) была хрупкой по факту, не только в теории
Прежний `ToolgunPlacementBridge` вызывал package-private
`CreatePhysicalSchematicSupport.loadToolgunBlueprint(...)` через рефлексию с
сигнатурой, угаданной по декомпилированному байткоду — включая параметр-enum
`PlacementSnapMode`, чей файл декомпилятор не смог восстановить вообще. Любое
изменение сигнатуры в новой версии Toolgun молча ломает интеграцию без
compile-time предупреждения, и сейчас невозможно даже проверить, что enum
действительно содержит константу `NONE` в исходном виде — код полагался на
fallback по первому элементу `enumConstants[0]`.

### 7.3. Найдена лучшая точка входа: встроенная команда Toolgun
`PortableStructurePrinterCommands` регистрирует Brigadier-команду:
```
/aerotoolgun print_blueprint <file> <pos>
```
Это **публичный, заявленный самим модом интерфейс** (а не деталь реализации) —
команда сама разбирает оба формата блупринтов и сама выбирает правильный
внутренний путь. Сторонней реализации не нужно знать ни `SubLevelFileStore`,
ни `PlacementSnapMode`, ни сигнатуры внутренних методов.

**Реализовано:** `ToolgunPlacementBridge.place(level, origin, blueprintName)`
теперь строит `CommandSourceStack` от имени консоли сервера
(`server.createCommandSourceStack()`, permission level 4 — ровно то, что требует
команда) и выполняет её через `server.getCommands().getDispatcher().execute(...)`.
Никакой рефлексии, никакого ручного парсинга NBT для самого размещения.

### 7.4. Важное ограничение — унаследовано от Toolgun, не от моста
Проверка байткода показала, что безыгроковая ветка
`CreatePhysicalSchematicSupport.placeCreatePhysicalSchematic(ServerLevel, ...)`
(3 параметра, без `ServerPlayer`) **всегда** бросает
`IOException("server-side physical schematic placement requires player context")`.
Это касается **только** Create-физических схем (тег `sub_levels` в формате
`CreatePhysicalSchematicSupport`). Обхода нет ни через команду, ни через рефлексию,
ни через программное управление `PortableStructurePrinterBlockEntity` — все три
пути сходятся в один и тот же internal-метод-заглушку.

Для обычных `.excraft`-блупринтов (формат `SubLevelFileStore`, то, что физически
лежит в `<gamedir>/blueprints/*.excraft` и что использует `excraft:`-namespace
в нашем моде) это ограничение **не действует** — команда работает полностью без
игрока.

**Реализовано:** `ToolgunPlacementBridge` теперь имеет две перегрузки —
`place(level, origin, name)` (от консоли сервера) и
`place(level, origin, name, @Nullable ServerPlayer)`. Когда вызывающий код
располагает реальным игроком (например, правый клик по `StructureSpawnerBlock`),
`ExcraftStructureHandler.triggerExcraft` прокидывает его дальше как
`ServerPlayer`, и мост строит `CommandSourceStack` через
`player.createCommandSourceStack().withPermission(4)` — права поднимаются только
для этого одного изолированного объекта команды, не выдавая игроку реальный op.
Это даёт Toolgun настоящий игровой контекст, и Create-физические схемы размещаются
корректно. При вызове без игрока (программный спавн через API без UI-инициатора)
мост по-прежнему использует консоль сервера — для `.excraft`-блупринтов этого
достаточно; Create-физические схемы в этом случае предсказуемо не размещаются,
о чём мост явно логирует предупреждение, а не падает молча.

### 7.5. Что было упрощено в `ExcraftStructureHandler`
Старая реализация сама читала и парсила NBT (включая проверку тега `sub_levels`),
кэшировала его в `config/physical_structures/excraft_cache/` и регистрировала
в `PhysicalStructureRegistry` — хотя дальнейшее размещение всё равно полностью
делегировалось в Toolgun, а не в `physical_structures`. Это кэширование удалено:
теперь класс только проверяет наличие файла (для понятной ошибки игроку) и
передаёт имя файла в `ToolgunPlacementBridge`, а сам Toolgun читает и
валидирует NBT внутри своей же команды.

### 7.6. Снаружи ничего не поменялось — публичный API мода тот же
Для кода, который уже использует `physical_structures` (включая ваш собственный
`StructureSpawnerBlock`), вся эта переделка прозрачна. Точка входа всё та же:

```java
// Игрок кликает по спаунеру, structureId = excraft:my_ship
StructureSpawnerBlock.placeAndTrigger(serverLevel, pos,
        ResourceLocation.fromNamespaceAndPath("excraft", "my_ship"));
```

Единственное практическое отличие для интегратора: теперь стоит **передавать
реального `Player`**, когда он есть (а не `null`), потому что это даёт мосту
возможность поднять `CommandSourceStack` от его профиля и тем самым включить
поддержку Create-физических схем — раньше эта возможность не существовала вообще
(старый мост падал в fallback при отсутствии игрока, и для Create-схем fallback
сам по себе ничего не размещал, только логировал предупреждение).

### 7.7. Открытый вопрос на будущее
Поскольку `SubLevelFileStore.java` и `PlacementSnapMode.java` не декомпилировались
вообще, у нас нет подтверждённого знания формата `.excraft` файла "из первых рук" —
только то, что Toolgun сам успешно его читает через свою же команду. Если в будущей
версии Toolgun появится официальный публичный API (события, capability), стоит
сразу мигрировать на него — Brigadier-команда остаётся лучшим доступным вариантом
именно потому, что альтернатив с лучшими гарантиями стабильности в этой версии
мода просто не существует, а не потому, что это идеальное архитектурное решение.

