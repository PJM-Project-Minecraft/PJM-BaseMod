# PJM World Map — Port Plan (Xaero look, leaner engine)

Faithful-but-lean fullscreen world map for PJM-BaseMod. Keeps Xaero's **look**
(texture-averaged colors + biome tint + top-left slope shading) and its
**persistence** (explored terrain survives relog, per server+dimension). Drops
Xaero's over-engineering: no LOD mip branch-tree, no PBO streaming, no object
pools, no MapProcessor pipeline, no cave layers, no translucent-overlay system,
no multitexture batcher, no framebuffer tricks.

Everything is **client-only** (`src/client`), respecting the CLAUDE.md hard
rule (`main` never imports `client`). The **only** `src/main` change is a tiny
S→C base-zone sync (Phase 5) — base zones are currently server-only. Capture
points are already client-mirrored (`ClientCapturePointState`).

---

## 0. Core design decisions (the lean bets)

| Xaero does | We do | Why |
|---|---|---|
| 64×64 tilechunk texture, 64 per region, PBO sub-rect uploads | **One `DynamicTexture`(512×512) per region**, write dirty 16×16 chunk into its `NativeImage`, `upload()` | 1 MB upload occasionally is free; kills the whole PBO/branch/pool stack |
| Store block-state + biome palettes, recompute color at load | **Store baked `baseColor` (texture-avg × biome-tint × glow) + `height`** — 6 bytes/pixel | Color pipeline lives in ONE place (scan only); load is memcpy + reshade. `ponytail:` tint won't auto-correct if the resource pack/biome registry changes between sessions — wipe cache dir then. On a fixed PvP server that never happens |
| Translucent overlays (water depth), topHeight, cave layers | **Skip** — water renders as its averaged water-top color | Analyses list all three as skippable; big branch cut. `ponytail:` lakes are flat blue, no depth tint |
| Per-pixel light byte + light→alpha | **Drop light from the visual and the record** | Surface is daylit; light unmentioned in the look. `ponytail:` no day/night dimming; re-add a light byte if wanted |
| 5-sample biome cross-blend | **Single-sample biome tint** (the pixel's own biome) | `ponytail:` slightly harder biome borders; add the 5-sample plus-blend later if seams look bad |
| Full BlockState identity for color | **`Block` registry-id → defaultBlockState** at scan (we already hold the live state; id is only for the color cache key) | Wool/terracotta colors are separate blocks; only lit-vs-unlit states differ, cosmetic |
| Adaptive frame-budget scheduler, dirty-field mixin | **K chunks/tick, main-thread snapshot → 1 ExecutorService thread color-compute → main-thread GPU upload** | Honors the requested "single background thread"; keeps ClientLevel access on-thread |

**Per-pixel record (RAM + disk): `int baseColor` (0xAARRGGBB, pre-shading) + `short height` (surface Y, `UNSET = Short.MIN_VALUE`). = 6 bytes.**
Slope + depth shading are **recomputed** from neighbor heights every time the
GPU texture is refreshed — never stored (matches Xaero: slope is load-time).

RAM per region: `int[262144]` (1 MB) + `short[262144]` (0.5 MB) + `NativeImage`
512² (1 MB GPU) ≈ **2.5 MB/region**. LRU cap (default 64 regions ≈ 160 MB
worst case; config knob) with `NativeImage.free()` on evict.

---

## 1. Package layout — `src/client/java/ru/liko/pjmbasemod/client/worldmap/`

```
worldmap/
  WorldMapEngine.java        Facade+singleton. Region LRU cache (RegionKey→Region), lifecycle.
                             onClientTick(mc): drive scanner, drain GPU queue, autosave tick.
                             onLogout(): flush all dirty → disk, free textures, clear.
                             heightAt(dimKey,wx,wz) for cross-region slope. regionsInView(...).

  data/
    MapConstants.java        REGION_BLOCKS=512, CHUNK=16, CHUNKS_PER_REGION=32, HEIGHT_UNSET,
                             shading constants, FORMAT_VERSION=1. All magic numbers.
    RegionKey.java           record(String dimKey,int rx,int rz). Map key + filename source.
    Region.java              Canonical RAM tile: int[512*512] baseColor, short[512*512] height,
                             BitSet scannedChunks(1024), boolean dirtyDisk, RegionTexture gpu.
                             putChunk(cx,cz,int[256] color,short[256] h): write + mark dirty.
    ChunkSnapshot.java       Transient main-thread capture handed to the worker:
                             RegionKey+inRegionChunkX/Z + BlockState[256] + short height[256]
                             + Holder<Biome>[256]. (No ClientLevel refs — safe off-thread.)

  color/
    BlockColorSampler.java   Texture-averaged ARGB per BlockState (ImageIO PNG mean, cached).
                             Xaero getUncachedTextureColor. Falls back to MapColor on black/missing.
    BiomeTintShim.java       BlockAndTintGetter over one (BlockState,Biome); feeds
                             Minecraft.getBlockColors().getColor(state,this,pos,tintIndex).
    ColorPipeline.java       BlockState+Biome+glow → baseColor (avg × tint × glow-boost).
                             Runs on the worker only.
    PixelShader.java         Pure: baseColor + h + hNorth + hNW → shaded 0xAARRGGBB
                             (depthBrightness × directional slope). demo()/__main__ self-check here.

  io/
    WorldId.java             worldKey (SP world-folder name / MP "mp_"+sanitize(ip)),
                             dimKey (level.dimension().location() → ':'→'.', '/'→'_').
    RegionCodec.java         Region ↔ gzip bytes. FORMAT_VERSION, 32×32 present-bool,
                             256×{int baseColor, short height}. Static encode/decode.
    RegionStore.java         Folder scheme + atomic write (.tmp→ATOMIC_MOVE) + load(key)→Region|null.
                             1 daemon writer thread draining a save queue; 60s autosave; flushAll().

  gpu/
    RegionTexture.java       NativeImage(512,512)+DynamicTexture, registered ResourceLocation,
                             NEAREST filter. writeChunk(cx,cz,int[256] shaded), upload(), free().
    GpuRefreshQueue.java     ConcurrentLinkedQueue<RegionKey> of GPU-dirty regions; drainN(k) on
                             render thread reshades changed chunks → RegionTexture → upload().

  scan/
    MapScanner.java          Main-thread. Each tick: pick ≤K FULL chunks (self+8 neighbors FULL)
                             in a window around player not yet scanned/dirty → ChunkSnapshot →
                             submit to ColorWorker. Requests RegionStore.load on region-enter.
    ColorWorker.java         Single-thread ExecutorService. ChunkSnapshot → ColorPipeline per
                             pixel → Region.putChunk → enqueue GpuRefreshQueue + RegionStore save.

  gui/
    MapScreen.java           extends Screen (NOT PjmBaseScreen). Camera(cameraX/Z,userScale,
                             destScale), pan/zoom/follow, player arrow, X/Z + zoom readout.
                             render(): tiles → arrow → overlays → readouts → super (widgets).
    MapRenderer.java         Static: given camera + regionsInView, pose push/translate/scale,
                             blit each RegionTexture quad at world pos. World↔screen helpers.

  overlay/
    MapOverlays.java         Draws capture-point polygons (ClientCapturePointState) + base-zone
                             AABB rects (ClientBaseZoneState) in map space via MapRenderer's
                             world→screen transform. Owner-team color, labels.
```

**Client mirror + common sync for base zones (Phase 5, the one `src/main` touch):**
```
src/client/.../client/basezone/ClientBaseZoneState.java   volatile List<BaseZoneView>, update/reset (mirror convention, like ClientCapturePointState)
src/main/.../common/network/packet/BaseZoneMapSyncPacket.java   S→C: List<{name,dim,owner,minX,minY,minZ,maxX,maxY,maxZ}>
+ ClientPacketProxy.baseZoneMapSync (default no-op)
+ PjmNetworking register + VERSION "47"→"48"
+ ClientPacketHandlersImpl.baseZoneMapSync → ClientBaseZoneState.update
+ BaseZoneManager.broadcast(server) on change + PjmServerEvents.onLogin initial send
```

**Skipped classes** (Xaero has them, we don't): `MapProcessor`, `MapRunner`,
`MapWriter`, `Branch*RegionTexture`, `LeveledRegion`, `TextureUploader`/PBO,
`MapTilePool`/`MapPool`, `BiomeBlendCalculator`, `MapTileChunk`/`MapTile`
nesting, `MapCamera` (folded into MapScreen).

---

## 2. Adapted algorithms (code-ready, exact constants)

### 2.1 Surface pick — main thread, per column (x,z ∈ 0..15) of a FULL chunk
```
int surfaceStart = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);   // start Y
if (surfaceStart >= level.getMaxBuildHeight()) surfaceStart = level.getMaxBuildHeight()-1;
BlockState surf = null; int surfY = level.getMinBuildHeight();             // sentinel floor
MutableBlockPos p = new MutableBlockPos();
for (int h = surfaceStart; h >= level.getMinBuildHeight(); h--) {
    p.set(wx, h, wz);
    BlockState s = chunk.getBlockState(p);
    if (isInvisible(s)) continue;                 // short grass, torch, flowers(off), glass, tall/double plants
    FluidState f = s.getFluidState();
    if (!f.isEmpty()) s = f.createLegacyBlock();   // water/lava render as their own top color
    else if (s.isAir()) continue;
    int col; try { col = s.getMapColor(level, p).col; } catch (Throwable t) { continue; }
    if (col == 0) continue;                         // no/black vanilla color → skip (invisible-ish)
    surf = s; surfY = h; break;                     // FIRST opaque colored block = surface
}
// snapshot: state=surf (or AIR), height=(short)surfY, biome=level.getBiome(p@surfY)
```
`isInvisible(s)`: `s.getRenderShape()==INVISIBLE && !(block instanceof LiquidBlock)` ‖ TORCH ‖
SHORT_GRASS ‖ GLASS/GLASS_PANE ‖ non-flower DoublePlantBlock ‖ (flowers unless config).
**Overlays/topHeight dropped** — first opaque hit is the pixel.

### 2.2 Base color — worker thread (`ColorPipeline`)
**Phase 1–2 (bootstrap):** `baseColor = 0xFF000000 | state.getMapColor(level,pos).col`.

**Phase 3 (the Xaero palette) — texture-averaged (`BlockColorSampler`):**
```
BakedModel m = mc.getBlockRenderer().getBlockModelShaper().getBlockModel(state);
BakedQuad up = biggestQuad(m, state, Direction.UP);        // max XZ-area UP quad
TextureAtlasSprite sprite = (up != null && up.getSprite()!=missing) ? up.getSprite()
                                                                    : m.getParticleIcon(ModelData.EMPTY);
int tintIndex = (up != null) ? up.getTintIndex() : 0;
ResourceLocation png = ResourceLocation(sprite.contents().name().getNamespace(),
                                        "textures/"+sprite.contents().name().getPath()+".png");
BufferedImage img = ImageIO.read(resourceManager.getResource(png)... );   // SOURCE png, not atlas
int ts = min(img.width, img.height);                       // square = 1st animation frame
int diff = max(1, min(4, ts/8));                            // 16px→2, 32px→4  (~8×8=64 samples)
int parts = ts/diff;
long r=0,g=0,b=0,a=0,n=0;
for (i in 0..parts-1) for (j in 0..parts-1) {
    int rgb = (img.colorModel.numComponents<3) ? grayToArgb(raster,i*diff,j*diff)
                                               : img.getRGB(i*diff, j*diff);
    int al = (rgb>>>24)&0xFF;
    if (rgb!=0 && al>10) { r+=(rgb>>16)&0xFF; g+=(rgb>>8)&0xFF; b+=rgb&0xFF; n++; }   // alpha>10, unweighted mean
}
if (n==0) n=1;
int avg = 0xFF000000 | ((int)(r/n)<<16) | ((int)(g/n)<<8) | (int)(b/n);
if ((avg&0xFFFFFF)==0) throw Black;                         // pure-black top → fall back
// cache: Map<BlockState,Integer> color, Map<BlockState,Integer> tintIndex
```
Fallback chain (FileNotFound while convert / Black / any throw): use particle icon,
then `state.getMapColor(level,pos).col`.

**Biome tint (multiply), when `tintIndex != -1`:**
```
int tint = mc.getBlockColors().getColor(state, new BiomeTintShim(state, biome), pos, tintIndex);
// tintIndex==-1 (stone etc.) → tint=-1=0xFFFFFFFF → below is a no-op
r = ((avg>>16&0xFF) * (tint>>16&0xFF)) / 255;   // per-channel MULTIPLY
g = ((avg>>8 &0xFF) * (tint>>8 &0xFF)) / 255;
b = ((avg    &0xFF) * (tint    &0xFF)) / 255;
```
`BiomeTintShim implements BlockAndTintGetter`: `getShade→1f`, `getBlockState→state`,
`getBlockTint(pos,resolver)→resolver.getColor(biome.value(), pos.x, pos.z)`,
`getLightEngine→null`, dims from `mc.level`. Vanilla grass/foliage/water resolvers
come free through `getBlockColors().getColor`.

**Glow boost (emissive), fold into baseColor:**
```
if (state.getLightEmission() > 0) { long t=r+g+b; double k=Math.max(1.0, 407.0/Math.max(1,t));
    r=min(255,(int)(r*k)); g=min(255,(int)(g*k)); b=min(255,(int)(b*k)); }
baseColor = 0xFF000000 | (r<<16)|(g<<8)|b;   // stored: pre-shading, tint+glow baked
```

### 2.3 Slope + shading — render thread (`PixelShader`), recomputed every refresh
```
int hN  = heightAt(x, z-1);     // NORTH  (UNSET → treat as == h → slope 0)
int hNW = heightAt(x-1, z-1);   // NORTH-WEST
int h   = height[idx];
int vSlope = clamp(h - hN , -128, 127);      // ONLY these two neighbors = top-left sun
int dSlope = clamp(h - hNW, -128, 127);

// depth (slopes>=2 range)
double depth = clamp(h / 63.0, 0.9, 1.0);

// directional light (slopes==2 path), constants EXACT:
final double AMBIENT_COLORED=0.2, AMBIENT_WHITE=0.5, MAX_DIRECT=0.6666667, Q=0.88388;
double cos;
if (vSlope==1 && dSlope==1) cos = 1.0;
else { double crossX=vSlope-dSlope, crossZ=-vSlope, cast=1-crossZ;
       double mag=Math.sqrt(crossX*crossX + 1 + crossZ*crossZ);
       cos = cast/mag/Math.sqrt(2); }
double direct = (cos==1)?MAX_DIRECT : (cos>0? Math.ceil(cos*10)/10.0*MAX_DIRECT*Q : 0);
double bright = AMBIENT_COLORED /*shadow=1*/ + (AMBIENT_WHITE + direct);   // 0.7 .. 1.3667
double f = bright * depth;

int R = min(255,(int)((baseColor>>16&0xFF)*f));
int G = min(255,(int)((baseColor>>8 &0xFF)*f));
int B = min(255,(int)((baseColor    &0xFF)*f));
int shaded = 0xFF000000 | (R<<16)|(G<<8)|B;      // alpha 255 (opaque map)
```
Unscanned pixel (`height==UNSET`) → shaded = `0x00000000` (transparent, background shows).

### 2.4 GPU pixel packing (avoid R/B swap)
`NativeImage` (RGBA fmt) stores ABGR little-endian; `setPixelRGBA` wants `0xAABBGGRR`.
Our `shaded` is `0xAARRGGBB`. Convert at the GPU boundary ONLY:
```
static int toAbgr(int argb){ return (argb&0xFF00FF00) | ((argb>>16)&0xFF) | ((argb&0xFF)<<16); }
img.setPixelRGBA(px, pz, toAbgr(shaded));
```
`RegionTexture` ctor: `tex.setFilter(false,false)` (NEAREST, no mipmap). `upload()` after edits.

### 2.5 On-disk format (`RegionCodec`) — gzip(DataOutputStream)
```
writeInt(FORMAT_VERSION=1)
for cz in 0..31: for cx in 0..31:                 // 1024 chunks, row-major
   boolean present = scannedChunks.get(cz*32+cx)
   writeBoolean(present)
   if present: for z in 0..15: for x in 0..15 {    // 256 px, row-major
       writeInt(baseColor[pixelIdx]); writeShort(height[pixelIdx]);  // 6 bytes/px
   }
```
Undiscovered chunk → `present=false` (no data). Region with 0 present chunks →
**delete the file** (don't leave empty). Read mirrors it. `pixelIdx = (cz*16+z)*512 + (cx*16+x)`.
Raw ≈ 1.5 MB/region, ~5–10× smaller gzipped.

### 2.6 Folder scheme (`WorldId` + `RegionStore`)
```
<gameDir>/pjmbasemod/worldmap/<worldKey>/<dimKey>/r.<rx>.<rz>.pjmmap
worldKey: SP → mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).getParent().getFileName()
          MP → "mp_" + serverIp.replace(':','_').replaceAll("[^A-Za-z0-9._-]","_")   (strip port)
dimKey : mc.level.dimension().location().toString().replace(':','.').replace('/','_')
regionX = worldBlockX >> 9    (512-block regions)
```
Atomic write: `Files.write(tmp)` → `Files.move(tmp, dst, REPLACE_EXISTING, ATOMIC_MOVE)`.

---

## 3. Integration points

- **Keybind** — add to `ModKeyBindings`:
  `OPEN_WORLD_MAP = new KeyMapping("key.pjmbasemod.world_map", KEYSYM, GLFW_KEY_N, CATEGORY)`
  (M is taken by moderation); `event.register(OPEN_WORLD_MAP)` in `onRegister`.
  Lang key `key.pjmbasemod.world_map` + screen title `gui.pjmbasemod.map` into **all 5**
  lang files (ru/en/uk/de/zh).
- **Open the screen** — in `ClientEvents.onClientTick` (already the keybind hub), after the
  `mc.screen != null` guard: `while (OPEN_WORLD_MAP.consumeClick()) mc.setScreen(new MapScreen());`
- **Scan scheduling** — driven by the existing `ClientEvents.onClientTick(ClientTickEvent.Post)`:
  add `WorldMapEngine.get().onClientTick(mc);` near the top (runs whether the map is open or
  not, so terrain accumulates as you walk). This call: (1) `MapScanner` picks ≤K FULL chunks →
  snapshots → submits to `ColorWorker`; (2) `GpuRefreshQueue.drainN(k)` reshades+uploads dirty
  regions (this event IS the render thread on the single-threaded client — GL-safe);
  (3) autosave tick. `ColorWorker` is a lazily-started daemon `Executors.newSingleThreadExecutor`.
- **Logout / disconnect** — in `ClientEvents.onLogout` (already resets client mirrors):
  `WorldMapEngine.get().onLogout();` → `RegionStore.flushAll()` (drain save queue synchronously),
  `RegionTexture.free()` all, clear cache. Also `ClientBaseZoneState.reset()` (Phase 5).
- **ClientInit** — **no change needed**. Scanning rides `ClientEvents` (game-bus
  `@EventBusSubscriber(Dist.CLIENT)`); keybind self-registers; the only ClientInit-adjacent add
  is Phase 5's `ClientPacketHandlersImpl.baseZoneMapSync`.
- **Capture-point overlay** — `MapOverlays` reads `ClientCapturePointState.points()` (already
  synced, no new packet). Per point: map its `Vertex(x,z)` polygon through MapScreen's
  world→screen transform, fill = `contested?0xFFC13D : owner.isEmpty()?0x9B9B9B : ownerColor`,
  label at `CapturePoint.centroid(...)`.
- **Base-zone overlay** — needs the new mirror. `MapOverlays` reads `ClientBaseZoneState.zones()`;
  per zone draw the rect `[minX,minZ]..[maxX,maxZ]` (world→screen), stroke+fill in owner-team
  color (client-side `Teams`-equivalent color or ship the color in the packet — ship it, client
  has no server `Teams`). Only zones whose `dim` matches `mc.level.dimension()`.
- **World/dimension folders** — as §2.6; `RegionStore` resolves lazily on first access after
  join and re-resolves on dimension change (compare `dimKey`).

---

## 4. Phase order (each ends green on `./gradlew compileClientJava`, user-testable)

### Phase 1 — "renders SOMETHING": in-memory map, MapColor, pan/zoom
Scan FULL chunks in a window around the player straight on the client tick into **one** shared
in-memory `Region` (dimension of the player), base color = **vanilla MapColor** (`§2.1` pick +
`§2.2` Phase-1 line), **full slope+depth shading** (`§2.3` — it is pure math, include it now for
the real relief look), `RegionTexture` upload (`§2.4`), `MapScreen` (extends `Screen`) with
camera + drag-pan + scroll-zoom-toward-cursor + follow-player (skeleton from the GUI analysis).
No worker thread, no disk, no biome tint, no texture PNG averaging, no overlays.
Classes: `MapConstants, RegionKey, Region, RegionTexture, PixelShader, MapScanner(inline/simple),
MapScreen, MapRenderer, WorldMapEngine`, keybind + 5 lang entries.
- **Compile checkpoint:** `./gradlew compileClientJava` green.
- **In-game test:** press N → crude-but-real shaded top-down map of nearby loaded chunks; drag
  to pan, wheel to zoom toward cursor, camera follows until you drag.

### Phase 2 — background thread + region tiling + LRU + eviction
Introduce `ChunkSnapshot` (main-thread capture), `ColorWorker` (single daemon ExecutorService,
does color compute), `GpuRefreshQueue` (drain-on-render-thread). Tile the world into 512² regions
keyed by `RegionKey`; `WorldMapEngine` holds an LRU `LinkedHashMap` cache with `NativeImage.free()`
on evict (cap = config, default 64). `MapScanner` scans an expanding ring around the player every
tick (self+8-neighbors-FULL gate for clean slopes; mark neighbor region dirty on region-load so
seams reshade). Still MapColor. `MapRenderer` blits all `regionsInView`.
- **Compile checkpoint:** green.
- **In-game test:** walk a few hundred blocks; the whole traversed area stays on the map and
  renders as multiple region tiles; RAM stays bounded (evicts far regions).

### Phase 3 — full color pipeline (the Xaero palette)
Add `BlockColorSampler` (ImageIO source-PNG averaging + `Map<BlockState,Integer>` caches,
`§2.2`), `BiomeTintShim` (`BlockAndTintGetter`), `ColorPipeline` (avg × biome-tint × glow).
`ColorWorker` now calls `ColorPipeline` instead of MapColor. `biggestQuad` helper
(`BakedModel.getQuads(state,UP,rnd)+getQuads(state,null,rnd)`, max XZ area from
`BakedQuad.getVertices()`). Keep the MapColor fallback for black/missing/thrown.
- **Compile checkpoint:** green (note: `javax.imageio`/`java.awt` are fine — client set only).
- **In-game test:** map now shows rich averaged block colors, correct grass/foliage/water biome
  tint, glowstone/lava glow; grass plains vs. desert vs. forest visibly differ.

### Phase 4 — on-disk persistence (survives relog)
Add `WorldId`, `RegionCodec` (`§2.5`), `RegionStore` (folder scheme `§2.6`, atomic write, 1 daemon
writer thread, 60s autosave, load-on-region-enter, delete-if-empty). `WorldMapEngine.onClientTick`
autosaves dirty regions; `onLogout` flushes all synchronously; region-enter tries `RegionStore.load`
before scanning (loaded regions skip re-scan of already-present chunks).
- **Compile checkpoint:** green.
- **In-game test:** explore, disconnect, reconnect (and relog to a different dimension) → explored
  terrain is instantly back; verify files under `pjmbasemod/worldmap/<world>/<dim>/`.

### Phase 5 — overlays (capture points + base zones)
`MapOverlays` draws capture-point polygons from `ClientCapturePointState` (no new packet) and
base-zone rects from a **new** `ClientBaseZoneState`. `MapScreen` gains player arrow (constant
screen size, rotate by `player.getYRot()`), top-center X/Z-under-cursor readout, bottom-center
zoom readout. **New src/main sub-task (the only server-side change):**
- `BaseZoneMapSyncPacket` (S→C) carrying `List<{name,dim,owner,ownerColor,minX,minY,minZ,maxX,maxY,maxZ}>`
  (ship `ownerColor` via `Teams.color(server, owner)` so the client needs no server Teams).
- `ClientPacketProxy.baseZoneMapSync` default no-op; register in `PjmNetworking`; **bump
  `VERSION "47"→"48"`**.
- `ClientPacketHandlersImpl.baseZoneMapSync → ClientBaseZoneState.update`.
- `BaseZoneManager.broadcast(server)` from its create/edit/delete command paths;
  `PjmServerEvents.onLogin` sends the initial snapshot (next to `CapturePointManager.sendInitialSync`).
- **Compile checkpoint:** green (`compileJava` + `compileClientJava` — this phase touches `main`).
- **In-game test:** capture-point polygons + team base rectangles overlay correctly at world
  positions, track owner colors, pan/zoom with the map, and update live as ownership changes.

---

## 5. Risks / gotchas

- **Off-thread GL is illegal.** Only `ColorWorker` runs off-thread and it touches **zero** GL and
  **zero** `ClientLevel` — it consumes an immutable `ChunkSnapshot`. All `NativeImage`/`DynamicTexture`
  create+upload happens in `onClientTick`/`MapScreen.render` (render thread). `NativeImage` is native
  memory → `free()` on evict/logout or you leak off-heap.
- **ClientLevel is not thread-safe.** Surface pick, `getBiome`, `getBlockState` are main-thread only
  (in `MapScanner`). The snapshot copies primitives + a `BlockState`/`Holder<Biome>` ref (immutable).
- **`getMapColor` can throw** for broken modded blocks — try/catch, treat as no-color (skip column).
- **R/B channel swap** — `NativeImage` is ABGR; convert with `toAbgr` at the GPU boundary only
  (`§2.4`). Verify once against a known block (grass = green, redstone = red).
- **Slope seams at region edges** — a region's row-0/col-0 pixels need the neighbor region's last
  row/col heights. If the neighbor isn't loaded, slope=0 (flat) for that edge; mark both regions
  GPU-dirty when the neighbor loads so the seam reshades. Same for newly-scanned chunks whose N/W
  neighbor chunk arrives later.
- **Memory** — 2.5 MB/region; cap the LRU (default 64 ≈ 160 MB) and free on evict; expose the cap
  in config. `ponytail:` fixed cap; make it a config knob if large-render-distance users OOM.
- **Mappings** — target is Mojang mappings 1.21.1 (the decompile already uses them). Confirm at
  code time: `Heightmap.Types.WORLD_SURFACE`, `LevelResource.ROOT`, `ModelData.EMPTY`,
  `BakedQuad.getVertices()` stride, `GuiGraphics.blit(Function<ResourceLocation,RenderType>,...)`
  overload, `DynamicTexture.setFilter`.
- **`height` range** — plain `short` holds MC world Y (−2048..2047) with room to spare; `UNSET =
  Short.MIN_VALUE` can't collide with a real height. (We deliberately avoid Xaero's 12-bit packing.)
- **Resource-pack / biome-registry change between sessions** — baked `baseColor` won't auto-correct
  (`ponytail:` documented ceiling). Bump `FORMAT_VERSION` or wipe the `worldmap/` dir to force rescan.
- **First map open after long exploration** — cap GPU uploads per frame (`drainN`, K≈4) so the first
  render of many freshly-loaded regions doesn't hitch.
- **`PixelShader` correctness** — leave one `demo()`/`assert` self-check (flat terrain → f≈depth,
  a north-facing +slope → brighter than −slope) so the shading math can't silently break.
