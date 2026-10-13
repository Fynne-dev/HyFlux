# HyFlux

An energy library for Hytale server mods, modelled on Minecraft's **Redstone Flux** (RF).
HyFlux handles energy being **produced**, **carried**, **stored** and **consumed**. Your mod only
says which blocks are generators, machines, batteries and cables; HyFlux connects them, moves
the energy every tick, saves it, and cleans up when blocks are broken.

- RF-style `IEnergyStorage` (`receiveEnergy` / `extractEnergy` with `simulate`), in **HF** units
- Ready-made generator, machine, battery and cable classes you extend or use as they are
- Cable networks that form, merge and split on their own
- Redstone Flux style transfer between blocks that touch, no cable needed
- Priorities, per-tick rate limits and cable throughput tiers
- Saving per world, energy on items, event listeners, and `/hyflux` admin commands

Works with Hytale server **0.7** (Java 25).

## Contents

- [Installing](#installing-server-owners)
- [Quick start: energy blocks without Java](#quick-start-energy-blocks-without-java)
- [Tutorial: your first HyFlux mod (Java)](#tutorial-your-first-hyflux-mod-java)
- [How energy moves](#how-energy-moves)
- [API recipes](#api-recipes)
- [API reference](#api-reference)
- [Common mistakes](#common-mistakes)
- [Admin commands](#admin-commands) and [configuration](#configuration)
- [Building HyFlux from source](#building-hyflux-from-source)

## Installing (server owners)

Put `HyFlux-1.0.0.jar` in your `Mods` folder (singleplayer: `%APPDATA%/Hytale/UserData/Mods`).
HyFlux adds no blocks of its own; install the mods that use it. `HyFluxExample-1.0.0.jar` adds a
demo Flux Generator, Flux Cable, Flux Battery and Flux Lamp.

## Quick start: energy blocks without Java

The simplest way to use HyFlux needs **no code**. Add a file called `hyflux.json` to the root of
your mod (next to `manifest.json`) and list your blocks:

```json
{
  "Blocks": {
    "MyMod_Generator": { "Type": "Generator", "Capacity": 8000, "Output": 120, "Generation": 60, "ActiveState": "On" },
    "MyMod_Cable":     { "Type": "Cable", "Rate": 256 },
    "MyMod_Battery":   { "Type": "Battery", "Capacity": 500000, "Rate": 1000 },
    "MyMod_Lamp":      { "Type": "Machine", "Capacity": 200, "Input": 20, "Usage": 10, "ActiveState": "On" }
  }
}
```

Each key is the id of a block in your asset pack (the block item's file name). Then add HyFlux
to the `Dependencies` in your `manifest.json`:

```json
"Dependencies": { "HyFlux:HyFlux": "*" }
```

That's all. HyFlux finds `hyflux.json` in every installed mod when the world loads. A mod with
only a manifest, block JSON files and `hyflux.json` (no Java, no `Main`) works too.

| `Type` | Settings (HF and HF per tick) |
|---|---|
| `Generator` | `Capacity` (buffer), `Output` (most it gives per tick), `Generation` (made per tick) |
| `Machine` | `Capacity` (buffer), `Input` (most it takes per tick), `Usage` (used per working tick) |
| `Battery` | `Capacity`, `Rate` (in and out per tick), or separate `Input` and `Output` |
| `Cable` | `Rate` (most the cable network moves per tick) |

Every type also accepts `Priority` (higher is served first). Generators and machines accept
`ActiveState`: the name of a block state to show while running, defined under
`BlockType.State.Definitions` in the block JSON (see [step 3](#step-3-define-the-blocks)). The
block switches back to its normal look when it stops.

Server owners can power blocks from any mod, including vanilla ones, without touching that mod:
put the same kind of file in `mods/HyFlux_HyFlux/blocks/` (any name ending in `.json`).

Use the Java API below when a block needs its own logic: fuel, warm-up, recipes or custom saving.

## Tutorial: your first HyFlux mod (Java)

In this tutorial you build a small mod with four blocks:

| Block | What it does |
|---|---|
| Thermal Generator | Warms up for 10 seconds, then makes 60 HF every tick |
| Copper Cable | Carries up to 256 HF per tick |
| Battery | Stores 500,000 HF |
| Electric Grinder | Uses 40 HF per tick and finishes an item every 3 seconds |

The finished example mod in [`example/`](example) follows the same steps, so you can compare.

### Step 1: Set up the project

You need a JDK 25 or newer and Gradle (or a Gradle wrapper). Create this layout:

```
MyMod/
├── settings.gradle.kts
├── build.gradle.kts
├── libs/
│   ├── HytaleServer.jar
│   └── HyFlux-1.0.0.jar
└── src/main/
    ├── java/com/example/mymod/
    │   ├── MyModPlugin.java
    │   ├── ThermalGeneratorNode.java
    │   └── ElectricGrinderNode.java
    └── resources/
        ├── manifest.json
        └── Server/
            ├── Item/Items/MyMod/
            │   ├── MyMod_Thermal_Generator.json
            │   ├── MyMod_Copper_Cable.json
            │   ├── MyMod_Battery.json
            │   └── MyMod_Electric_Grinder.json
            └── Languages/en-US/mymod.lang
```

Copy the two jars into `libs/`:

- `HytaleServer.jar` is in your Hytale install:
  `%APPDATA%/Hytale/install/release/package/game/latest/Server/HytaleServer.jar`
- `HyFlux-1.0.0.jar` is built from this repository (see
  [Building HyFlux from source](#building-hyflux-from-source)). Put
  `HyFlux-1.0.0-sources.jar` next to it too, so your IDE shows HyFlux's documentation.

`settings.gradle.kts`:

```kotlin
rootProject.name = "MyMod"
```

`build.gradle.kts`:

```kotlin
plugins {
    java
}

dependencies {
    // The server and HyFlux are both present at runtime, so compile against them
    // but never bundle them into your jar.
    compileOnly(files("libs/HytaleServer.jar"))
    compileOnly(files("libs/HyFlux-1.0.0.jar"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25) // Hytale runs on Java 25
}
```

### Step 2: Declare HyFlux in `manifest.json`

`src/main/resources/manifest.json` tells Hytale about your plugin. The `Dependencies` entry makes
HyFlux load before your mod and lets your code use its classes:

```json
{
  "Group": "MyName",
  "Name": "MyMod",
  "Version": "1.0.0",
  "Description": "Machines powered by HyFlux",
  "Authors": [{ "Name": "MyName" }],
  "Main": "com.example.mymod.MyModPlugin",
  "ServerVersion": ">=0.7.0",
  "Dependencies": {
    "HyFlux:HyFlux": "*"
  },
  "OptionalDependencies": {},
  "IncludesAssetPack": true
}
```

`IncludesAssetPack: true` is needed because your jar also contains block definitions (step 3).

### Step 3: Define the blocks

Each block is an item with a `BlockType`, in `Server/Item/Items/`. The file name is the block id
that you bind to HyFlux in step 6. This one reuses a vanilla texture, so no art is needed.

`Server/Item/Items/MyMod/MyMod_Thermal_Generator.json`:

```json
{
  "TranslationProperties": {
    "Name": "mymod.items.MyMod_Thermal_Generator.name",
    "Description": "mymod.items.MyMod_Thermal_Generator.description"
  },
  "ItemLevel": 5,
  "Quality": "Uncommon",
  "MaxStack": 64,
  "Categories": ["Blocks.Metal"],
  "PlayerAnimationsId": "Block",
  "BlockType": {
    "Material": "Solid",
    "DrawType": "Cube",
    "Group": "Metal",
    "HitboxType": "Full",
    "Flags": {},
    "Gathering": { "Breaking": { "GatherType": "Rocks", "ItemId": "MyMod_Thermal_Generator" } },
    "BlockParticleSetId": "Metal",
    "Textures": [{ "Weight": 1, "All": "BlockTextures/Metal_Copper_Decorative.png" }],
    "ParticleColor": "#de6e37",
    "BlockSoundSetId": "Metal",
    "PhysicalMaterialId": "Metal",
    "CubeShadingMode": "Standard"
  },
  "IconProperties": { "Scale": 0.58823, "Rotation": [22.5, 45, 22.5], "Translation": [0, -13.5] },
  "Icon": "Icons/ItemsGenerated/Metal_Copper_Decorative.png",
  "ItemSoundSetId": "ISS_Items_Metal"
}
```

Make the cable and battery the same way, with their own id, names and textures.

The grinder also gets an **`On` state**, so it can look different while it runs. Add a `State`
block inside its `BlockType`. Everything in `On` overrides the normal look; here it glows:

```json
    "State": {
      "Definitions": {
        "On": {
          "Light": { "Color": "#fc8" },
          "Textures": [{ "Weight": 1, "All": "BlockTextures/Metal_Copper_Ornate.png" }]
        }
      }
    }
```

Finally, give the blocks names in `Server/Languages/en-US/mymod.lang`. The key in the JSON is
`mymod.items.<id>.name`, where `mymod` is the file name:

```properties
items.MyMod_Thermal_Generator.name = Thermal Generator
items.MyMod_Thermal_Generator.description = Makes 60 HF/t once it has warmed up.
items.MyMod_Copper_Cable.name = Copper Cable
items.MyMod_Battery.name = Battery
items.MyMod_Electric_Grinder.name = Electric Grinder
```

### Step 4: Write the generator

Generators extend `ProducerNode`. The constructor sets three numbers: how much energy the
generator can hold, how fast it gives energy out, and how much it makes per tick. Override
`getGeneration` to decide how much to make each tick:

```java
package com.example.mymod;

import dev.hyflux.api.node.NodeContext;
import dev.hyflux.api.node.NodeData;
import dev.hyflux.api.node.NodeType;
import dev.hyflux.api.node.ProducerNode;

/** A generator that warms up for 10 seconds, then makes 60 HF every tick. */
public final class ThermalGeneratorNode extends ProducerNode {

    // The id is written into world saves: never change it after release.
    public static final NodeType TYPE = NodeType.of("mymod:thermal_generator", ThermalGeneratorNode::new);

    private static final int WARMUP_TICKS = 300; // 10 seconds at 30 ticks per second

    private int heat;

    public ThermalGeneratorNode() {
        super(8_000, 120, 60); // holds 8000 HF, gives out up to 120 HF/t, makes up to 60 HF/t
    }

    @Override
    protected long getGeneration(NodeContext context) {
        if (heat < WARMUP_TICKS) {
            heat++;
        }
        return getGenerationPerTick() * heat / WARMUP_TICKS;
    }

    // Save and load your own fields. Stored energy is saved for you.
    @Override
    protected void writeData(NodeData data) {
        data.putInt("Heat", heat);
    }

    @Override
    protected void readData(NodeData data) {
        heat = data.getInt("Heat", 0);
    }
}
```

When nothing takes its energy, a generator keeps generating until it is full, then holds the
energy until a machine, battery or cable needs it.

### Step 5: Write the machine

Machines extend `ConsumerNode`. HyFlux fills the machine's buffer; every tick the machine
spends `usagePerTick` HF and runs `doWork`. If there is not enough energy, it pauses for that tick.

```java
package com.example.mymod;

import dev.hyflux.api.node.ConsumerNode;
import dev.hyflux.api.node.NodeContext;
import dev.hyflux.api.node.NodeData;
import dev.hyflux.api.node.NodeType;

/** A machine that uses 40 HF per tick and finishes one item every 3 seconds. */
public final class ElectricGrinderNode extends ConsumerNode {

    public static final NodeType TYPE = NodeType.of("mymod:electric_grinder", ElectricGrinderNode::new);

    private static final int TICKS_PER_ITEM = 90; // 3 seconds

    private int progress;
    private long itemsMade;

    public ElectricGrinderNode() {
        super(10_000, 200, 40); // holds 10000 HF, takes in up to 200 HF/t, uses 40 HF per working tick
        setActiveState("On");   // show the block's "On" state (step 3) while it runs
    }

    @Override
    protected boolean canWork(NodeContext context) {
        return true; // return false while there is nothing to grind, so the machine idles without using energy
    }

    @Override
    protected void doWork(NodeContext context) {
        progress++;
        if (progress >= TICKS_PER_ITEM) {
            progress = 0;
            itemsMade++;
            // Your machine's job goes here: turn an input item into an output, drop a product, ...
        }
    }

    public int getProgressPercent() {
        return progress * 100 / TICKS_PER_ITEM;
    }

    public long getItemsMade() {
        return itemsMade;
    }

    @Override
    protected void writeData(NodeData data) {
        data.putInt("Progress", progress);
        data.putLong("ItemsMade", itemsMade);
    }

    @Override
    protected void readData(NodeData data) {
        progress = data.getInt("Progress", 0);
        itemsMade = data.getLong("ItemsMade", 0);
    }
}
```

`setActiveState("On")` switches the block to its `On` state while the machine runs and back when
it stops. State variants share their block's binding, so the machine keeps its node and energy.
For other effects (sounds, particles), override `onPoweredChanged(context, powered)`.

### Step 6: Bind the blocks in your plugin

Bind each block id to a node in your plugin's `setup()`. That's all the wiring there is: HyFlux
creates the node when a player places the block, connects it, runs it every tick, saves it, and
removes it when the block is broken.

```java
package com.example.mymod;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hyflux.api.HyFlux;
import dev.hyflux.api.node.BatteryNode;
import dev.hyflux.api.node.CableNode;

public final class MyModPlugin extends JavaPlugin {

    public MyModPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // Blocks with custom behaviour use their own node class.
        HyFlux.blocks().bind("MyMod_Thermal_Generator", ThermalGeneratorNode.TYPE);
        HyFlux.blocks().bind("MyMod_Electric_Grinder", ElectricGrinderNode.TYPE);

        // Plain cables and batteries need no class of their own.
        HyFlux.blocks().bind("MyMod_Copper_Cable", () -> new CableNode(256));             // carries 256 HF/t
        HyFlux.blocks().bind("MyMod_Battery", () -> new BatteryNode(500_000, 1_000));   // 500k HF, 1000 HF/t in and out
    }
}
```

### Step 7: Build and try it

```bash
./gradlew build
```

Copy `build/libs/MyMod.jar` and `HyFlux-1.0.0.jar` into your `Mods` folder and start the game.
Then:

1. Place the Thermal Generator and the Electric Grinder **touching each other**. The grinder
   runs at full speed once the generator has warmed up.
2. Move the grinder away and connect the two with Copper Cables. It works over any distance.
3. Put a Battery on the cable. It stores the surplus and keeps the grinder running after you
   break the generator.
4. Look at any of your blocks and run `/hyflux probe` to see its energy, what it received and
   sent last tick, and its cable network.

If a block doesn't work, the server log prints a warning at startup for any bound block id that
doesn't exist. That is usually a typo between the JSON file name and `bind(...)`.

## How energy moves

HyFlux follows the rules of Redstone Flux mods such as Thermal Expansion.

### The four kinds of block

| Role | Base class | Gives energy to | Takes energy from |
|---|---|---|---|
| `PRODUCER` (generator) | `ProducerNode` | touching machines and batteries, and its cables | nothing |
| `CONSUMER` (machine) | `ConsumerNode` | **nothing**: machines never pass energy on | touching generators and batteries, and its cables |
| `STORAGE` (battery) | `BatteryNode` | touching machines, and its cables | touching generators, and its cables |
| `CONDUIT` (cable) | `CableNode` | carries energy for its cable network; holds none | |

### Blocks that touch

A generator or battery pushes energy straight into the blocks it touches, no cable needed:

- generator touching a machine: powers it
- generator touching a battery: charges it
- battery touching a machine: powers it
- machine touching a machine: **nothing**

Energy moves one block at a time and never *through* a block: a machine between a generator and a
battery does not charge the battery, and batteries that touch each other do not share.

### Cables

A connected run of cables is a **network** (`EnergyNetwork`) and carries energy over any
distance. Generators, machines and batteries plug into every cable run they touch, but a network
is made of cables only:

- a machine between two separate cable runs receives from both, but never passes energy from one
  run to the other
- a generator between two runs feeds both, but does not join them
- a battery between two runs charges from one and discharges into the other, because it stores
  the energy in between

A network moves at most as much per tick as its **slowest cable** (`CableNode(transferRate)`), so
cable tiers work like Redstone Flux ducts. Placing a cable that touches two runs merges them;
breaking one that held a run together splits it.

### Each tick

1. Every block runs its `tick`: generators fill their buffers, machines spend energy and work.
2. Generators push into the machines they touch.
3. Every cable network shares out its energy: machines are served first from generators, batteries
   cover what generators can't, and leftover generation charges batteries.
4. Batteries push into the machines they touch, then generators charge the batteries they touch.

Within each step, **higher priority goes first** (`setPriority`); equal priorities share evenly.
Rate limits count per tick across all of this: a machine that accepts 200 HF/t never takes more
than 200 HF in one tick, however many generators, batteries and cables feed it. No energy is ever
created or lost. Amounts are per world tick (30 per second).

### Unloaded chunks, saving and threading

- **Unloaded chunks**: blocks in unloaded chunks pause until their chunk loads again.
- **Saving**: each world saves to `<world save>/HyFlux/energy.json`, written every 60 seconds if
  something changed, on `/hyflux save`, when the world unloads, and on shutdown. If a mod is
  removed, its blocks' data is kept, so reinstalling it brings them back.
- **Threading**: every HyFlux callback runs on the thread of the world it belongs to. Call
  `EnergyWorld` methods that change things from that thread (inside a system, a player or world
  command, or `world.execute(...)`). HyFlux throws an error otherwise rather than corrupting the network.

## API recipes

Everything starts from the `HyFlux` class (`dev.hyflux.api.HyFlux`).

### Show a machine's energy (UIs, tooltips, commands)

```java
EnergyWorld energy = HyFlux.world(world);   // on the world's thread
ElectricGrinderNode grinder = energy.getNode(pos, ElectricGrinderNode.class);
if (grinder != null) {
    IEnergyStorage buffer = grinder.getEnergyStorage();
    NodeFlow flow = energy.getFlow(pos);     // what it received and sent last tick
    String text = "Grinder " + grinder.getProgressPercent() + "% "
        + EnergyUtil.bar(buffer) + " " + EnergyUtil.format(buffer.getEnergyStored())
        + " (receiving " + EnergyUtil.formatRate(flow.received()) + ")";
}
```

`EnergyUtil.format(12500)` gives `12.5k HF`, `formatRate` adds `/t`, and `bar` draws `[|||||.....]`.

### Inspect the cable networks of a block

```java
for (EnergyNetwork network : energy.getNetworksAt(pos)) {
    NetworkStats stats = network.getLastStats();
    // stats.consumed(), stats.demand(), stats.produced(), stats.charged(), stats.discharged()
    // network.getStoredEnergy(), network.getTransferLimit(), stats.limited() = cables are the bottleneck
}
```

### Place energy blocks from code

Player placing and breaking is detected for you, and breaks that skip events (explosions,
commands) are caught by a background check. To place a block from code, use `HyFlux.placeBlock`,
which also creates the block's energy node right away:

```java
HyFlux.placeBlock(world, new BlockPos(x, y, z), "MyMod_Battery");     // false if the chunk isn't loaded
String id = HyFlux.getBlockId(world, new BlockPos(x, y, z));            // null if the chunk isn't loaded
HyFlux.setBlockState(world, new BlockPos(x, y, z), "On");              // or "default" for the normal look
```

These helpers use Hytale's current block API for you, so your mod doesn't break when Hytale
changes it. If you place blocks some other way, call `HyFlux.world(world).refreshBlock(pos)` afterwards.

### Choose which sides connect, and priorities

```java
BatteryNode battery = energy.getNode(pos, BatteryNode.class);
battery.setSideEnabled(Direction.UP, false); // nothing connects through the top any more
battery.setPriority(10);                     // charged and drained before other batteries
```

Both settings are saved. For fixed rules (a generator that only outputs from its front, say),
override `canConnect(Direction side)` in your node class instead.

### Energy items (battery packs, tools)

```java
HyFlux.items().register("MyMod_Battery_Pack", 50_000, 500);   // in setup(): capacity, max transfer

ItemStack stack = container.getItemStack(slot);
if (ItemEnergy.isEnergyItem(stack)) {
    ItemEnergyStorage cell = ItemEnergy.storage(stack);         // an IEnergyStorage view of the stack
    EnergyUtil.transfer(battery.getEnergyStorage(), cell, 500); // charge it from a battery block
    container.setItemStackForSlot(slot, cell.getStack());       // item stacks are immutable: put it back
}
```

Energy is stored in the item's metadata (`HyFlux_Energy`) and counts per stack, so give energy
items a `MaxStack` of 1.

### Listen to events

```java
HyFlux.addListener(new EnergyListener() {
    @Override
    public void onNodeAdded(EnergyWorld world, BlockPos pos, EnergyNode node) { }

    @Override
    public void onNodeRemoved(EnergyWorld world, BlockPos pos, EnergyNode node, DetachReason reason) { }

    @Override
    public void onNetworkTick(EnergyNetwork network, NetworkStats stats) {
        // runs every tick for every network: keep it cheap
    }
});
```

Also available: `onNetworkCreated` and `onNetworkRemoved`. A node itself gets `onAdded`,
`onRemoved(reason)`, `onNetworkChanged` and `onPoweredChanged` (machines).

### Nodes that are not blocks

A node can live at any position without a block, for example for a multiblock controller.
Register its type so it can be restored after a restart:

```java
NodeType source = NodeType.of("mymod:creative_source", () -> new ProducerNode(1_000_000, 10_000, 10_000));
HyFlux.nodeTypes().register(source);          // in setup()
HyFlux.world(world).addNode(pos, source);     // later, on the world thread
HyFlux.world(world).removeNode(pos);
```

### Make HyFlux optional

List it under `OptionalDependencies` instead of `Dependencies`. Your mod must then run without
HyFlux's classes, so keep every HyFlux call in its own class and only touch that class when
HyFlux is there:

```java
// In your plugin's setup():
if (isHyFluxInstalled()) {
    HyFluxCompat.register();   // a class of yours that does the HyFlux.blocks().bind(...) calls
}

private static boolean isHyFluxInstalled() {
    try {
        Class.forName("dev.hyflux.api.HyFlux");
        return true;
    } catch (ClassNotFoundException | LinkageError e) {
        return false;
    }
}
```

This is the standard Java pattern for optional dependencies; it has not been tested with HyFlux in game yet.

## API reference

Only the `dev.hyflux.api` packages are public API; `dev.hyflux.core` and `dev.hyflux.plugin` are
internal and may change. The javadoc jar (`HyFlux-1.0.0-javadoc.jar`) documents every method.

| Class | Package | Use it to |
|---|---|---|
| `HyFlux` | `api` | Get everything else: `blocks()`, `items()`, `nodeTypes()`, `world(World)`, `hytaleWorld(...)`, `addListener(...)`, and the block helpers `placeBlock`, `getBlockId`, `setBlockState` |
| `ProducerNode` | `api.node` | Make a generator (`getGeneration`, `generate`, `getLastGenerated`) |
| `ConsumerNode` | `api.node` | Make a machine (`canWork`, `doWork`, `onPoweredChanged`, `isPowered`) |
| `BatteryNode` | `api.node` | Make a battery |
| `CableNode` | `api.node` | Make a cable with a transfer rate |
| `AbstractEnergyNode` | `api.node` | Shared base: `getStorage()`, `setPriority`, `setSideEnabled`, `setActiveState`, `writeData`/`readData`, `onAdded`/`onRemoved`, `getContext()` |
| `EnergyNode` | `api.node` | Implement a node from scratch when the base classes don't fit |
| `NodeType` | `api.node` | Name a kind of node: `NodeType.of("mymod:id", MyNode::new)` |
| `NodeContext` | `api.node` | Passed to node callbacks: `pos()`, `world()`, `network()`, `networks()`, `markDirty()`, `setBlockState(...)` |
| `NodeData` | `api.node` | Save and load your node's fields |
| `EnergyWorld` | `api.network` | Per-world access: `getNode`, `getStorage`, `getFlow`, `getNetwork`, `getNetworksAt`, `refreshBlock`, `addNode`, `removeNode` |
| `EnergyNetwork` | `api.network` | A cable network: `getLastStats`, `getStoredEnergy`, `getTransferLimit`, `count(role)` |
| `NetworkStats` | `api.network` | What a network did last tick |
| `NodeFlow` | `api.network` | What one block received and sent last tick |
| `IEnergyStorage` | `api.energy` | The RF-style storage interface every node exposes |
| `EnergyStorage` | `api.energy` | The standard buffer: `generate`, `consume`, `tryConsume`, `setEnergy` |
| `EnergyUtil` | `api.energy` | `transfer`, `format`, `formatRate`, `bar` |
| `ItemEnergy` | `api.item` | Read and change energy on item stacks |
| `EnergyListener` | `api.event` | React to blocks and networks appearing, disappearing and ticking |
| `BlockPos`, `Direction` | `api.math` | Block positions and the six faces |

## Common mistakes

- **HyFlux is missing from `Dependencies` in `manifest.json`.** Your mod loads before HyFlux, or
  can't see its classes, and `HyFlux.blocks()` throws "HyFlux is not loaded".
- **The HyFlux jar is bundled into your jar.** Use `compileOnly`, never `implementation`. A
  bundled copy of the API is never set up, so every call through it fails with "HyFlux is not loaded".
- **Blocks are bound outside `setup()`.** Register in `setup()` so bindings exist before worlds
  load and saved blocks can be restored.
- **A node type id is changed after release.** The id (`"mymod:grinder"`) is how saves find your
  node class; renaming it orphans every placed block of that type.
- **`EnergyWorld` is called from another thread.** Use `world.execute(() -> ...)`.
- **Blocks are placed from code with Hytale's own calls.** HyFlux only sees player placements by
  itself. Use `HyFlux.placeBlock(...)`, or call `refreshBlock(pos)` afterwards.
- **A block id in `hyflux.json` doesn't match the block's file name.** At startup the server log
  warns about every bound block id that doesn't exist, and about invalid entries, naming the file.
- **Machines touching each other are expected to share.** They don't; connect them with cables or
  put a generator or battery next to them.
- **An energy item has `MaxStack` above 1.** Energy is per stack, so stacked items share one charge.

## Admin commands

`/hyflux` (alias `/hf`):

| Command | What it does |
|---|---|
| `/hyflux info` | Version, registered node types, bound blocks and energy items, and counts per world |
| `/hyflux probe` | Shows the block you are looking at: energy, what it received and sent last tick, and its cable networks |
| `/hyflux networks [--world=name]` | Lists the largest cable networks of a world |
| `/hyflux scan [radius]` | Re-checks the blocks around you, adding missing nodes and removing stale ones (after building with tools or prefabs) |
| `/hyflux save` | Saves every energy world now |
| `/hyflux selftest` | Runs the energy engine against scripted setups and prints pass or fail |

The example mod adds `/hyfluxdemo here`, `/hyfluxdemo at <x> <y> <z>` (a cabled rig),
`/hyfluxdemo touching <x> <y> <z>` (blocks touching without cables),
`/hyfluxdemo bridge <x> <y> <z>` (two cable runs with a lamp between them) and
`/hyfluxdemo status <x> <y> <z>`.

## Configuration

`mods/HyFlux_HyFlux/config.json`, created on first start:

| Key | Default | Meaning |
|---|---|---|
| `AutosaveIntervalSeconds` | 60 | Seconds between saves of worlds that changed |
| `ValidateIntervalTicks` | 20 | Ticks between background checks for blocks that disappeared without an event |
| `ValidateBatchSize` | 64 | Blocks checked per background check |
| `DirectTransfer` | true | Generators and batteries push into touching blocks. `false` makes cables the only way. Takes effect after a restart |
| `TickUnloadedChunks` | false | Keep blocks in unloaded chunks running |
| `MaxScanRadius` | 32 | Largest `/hyflux scan` radius |
| `DebugLogging` | false | Log every node HyFlux adds or removes |

## Building HyFlux from source

You need a JDK 25 or newer and a local Hytale install. The build finds `HytaleServer.jar` in the
default launcher location; otherwise pass `-PhytaleServerJar=<path>` or set `HYTALE_SERVER_JAR`.

```bash
./gradlew build
```

This produces:

- `hyflux/build/libs/HyFlux-1.0.0.jar`, the plugin and API, plus `-sources` and `-javadoc` jars
- `example/build/libs/HyFluxExample-1.0.0.jar`, the example mod

`./gradlew :hyflux:test` runs the engine tests. `./gradlew :hyflux:publishToMavenLocal`
publishes `dev.hyflux:hyflux:1.0.0` to your local Maven repository, if you prefer
`compileOnly("dev.hyflux:hyflux:1.0.0")` with `repositories { mavenLocal() }` over a jar in `libs/`.

## Project layout

```
hyflux/src/main/java/dev/hyflux/
  api/        public API: HyFlux, energy, nodes, networks, registries, items, events
  core/       the simulation (plain Java, no Hytale classes, unit tested)
  plugin/     Hytale integration: plugin, world ticking, block events, saving, commands
example/      a mod built on HyFlux: Flux Generator, Flux Cable, Flux Battery, Flux Lamp
```

## Status

Built and tested against Hytale server **0.7.0** on a headless server:
- generators, machines, batteries and cables, both touching and on cable runs
- separate cable runs, unloaded chunks, and saving across restarts
- `hyflux.json` loading from a Java mod and from a content-only mod with no Java

On 0.6.8 it was also tested by placing and breaking blocks in the game client. That hasn't been
repeated on 0.7 yet, and neither has the energy item helpers or HyFlux as an optional dependency.

## License

HyFlux is released under the [MIT License](LICENSE). Your mod can depend on it whatever license
your mod uses.
