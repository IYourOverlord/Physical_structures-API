# Physical Structures API

A mod for **Minecraft 1.21.1 (NeoForge 21.1.227)** that takes a standard `.nbt` structure template
(the same kind created by a Structure Block) and turns it into a **physical
object** — places blocks in the world and “assembles” them into a movable sub-level of
the [**Sable**](https://github.com/) mod (`dev.ryanhcode.sable`), giving the structure
independent physics and the ability to move — like ships in Create: Aeronautics.

The mod is designed primarily as a **library / API for other mods**: it gives
a Java programmer (or datapack author) a single call to spawn a ready-made physical
structure, plus a set of events/hooks for integrating it into their own logic.

---

## Contents

1. [What the mod does and how it works](#1-what-the-mod-does-and-how-it-works)
2. [Installation and dependencies](#2-installation-and-dependencies)
3. [Quick start](#3-quick-start)
4. [Ways to define a structure](#4-ways-to-define-a-structure)
   - [4.1. JSON datapack (single structure)](#41-json-datapack-single-structure)
   - [4.2. JSON datapack (composite structure — multiple NBT parts)](#42-json-datapack-composite-structure--multiple-nbt-parts)
   - [4.3. Runtime registration from Java](#43-runtime-registration-from-java)
5. [Java API in detail](#5-java-api-in-detail)
   - [5.1. `PhysicalStructures` — main facade](#51-physicalstructures--main-facade)
   - [5.2. `PlacementOptions` — fine-grained placement configuration](#52-placementoptions--fine-grained-placement-configuration)
   - [5.3. `PlacementResult` — spawn result](#53-placementresult--spawn-result)
   - [5.4. `StructureMetadata` — get size without spawning](#54-structuremetadata--get-size-without-spawning)
   - [5.5. `canPlace` — check whether it fits without modifying the world](#55-canplace--check-whether-it-fits-without-modifying-the-world)
6. [Events (NeoForge Event Bus)](#6-events-neoforge-event-bus)
   - [6.1. `PhysicalStructurePlacingEvent` (before placement, cancellable)](#61-physicalstructureplacingevent-before-placement-cancellable)
   - [6.2. `PhysicalStructurePlacedEvent` (after placement)](#62-physicalstructureplacedevent-after-placement)
   - [6.3. `PhysicalStructureDespawnedEvent` (after removal)](#63-physicalstructuredespawnedevent-after-removal)
7. [Gameplay mechanics: block and item](#7-gameplay-mechanics-block-and-item)
   - [7.1. `StructureSpawnerBlock` — spawner block](#71-structurespawnerblock--spawner-block)
   - [7.2. `StructureSpawnerItem` — item with a bound structure](#72-structurespawneritem--item-with-a-bound-structure)
8. [World generation (Worldgen Feature)](#8-world-generation-worldgen-feature)
9. [Extension: custom structure sources (`StructureSourceProvider`)](#9-extension-custom-structure-sources-structuresourceprovider)
10. [Compatibility with Create Aeronautics Toolgun (`excraft:`)](#10-compatibility-with-create-aeronautics-toolgun-excraft)
11. [Practical usage scenarios](#11-practical-usage-scenarios)
12. [FAQ / troubleshooting](#12-faq--troubleshooting)
13. [Building from source](#13-building-from-source)
14. [License](#14-license)

---

## 1. What the mod does and how it works

Data flow — from a file on disk to a physical object in the world:

```
.nbt (mod/datapack resource OR arbitrary file on disk)
        │
        ▼
PhysicalStructureDefinition   — id, NBT path, default rotation, assembly delay
        │  registered in...
        ▼
PhysicalStructureRegistry     — static registry id → Definition / Set
        │  read through...
        ▼
PhysicalStructures.spawn(...) (public API) ──> internal StructurePlacer engine
        │
        ├─ 1. loads StructureTemplate from NBT
        ├─ 2. template.placeInWorld(...)             — actual blocks appear in the world
        ├─ 3. collects a list of non-empty BlockPos
        ├─ 4. if assembly delay == 0 → immediately assembles into a sub-level
        │      otherwise → queues it; completion proceeds on the server tick
        └─ 5. SubLevelAssemblyHelper.assembleBlocks(...) (Sable)
               → creates ServerSubLevel, assigns a UUID,
                 fires PhysicalStructurePlacedEvent
```

Key idea: **blocks are only an intermediate step**. The final result is not a set of
ordinary blocks, but a separate Sable sub-level containing these blocks, which can
move, rotate, and physically interact with the world like a ship or vehicle.

---

## 2. Installation and dependencies

| Parameter | Value |
|---|---|
| Minecraft | `1.21.1` |
| Mod loader | NeoForge `21.1.227`+ |
| Java | 21 |

**Required dependencies** (without them the mod will not start — they are marked as `required` in
`neoforge.mods.toml`):

- `neoforge`
- `minecraft`
- `sable` (`dev.ryanhcode.sable`) — provides sub-levels and structure physics
- `cbc_autotarget` — required build dependency (used transitively)

**Optional dependency** (detected at runtime; absence does not break loading):

- `create_aeronautics_toolgun` (Excraft Toolgun) — if installed, the mod automatically
  enables compatibility with the `.excraft` blueprint format (see [section 10](#10-compatibility-with-create-aeronautics-toolgun-excraft)).

The repository also shows that the build uses the following as `compileOnly` (meaning
only for compilation — the actual jar files of these mods are needed at runtime if the
corresponding functionality is used): Sable, `simulated`, Create, Flywheel, Ponder, Registrate.

### Installation as a regular mod (for player/server)

1. Install NeoForge `21.1.227`+ for Minecraft `1.21.1`.
2. Put `sable` and `cbc_autotarget` in the `mods/` folder — without them the game will not start.
3. Put the `physical_structures` jar in `mods/`.
4. (Optional) Install `create_aeronautics_toolgun` if you want to use
   `.excraft` blueprints through this mod.

### Using it as a library (for another mod developer)

Add `physical_structures` to your `build.gradle` dependencies (as `compileOnly` +
runtime dependency through NeoForge mod management) and declare it as a `required`/`optional`
dependency in your `neoforge.mods.toml`, depending on whether it is required by
your mod.

---

## 3. Quick start

Minimal call from any other mod (for example, from a command handler, event, or
block logic):

```java
import net.minecraft.resources.ResourceLocation;
import org.exampl.physical_structures.api.PhysicalStructures;

// level — ServerLevel, pos — BlockPos where the structure origin will be
boolean ok = PhysicalStructures.spawnStructure(
        level, pos,
        ResourceLocation.fromNamespaceAndPath("mymod", "my_cannon"));
```

This will spawn the structure with id `mymod:my_cannon` (it must have been previously
registered — see [section 4](#4-ways-to-define-a-structure)), place the blocks, and
immediately assemble them into a physical sub-level.

A more modern and detailed variant (recommended for new integrations):

```java
import net.minecraft.world.level.block.Rotation;
import org.exampl.physical_structures.api.PhysicalStructures;
import org.exampl.physical_structures.api.PlacementOptions;
import org.exampl.physical_structures.api.PlacementResult;

PlacementResult result = PhysicalStructures.spawn(level, pos,
        ResourceLocation.fromNamespaceAndPath("mymod", "my_cannon"),
        PlacementOptions.withRotation(Rotation.CLOCKWISE_90));

if (result.status() == PlacementResult.Status.SUCCESS_ASSEMBLED) {
    UUID handle = result.handle(); // save it so it can be despawned later
}
```

---

## 4. Ways to define a structure

There are three ways to register a structure under your own `id` (`ResourceLocation`) so
that it can be spawned through the API.

### 4.1. JSON datapack (single structure)

Place the file at `data/<namespace>/physical_structures/<name>.json` — in a datapack or
in the `resources` of your own mod. The loader (`PhysicalStructureJsonLoader`) is a regular
`SimpleJsonResourceReloadListener`; it survives the `/reload` command.

**Example** (`data/mymod/physical_structures/my_cannon.json`):

```json
{
  "id":           "mymod:my_cannon",
  "nbt_location": "mymod:structures/my_cannon.nbt",
  "rotation":     "none",
  "assemble_delay_ticks": 0
}
```

| Field | Required | Description |
|---|---|---|
| `id` | yes | `ResourceLocation` of the structure — the value you pass to the API |
| `nbt_location` | yes | `ResourceLocation` path to the `.nbt` file in resources (a normal `structure` structure) |
| `rotation` | no | `"none"` \| `"clockwise_90"` \| `"counterclockwise_90"` \| `"clockwise_180"` (default `"none"`) |
| `assemble_delay_ticks` | no | delay in ticks before assembly into a Sable sub-level after blocks are placed (default `0` — immediate assembly) |

Place the `.nbt` file as a normal structure: `data/<namespace>/structures/<name>.nbt`
(this is the standard Minecraft path for `StructureTemplate`, created through a Structure Block
in-game or with third-party editors).

### 4.2. JSON datapack (composite structure — multiple NBT parts)

If the structure is assembled from several separate `.nbt` files with their own offsets
(for example, hull + tower + cannon), use the `parts` field instead of `nbt_location`:

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

Each part in `parts` is an object with fields:

- `nbt_location` — path to the `.nbt` file for this part;
- `offset` — an array of three integers `[x, y, z]`, the part offset relative to the common
  `origin` during placement (before rotation is applied).

The composite structure is spawned through `spawnStructureSet(...)` (see
[section 5.1](#51-physicalstructures--main-facade)) — all parts are placed and assembled
into one shared sub-level.

### 4.3. Runtime registration from Java

If structures are generated programmatically, live outside the mod resources (for example, in a separate
folder on the server disk), or depend on another mod’s logic — register them directly
in code, without a datapack:

```java
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import org.exampl.physical_structures.api.PhysicalStructures;

import java.nio.file.Path;

// Simple variant — rotation NONE, no delay:
PhysicalStructures.registerStructureFromFile(
        ResourceLocation.fromNamespaceAndPath("mymod", "generated_base"),
        Path.of("config/mymod/generated_structures/base_42.nbt"));

// Full variant — with rotation and assembly delay:
PhysicalStructures.registerStructureFromFile(
        ResourceLocation.fromNamespaceAndPath("mymod", "generated_base"),
        Path.of("config/mymod/generated_structures/base_42.nbt"),
        Rotation.CLOCKWISE_180,
        20 // ticks of delay before physical assembly
);
```

Or directly through a `PhysicalStructureDefinition` object (for example, if the NBT path is a
resource rather than a file on disk):

```java
import org.exampl.physical_structures.api.PhysicalStructureDefinition;
import org.exampl.physical_structures.api.PhysicalStructures;

PhysicalStructures.registerStructure(new PhysicalStructureDefinition(
        ResourceLocation.fromNamespaceAndPath("mymod", "my_turret"),
        ResourceLocation.fromNamespaceAndPath("mymod", "structures/my_turret.nbt")
));
```

Runtime-registered IDs are **protected from being overwritten** during datapack `/reload` (unlike
IDs coming from JSON, which are reloaded together with the datapack).

Remove the runtime registration:

```java
PhysicalStructures.unregisterStructure(ResourceLocation.fromNamespaceAndPath("mymod", "my_turret"));
```

---

## 5. Java API in detail

All classes are in the package `org.exampl.physical_structures.api`.

### 5.1. `PhysicalStructures` — main facade

The single entry point that should be used from another mod.

#### Simple spawning

```java
// Spawn with default settings (NONE rotation, immediate assembly)
PlacementResult spawn(ServerLevel level, BlockPos origin, ResourceLocation structureId);

// Spawn with full control through PlacementOptions
PlacementResult spawn(ServerLevel level, BlockPos origin,
                       ResourceLocation structureId, PlacementOptions opts);
```

**Example:**

```java
PlacementResult r = PhysicalStructures.spawn(level, pos,
        ResourceLocation.fromNamespaceAndPath("mymod", "my_cannon"),
        PlacementOptions.builder()
                .rotation(Rotation.CLOCKWISE_90)
                .postPlaceHook((blockPos, blockEntity) -> {
                    // for example, randomize loot in a structure chest
                    randomizeLoot(blockEntity);
                })
                .build());

if (r.status() == PlacementResult.Status.SUCCESS_ASSEMBLED) {
    myTracker.put(r.placementId(), r.handle());
}
```

#### `canPlace` — dry-run without modifying the world

```java
boolean canPlace(ServerLevel level, BlockPos origin, ResourceLocation structureId, Rotation rotation);
```

Checks for NBT availability, loaded chunks, and world Y boundaries **without modifying the world**:

```java
if (PhysicalStructures.canPlace(level, pos, id, Rotation.NONE)) {
    PhysicalStructures.spawn(level, pos, id);
}
```

#### `getMetadata` — metadata without placement

```java
Optional<StructureMetadata> getMetadata(ServerLevel level, ResourceLocation structureId);
```

```java
PhysicalStructures.getMetadata(level, id).ifPresent(meta -> {
    LOGGER.info("{}: {}x{}x{}", id, meta.sizeX(), meta.sizeY(), meta.sizeZ());
});
```

#### Composite structures (multiple parts)

```java
boolean spawnStructureSet(ServerLevel level, BlockPos origin, ResourceLocation setId);
boolean spawnStructureSet(ServerLevel level, BlockPos origin, ResourceLocation setId, Rotation rotation);

PhysicalStructurePlacer.PlaceResultHandle spawnStructureSetWithHandle(
        ServerLevel level, BlockPos origin, ResourceLocation setId);
PhysicalStructurePlacer.PlaceResultHandle spawnStructureSetWithHandle(
        ServerLevel level, BlockPos origin, ResourceLocation setId, Rotation rotation);
```

#### Removing a structure

```java
boolean despawnStructure(ServerLevel level, UUID handle);
```

`handle` is the UUID obtained from `PlacementResult.handle()` or from the `handle()` of the `PhysicalStructurePlacedEvent`. Removes the Sable sub-level and fires
`PhysicalStructurePlacedEvent`. Removes the sub-level in Sable and fires
`PhysicalStructureDespawnedEvent`.

```java
boolean removed = PhysicalStructures.despawnStructure(level, savedHandle);
```

#### Registry / inspection

```java
Set<ResourceLocation> availableStructures();       // all registered IDs
boolean isRegistered(ResourceLocation structureId); // whether a specific ID exists
```

```java
for (ResourceLocation id : PhysicalStructures.availableStructures()) {
    LOGGER.info("Known structure: {}", id);
}
```

#### Runtime registration (see also [section 4.3](#43-runtime-registration-from-java))

```java
void registerStructure(PhysicalStructureDefinition def);
void registerStructureFromFile(ResourceLocation id, Path nbtFile, Rotation defaultRotation, int assembleDelayTicks);
void registerStructureFromFile(ResourceLocation id, Path nbtFile); // rotation=NONE, delay=0
boolean unregisterStructure(ResourceLocation structureId);
```

#### Deprecated (legacy) methods

Kept for backward compatibility, but marked `@Deprecated` — new code should
use `spawn(...)` with `PlacementOptions`:

```java
@Deprecated boolean spawnStructure(ServerLevel level, BlockPos origin, ResourceLocation structureId);
@Deprecated boolean spawnStructure(ServerLevel level, BlockPos origin, ResourceLocation structureId, Rotation rotation);
@Deprecated PhysicalStructurePlacer.PlaceResult spawnStructureResult(ServerLevel level, BlockPos origin, ResourceLocation structureId);
```

---

### 5.2. `PlacementOptions` — fine-grained placement configuration

A single configuration object instead of a growing list of overloads. It is built through
a builder or ready-made factories.

```java
// Rotation only; everything else uses defaults
PlacementOptions opts = PlacementOptions.withRotation(Rotation.CLOCKWISE_90);

// Full configuration
PlacementOptions opts = PlacementOptions.builder()
        .rotation(Rotation.CLOCKWISE_90)
        .assembleDelayTicksOverride(20)                  // override assembly delay
        .snapToHeightmap(Heightmap.Types.WORLD_SURFACE_WG) // “snap” to the surface
        .postPlaceHook((pos, blockEntity) -> randomizeLoot(blockEntity))
        .spawnerPos(spawnerBlockPos)                      // position of the initiating block (for the event)
        .blocksOnlyNoAssemble(true)                        // blocks only, without physical assembly
        .build();
```

| Option | Default | Purpose |
|---|---|---|
| `rotation(Rotation)` | `NONE` | structure rotation during placement |
| `assembleDelayTicksOverride(int)` | `-1` (do not override) | overrides the assembly delay specified in JSON/Definition |
| `spawnerPos(BlockPos)` | `null` | position of the initiating spawner block — included in `PhysicalStructurePlacedEvent.spawnerPos()` |
| `snapToHeightmap(Heightmap.Types)` | `null` | if set, the origin Y coordinate is automatically raised to the specified heightmap before placement |
| `postPlaceHook(BiConsumer<BlockPos, BlockEntity>)` | `null` | called for each `BlockEntity` immediately after blocks are placed, **before** assembly in Sable — useful for randomizing loot, setting NBT, etc. |
| `blocksOnlyNoAssemble(boolean)` | `false` | if `true`, places only blocks without physical Sable assembly (for static decorations) |
| `deferAssemblyToServerThread(boolean)` | `false` | defers assembly to the next server tick through a queue, even with zero delay — needed for safe calls from worldgen |

**Ready-made preset for worldgen:**

```java
PlacementOptions.forWorldgen(Rotation rotation)
```

Sets `snapToHeightmap = WORLD_SURFACE_WG` and `deferAssemblyToServerThread = true` — blocks
are placed immediately, while Sable assembly is deferred to a server tick when the chunk is guaranteed
to be loaded (direct Sable calls outside the server thread are unsafe).

```java
PhysicalStructures.spawn(level, origin, id, PlacementOptions.forWorldgen(Rotation.NONE));
```

---

### 5.3. `PlacementResult` — what the spawn returned

A detailed result of a placement attempt, replacing the coarse three-state `PlaceResult`.
`PlaceResult`.

```java
public enum Status {
    SUCCESS_ASSEMBLED,   // structure placed and immediately assembled — handle() is available
    SUCCESS_PENDING,     // blocks placed, Sable assembly deferred (delay > 0)
    SUCCESS_BLOCKS_ONLY, // blocks only without physics (blocksOnlyNoAssemble)
    UNKNOWN_ID,          // ID not found in registry
    LOAD_FAILED,         // NBT not found or corrupted
    ASSEMBLY_FAILED,     // blocks placed, but Sable could not assemble the sub-level
    CANCELLED            // PhysicalStructurePlacingEvent cancelled the placement
}
```

Example handling all branches:

```java
PlacementResult r = PhysicalStructures.spawn(level, origin, id, opts);
switch (r.status()) {
    case SUCCESS_ASSEMBLED   -> { UUID handle = r.handle(); /* already in the world */ }
    case SUCCESS_PENDING     -> { /* blocks are present; sub-level will be assembled later —
                                   listen for PhysicalStructurePlacedEvent by r.placementId() */ }
    case SUCCESS_BLOCKS_ONLY -> { /* decoration without physics */ }
    case UNKNOWN_ID           -> LOGGER.warn("Unknown structure ID: {}", id);
    case LOAD_FAILED          -> LOGGER.error("Failed to load NBT: {}", r.errorMessage());
    case ASSEMBLY_FAILED      -> LOGGER.error("Sable failed to assemble: {}", r.errorMessage());
    case CANCELLED            -> { /* cancelled by a PhysicalStructurePlacingEvent listener */ }
}
```

Useful methods:

```java
r.isSuccess();     // true for any of the three SUCCESS_* statuses
r.handle();         // sub-level UUID (only for SUCCESS_ASSEMBLED), otherwise null
r.placementId();    // stable UUID for this attempt — matches the ID in events
r.errorMessage();   // reason for LOAD_FAILED / ASSEMBLY_FAILED
r.toLegacy();        // convert to the old three-state PlaceResult
```

`placementId()` is especially useful with `SUCCESS_PENDING`: it is generated **before** queuing
and will arrive in `PhysicalStructurePlacedEvent.placementId()` after actual assembly — this
allows a specific `spawn(...)` call to be unambiguously linked to its result even with a delay
(without relying on a race based on matching position/time).

---

### 5.4. `StructureMetadata` — get the size without spawning

```java
public record StructureMetadata(
        ResourceLocation id,
        int sizeX, int sizeY, int sizeZ,
        Rotation defaultRotation,
        int assembleDelayTicks,
        boolean isComposite
) {}
```

Loads only the NBT header (size), without modifying the world:

```java
PhysicalStructures.getMetadata(level, id).ifPresent(m -> {
    LOGGER.info("{} size: {}×{}×{}, default rotation: {}",
            id, m.sizeX(), m.sizeY(), m.sizeZ(), m.defaultRotation());
});
```

Useful, for example, for a dungeon generator: first determine the structure dimensions, then
decide whether it fits in the selected location before actually placing it.

---

### 5.5. `canPlace` — check whether it fits without modifying the world

See [section 5.1](#51-physicalstructures--main-facade) above — `canPlace(level, origin, id, rotation)`
checks for NBT availability, loaded chunks, and world height boundaries.

---

## 6. Events (NeoForge Event Bus)

All events are in the package `org.exampl.physical_structures.api.event` and are published
on the **regular game event bus** (`NeoForge.EVENT_BUS`), not the mod event bus:

```java
NeoForge.EVENT_BUS.register(MyEventListener.class);
```

### 6.1. `PhysicalStructurePlacingEvent` (before placement, cancellable)

Fires **before** the structure is placed. It allows you to:

- **cancel** placement (for example, in a protected claims zone of a claims mod);
- **change** the position or rotation at the last moment;
- **read** `placementId()`, which will also arrive in `PhysicalStructurePlacedEvent`.

```java
@SubscribeEvent
public static void beforePlace(PhysicalStructurePlacingEvent event) {
    if (ClaimsAPI.isProtected(event.level(), event.origin())) {
        event.setCanceled(true);
        return;
    }
    // for example, force any rotation other than NONE to be forbidden
    event.setRotation(Rotation.NONE);
}
```

Available methods:

```java
ServerLevel level();
BlockPos origin();                          // can be changed through setOrigin(pos)
Rotation rotation();                        // can be changed through setRotation(rotation)
PhysicalStructureDefinition definition();
PlacementOptions options();
UUID placementId();
void setOrigin(BlockPos pos);
void setRotation(Rotation rotation);
void setCanceled(boolean cancel);           // inherited from ICancellableEvent
```

> NeoForge 21.1 / EventBus 7.x is used: cancellability is implemented through
> `ICancellableEvent`, not through the deprecated `@Cancelable` annotation.

### 6.2. `PhysicalStructurePlacedEvent` (after placement)

Fires after successful placement **and** assembly (or queuing, depending on the status —
see `PlacementResult`).

```java
@SubscribeEvent
public static void onPlaced(PhysicalStructurePlacedEvent e) {
    if (e.structureId().equals(MY_ID)) {
        e.level().playSound(null, e.origin(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1f, 1f);
    }
}
```

Available methods:

```java
ServerLevel level();
BlockPos origin();
PhysicalStructureDefinition definition();
ResourceLocation structureId();
int blockCount();
@Nullable BlockPos spawnerPos();   // position of the initiating spawner block, if there was one
@Nullable UUID handle();           // sub-level UUID for despawnStructure(...); null for blocksOnly
UUID placementId();                // matches placementId() from PhysicalStructurePlacingEvent
```

### 6.3. `PhysicalStructureDespawnedEvent` (after removal)

The symmetric event to `PhysicalStructurePlacedEvent`; fires when
`PhysicalStructures.despawnStructure(...)` is called.

```java
@SubscribeEvent
public static void onDespawn(PhysicalStructureDespawnedEvent e) {
    MapMarkers.remove(e.handle());
    e.level().playSound(null, e.origin(), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1f, 1f);
}
```

Available methods:
ServerLevel level();
UUID handle();
ResourceLocation structureId();
@Nullable BlockPos origin();  // may be null if the record did not save the position
```

---

## 7. Gameplay mechanics: block and item

The mod provides two ready-made gameplay methods for triggering placement — both use the same
public API that is also available to third-party code.

### 7.1. `StructureSpawnerBlock` — spawner block

The `BlockEntity` of this block stores `structure_id` (saved in the block NBT, survives
`/reload` and chunk reload). When right-clicked by a player or called programmatically, the block:

1. checks whether a `StructureSourceProvider` is registered for this ID
   (for example, `excraft:` for Toolgun) — if so, delegates to it;
2. otherwise looks for the ID in its own `PhysicalStructureRegistry`;
3. on success, places the structure above itself and removes the spawner block.

**Programmatic placement of the block + structure “in one call”:**

```java
StructureSpawnerBlock.placeAndTrigger(serverLevel, pos,
        ResourceLocation.fromNamespaceAndPath("mymod", "my_cannon"));
```

**Manual trigger of an existing spawner block** (for example, from redstone logic or
your own command):

```java
if (level.getBlockState(pos).getBlock() instanceof StructureSpawnerBlock spawner) {
    spawner.trigger(serverLevel, pos, playerOrNull);
}
```

### 7.2. `StructureSpawnerItem` — item with a bound structure

A `BlockItem` that, when placed in the world, creates a `StructureSpawnerBlock` with an already
preconfigured `structure_id` (stored in the data component). The item tooltip
shows the bound ID.

**Give a player an item that will spawn a specific structure:**

```java
ItemStack stack = StructureSpawnerItem.forStructure(
        ResourceLocation.fromNamespaceAndPath("mymod", "my_cannon"));
player.getInventory().add(stack);
```

The player places this item like a normal block — placement automatically creates
a spawner block with this ID, ready to be activated with a right-click.

> The repository also contains an earlier/alternative class
> `org.exampl.physical_structures.init.SpawnStructureItem` — a regular `Item` (not a
> `BlockItem`) that immediately places the structure when clicking a block face through
> `PhysicalStructurePlacer.place(...)`, without an intermediate spawner block. It is hard-
> bound to a single `structureId` specified in the constructor — convenient if you
> register a separate item for each structure manually.

---

## 8. World generation (Worldgen Feature)

The mod registers its own `Feature` type — `physical_structures:physical_structure` —
which can be used in standard datapack worldgen (`configured_feature` +
`placed_feature`) so that structures physically appear during chunk generation.

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

| Config field | Required | Default | Description |
|---|---|---|---|
| `structure_id` | yes | — | structure ID (must be registered) |
| `rotation` | no | `NONE` | rotation during generation |
| `snap_to_surface` | no | `false` | snap Y to the world surface |
| `assemble_delay_ticks` | no | `1` | delay before Sable assembly (important for worldgen — direct Sable calls outside the server thread are unsafe) |

**`placed_feature`** (`data/<ns>/worldgen/placed_feature/<name>.json`) — standard
Minecraft placement mechanism with filters (frequency, biome binding, heightmap, etc.):

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

Then connect the `placed_feature` to the desired biomes through the tag
`data/<ns>/worldgen/biome_modifier/*.json` or directly in biomes, as with any other
vanilla or modded feature.

**Java programmatic equivalent** (if you call placement not through ChunkGenerator/Feature,
but manually from your own generation code) — use the ready-made preset:

```java
PhysicalStructures.spawn(level, origin, id, PlacementOptions.forWorldgen(Rotation.NONE));
```

---

## 9. Extension: custom structure sources (`StructureSourceProvider`)

If you want `StructureSpawnerBlock` and the mod infrastructure in general to work
with your own “blueprint” format (not `.nbt` from `PhysicalStructureRegistry`,
but something custom), register a provider without touching the mod core:

```java
public class MyBlueprintProvider implements StructureSourceProvider {

    @Override
    public String providerId() { return "mymod:blueprint_provider"; }

    @Override
    public boolean supports(ResourceLocation id) {
        // for example, handle all IDs from our namespace
        return "myblueprints".equals(id.getNamespace());
    }

    @Override
    public boolean place(ServerLevel level, BlockPos origin, ResourceLocation id, @Nullable Player player) {
        return MyBlueprintSystem.spawn(level, origin, id.getPath());
    }
}
```

Registration (for example, in your mod constructor or on `FMLCommonSetupEvent`):

```java
StructureSourceProviderRegistry.register(new MyBlueprintProvider());
```

After this, IDs of the form `myblueprints:whatever` will automatically be intercepted by your
provider wherever the mod checks the structure source — primarily in
`StructureSpawnerBlock.trigger(...)`. Providers are checked **in registration order**;
the first whose `supports(id)` returns `true` wins.

Useful static registry methods:

```java
StructureSourceProviderRegistry.isHandled(id);              // whether a provider exists for the ID
StructureSourceProviderRegistry.place(level, origin, id, player); // place through the found provider
StructureSourceProviderRegistry.registeredProviderIds();    // list of all provider IDs (for debugging)
```

---

## 10. Compatibility with Create Aeronautics Toolgun (`excraft:`)

If the `create_aeronautics_toolgun` mod is present in the modpack, `physical_structures`
automatically registers `ExcraftCompat` as a `StructureSourceProvider` for the `excraft:` namespace.
This allows `StructureSpawnerBlock` to use IDs of the form
`excraft:my_ship`, which point to files `<gamedir>/blueprints/*.excraft`.

Technically, the bridge delegates placement to the Toolgun’s built-in command:

```
/aerotoolgun print_blueprint <file> <pos>
```

executed on behalf of the server `CommandSourceStack` (or the player, if known —
this is important for physical Create schematics that require game context).

**Practical note for integrators:** if you programmatically call structure placement with
the `excraft:` namespace and have a real `ServerPlayer` (not `null`),
pass it. This enables support for Create physical schematics (`sub_levels` format
`CreatePhysicalSchematicSupport`), which **cannot** be placed without game context —
this is a limitation of the Toolgun itself, not the bridge. Ordinary `.excraft` blueprints
(the `SubLevelFileStore` format) work without a player, entirely server-side.

> Detailed technical analysis (decompilation, Toolgun 0.2.0 bytecode limitations,
> reflection alternatives) — see `src/API_INTEGRATION_NOTES.md`, section 7, inside the
> repository. This is an internal engineering note, not part of the public API.

---

## 11. Practical usage scenarios

### Scenario A: “Spawn a structure by command”

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
            ctx.getSource().sendSuccess(() -> Component.literal("Result: " + r), true);
            return r.isSuccess() ? 1 : 0;
        }));
}
```

### Scenario B: “Check size and location before spawning in a dungeon”

```java
ResourceLocation id = ResourceLocation.fromNamespaceAndPath("dungeonmod", "boss_room");
Optional<StructureMetadata> meta = PhysicalStructures.getMetadata(level, id);

meta.ifPresentOrElse(m -> {
    if (roomFits(m.sizeX(), m.sizeY(), m.sizeZ())
            && PhysicalStructures.canPlace(level, candidatePos, id, Rotation.NONE)) {
        PhysicalStructures.spawn(level, candidatePos, id);
    } else {
        // choose another location or skip
    }
}, () -> LOGGER.warn("Structure {} is not registered", id));
```

### Scenario C: “Protected territory zones cancel spawning”

```java
@SubscribeEvent
public static void onPlacing(PhysicalStructurePlacingEvent e) {
    if (MyClaimsMod.isProtected(e.level(), e.origin())) {
        e.setCanceled(true);
    }
}
```

### Scenario D: “Randomize loot immediately after placing blocks”

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

### Scenario E: “Track a structure and be able to remove it later”

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

If assembly can be deferred (`SUCCESS_PENDING`), get the `handle` from the event:

```java
@SubscribeEvent
public static void onPlaced(PhysicalStructurePlacedEvent e) {
    if (e.placementId().equals(myPendingPlacementId) && e.handle() != null) {
        myTracker.put(e.placementId(), e.handle());
    }
}
```

### Scenario F: “Give the player a blueprint item that places a structure”

```java
ItemStack blueprintItem = StructureSpawnerItem.forStructure(
        ResourceLocation.fromNamespaceAndPath("mymod", "watchtower"));
player.getInventory().add(blueprintItem);
```

### Scenario G: “Structures in world generation”

See [section 8](#8-world-generation-worldgen-feature) — a datapack with `configured_feature` +
`placed_feature`, attached to the desired biomes through `biome_modifier`.

---

## 12. FAQ / troubleshooting

**The structure does not spawn, `UNKNOWN_ID`.**
Check that the ID is actually registered: `PhysicalStructures.isRegistered(id)`
or `PhysicalStructures.availableStructures()`. Common causes: a typo in the namespace,
JSON was not loaded (check the path `data/<ns>/physical_structures/<name>.json` and the logs
for `[PhysicalStructures] Bad JSON`), or runtime registration was not called before the
spawn attempt.

**`LOAD_FAILED`.**
The NBT file was not found at the specified `nbt_location`/path or is corrupted. Check that the file
actually exists at `data/<ns>/structures/<name>.nbt` inside the resources/datapack (for
JSON registration), or that the supplied `Path` actually exists on disk (for
`registerStructureFromFile`).

**`ASSEMBLY_FAILED`.**
Blocks were placed, but Sable could not assemble the sub-level. Check `errorMessage()` in
`PlacementResult`, and also make sure `sable` is actually loaded and does not report errors in the log.

**`CANCELLED`.**
A listener for `PhysicalStructurePlacingEvent` called `setCanceled(true)`. Usually this is
done by the territory-protection code; look for handlers for this event in loaded mods.

**Worldgen spawning “hangs” or crashes.**
Use `PlacementOptions.forWorldgen(rotation)` — it defers Sable assembly to a
server tick because direct Sable calls outside the server thread are unsafe during
chunk generation.

**Create physical schematics via `excraft:` cannot be placed programmatically.**
This is a limitation of the Toolgun itself: playerless placement of `CreatePhysicalSchematicSupport`
always throws `IOException`. Pass a real `ServerPlayer` if you have one —
see [section 10](#10-compatibility-with-create-aeronautics-toolgun-excraft).

---

## 13. Building from source

```bash
git clone https://github.com/IYourOverlord/Physical_structures-API.git
cd Physical_structures-API
./gradlew build
```

The built jar will appear in `build/libs/`. To run the test client/server in the development
environment (after `./gradlew build` and importing the project into an IDE with NeoForge
ModDev support):

```bash
./gradlew runClient
./gradlew runServer
```

Note: `build.gradle` uses `compileOnly fileTree(dir: 'libs', ...)`
for Sable/Create/Flywheel/Ponder/Registrate dependencies — the corresponding jar files
must be manually placed in the `libs/` folder before building if they are not already there.

---

## 14. License

See the [`LICENSE`](LICENSE) file in the repository root.
