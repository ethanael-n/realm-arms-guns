# Gunmetal 1.0.8

Formerly two mods, Realm Arms (guns, bows, crossbows, cartridges, attachments) and Realm Raids (the
raid illagers that use them). Merged on 2026-10-15 into one mod with one id and one namespace:
mod id `gunmetal`, every item, entity, sound, particle, tag, recipe, loot table, advancement and asset
under `gunmetal:` (was `realmarms:` / `realmraids:`). Java packages: `net.gunmetal.arms` (was
`net.realmarms.weapons`) and `net.gunmetal.raids` (was `net.realmraids`); class names unchanged.
No gameplay changes in the merge.

Targets Minecraft 26.1.2, Fabric Loader 0.19.3+, Fabric API 0.155.2+26.1.2, Ranged Weapon API
4.0.0+26.1.2 (bundled inside the jar), Java 25.

## Building
`gradlew build` -> `build/libs/gunmetal-1.0.8.jar`. One jar; nothing to copy into `libs/` any more.
Remove the old `realmarms-weapons-*.jar` and `realmraids-*.jar` from the mods folder (the mod refuses
to load next to them). Optional mods as before: Create (gun and Artificer recipes, Artificer drops),
Alloyed (steel recipes), Alex's Caves (stun, sulfur, Irradiated), Additional Additions (patina),
Enderscape, Frostiful, Guard Villagers, Backslot.

## 1.0.8 changes
- Brass Deployable throw plays the vanilla thrown-item sound again (`minecraft:entity.snowball.throw`); the
  placeholder `sounds/item/brass_deployable/throw.ogg` was removed.
- Removed unused textures: `textures/entity/illager/pillagerbase.png` (a work file) and
  `textures/particle/scope_windup_0`-`4.png` (`particles/scope_windup.json` only uses `scope_windup_5`).

## 1.0.7 changes
- **Working resource pack built in.** Everything from the working pack (submitted 2026-10-17) now ships
  inside the jar: the 18 EMF models/animations (`assets/gunmetal/emf/cem/`, including the Aviator poses),
  sounds, textures, item models and particle definitions (288 files). The resource pack is no longer
  needed on clients; any pack placed above the mod still overrides it as before.
- `sounds.json`: the pack's 59 events replace the mod's placeholder definitions; the other 38 (guns,
  bullets, dispenser) are unchanged.
- Not copied: the pack's Trooper helmet item/model (the helmet stays blank, as set in 1.0.6), its
  `pack.mcmeta` and `READ ME.txt`.
- Removed 18 placeholder sounds nothing plays any more: `sounds/sharpshooter/` (ambient, hurt, death) and
  `sounds/trooper/` ambient, hurt and death (`trooper/horn.ogg` stays).
- The models only take effect with Entity Model Features installed (Fresh Animations is not required:
  every model and animation file is self-contained). Without EMF the mobs keep their vanilla-shaped models.

## 1.0.6 changes
- **Stance accuracy (players only):** a shot's spread is x0.75 while sneaking and x1.5 while sprinting
  (`GunConfig.SNEAK_SPREAD` / `SPRINT_SPREAD`, applied in `GunFiring.stanceSpreadMultiplier`). Walking and
  standing are unchanged; no crosshair change. Mobs keep their own aim error. A crouched click with a loaded
  revolver fans the hammer, and fanned rounds keep their fixed cone, so the sneak bonus never applies to
  the revolver (the repeater can't fan, so it gets it).

## 1.0.5 changes
- **Raider friendly fire:** a bullet fired by a raider (pillagers, Troopers, Aviators, Bombardier shells,
  the Siege Balloon cannon...) does half damage to another raider, ravagers included
  (`GunConfig.RAIDER_FRIENDLY_FIRE`). Bursts are unchanged.
- **Repeater models** use the revolver's texture (`item/revolver`); the duplicate `item/repeater.png` is gone.
- **Recipes needing optional mods** now all carry load conditions, so without the mod they're skipped
  quietly instead of logging an error: Brass Deployable (Create), platinum bow / crossbow (Earth and
  Water), rose gold bow / crossbow (Additional Additions), steel bow / crossbow (Alloyed).

### Recipes that need an optional mod (1.0.5)

| Recipe | Needs | Modded ingredients |
|---|---|---|
| brass pistol, carbine, musketoon, arquebus, blunderbuss, rifle, volleygun | Create | brass ingots, brass sheets |
| brass ignition -> brass ingot (smelting, blasting) | Create | result is `create:brass_ingot` |
| copper carbine, musketoon, volleygun | Create | copper sheets |
| Brass Deployable | Create | brass block |
| Blaze Cartridge | Create | cinder flour |
| steel pistol, carbine, musketoon, arquebus, blunderbuss, rifle, volleygun | Alloyed | steel ingots, steel sheets |
| steel bow, steel crossbow | Alloyed | steel ingots |
| steel ignition -> steel ingot (smelting, blasting) | Alloyed | result is `alloyed:steel_ingot` |
| platinum bow, platinum crossbow | Earth and Water | `earth-and-water:steel_ingot` |
| rose gold bow, rose gold crossbow | Additional Additions | rose gold ingots |
| Nuclear Cartridge | Alex's Caves | uranium shards, sulfur dust |
| Void Cartridge | Enderscape | Void Lachryma recipe type |
| Sunken Cartridge | any of Bigger Fish, Alex's Mobs, Legacies and Legends | leech / shark tooth / trident shard (tag has no vanilla item) |

Always craftable in vanilla: copper pistol, arquebus, blunderbuss, hand cannon, copper ignition
recipes, the stone / copper / iron / golden / diamond / netherite bows and crossbows, the plain,
ice (snowballs), poison (bones, poisonous potatoes, rotten flesh) and unstable cartridges. Loading a
Brass Deployable (`brass_deployable_loaded`) works in vanilla; its Create / Additional Additions / Alex's
Caves ingredients are optional extras. Ignitions have no recipe in any setup (mob drops only).

## 1.0.4 changes
- **Attachments removed** for good: the rifled / shadoline barrels, the three barrel shrouds, the fuse,
  the repeater mechanism, the firelance head, their code (`attachment/`, the shroud durability mixin,
  the fuse recoil), item models, textures, translations, the gun item definitions' attachment layers,
  and the `gun.fuse` sound. The ignitions stay (now `registry/Ignitions`): gun crafting and repair
  material.
- **Trooper helmet** item model is empty (`items/trooper_helmet.json`, `minecraft:empty`): the helmet
  is part of the Trooper's own model.
- **Aviators:** each paratrooper / raid Aviator carries a revolver, a repeater or a hatchet, evenly
  (Illager Invasion's platinum infused hatchet, or a copper axe without that mod; only swung, never
  thrown). Hatchet hits do 10 before difficulty (15 on Hard): the Aviator's base attack damage is set
  so that base + the hatchet's own bonus = 10 (`HATCHET_DAMAGE`; the item itself is untouched).
  Pilots draw the **Pilot Revolver**: a normal revolver renamed "Pilot Revolver"
  (`item.gunmetal.pilot_revolver`) with 1796 durability (was 1351).
- **Aviator weapon drops:** every weapon (revolver, repeater, hatchet, Pilot Revolver) drops with an 8%
  chance on a player kill, randomly damaged, +1% per Looting level (vanilla's mob gear rule). The
  pilot's guaranteed drop is gone.
- **Repeater recipe removed** (and its recipe-book advancement).
- **Realm Arms migration:** a world saved with Realm Arms (2.17.3 tested against the source) loads
  its things as Gunmetal's once Realm Arms is removed: every registry lookup for `realmarms:<name>`
  that doesn't exist falls back to `gunmetal:<name>` (items, stack data like `realmarms:loaded` /
  `loading_stage` / `loaded_cartridge`, bullets, the bullet damage type...). Mixin `LegacyIdMixin`.

## 1.0.3 changes
- **Siege Balloon:** 6 paratroopers again (drops of 2, 2, 2). Each crossbow arrow fires with the pistol's
  flash (was the volleygun's). Shrapnel: 2-24 blocks/s (was 10-70), 7 damage each (was 4). Envelope
  tatters last a random 4-12 s each (particle provider; the Artificer's construct bits are unchanged).
  Raid waves (independent rolls; `bonus_chance` now also takes a list, one independent roll per entry):
  first balloon 1/2/4/8/16/32/64/100%, second 0/0/0/1/4/8/16/32%, third 0/0/0/0/1/2/4/8% (waves 1-8).
- **Aviators:** copper guns instead of brass (pistol, carbine, musketoon; revolver and repeater
  unchanged), copper axe / sword instead of golden, hitting 2 less than iron (sword 4, axe 7 before
  difficulty; `MELEE_DAMAGE_OFFSET`). A pilot that has drawn its revolver always drops it on a player
  kill, randomly damaged (vanilla's gear-drop rule). New loot table `entities/aviator`: on a player kill,
  2.5% (no Looting bonus) for one of 9 parts, evenly: Immersive Aircraft industrial gears, sturdy pipes,
  gyroscope, hull reinforcement, improved landing gear, steel boiler, enhanced propeller; Create
  propeller, white sail (in `entities/aviator_salvage_immersive_aircraft` / `_create`, each only loaded
  with its mod; a missing mod just removes its share).
- **Bombardier:** 1.1x size (model and hitbox 0.66 x 2.15, vanilla scale attribute). Its shells fly
  through Siege Balloons and their bursts spare them (entity type tag `gunmetal:shell_pass_through`).
- **Sharpshooter:** reload 3.5-4.5 s (was 4.5-5.5); its bullets have no drop (it aims straight).
  Smoke teleport: 2-32 blocks (was 2-8), finding ground up to 8 above or below (was 4); the hit that
  sets it off plays `entity.sharpshooter.smoke_bomb` (default: the illusioner's mirror move); the smoke
  bomb also gives off 10 `sculk_soul` particles anywhere in a 4-block cube around where it stood.

## 1.0.2 changes
- **Aviators:** 22 health (was 24). Their own sound events `entity.aviator.ambient` / `.hurt` / `.death`
  (defaults: the pillager's; celebrating stays the pillager's).
- **Revolver fan:** every 3 ticks (was 2); fanned rounds do 4 less damage (15 -> 11 before multipliers;
  `ClipGunItem.FAN_DAMAGE_PENALTY`), players' and Aviators' alike. Aviators fan every 3 ticks too.
- **Siege Balloon:**
  - 120 health (was 100). Damage stages under 70 (damaged) and under 25 (burning); were 50 / 20 or less.
  - Heights and sinking: damaged -> mid rise at 1 block/s (as before); burning -> low rise at 1.5
    blocks/s; pilot gone -> low rise at 1 block/s (as before, tethered or not); ravager gone with the
    pilot aboard -> low rise at 0.3 blocks/s and no health loss (was: mid rise, 1 health a second).
    When several apply, the fastest one still pulling it down wins. With both pilot and ravager gone it
    still loses 5 health a second.
  - Low rise: bottom 2.7 above the ground (was 3), an iron golem's height, so golems can reach it.
  - Pilot seat 0.3 further forward (crossbow muzzles and the front tether stay put).
  - Each crossbow arrow fires with the volleygun's muzzle flash at its barrel.
  - New particle `envelope_tatters` (same behavior as `construct_bits`; textures
    `particles/envelope_tatters.json`, placeholders `textures/particle/envelope_tatters_1`-`4`): replaces
    the death burst's construct bits, and 10 are thrown out at each damage stage change.
  - The tether snapping (or the ravager dying) plays `entity.siege_balloon.untether` (default: a lead
    breaking).
  - Shrapnel at its death: 30 pieces from the centre, each in a random direction at or above the
    horizon, 10-70 blocks/s, normal bullet gravity and drag, gone after 10 s. 4 damage each (flat, no
    difficulty scaling, credited to nobody, hits anyone, raiders included) and sets what it hits on
    fire for 4 s. No smoke or flash; vanilla `flame` trail; sprite `textures/entity/bullet_shrapnel.png`.
    Never breaks blocks. Constants `SHRAPNEL_*`.

## 1.0.1 changes
- **Revolver fan:** every 2 ticks (was 4). Aviators fan every 2 ticks too.
- **Ghost shots fixed** (revolver / repeater): clicking very fast could fire a round on the server that
  your client had refused, so it made no sound and no flash for you. Now the client tells the server
  which clicks it fired (packet `gunmetal:clip_shot`, 1 byte per round) and the server fires exactly
  those, still checked against its own clock (2 ticks of slack).
- **Siege Balloon tether chains (visual):** the front chain starts 0.5 above the bottom (was 2.5); the
  rear pair 1.5 to each side (was 3) and 3.5 behind the centre (was 3), still 2.5 up. Constants
  `FRONT_TIE_HEIGHT`, `REAR_TIE_HEIGHT`, `REAR_TIE_SIDE`, `REAR_TIE_BACK`.
- **Balloon death visuals:** the ring grows 6 -> 18 blocks across (x3); the smoke puffs are 3.75 blocks
  across (+25%) and spread through a 6-block ball (was 4).
- **New model signal:** `round(balloon_signal3.ry)` = 1 while the balloon has no tether drawn (cut loose,
  or spawned without a ravager).

## What the merge changed (1.0.0)
- Resource packs: everything under `assets/realmarms/` and `assets/realmraids/` now lives in
  `assets/gunmetal/` (EMF models: `assets/gunmetal/emf/cem/`), and every `realmarms:` /
  `realmraids:` id inside pack files becomes `gunmetal:`. `tools/convert_pack_to_gunmetal.py` does this (run it with no arguments for help).
- Datapacks / commands: `realmarms:x` and `realmraids:x` become `gunmetal:x` (items, entities, tags
  like `#gunmetal:guns`, `#gunmetal:rally_followers`, the `gunmetal:raid_spawns.json` file).
- Translation keys: `item.gunmetal.*`, `entity.gunmetal.*`, `subtitles.gunmetal.*`, `tooltip.gunmetal.*`,
  `advancements.gunmetal.*`, `death.attack.gunmetal.*`.
- Mixin configs: `gunmetal-arms.mixins.json`, `gunmetal-raids.mixins.json`.
- `musketmod:` stays (legacy Musket Mod item conversion).

# Part 1: Arms (formerly Realm Arms 2.18.3)

A Fabric mod that replaces the old datapack-based tiered bows/crossbows with real items,
built on top of [Ranged Weapon API](https://modrinth.com/mod/ranged-weapon-api).

Targets **Minecraft 26.1.2**, Fabric Loader 0.19.3+, Fabric API 0.155.2+26.1.2,
Ranged Weapon API 4.0.0+26.1.2, Java 25.

### What this mod adds

18 items: a bow and a crossbow for each of 9 material tiers (stone, copper, iron, golden,
rose gold, platinum, steel, diamond, netherite), each with its own damage, draw time,
velocity, durability, crafting recipe and textures. No item components/attribute-modifier
NBT hacks — the balance numbers are plain Java, translated into the RangedWeaponAPI
`ranged_weapon:properties` component + attribute modifiers at registration time.


### How the numbers were translated

Per the Ranged Weapon API source (`RangedWeaponConfig`):

- `damage` = the literal number of hearts/half-hearts of damage dealt on a fully charged,
  non-critical, point-blank hit. This is exactly what you specified per material — no
  conversion needed.
- `pull_time` = draw time **in ticks** (20 ticks = 1 second). 0.7s→14, 1s→20, 1.2s→24,
  1.4s→28.
- `velocity` (optional bonus) = target velocity − baseline (3.0 for bows, 3.15 for
  crossbows). E.g. rose gold bow at 4.0 velocity → bonus `1.0`; rose gold crossbow at
  3.8 → bonus `0.65`. Materials that keep vanilla velocity pass `null` (no modifier
  added at all).

All of this lives in the `MATERIALS` table at the top of `RealmArmsWeapons.java` — it's
the only place you should need to touch to retune numbers later.

### Assumptions made (flag if any of these are wrong)

- **Mod id / asset namespace**: `gunmetal` — confirmed correct.
- **Durability is shared** between a tier's bow and crossbow — confirmed correct.
- **Repair material = the same ingredient used in the crafting recipe** — confirmed correct.
- **Netherite smithing upgrade** — confirmed correct.
- **New crossbow "standby" texture wiring**: your updated assets added a second per-material
  crossbow texture (`<material> crossbow standby.png`), but the model templates you sent still
  literally point every state (standby, all 3 pull frames, loaded) at the original plain
  `<material> crossbow.png`. I wired the new `_standby` file into just the idle/unloaded model,
  mirroring how bows already use a distinct texture for standby vs. the moment of full draw, and
  kept the plain file for the 3 pull frames + loaded states. If that split was meant to work
  differently, it's a one-line fix in `gen.py`/the model JSONs — just tell me.
- **Recipe shapes are vanilla's**, with only the stick slot swapped for your material ingredient.
- **Recipe-unlock advancements were skipped** (unchanged from before — still happy to add on
  request).
- **Bowstring/crossbow-string "alt" texture sets are unused.** Your update added a parallel
  `crossbow_alt_*` set alongside the existing `bow_alt_*` one; both are copied into the project
  but not wired to any material. Tell me which materials, if any, should use them.
- **Ranged Weapon API is bundled into this mod's jar** via Loom's `include` (Jar-in-Jar).
- **Creative tab**: all 18 items are added to the vanilla Combat tab, after the vanilla
  bow/crossbow.

### Fixed since the last build

- **Recipes weren't loading at all.** Minecraft 26.1.2 simplified the recipe ingredient JSON
  format: a plain item is now just a bare string (`"minecraft:iron_ingot"`), and a tag is a
  string prefixed with `#` (`"#c:stones"`) — the old `{"item": "..."}` / `{"tag": "..."}` wrapper
  objects from 1.20.x are no longer valid and silently fail to parse. Every recipe file has been
  rewritten to the new format.
- **Balance numbers updated** to your latest pass — see the `MATERIALS` table in
  `RealmArmsWeapons.java` for the current damage/pull-time/velocity-bonus values. Notably steel
  is now a slow (5s draw), hard-hitting outlier tier on both weapons.
- **Crossbow art is no longer placeholder.** Real `crossbow_arrow.png`/`crossbow_firework.png`
  overlays are wired into the loaded states for every material.
- **Crossbow pulling/loaded states rendering as vanilla — actually fixed this time.** Two
  wrong guesses in a row on my part before landing on the real cause, so here's the full story:
  1. First guess: wire the new `<material>_crossbow_standby` texture into the idle model only.
     Wrong — broke every other state.
  2. Second guess, based on diffing your original working pack: revert to the old plain
     `<material>_crossbow.png` for every state. This made it *worse* — every state, including
     standby, started rendering as vanilla.
  3. Turned out the plain `<material>_crossbow.png` file is a **leftover placeholder that's
     byte-identical across all 9 materials** (confirmed by hashing each one) — it was never
     actually updated, in either asset drop. The new `<material>_crossbow_standby` file is the
     real, distinct per-material art. The fix: that file is now used as the body texture (layer0)
     for *every* crossbow state — standby, all 3 pull frames, and both loaded states — under the
     plain `<key>_crossbow.png` name in the shipped pack (the crossbow body doesn't change shape
     between frames, only the generic string/mechanism overlay does, so it doesn't need a
     separate "pulling" body variant the way bows do).
  I rendered every state for several materials side-by-side before shipping this build to
  confirm each one is visually distinct and correct.
- **Netherite now uses the "alt" string/frame texture set**, on both the bow and crossbow, across
  every state (standby, all pull frames, loaded). Diffing against the original pack showed this
  was the one deliberate use of `bow_alt_*`/`crossbow_alt_*` in the whole thing — every other
  material uses the plain set. If you want the alt set on other materials, say which ones.
- Diffing also surfaced a small bug **in the original pack**, not mine: `iron_crossbow.json` and
  `netherite_crossbow.json` both have their full-draw threshold (`1.0`) pointing at the
  `pulling_1` model instead of `pulling_2` (copy-paste slip). My generated files use `pulling_2`
  correctly, so no action needed — just flagging in case that "off" full-draw frame was expected
  behavior somewhere else you're relying on.

### Not done yet

- **The original datapack-trim pass** from the very start of this project (stripping old
  item components/modifiers while leaving unrelated content alone) — that file never actually
  came through, and we moved on to porting the guns instead. Still happy to do it if you send
  the datapack over.

### Guns

Standalone since v2.17: Musket Mod Reloaded and Cloth Config are no longer needed (see
"v2.17 changes" below). The gun, bullet, pillager AI and first-person code was ported from
[Musket Mod Reloaded](https://modrinth.com/mod/musket-mod-reloaded) 1.6.4 (MIT license, by
Lufurrius); its sounds that we still use are CC0 (`assets/gunmetal/sounds/credits.txt`).

### Where things live

| What | File |
|---|---|
| Every gun stat (stages, spread, speed, damage, pellets, handedness, move speed, visuals) | `guns/GunType.java` |
| Tier durability / repair tags | `guns/GunTier.java` |
| Loading stage length (0.5 s) | `GunType.LOADING_STAGE_SECONDS` |
| Cartridge effects, durations, Ice numbers | `ammo/Cartridge.java` |
| Trail spacing, pellet trail share, accent frequency | `entity/RealmBulletEntity.java` (top of file) |
| Muzzle smoke/spark counts and speeds | `guns/GunFiring.java` (top of file) |
| Particle size, lifetime, gravity, growth, glow | `client/particle/RealmParticle.java` (`Style`) |
| Projectile sprite sizes | `client/RealmBulletRenderer.java` |
| Pillager/guard range / load on the move / retreat per gun | `GunType.mobTactics()`; retreat distance, aim time, max fire range in `ai/PillagerGunGoal.java` and `ai/GuardGunGoal.java` |
| Mob damage multiplier (0.4), burst interval, gunshot pitch range per gun, hand cannon burst radius | `GunType.MOB_DAMAGE_MULTIPLIER`, `BURST_INTERVAL_TICKS`, `firePitchVariation()`, `impactExplosionRadius()` |
| Loading sounds (which stage plays what) | `guns/GunLoading.java` |
| Recoil strength per gun | `GunType.gunKick()` / `gunKickScale()`; angles and distances in `client/GunRecoil.java` |
| Crosshair size | `client/GunCrosshair.java` |
| Max bullet distance, scope zoom, rifle drop reduction, dispenser stats, pillager gun chance | `guns/GunConfig.java` |

There's no config file: the settings that used to be in `config/musketmod.txt` are constants
in `guns/GunConfig.java` (same values as Musket Mod's defaults). Bullets fired by mobs and
dispensers use `GunType.MOB_DAMAGE_MULTIPLIER` (0.5).

### Items

21 guns: `copper_/brass_/steel_` + `pistol, carbine, musketoon, arquebus, blunderbuss, volleygun`,
`brass_rifle`, `steel_rifle`, and `hand_cannon` (copper only). Steel has no recipe (drops only).
`gunmetal:blunderbuss` is gone; use `gunmetal:copper_blunderbuss` in loot tables.

7 cartridges: `gunmetal:blaze_cartridge, ice_cartridge, nuclear_cartridge, void_cartridge,
sunken_cartridge, poison_cartridge, unstable_cartridge`. New cartridge types must be added at the END of the
`Cartridge` enum (guns remember the loaded cartridge by its position). They're picked up in the normal ammo order (offhand, main hand,
inventory), and whichever one was used is remembered on the gun until it fires.

### Mixins (what they patch)

- `Pillager` - adds the Realm Arms pillager AI (+ `MobAccessor` for `Mob.goalSelector`), the
  chance to spawn with a gun from `gunmetal:pillager_weapon`, and the crossbow arm pose while
  holding a gun.
- `Mob`, `ItemStack` - advancement tracking and gun melee hooks (unchanged).
- Client: `ItemInHandRenderer` (first-person gun poses, reload pull-back, gun kick),
  `HumanoidModel` (third-person aiming arms), `LocalPlayer` (no sprinting while loading, slow
  walk while scoped, scope recoil), `AbstractClientPlayer` (scope zoom), `Player` (scope counts as
  spyglass), `Minecraft` (left-click fires while scoped, auto-release, scope key),
  `MultiPlayerGameMode` (blocks accidental use while firing).

Guard Villagers support needs no mixin: guards get their gun AI when they're added to a world
(`compat/GuardVillagersCompat.java`), and nothing happens if Guard Villagers isn't installed.

If any of these can't find its target, the game stops at launch with a mixin error naming the
file. Nothing is written to worlds, so it's safe to test.

### Models

Each gun type has three pose files in `models/item/gun/`: `<type>_idle.json`,
`<type>_loading.json`, `<type>_loaded.json`. They hold the display transforms (and the pistol's
3D shape), and every tier of that gun uses them, so tuning a pose in Blockbench tunes all tiers.
Per-item files (`copper_pistol_loading.json` etc.) only set textures. `items/<id>.json` picks the
pose: loaded, then loading (`gunmetal:loading_stage` present), then idle.

### Placeholder assets (all meant to be replaced)

- Gun textures for carbine, musketoon, rifle, hand cannon, volleygun and brass/steel
  blunderbuss: tinted copies of existing art. Pistol, arquebus and copper blunderbuss use your art.
- `textures/item/<cartridge>.png`: tinted copies of the cartridge texture.
- `textures/entity/bullet_round.png`, `bullet_jagged.png`, `bullet_heavy.png`.
- `textures/particle/*.png`: 19 particles, each a distinct flat color (see table below).
  To animate one, add frames and list them in order in `particles/<name>.json`; they play
  over the particle's lifetime.
- `sounds/gun/<type>_fire.ogg`: 8 placeholder bangs, each a different pitch. Keep them **mono**
  so they fade with distance.

### Particles by gun

| Gun | Projectile | Trail | Smoke | Flash | Sparks |
|---|---|---|---|---|---|
| Pistol | bullet_round | trail_small | smoke_subtle | flash_light | - |
| Carbine | bullet_round | trail_small | smoke_medium | flash_medium | - |
| Musketoon | bullet_jagged | trail_standard | smoke_subtle | flash_light | - |
| Arquebus | bullet_round | trail_standard | smoke_medium | flash_medium | - |
| Blunderbuss | bullet_jagged | trail_dense | smoke_heavy | flash_heavy | by material |
| Rifle | bullet_round | trail_dense | smoke_medium | flash_heavy | - |
| Hand Cannon | bullet_heavy | trail_black | smoke_heavy | flash_heavy | spark_copper |
| Volleygun | bullet_jagged | trail_dense | smoke_heavy | flash_heavy | by material |

Sparks: `spark_copper` (green), `spark_brass` (orange), `spark_steel` (yellow). They come from the
lock near the hand and fly out to the left and right, then fall.
Accents, mixed into any gun's trail: `accent_blaze`, `accent_ice`, `accent_nuclear`,
`accent_void`, `accent_sunken`, `accent_poison`. Standard cartridges have no accent. Blaze and Nuclear accents,
flashes and sparks glow in the dark.

Placeholder colors: trail_small pink, trail_standard blue, trail_dense red, trail_black black,
smoke subtle/medium/heavy pale yellow/tan/brown, flash_light white, flash_medium yellow,
flash_heavy red-orange, accents orange/cyan/green/purple/teal/olive.

### Optional-mod recipes

Recipes that use Create, Alex's Caves or Enderscape items only load when that mod is present.
Ice, Sunken and Poison cartridges use the tags `gunmetal:ice_cartridge_materials`,
`gunmetal:sunken_cartridge_materials` and `gunmetal:poison_cartridge_materials`, whose mod entries are optional, so missing mods just remove
those options. The Void cartridge uses Enderscape's `enderscape:void_lachryma` recipe type with a
standard cartridge as input.

### Ice with Frostiful

Frostiful replaces vanilla frozen ticks with Thermoo temperature, and converts any frozen-tick
increase 1:1 into cold. Mobs there are fully frozen at -6300, not 140, so 120 ticks did almost
nothing. With Frostiful installed, each Ice shot now cools the target by
`ICE_FROSTIFUL_FRACTION_PER_SHOT` (default 120/140) of its own range to fully frozen, reduced by
frost resistance. How fast it warms back up is Frostiful's call, so tune that constant in
`Cartridge.java` after testing. Without Frostiful, the vanilla 120-tick behavior is unchanged.

### Stale bullets

Bullets that fly past the simulation distance stop ticking on the server and would otherwise hang
in the air until their chunk unloads. Coming back in range made the client replay them as ghost
bullets. Every bullet now carries its fire time and is removed on both sides once it is older than
120 ticks. (Musket Mod's own bullets have the same underlying behavior; it just isn't visible
without trails.)

### Pillager AI

Pillagers holding a Realm Arms gun use our own AI instead of Musket Mod's, which always closed
to 8 blocks, only reloaded standing still, and then fired at anything in sight.

| Gun | Range | Loads on the move | Retreats |
|---|---|---|---|
| Pistol | 10 | yes | yes |
| Carbine | 14 | yes | no |
| Musketoon | 12 | yes | no |
| Blunderbuss | 8 | no | no |
| Arquebus | 16 | yes | yes |
| Rifle, Hand Cannon | 26 | no | yes |
| Volleygun | 22 | no | no |

- **Retreat:** they back away when a target is inside 60% of their range. After 3 seconds of
  retreating they stand and shoot, until their next shot.
- **Firing range:** they only fire at targets within 1.25x their range.
- **Loading while moving:** walking in while loading or holding a loaded gun is half speed.
  "No loading on the move" means they only *start* loading once stopped. A reload already
  underway finishes even if they start moving again.
- **Pillagers don't track targets beyond 32 blocks** by default.

### Recoil and crosshair

- **Camera kick:** only for the local player. The view is thrown up and then recovers about 60%
  over 6 ticks. There's no second camera kick while scoped with the rifle, because Musket Mod's
  scope recoil already applies.
- **Gun kick:** the first-person model pushes back and tips up, then settles.
- **Crosshair:** replaces the vanilla crosshair while a Realm Arms gun is in the main hand, in
  first person (including while scoped with the rifle). Textures:
  `textures/gui/sprites/hud/crosshair_0/20/40/60/80/100.png`. The stage rounds down, and the
  100% texture only appears once the gun is loaded.

### v2.3 changes

- **Pistol spread:** 2.0°, up from 1.5°.
- **Carbine bayonet:** a spear jab using vanilla's spear components. It reaches 2-4.5 blocks,
  hits everything in line, needs a full attack charge, and can't crit or sweep. Damage is
  4/5/6.5 with attack speed 1.0 (one jab per second; changed in v2.4). In first person it uses Musket Mod's bayonet
  thrust animation.
- **Musketoon axe-head:** a normal melee swing at 6/8/10 damage and attack speed 1.2 (the old
  carbine stats).
- **Volleygun burst:** each trigger pull fires 7 shots of 6 pellets, one tick apart
  (`GunType.burstShots()`, `BURST_INTERVAL_TICKS`). Each pellet still deals damage / 42, so a
  full hit totals the same as before. Each shot aims where you're looking at that moment.
  - The whole burst costs one cartridge and one durability, and Ice counts once per burst.
  - Smoke and sparks are split across the burst, and each shot plays the fire sound.
  - Camera kick totals 1.5x the heavy kick, spread across the burst.
  - The burst stops if you switch away from the gun.
- **Cartridges in the offhand are invisible**, for players and mobs. This covers all six special
  cartridges and Musket Mod's standard one, via the `items/*.json` definitions.

### v2.4 changes

- **Melee swing:** melee swings and hand swings with any Realm Arms gun except the carbine now
  chop forward (muzzle dips up to 50°). Before, they spun the gun 180° toward the player.
- **Muzzle effects on low-held guns:** blunderbuss, musketoon, volleygun and hand cannon emit
  their muzzle flash, smoke and sparks 3 pixels lower, relative to the shooter's view
  (`GunType.muzzleDropPixels()`).
- **Muzzle flash:** it starts 1 pixel further out and moves with the shooter for its brief
  life, so running forward no longer leaves it behind.
- **Pillager speed:** pistol, carbine, musketoon, blunderbuss and arquebus pillagers walk at full
  speed while carrying a loaded gun (`Tactics.fullSpeedLoaded`). Pistol pillagers now retreat.
- **Carbine jab:** 4/5/6.5 at attack speed 1.0, roughly the same damage per second as before.
- **Smoke:** twice as many particles per shot (6/12/20), starting twice as spread out and drifting twice as widely.
- **Gun kick:** doubled, to 0.06/0.12/0.20 blocks back and 10/20/32° tip.

### v2.5 changes

- **Particle sizes** (`RealmParticle.Style`): trails 3x, cartridge accents 2.5x, smoke 1.8x,
  muzzle flashes 3.5x, sparks 2x.

### v2.6 changes

- **Flash sizes:** all flashes are drawn the same size (the medium size); the textures carry the
  size differences. Each flash spawns at a random 90° rotation.
- **Sparks on every gun** (per trigger pull, from the lock): pistol/carbine/musketoon 3 per side,
  arquebus/rifle 5 per side, blunderbuss/volleygun 8 per side, hand cannon 10 straight up from
  the top of the gun (`GunType.sparksPerSide()` / `sparksUpward()`). The volleygun's are split
  across its burst.
- **Accent particles:** ice and sunken last 2 s and sink slowly until they settle on the ground.
  Nuclear and poison last 3 s and drift off in a random direction.
- **Accent flashes** (drawn over the normal flash, 1 tick instead of 2-3):
  `flash_accent_blaze/ice/nuclear/void/sunken/poison` for special cartridges, and
  `flash_accent_rifle` on every rifle shot.
- **Volleygun flash:** the volleygun has its own flash (`flash_volleygun`), with a fresh random
  rotation on each shot of the burst.
- **Aftersmoke:** every smoke burst adds half as many aftersmoke particles, which behave like the
  gun's smoke but last twice as long. Three internal types (`aftersmoke_subtle/medium/heavy`)
  share one texture: `textures/particle/aftersmoke.png`.
- **Particle variants and animation:** the texture names in a particle's json decide how it
  behaves; see the note at the top of `client/particle/RealmParticle.java`.

### v2.7 changes

- **Flashes** (including accent flashes) are drawn 0.8x their previous size.
- **Accent particles:**
  - Sunken sinks twice as fast.
  - Void lasts 2 s.
  - Nuclear lasts 1.5 s.
  - Ice spreads further outward.
  - Blaze lasts half as long (about 0.3 s) and bursts further outward.
- **Gun kick:** 1.75x stronger, and it settles half as fast.
- **Camera kick:** removed entirely.
- **Trails and smoke:** all four trails are drawn at the standard trail's size, and all three smokes
  (and aftersmokes) at the medium smoke's size and growth.
- **Melee:** carbine jab 6/7/8.5, musketoon melee 8/10/12.
- **Rifle trail:** standard instead of dense.

### v2.8 changes

- **Gun kick per gun** (`GunType.gunKickScale()`): arquebus 0.6x, volleygun 0.5x, blunderbuss
  1.2x.
- **Crosshair:** drawn at 32 GUI pixels (was 15), for 64x64 textures. `SIZE` in
  `client/GunCrosshair.java`.
- **Reload stages:** 0.5 s each (was 0.6). Applies to pillagers too.
- **Aftersmoke:** spreads further sideways, starting up to 0.15 blocks wider on x/z and drifting
  outward. Its vertical motion is unchanged.
- **Accent particles** (not accent flashes): 50% larger.
- **Rifle spread:** 0.3° for brass, 0.2° for steel (was 0.1 / 0.0).

### v2.9 changes

- **Flash no longer hidden by smoke:** smoke and aftersmoke stay invisible for their first 3
  ticks (`SMOKE_DELAY` in `RealmParticle`), so the 2-3 tick flash is always seen first. Their
  visible lifetime is unchanged.
- **Crosshair:** drawn at 64 GUI pixels, so one texture pixel equals one GUI pixel with 64x64
  textures.

### v2.10 changes

- **Flashes 1.2x larger** (all flashes and accent flashes drawn at 1.176) and **1 tick longer**:
  light/medium/volleygun 3 ticks, heavy 4, accent flashes 2. Smoke now stays hidden for 4 ticks
  (`SMOKE_DELAY`) so it still never covers the longest flash.
- **Gunshot pitch:** every gunshot (player, mob, each shot of a volleygun burst, cartridge from a
  dispenser) plays at a random pitch between 0.9 and 1.1 (`GunType.FIRE_PITCH_VARIATION`).
- **Mob damage multiplier 0.4** for Realm Arms guns (`GunType.MOB_DAMAGE_MULTIPLIER`), set in
  code; `mobDamageMultiplier` in musketmod.txt now only affects Musket Mod's own guns.
- **Volleygun burst:** 2 ticks between shots (`GunType.BURST_INTERVAL_TICKS`). A full burst now
  takes 12 ticks (0.6 s) instead of 6. The gun kick follows the new timing.
- `GunType.pillagerTactics()` is now `mobTactics()` (shared by pillagers and guards).

### v2.11 changes

- **Gunshot pitch** now random between 0.5 and 1.5 (`GunType.FIRE_PITCH_VARIATION = 0.5`).
- **Flashes 1.1x larger** (quad 1.294), accent flashes included.
- **Status effects +1 s:** Nuclear 17 s, Void 5 s, Sunken 10 s, Poison 3 s (spread guns still get
  half). Durations and damage per cartridge are in the `Cartridge` enum.
- **Blaze rework:** 50% damage, 9 s on fire (the old 4 s doubled, +1 s).
- **Cartridge damage multipliers** (`Cartridge.damageMultiplier`) apply to guns and dispensers.
- **Unstable Cartridge:** 130% damage on every shot. Each trigger pull has a 30% chance to misfire
  (a volleygun burst rolls once for the whole burst): the vanilla tool-break sound plays, and that
  shot gets +9 degrees of spread, flies at 60 blocks/s, and costs 10 durability in total (9 on top
  of the normal 1; a hand cannon, with 4 durability, breaks). Creative mode takes no durability.
  Tuning: `Cartridge.UNSTABLE_*`.
- **Signal Cartridge:** 10% damage. Targets glow for 10 s and burn for 8 s (the fire isn't halved
  for spread guns). The shooter hears vanilla's arrow-hit "ding" on a hit, once per shot. The
  bullet drops 80% less (`SIGNAL_GRAVITY_MULTIPLIER = 0.2`) and leaves its own large glowing
  trail, `trail_signal` (3x the size of the other trails; `Style.TRAIL_SIGNAL`), instead of the
  gun's.
- **Shulker Cartridge:** 100% damage, Levitation I for 3 s (1.5 s from spread guns).
- The three new cartridges have no accent particles or accent flashes yet.

### v2.18.3 changes
- **Revolver:** one shot per 10 ticks (was 5). **Fan the hammer** is now one trigger pull: right-click
  while crouching with it loaded fires the whole clip, the first round at once and the rest every 4
  ticks, each anywhere in an 8 degree cone, wherever you're looking as each goes off. It can't be
  stopped (standing up doesn't); only the revolver leaving that hand or dying ends it. Each fanned
  round's sound is heard by everyone, the shooter too. Fan crosshair and the gun held 2 px low show
  while crouching with it loaded and during a fan. `gunmetal:fanning` marks a stack mid-fan (not saved).
- **Repeater:** one shot per 15 ticks (was 6), can't fan any more, 18 damage (was 17).
- **Fixed:** letting go of a reload after the 6th cartridge went in (during the closing stage) left the
  gun unable to reload or fire. A full clip now resumes without needing a cartridge.

### v2.18.2 changes
- Revolver: at most one shot every 5 ticks when not fanning (was 3); fanning stays every 2 ticks.

### v2.18.1 changes (revolver / repeater rework and fixes)
- **Reload:** 8 stages (4 s). Stage 1 and stage 8 load nothing; stages 2-7 each put one cartridge in
  (6 rounds). Sounds: `load_first` after stage 1, `round` after each of stages 2-7, `ready` when full.
- **Damage:** revolver 15, repeater 17 (before the mob multiplier and difficulty; both still fall off
  to 0.9x at 32 blocks). **Spread:** flat 1.6 (revolver) / 0.8 (repeater); the revolver's fanning
  penalty is gone.
- **Fire rate:** at most one shot every 3 ticks (revolver) / 6 ticks (repeater). Each round still needs
  its own click.
- **Fan the hammer:** crouching with the gun loaded. Every 2 ticks (revolver) / 3 ticks (repeater),
  but each shot lands anywhere within an 8 deg (revolver) / 4 deg (repeater) cone instead of the
  normal spread. First person: the gun is held 2 px lower. Mobs fan when their
  `CustomGunner#isFanning(gun)` says so (Realm Raids' Aviators within 4 blocks).
- **Crosshair:** clip guns use their own set: `hud/clip_crosshair_1` ... `_6` (rounds in the clip;
  counts up as cartridges go in, down with each shot), `hud/clip_crosshair_empty`,
  `hud/clip_crosshair_fan` (while fanning). Placeholders: the 0% crosshair with six pips / the 100%
  crosshair tinted orange.
- **Fixed:** shots that made no sound and the hand "re-equip" dip when clicking fast. The fire-rate
  check compared the server's clock (stored on the gun) with the client's, which drift a few ticks
  apart. The last shot is now timed per shooter and hand on each side separately (the server allows 1
  tick of slack), and the clip count (`gunmetal:clip_rounds`) no longer triggers the swap animation.
  `gunmetal:last_shot` is no longer used; it stays registered so 2.18.0 guns load, and is stripped
  when they tick. The loading crosshair also stays steady when the server updates the gun mid-reload.
- Repeater placeholder model/texture now the brass pistol's (like the revolver).
- Add-on hooks: `GunItem#spreadShot(direction, random, spread, stack, shooter)`,
  `CustomGunner#isFanning(gun)`, `ClipGunItem.isFanning(shooter, stack)`.

### v2.18.0 changes
Two new clip guns (`guns/ClipGunItem`), both untiered and built on brass stats:
- **Revolver** (`gunmetal:revolver`): brass pistol damage (17, falling to 0.9x at 32 blocks) and
  ballistics (140 m/s), one-handed and dual-wieldable. Spread 1.0 for an aimed shot; fanning it adds
  0.9 for every tick under 11 since its last shot (10 ticks: 1.9, 5 ticks: 6.4). At most one shot
  every 5 ticks. Mobs fight with it like a pistol. No crafting recipe.
- **Repeater** (`gunmetal:repeater`): brass carbine damage (18) and ballistics (160 m/s, spread 1.2),
  two-handed, no bayonet jab, no fanning penalty, at most one shot every 15 ticks. Mobs fight with it
  like an arquebus. Shapeless recipe (needs Alloyed): revolver + steel ignition + any planks + steel ingot.
- **Clip:** 6 rounds, filled over 12 stages (6 s) from empty; every 2nd stage takes a cartridge. The
  first cartridge sets the kind for the whole clip; later stages only take that kind, and loading stops
  (keeping its progress) when there's none left. A partly filled clip can't fire; a gun with rounds left
  fires instead of reloading. Each round needs its own click. Tooltip shows rounds (and a special kind).
  Mobs load a full clip for free (a special cartridge in their other hand stamps the clip, as before).
- **Durability 1351** each (1 per shot). Unrepairable: no anvil repair material, not in
  `enchantable/durability` (no Unbreaking/Mending), and two can't be combined in the crafting grid
  (`RepairItemRecipeMixin`). No attachments.
- Sounds: `gun.revolver.*` / `gun.repeater.*` — `fire`, `load_first` (end of stage 1), `round` (each
  cartridge in), `ready` (clip full); other stages use the shared `gun.load1`. Placeholders reuse the
  pistol / carbine sounds (`round` = `gun/load2`).
- Assets (placeholders): `items/revolver.json`, `items/repeater.json`, models `revolver_*` (parent
  `gun/pistol_*`) and `repeater_*` (parent `gun/carbine_*`), textures `item/revolver.png` /
  `item/repeater.png` (copies of the brass pistol / carbine).
- Tags: both in `gunmetal:guns`; revolver in Backslot's belt slot, repeater in its back slot.
- New add-on hooks: `GunItem#shotSpread(stack, shooter)`, `loadingStages(stack)`, `mobTactics()`,
  `acceptsAttachments()`, `hasBayonetJab()`, `onShotFired(entity, stack)`;
  `CustomGunner#mobShotDelay(gun)` (aim ticks before each shot, including between clip rounds).
- Mob AI (pillager and guard goals): with rounds left after a shot, they aim again (the normal
  1–2 s aim) before the next one.

### v2.17.9 changes
- `GunBullet.damageModifier` (server, add-ons): a `(target, damage, source) -> damage` hook that reworks
  a bullet's direct-hit damage per target, after the PvP/mob and range multipliers. Realm Raids'
  Sharpshooter uses it for separate player/mob damage and its weakened armor.
- Sniper trail (`Trail.SNIPER`) also leaves a flat square every 3 blocks (particle
  `gunmetal:trail_sniper_ring`, `client/particle/ShotRingParticle`): one block across, its face looking
  down the shot's path, drawn from both sides, never moves, gone after 0.5 s. Texture
  `textures/particle/trail_sniper_ring.png` (placeholder: a faint ring); several textures in
  `particles/trail_sniper_ring.json` play as frames over its life. Spacing: `SNIPER_RING_SPACING` in
  `RealmBulletEntity`.

### v2.17.8 changes
Add-on hooks (no change to any gun):
- `CustomGunner#pelletCount(gun)`: bullets per shot instead of the gun's (each still gets the shot's
  damage divided by that count). Realm Raids' level-3 drone fires a 3-pellet musketoon blast.
- `CustomGunner#spreadOverride(gun)`: per-bullet spread (degrees, std. dev.) instead of the gun's.

### v2.17.7 changes
Add-on hooks (no change to any gun):
- `GunBullet.damageTypeOverride` (server): a damage type key used for the direct hit instead of
  `gunmetal:bullet`. Realm Raids' Sharpshooter uses one tagged `bypasses_enchantments`.
- `RealmBulletEntity.illagerBurstScale` (default 1): extra burst damage factor for entities in
  `#minecraft:illager`. Realm Raids' Bombardier shells use 0.5.

### v2.17.6 changes
Add-on hooks (no change to any gun):
- `entity/BulletPayload` on `GunBullet.payload` (server): `onHitEntity` (return true to skip damage),
  `onImpact`, `onExpire`. Used by Realm Raids' Trooper smoke round.
- `Trail.SNIPER` (particle `trail_sniper`, placeholder = standard trail) for Realm Raids' Sharpshooter.
- Cannonball sprite drawn at 0.6 (was 0.3).

### v2.17.5 changes
- Ghost bullets for add-ons: `GunBullet#setGhostExcept(entityId)` (synced `GHOST_EXCEPT`) makes a
  bullet hit only players, iron golems and that entity (-1 = none) and pass through everything else.
  Used by Realm Raids' Sharpshooter. No change to any gun.

### v2.17.4 changes
Hooks for the Realm Raids add-on (no change to any existing gun):
- `guns/CustomGunner`: a mob that implements it can adjust each bullet before it spawns
  (`customizeBullet`) and replace the fire sound/volume when it fires through `GunItem#mobUse`.
- Bullet overrides: `GunBullet.mobDamageMultiplier` (replaces the 0.6 mob multiplier for that bullet),
  `GunBullet.knockback` (arrow Punch levels, direct hits), `GunBullet#setLifetimeTicks` (synced;
  also lifts the distance limit), `RealmBulletEntity.burstDamageScale` (replaces the burst's mob scale).
- `GunType.Projectile.CANNONBALL` / `Trail.CANNONBALL` with placeholder assets
  (`textures/entity/bullet_cannonball.png`, particle `trail_cannonball`); no gun uses them.
- `ai/PillagerGunGoal` is generic (`PillagerGunGoal<T extends PathfinderMob>`) with optional extra aim ticks.

### v2.17.3 changes
- **Range scaling** (all from the distance each bullet already tracks; one distance calculation per hit):
  - Rifle: listed damage 24 brass / 29 steel (0.8x the arquebus), times `1 + 0.7 x min(d, 92) / 92`,
    so 1.7x (40.8 / 49.3) from 92 blocks on. Per shot it passes the arquebus at ~33 blocks.
    `GunType.RIFLE_RANGE_BONUS`, `RIFLE_RANGE_PEAK`.
  - Pistol and carbine: soft falloff, `1 - 0.1 x min(d, 32) / 32`, so 0.9x from 32 blocks on.
    `GunType.SIDEARM_FALLOFF`, `SIDEARM_FALLOFF_END`.
  - Hand cannon burst radius: 0.2 at the muzzle, rising linearly to 1.4 at 64 blocks and beyond
    (`HAND_CANNON_MIN_RADIUS`, `HAND_CANNON_MAX_RADIUS`, `HAND_CANNON_RADIUS_PEAK`). The ball's own
    hit damage is unchanged. Mob AI keeps friendlies clear of the 1.4 maximum.
  - Mob and guard shots use the same curves, then the mob multiplier.
  - Not shown in tooltips.
- Headshots removed for every gun (from 2.17.2, which never went live): the `gunmetal:headshotable`
  tag, `GunConfig.HEADSHOT_MULTIPLIER` and the head-hitbox check are gone. A tag file of that name
  left in a datapack is harmless.

### v2.17.1 changes
- Gun mob damage multiplier 0.6 (was 0.5).
- Bows and crossbows +5% DPS (whole-number damage, so some tiers moved more or less). Steel crossbow:
  4 s charge, 34 damage. See `MATERIALS` in RealmArmsWeapons.
- Rifle: 24 damage at brass with a x1.625 headshot (replaced in 2.17.3, never went live).

### v2.17 changes (standalone)

- **Musket Mod Reloaded and Cloth Config are no longer dependencies.** Remove both from the pack.
  `fabric.mod.json` lists Musket Mod as a conflict (a warning, not a crash) so it's obvious if it's
  left in.
- New item `gunmetal:cartridge` (the standard cartridge, same recipe: 3 nuggets + gunpowder +
  paper -> 3). Old worlds: any `musketmod:cartridge` turns into `gunmetal:cartridge` in player
  inventories, and still fires from dispensers. Guns keep their loaded state (old
  `musketmod:loaded`/`loading_stage` data is moved over the first time the gun is in a player's
  inventory; mobs' guns just reload once).
- Everything Musket Mod provided is now under `gunmetal:`:
  - damage type `gunmetal:bullet` (death messages in the lang file)
  - tags `gunmetal:destroyed_by_bullets`, `gunmetal:dropped_by_bullets` (blocks),
    `gunmetal:headshotable` (entity types; removed in 2.17.3), `gunmetal:guns` (items)
  - loot tables `gunmetal:pillager_weapon` (default: copper pistol) and
    `gunmetal:chests/pillager_outpost` (up to 2 x 1-5 cartridges, like before). Any
    `gunmetal:<path>` loot table with the same path as a vanilla one is rolled into that vanilla
    table, so a datapack can add loot to any vanilla chest the same way.
  - sounds `gunmetal:gun.load0`, `gunmetal:dispenser_fire`, `gunmetal:bullet.fly_by`,
    `gunmetal:bullet.water_hit`
- Recipe unlocks now use `gunmetal:cartridge` (gunpowder -> standard cartridge; picking up a
  standard cartridge -> special cartridges and guns). The Void Cartridge (Enderscape) recipe now
  takes `gunmetal:cartridge`.
- Dropped with Musket Mod: its own guns, skeleton guns, the smithing template, and its enchantment
  tags (Realm Arms guns only ever took Unbreaking/Mending, which still works).
- Attachments and ignitions stay built but switched off (`Attachments.ENABLED = false`); while off,
  attachments already on a gun do nothing.
- Creative loads now pick the special cartridge you're holding (before, creative skipped it).
- Mobs (pillagers, guards) now use special cartridges held in their other hand: when a load
  finishes, one special cartridge is spent and that round is fired (effects, visuals, Unstable
  misfires). With no special cartridge there, they fire free standard rounds as before. Unstable
  misfires no longer cost a mob's gun durability (mobs never lose gun durability from shooting).
- Cartridges are now `ammo/CartridgeItem`: a stack picked up by a player loses any
  `minecraft:equippable` component (datapacks use one to put cartridges in a mob's offhand), so
  dropped mob cartridges stack with crafted ones.
- Bullets not fired by a player (mobs, guards, dispensers) expire after 60 ticks / 160 blocks
  instead of 100 / 256 (`GunConfig.MOB_BULLET_LIFETIME`, `MOB_BULLET_MAX_DISTANCE`), for big
  raid fights. Players' bullets are unchanged.
- `compat/CaptainBanners`: patrol, outpost and raid captains get their ominous banner back at the
  end of the tick they spawn in if their head slot is empty. Armored Foes clears armor slots
  (head included) for mobs in `armoredfoes:discards_original_equipment`, which removed the banner,
  so captains didn't count as captains (no ominous bottle drop, no Voluntary Exile).
- Mob damage multiplier stays 0.5. Hostile mobs' aim error 3 degrees (was 2); guards keep 2.
- Bows and crossbows rebalanced (see `MATERIALS` in RealmArmsWeapons). Golden durability 64.
  Quick Charge can't take a crossbow's charge below 0.5 s (`mixin/QuickChargeFloorMixin`).
  Damage values are whole numbers so tooltips read cleanly.
- Special cartridges craft 2 per recipe (standard cartridge still 3).
- Carbine spear damage type is set with `delayedHolderComponent` (it used to log
  "Couldn't apply minecraft:damage_type to the carbine" at startup and fall back to a plain attack).
- New textures for the ignitions and the blaze, nuclear, poison and void cartridges.
- Ignitions (`gunmetal:copper_ignition`, `brass_ignition`, `steel_ignition`) are always registered
  (no longer tied to the attachments switch). Not craftable; pillagers drop them (datapack). They
  smelt or blast back into their ingot (brass needs Create, steel needs Alloyed), and guns repair
  with them (`gunmetal:<tier>_gun_repair` tags; bows/crossbows keep `<tier>_repair`).
- New gun recipes; every gun except the hand cannon needs its tier's ignition, and steel guns are
  now craftable (Alloyed steel ingot/sheet). Key: I ingot, S sheet, N ignition, W planks,
  H tripwire hook, G spyglass. Pistol `NII/WH`, carbine `N  /WII/WHS`, musketoon `N  /WII/H S`,
  arquebus `N  /III/WH `, blunderbuss `N I/III/WHI`, rifle `NG /ISS/WH `, volleygun `NIS/IIS/WHS`,
  hand cannon ` RI/ TI/T  ` (R = #c:ropes, I copper ingot, T stick). Recipes unlock on picking up a
  cartridge or that tier's ignition.
- Item frames: every gun and attachment model's `fixed` display was a 2x, unflattened copy of the
  Blockbench default, so the gun sat mostly inside the wall. It's now the GUI view turned to face
  out of the frame.
- Third person: players holding a loaded crossbow of any kind (Realm Arms crossbows included) aim it
  (`mixin/client/AvatarRendererMixin`; vanilla only checks the vanilla crossbow).

**Migrating your pack/datapacks:**

| Was | Now |
|---|---|
| `data/musketmod/loot_table/pillager_weapon.json` | `data/gunmetal/loot_table/pillager_weapon.json` |
| `data/musketmod/loot_table/chests/*.json` | `data/gunmetal/loot_table/chests/*.json` (only if you want extra loot) |
| `data/musketmod/tags/...` (headshotable, destroyed/dropped_by_bullets) | `data/gunmetal/tags/...` |
| `assets/musketmod/textures/item/cartridge.png` | `assets/gunmetal/textures/item/cartridge.png` (already in the mod) |
| `assets/musketmod/sounds/musket_load0.ogg` | `assets/gunmetal/sounds/gun/load0.ogg`, event `gunmetal:gun.load0` |
| `assets/musketmod/sounds/musket_load1/2.ogg` | unused (Realm Arms guns already used `gunmetal:gun.load1/2`) |
| `config/musketmod.txt` | `guns/GunConfig.java` |
| `musketmod:cartridge` in recipes/loot/commands | `gunmetal:cartridge` |

### v2.16 changes (first live build: v2.14 + these; no ignitions/attachments)

- Hand cannon burst radius 1.3 (about 19 at impact, 10 at 1 block, 4 at 2, nothing past 2.6).
- New spark textures.
- Poison 4 s.
- Recipes: hand cannon uses sticks instead of planks; snowballs count for Ice Cartridges; bones
  count for Poison Cartridges. (The carbine recipe already had the sheet bottom-right.)
- Recipe unlocks: gunpowder unlocks the standard cartridge; picking up a standard cartridge
  unlocks every special cartridge and every Realm Arms gun. Musket Mod's own recipe unlocks
  (flint and steel -> musket/pistol/blunderbuss/cartridge/template) are switched off.
- Advancements (Adventure tab):

  | Advancement | Type | Under | Condition |
  |---|---|---|---|
  | Gun Mettle | task | Adventure | have any steel gun |
  | More Gun | task | Gun Mettle | have each of the 8 gun types at some point |
  | Highlander | challenge | More Gun | have each of the 21 guns at some point |
  | Second Scoper | task | Adventure | rifle kill on a mob/player holding a Realm Arms rifle |
  | Sniping's a Good Job Mate | challenge | Second Scoper | rifle kill on a monster > 100 blocks from where you fired |
  | Intruder Alert | challenge | Adventure | pistol kill on a pillager that never targeted anything |
  | Random Crits are Fair and Balanced | task | Adventure | monster killed by a misfired shot |
  | I'm Runnin' Circles Around Ya! | task | Adventure | mob killed by a shot fired while moving > 30 m/s |
  | W+M1 | task | Adventure | one Blaze shot sets 5 different mobs on fire |
  | You are All Maggots! | task | Adventure | hand cannon kill on a monster >= 50 blocks from where you fired |
  | Black Scottish Cyclops | challenge | You are All Maggots! | 5 monster kills from one hand cannon shot |
  | Bid Farewell, Cruel World! (hidden) | task | Black Scottish Cyclops | killed by your own hand cannon's burst |
  | Cry Some More | challenge | Adventure | 5 monster kills from one volleygun burst |

### v2.15 changes

- Reverted the stage-split test. Hand cannon burst radius 1.3. New spark textures.
- **Ignitions:** `gunmetal:copper_ignition`, `brass_ignition`, `steel_ignition` (items only, no
  recipes or uses yet).
- **Attachments** (removed in Gunmetal 1.0.4).

### v2.14 changes

- **Damage** (base tier, tiers still +20% each, rounded):

  | Gun | Before (c/b/s) | Now (c/b/s) |
  |---|---|---|
  | Carbine | 16/19/23 | 15/18/22 (15.2) |
  | Musketoon | 18/22/26 | 17/21/25 (17.1) |
  | Arquebus | 27/32/39 | 25/30/36 (24.84) |
  | Blunderbuss | 30/36/43 | 28/33/40 (27.6) |
  | Hand cannon | 42 | 37 |

- **Hand cannon burst radius 1.8** (was 0.8). Vanilla explosion damage at that radius: about 26 at
  the impact point, 17 at 1 block, 9 at 2, 3 at 3, nothing past 3.6 (on Hard, players take 1.5x
  from explosions). The target hit directly still takes no extra. A burst from a mob's hand
  cannon is scaled by the mob damage multiplier, like its bullets. Guards now keep 3.9 blocks
  between friendlies and a hand cannon target.
- **Unstable misfire** deals 40% of the gun's damage (`UNSTABLE_MISFIRE_DAMAGE`).
- **Mob damage multiplier 0.5.**
- **Advancements** (Adventure tab; `data/gunmetal/advancement/`, names in the lang file,
  thresholds at the top of `advancement/RealmAdvancements.java`):

  | Advancement | Frame | Parent | How |
  |---|---|---|---|
  | Gun Mettle | task | Adventure | data: pick up any steel gun |
  | Highlander (hidden) | task | Gun Mettle | data: pick up each of the 8 gun types at some point |
  | Second Scoper | task | Adventure | rifle kill on a mob/player holding a rifle (any scoped musket) |
  | Sniping's a Good Job Mate | challenge | Second Scoper | rifle kill on a monster > 115 blocks from where you fired |
  | Intruder Alert | task | Adventure | pistol kill on a pillager that never targeted anything |
  | Random Crits are Fair and Balanced | task | Adventure | monster killed by a misfired shot |
  | Grass Grows, Birds Fly | task | Adventure | mob killed by a shot fired while moving > 30 m/s |
  | W+M1 | task | Adventure | one Blaze shot sets 5 different mobs on fire |
  | Black Scottish Cyclops | challenge | Adventure | 3 monsters killed by one hand cannon shot (ball or burst) |
  | Bid Farewell, Cruel World! (hidden) | task | Black Scottish Cyclops | killed by your own hand cannon's burst |
  | Cry Some More | challenge | Adventure | 5 monsters killed by one volleygun burst |

  "Monster" = anything vanilla counts as hostile. Only players' shots count. A pillager is marked
  the first time it targets anything (`mixin/MobMixin`, stored as the entity tag
  `gunmetal.aggroed`), so in practice Intruder Alert needs a one-shot kill.

### v2.13 changes

- **Finished assets** from the resource pack are now in the mod (textures, models, particles,
  sounds). Not copied: the inventory icons (not used), `textures/item/cartridge.png` (a copy of
  the standard cartridge that nothing uses), `volleygun_fire.ogg` (replaced by `_1`-`_5`), and
  the .psd/.alv work files. The old single-texture particle placeholders were removed.
- **Shared loading sounds are now Realm Arms' own** (`gunmetal:gun.load1`, `gun.load2`,
  `gun.load0`), so they no longer depend on replacing Musket Mod's files (two mods shipping the same
  file path would load in an unpredictable order). Your `musket_load1/2.ogg` are now
  `sounds/gun/load1.ogg` and `load2.ogg`. `gun.load0` still plays Musket Mod's sound.
- **Crosshair** 70 GUI px (was 64).
- **Pistol** spread 1.5 degrees (was 2.0).
- **Blaze** 6 s on fire.
- **Spread guns** apply cartridge effects for 75% of the duration (was 50%;
  `Cartridge.SPREAD_GUN_DURATION`).
- **Melee +1:** carbine jab 7/8/9.5, musketoon 9/11/13.
- **Unstable recipe** takes any nugget (`#c:nuggets`, the same as the standard cartridge).
- **Recipe unlocks:** picking up gunpowder unlocks every gun and cartridge recipe, Realm Arms'
  and Musket Mod's (`data/gunmetal/advancement/recipes/guns/`, one per recipe; the optional-mod
  ones carry the same load conditions as their recipe).

### v2.12 changes

- **Pitch per gun** (`GunType.firePitchVariation()`): pistol 0.9-1.1, carbine and volleygun
  0.7-1.3, blunderbuss and rifle 0.6-1.4, arquebus, hand cannon and musketoon 0.5-1.5; cartridges
  from dispensers 0.9-1.1.
- **Hand cannon burst:** the ball bursts where it lands (entity or block): radius 0.8, never
  breaks blocks, no cartridge effect (the ball's own hit still applies it). Vanilla explosion
  damage at that radius: up to about 12 right at the impact point, about 4 a block away, nothing
  past 1.6 blocks, and a knockback shove. The target that was hit directly takes no extra (its
  damage immunity from the ball covers the smaller burst). The shooter can be caught at point
  blank. Guards won't fire a hand cannon while a friendly is within 1.9 blocks of the target.
  Ball damage and every other stat are unchanged.
- **Loading sounds:** each gun has its own first-stage and ready sound (see Sounds).
- **Removed:** Signal and Shulker cartridges (and `trail_signal`).
- **Unstable:** 50% misfire chance, misfire +12 degrees spread and 30 blocks/s. Recipe: 3 iron
  nuggets, paper and redstone or glowstone dust (Musket Mod's cartridge recipe with the gunpowder
  swapped); makes 3.
- **Nuclear:** hitting a target that already has Irradiated I replaces it with Irradiated II for
  5 s (2.5 s from spread guns). Nuclear now applies once per target per shot, so one shotgun blast
  can't apply I and upgrade it at once. Hitting a target with Irradiated II adds Irradiated I
  underneath it, which takes over when II runs out.
- **Void:** 8 s (was 5).
- **Quick Charge:** removed from Realm Arms guns (no longer in the enchantable tag) and ignored
  by their loading time.

### Guard Villagers

With Guard Villagers installed, a guard holding a Realm Arms gun fights with it
(`ai/GuardGunGoal.java`):

- **Same ranges and behavior as pillagers** (`GunType.mobTactics()`), at guard walking speed.
- **No friendly fire:** a guard won't shoot while a villager, player, iron golem, other guard or
  tamed pet is in the line of fire, including a few blocks past the target. The check allows for
  the gun's spread, so wide guns (blunderbuss, volleygun) need a wider gap. After 1 second
  without a clear shot the guard walks to find one.
- **Patrolling guards hold their post:** they load and fire from where they stand, never
  approaching or retreating.
- **No shield while holding a gun:** raising a shield would cancel every reload, so the shield
  goal is switched off while the guard's gun is in hand. Guards with swords etc. are unaffected.
- **Eating pauses the gun** (it resumes once they've eaten).
- **No ammo or durability used**, the same as pillagers. Damage uses the 0.4 mob multiplier.
- **Arms:** guards use the crossbow-loading pose while reloading and raise the gun to aim once
  loaded (Musket Mod's arm posing works on the guard model).

**Giving guards guns:** put one in a guard's main-hand slot in its menu (Hero of the Village or
enough reputation, per Guard Villagers' own rules), or add guns to the loot table guards spawn
with, `data/guardvillagers/loot_table/entities/guard_main_hand.json`. Add entries to the
`entries` list next to the swords and bows, for example:

```json
{ "type": "minecraft:item", "name": "gunmetal:copper_pistol",   "weight": 40 },
{ "type": "minecraft:item", "name": "gunmetal:copper_carbine",  "weight": 40 },
{ "type": "minecraft:item", "name": "gunmetal:copper_arquebus", "weight": 25 },
{ "type": "minecraft:item", "name": "gunmetal:brass_rifle",     "weight": 5 }
```

Weights are relative to the other entries (the iron sword is 300). A two-handed gun with a
shield in the offhand still works (the gun is in the main hand); the shield just isn't raised.

### Sounds

Every sound can be replaced from a resource pack's `assets/<namespace>/sounds.json`.

| Sound | Event | When |
|---|---|---|
| Gunshot, per gun | `gunmetal:gun.<type>.fire` | every shot |
| First stage, per gun | `gunmetal:gun.<type>.load_first` | end of loading stage 1 |
| Middle stages, shared | `gunmetal:gun.load1` | end of each stage from 2 to N-2 |
| Second-to-last stage, shared | `gunmetal:gun.load2` | end of stage N-1 (3+ stage guns) |
| Ready, per gun | `gunmetal:gun.<type>.ready` | end of the last stage: the gun is loaded |
| Resuming, shared | `gunmetal:gun.load0` | halfway into the stage you resume at, if you let go past stage 1 |
| Dispenser shot | `gunmetal:dispenser_fire` | a dispenser fires a cartridge |
| Bullet fly-by, water hit | `gunmetal:bullet.fly_by`, `gunmetal:bullet.water_hit` | a bullet passes near you / hits water |

`<type>` is `pistol, carbine, musketoon, arquebus, blunderbuss, rifle, hand_cannon, volleygun`.
Files live in `assets/gunmetal/sounds/gun/` (`<type>_start`, `<type>_ready`, `<type>_fire`,
`load0/1/2`) and `assets/gunmetal/sounds/bullet/`.

Sequences (stages): pistol (2) load_first, ready. Carbine and musketoon (3) load_first, load2,
ready. Arquebus and blunderbuss (4) load_first, load1, load2, ready. Rifle, hand cannon and
volleygun (5) load_first, load1, load1, load2, ready.

To replace a shared sound, add to your pack's `assets/gunmetal/sounds.json`:

```json
{
  "gun.load1": { "replace": true, "sounds": ["gunmetal:gun/load_stage"] }
}
```

(file at `assets/gunmetal/sounds/gun/load_stage.ogg`; mono .ogg so it fades with distance.)

**Random variants:** list more than one sound and one is picked at random each time. `weight`
makes one more likely, and `pitch`/`volume` adjust individual files:

```json
"sounds": [
  { "name": "gunmetal:gun/volleygun_fire_1", "weight": 6 },
  { "name": "gunmetal:gun/volleygun_fire_2", "weight": 1 },
  { "name": "gunmetal:gun/volleygun_fire_3", "weight": 1, "pitch": 0.95 }
]
```

Each sound needs its own `{ }`. Unlike particle textures, no naming convention is needed.


# Part 2: Raids (formerly Realm Raids 1.5.8)

Four extra raid illagers (three built on Realm Arms guns, plus the Artificer and its summons). Raids only: they never spawn naturally, in
patrols or in outposts. Spawn eggs are in the Spawn Eggs creative tab for testing.


### The mobs (tuning constants at the top of each class)

All three target players, villagers, iron golems and Guard Villagers guards (`entity/RaidIllager`).
Held weapons never drop; drops come from `data/gunmetal/loot_table/entities/` (player kill; gun +1%
per Looting level; ignitions x1.25 at Looting I, +0.25x base per further level).

Ground speed note: a mob's real ground speed grows with the *square* of its speed value, so 0.2 is
about a third of a pillager's 0.35 (about 1.7 blocks/s), not 57%.

### Bombardier (`entity/Bombardier`, `ai/BombardierAttackGoal`)
- 30 HP, speed 0.2, 1.1x size (1.0.3; hitbox 0.66 x 2.15). Its shells pass through Siege Balloons and
  their bursts spare them (1.0.3). Hand cannon held but not drawn (the cannon belongs to its model). Idle pose is
  the pillager's normal stance (never crossed arms), CROSSBOW_HOLD while it has a target.
- Walks in to 75 blocks, fires up to 90, never backs off, doesn't need to see targets.
- Within 16 blocks with line of sight it fires straight; otherwise it lobs a steep arc at a random
  point within its miss margin of where the target stands (no leading). Shells: 60 m/s, 10 s life.
- Shells and muzzle smoke start 0.5 blocks to its right of its eyes (`MUZZLE_RIGHT`, 1.5.5; "right"
  as seen facing the target). With a wall right there it fires from its eyes instead.
- Ramp-up over its first 24 shots (saved with the mob), evenly from first shot to peak:

  | | First shot | Peak (24 shots fired) |
  |---|---|---|
  | Reload | 10 s | 4.8 s |
  | Burst radius (uniform roll) | 0.9-1.8 | 3.3-4.0 (the low end grows 0.1 a shot) |
  | Miss margin | 20 blocks | 4 (-1 per shot, floor reached after 16 shots) |

- First shot at a new target 3-5 s after it picks one; the reload timer is kept between targets.
- It stands still for the last 3 s before each shot ("loading"). The rest of the time, once in
  range, it steps away from other Bombardiers/Sharpshooters closer than 8 blocks (`ai/Spacing`).
- Shells: 0.2x the hand cannon's direct damage, 0.5x the normal burst damage (these replace the 0.6
  mob multiplier). Bursts hurt the Bombardier itself and other illagers, illagers (#minecraft:illager)
  at half damage, and reach twice their radius.
- Weakness (1.4.7): players hurt by its shells (direct hit or burst; not if fully blocked) get Weakness
  I for 6 s from its 6th shot, 12 s from its 12th, Weakness II for 12 s from its 18th.
- Drops: steel ignition 50%.

### Sharpshooter (`entity/Sharpshooter`, `ai/SharpshooterAttackGoal`)
- 20 HP, speed 0.23 (about 30% faster on the ground than the Bombardier). Steel rifle drawn with
  `gunmetal:sharpshooter_rifle`.
- Holds 40-70 blocks (fires up to 80), prefers high ground, never backs off; between shots it keeps
  12 blocks from other Bombardiers/Sharpshooters. Walking between shots it uses the idle pose
  (state 0); standing with a target it uses "target acquired" (state 1).
- Shot cycle (one shot every 8.5-9.5 s, 9 on average; 1.4.8):
  - glint, 4 s: stands still, the scope glint shows 1.6 blocks in front of its eyes (flickering; a random
    texture from `scope_glint.json` per glint), tracks the target for 2.5 s;
  - windup, last 1.5 s (1.5.5; was 2 s): aim locked where the target was, windup particle at its head, the glint's
    size, turned 45 deg (a diamond);
  - shot at the locked spot, even if the target moved or ducked;
  - 3.5-4.5 s reload standing still (1.0.3; was 4.5-5.5) (even if the target is gone), then the next glint.
- Shot: 21 damage to players, 32 to anything else (any difficulty; no mob multiplier, no rifle range
  bonus), with armor and its enchantments only 25% as effective as usual (75% less): the mod works
  out what armor, toughness, Protection and Projectile Protection would normally take off, takes off a
  quarter of it, and deals the rest through armor. Shields, Resistance and absorption work as usual;
  armor isn't worn down by these shots. Players: no armor 21, full iron / diamond / netherite
  with Protection IV about 17.3 / 16.5 / 16.4. Unarmored mobs: 32. Punch II knockback.
- Bullet speed 1.5x the steel rifle's, no bullet drop (1.0.3; it aims straight at the spot). Trail: Realm Arms' `trail_sniper`
  particle, plus a flat square every 3 blocks facing along the shot (`trail_sniper_ring`, 0.5 s).
- No aim error at any range (1.5.5: the close-range error is gone): every shot goes to the locked spot.
- Smoke teleport (1.5.5): one charge, back 40 s after it's used.
  - A melee hit (a mob or player striking it directly; not raiders) sets it off 0.5 s later.
  - The first projectile that hits it, of any kind the Artificer's pulse destroys or stops
    (`#minecraft:arrows`, Realm Arms bullets, the Artificer's DESTROYED / STOPPED lists; not raiders'
    own), sets it off at once and does no damage (like an enderman).
  - A Trooper smoke-round cloud bursts at its feet (sound included), then it jumps in a fully random
    direction 2-32 blocks (1.0.3; was 2-8), up to 8 up or down, onto safe footing (no spot found: nothing
    happens and the charge is kept). 1.0.3: the triggering hit plays `entity.sharpshooter.smoke_bomb`, and
    the bomb gives off 10 `sculk_soul` particles in a 4-block cube around where it stood.
  - Then 4 s of invisibility (rifle hidden) during which it can't aim, like the raid-spawn stealth.
    A glint or windup in progress is dropped; a reload in progress carries on (the jump doesn't reload it).
- Ghost bullets: they pass through everything except players, iron golems and the mob it aimed at.
  Synced, so the client draws them the same way.
- Raid-spawned Sharpshooters start invisible (no particles) for 15 s. Meanwhile they pick a target
  and get into position, but don't start a glint until the invisibility wears off. Its rifle isn't
  drawn while it is invisible.
- Damage type `gunmetal:sharpshooter_shot` (otherwise like `gunmetal:bullet`): tagged
  `minecraft:bypasses_armor` and `minecraft:bypasses_enchantments` (the reduced armor is applied by
  the mod beforehand), scaling `never`.
- The glint and windup are tied to the Sharpshooter that made them: they vanish if it dies.
- Drops: steel rifle 8%, steel ignition 50%, echo shard 8% (1.5.2; player kill, Looting has no effect).

### Trooper (`entity/Trooper`, `entity/SmokeRound`, `ai/TrooperGunGoal`, `ai/FixBayonetGoal`, vanilla `SpearUseGoal`)
- 28 HP, speed 0.35. Knockback resistance 0.75 while its shield holds, none once it breaks (1.5.0).
- Shield (since 1.4.5; replaces the helmet): cuts all damage it takes by 80% and breaks after 3 hits
  of any kind (`entity.trooper.shell_break`). While it's up, hits play `entity.trooper.shield_hit`
  (placeholder: the vanilla shield block sound) instead of the hurt sound. Every damage tick counts, so fire, poison and the like
  strip it fast. Raider-caused damage (#minecraft:raiders) is reduced but doesn't count toward
  breaking it; /kill, the void and other "bypasses invulnerability" damage ignore it. Stacks with the
  charge's 30% and Resistance. It is a part of your trooper.jem, shown by `hat.ry` = hits left (see Models).
- Officers: 18% of Troopers, drawn with `textures/entity/illager/trooper_officer.png`.
- Steel arquebus (drawn as `gunmetal:trooper_arquebus`) at 0.8x damage, spawns unloaded (its first
  move is a reload) with 7 shots (officers 6; 1.5.5), fired like a pillager arquebusier but 0.5 s slower.
- Attack damage 5 (pillager/vindicator 5; 1.4.7): the bayonet's jab and charge use it.
- Its last shot is always a smoke round: 20 m/s, 1 s life, no damage; it bursts on impact or when it
  runs out into 25 big puffs that spread across the ground and settle, last ~9 s and fade out over the last 5 s (particle
  `smoke_cloud`). A puff that runs into a wall or a mob stops there and sinks to the ground under
  light gravity. Every puff also creeps 0.15 blocks/s in its own random direction for its whole life. A direct hit blinds the target for 3 s.
- Out of shots: stands still 1.5 s (`entity.trooper.fix_bayonet`), then switches to an unbreakable
  stone spear drawn as `gunmetal:trooper_bayonet` (`entity.trooper.equip_bayonet`). It also does this
  at once, giving up its shots and the smoke round, when it drops below 10 health for any reason
  (1.5.5: melee hits alone no longer trigger it).
- Rally: called by a Trooper below 20 health when it fires its smoke round, or by an officer whenever
  it fixes its bayonet (any reason, unless a rally called it). It blows a horn (once; heard
  ~128 blocks, `entity.trooper.horn`, your charge recording) and every Trooper within 32 blocks gets
  Resistance I for 12 s, and those that still have shots fix bayonets. Every mob in
  `#gunmetal:rally_followers` within 32 blocks (vindicators, ravagers, Illager Invasion's bashers
  and inquisitors; edit the tag to change) gets the same Resistance. It and every Trooper reached yell
  (`entity.trooper.rally_yell`, placeholder: the Trooper's own ambient lines, heard to 24 blocks),
  each after its own 0-5 s delay. Troopers called this way never rally others.
- Bayonet phase: vanilla spear AI (charge through, back off, charge again) plus ordinary melee.
  Real ground speed 1.2x normal, 1.75x while charging. 30% less damage taken while charging, and
  none from other Troopers' bayonets.
- Drops: steel arquebus 8%, steel ignition 2.5%.

### Artificer (`entity/Artificer`, `entity/ArtificerDrone`, `entity/BrassGolem`, `entity/Puppeted`)
- Evoker base: 24 HP, speed 0.5, evoker model/animations (crossed arms, spell arm-raise), keeps
  away from players within 8 blocks like an evoker. Drawn at pillager size (93.75%).
- Summons (1.5.0; arms raised 2.5 s, the summons 1 s in; one move every 7 s +-1 s while it has a
  target). Caps: drones 2, brass golem 1 (1.5.1), Puppeted 4 (its summons within 48 blocks count).
  Moves alternate A, B, A, B:
  - A: two or three summons (even odds, 1.5.1; three only if three fit), each a random kind still
    under its cap (the same kind may come more than once). If fewer than 2 fit, A is skipped and B
    happens instead.
  - B: every summon it owns is upgraded, then one random summon joins (it isn't upgraded this move;
    with everything at its cap B only upgrades).
  - Upgrades: drones and brass golems go up a level (max 3; at level 3 they get Regeneration I for
    5 s instead); Puppeted gain 16 health. Brass sparks and the summon marker at once; the anvil sound
    0-21 ticks later (random, in 3-tick steps) so a group doesn't play it all at once.
  - Its golems arrive with a fuse count of 30.
  - Telegraph: the move is picked when the cast starts and shown to the model a second before the
    summons appear (see Models: `summon_signal`, `upgrade_signal`). Caps are checked again then.
  - None of them join the raid (the wave doesn't wait for them).
  - When it dies (or has been missing for 2 s) all its summons go with it (1.5.1): drones and
    Puppeted (completed ones too) die; brass golems jump to a full fuse count, light their 1.5 s
    fuse and blow up. Summons without an Artificer (spawn eggs) are unaffected.
- Pulse, every 5.5 s while it has a target (no arm-raise):
  - 2 s before: `entity.artificer.pulse_warning` and electric sparks from it (4.5 every 2 ticks).
  - The wave travels out to 5 blocks (0.2 blocks/tick, ~1.25 s; along the ground: 1.5 below to 2.5
    above its feet) and gives everything it reaches `alexscaves:stunned` I for 2 s (Slowness IV
    without Alex's Caves; 1.5.0), with `entity.artificer.electrified` and sparks on each. No damage.
    Raiders (#minecraft:raiders, including its
    drones), brass golems (both kinds) and creative/spectator players are immune.
  - Three flat rings at its feet (0.2 apart; particles `pulse_ring_low/mid/top`) spread out with the
    wave, spin (low and top counter-clockwise, middle clockwise; ~100 deg/s) and throb:
    top 1.5 s, pops in, fades out over 0.5 s, follows it; middle 3.5 s, fades in (0.1 s) and out
    over 1.5 s, follows it; bottom 2 s, fades in (0.1 s) and out over its last 1 s, stays
    where the pulse started.
  - For 1.5 s, projectiles that come within 5 blocks of it (except raiders' own):
    - destroyed: arrows (#minecraft:arrows), Realm Arms bullets, firework rockets,
      alexsmobs:shark_tooth_arrow, alexscaves:seeking_arrow / dark_arrow / burrowing_arrow,
      frostiful:packed_snowball;
    - stopped dead where they meet the pulse, then they fall (stopped again while still inside, so
      they sink slowly): tridents, legacies_and_legends:boomerang, kitchenprojectiles:knife, splash and lingering potions.
      They land and can be picked up; potions shatter where they land. One that still reaches the
      Artificer bounces off harmlessly;
    - everything else is left alone.
    (`entity.artificer.zap`, sparks `artificer_zap`, 1.5x bigger.) Also sparks appear here and there in
    the pulse area (5 blocks out, 1 below to 3 above its feet) at 40% of the warning's rate.
- Drone (`gunmetal:drone`): flies (bee model and motion; one model and one texture, the
  model shows parts by level through `level_signal`, see Models),
  circles 3-11 blocks from its target (1.5.1), 2.5-4.5 above it; every 14 s (1.5.1; the first 7-21 s
  after it gets a target) it swoops for 1 s to about 1 block from the target at 60% of its height,
  in reach of melee mobs and players. Its bullets only hit players, iron golems and
  its target; damage is fixed (no falloff) and given as what a player on Hard takes before armor:

  | Level | Scale (1.4.7: always 1) | Health | Shot every | Spread | Damage | Drops (player kill, +1%/Looting) |
  |---|---|---|---|---|---|---|
  | 1 | 1.0 | 4 | 6.7 s (1.5.1) | 3.5 deg | 8 (1.5.1) | propeller 8% |
  | 2 | 1.0 | 8 | 2.2 s (1.5.1) | 3 | 8 | propeller 16%, electron tube 8% |
  | 3 | 1.0 | 16 | 1 s | 2.5 | 8; every 4th shot a musketoon blast, 3 pellets x 6, spread 2 | propeller 32%, electron tube 16%, precision mechanism 8% |

  Upgrades past level 3 give Regeneration I for 5 s. Shots are at 1.25 volume. It dies the moment its Artificer dies (or has been missing for 2 s);
  without an Artificer (spawn egg) it lives on. In #minecraft:raiders.
- Brass golems, both kinds (`entity/AbstractBrassGolem`): 24 HP, speed 0.2 (a copper golem's),
  knockback resistance 1, step height 1.8, and they path straight over fences, walls and closed
  fence gates. Fuse count: +1 per second, +5 per damaging hit (fire ticks and explosions too;
  snowballs and eggs don't count). At the threshold (60) it lights a creeper-length fuse (1.5 s): it
  hisses, swells and flashes and keeps going at half real ground speed (1.5.0), then blows up: no
  block damage, everything the blast hurts is stunned for 1 s (Alex's Caves' Stunned / Slowness IV;
  not creative players), with a visual-only pulse (the Artificer's top and bottom rings at 60% size
  and 75% duration, 1.5.1) and debris.
  A lit fuse can't be put out. Killing a golem before it blows up defuses it: no blast. Ticking
  (volume 1, the fuse's: heard to 16 blocks, 1.5.1): 1 per second at a count of 0, rising exponentially to 8 per second at the
  threshold; silent over the lit fuse (the fuse sound carries it).
- Brass golem (`gunmetal:brass_golem`, hostile): creeper-style chase; arrives from its Artificer
  with a fuse count of 30. Reaching its target (in sight within 3 blocks) lights its 1.5 s fuse at
  once, and it keeps chasing through it. Blast radius 2 (4 across) at level 1; each upgrade level
  adds the blast of 2 gunpowder in a deployable (radius 2.72 / 3.6 at levels 2 / 3). Half damage to
  illagers (its Artificer too); its blast doesn't stun raiders. Killed: no blast, its loot drops. Own
  texture (`brass_golem_hostile.png`) at every level. Iron golems and guards attack it (it is an
  Enemy); illagers count it as an ally. Blows up with its Artificer (see Summons).
- Puppeted (`gunmetal:puppeted`): a plain melee mob (no weapons, no undead traits), normal size
  (1.5.0). Max health 38; appears with 6; each upgrade +16. Its state follows its health, both ways:
  - incomplete (6 or less): zombified piglin model, speed 0.15, attack 1, no drops, no XP;
  - completed (7 or more): piglin model with its own look (one of two, picked when it appears), speed
    0.25, attack 11 (1.5.1), 5 XP.
  Both die with their Artificer (1.5.1). Takes 20% of explosion damage; explosion knockback
  resistance 1 (1.5.1).
  Takes its Artificer's target, otherwise players, villagers, iron golems and guards. In
  #minecraft:raiders (not #minecraft:illager). Drops when completed: create:cogwheel 8% and
  create:large_cogwheel 4% (player kill, +1% per Looting level), 0-2 string (+0-1 per Looting level;
  any kill). Sounds: `entity.puppeted.ambient_incomplete`, `ambient_complete`, `hurt`, `death`,
  `step` (zombified piglin / piglin placeholders).
- Debris (1.5.0; counts and size 1.5.2): pieces (particle `construct_bits`, 6 textures; 0.525
  blocks across) thrown on direct hits (not fire/poison ticks), on death and when a golem blows up.
  Pieces per event: drone hit 1, death 2-3; Puppeted hit 1-3, death 2-5; brass golem (both kinds)
  hit 1-4, killed 1-4, blown up 4-12. Launched sideways, they fall, bounce and vanish after 1.5 s.
  Drawn by each player's game from events the game already sends: no network cost.
- Summons give off the Artificer's sparks constantly: Puppeted 0.375 per tick incomplete, 0.75
  completed; golems (both kinds) from 0.3 per tick at no
  fuse progress to 2.25 at a full fuse, drones 0.75 / 1.1 / 1.5 per
  tick at levels 1 / 2 / 3. The summon marker is 2.5 blocks across. All sparks (warning, pulse area,
  summons) are drawn by each player's game: no network cost.
- Drops (player kill): Artificer one of brass sheet / precision mechanism / brass hand /
  transmitter, plus a Brass Deployable 50%; golem 1-4 brass nuggets and 1-4 gunpowder (each +0-1 per
  Looting level).

### Siege Balloon (`entity/SiegeBalloon`, `entity/Aviator`, `entity/BalloonArrow`; 1.5.2, warship 1.5.3, rebalanced 1.5.4, 1.5.5, 1.5.6, 1.5.7, 1.5.8)
- Always a package (spawn egg, /summon, raid wave): the balloon + its own ravager (tethered) + an
  Aviator aboard as its pilot. In a raid all three join the wave and count toward the raid bar.
- Balloon: 6 wide x 4 tall hitbox (1.5.8; was 4x4x4), 120 health (1.0.2), 10 armor (1.5.8), no pathfinding,
  35 XP when a player kills it (1.5.8), no drops, knockback and explosion knockback resistance 1. The pilot stands just outside the bottom-front face, feet level with the
  bottom (1.5.7; was 0.5 above); hitboxes never turn, so at diagonal headings it is slid further out (up to x1.41) to stay
  outside the balloon's hitbox, while its model is drawn at the straight-on spot.
- Height ("ground" = first solid block or liquid straight below; rises at 1 block/s; 1.0.2):

  | Situation | Rise state | Sinks at |
  |---|---|---|
  | default (target or not) | high (keeps rising; the tether holds it) | |
  | damaged (under 70 health) | mid (bottom 11 above the ground) | 1 block/s |
  | burning (under 25 health) | low (bottom 2.7 above the ground) | 1.5 blocks/s |
  | pilot gone (tethered or not) | low | 1 block/s |
  | ravager gone, pilot aboard | low | 0.3 blocks/s |

  When several apply, the fastest one still pulling it down wins.

- Drift: 1 block/s in a random clear direction (re-picked every 4-10 s or when blocked), always; plus,
  while it has a pilot (tethered or not), 1 block/s toward its target until within 16 blocks of it.
  Turns 3 degrees/tick to face its target (the crossbow's, else the cannon's).
- Tether (custom): 18 +- 3 blocks (each balloon rolls a whole number from 15 to 21, evenly; saved,
  1.5.6) from the balloon's bottom-centre to the ravager's back; past that it is pulled back. Can't be
  sheared, cut, used or picked up. Failsafe snap past 30 blocks (1.5.6; was 24) or when the ravager
  has been missing 2 s. Drawn as three chains (visual only, 1.5.6): from straight above the pilot's
  spot (0.5 above the bottom) and from two rear points (1.5 to each side, 3.5 back, 2.5 up) (1.0.1), turning with the
  balloon, all meeting at the ravager's back; each sags by its own slack. The pull is still worked out
  from the bottom-centre.
- Losing health on its own, once a second, no flash or flinch (`bleed` sound each time): 5 once both the
  ravager and the pilot are gone (1.0.2: none with only one of them gone).
- Weapons and paratroopers work only while the pilot is aboard. Weapons fire in any direction (each
  needs a clear view). Each plays its own ready sound 1.5 s before firing (held until there is
  something to shoot at):
  - Cannon (0.3 below the bottom, 0.6 behind the centre; 1.5.8, moved 1 forward with the model): a hand cannon round, 33 to a Hard player before armor, every 4.5 s
    +-1 s within 48 blocks; 2 blocks/tick with normal gravity (aimed as an arc), 3 degrees of spread,
    fixed 1.3 blast (1.5.6; was 0.8). Prefers a different target than the crossbow; takes the crossbow's only when
    nothing else is in reach (and moves off it when something else is).
  - Crossbow (two barrels 3 blocks apart at the pilot's spot, 0.7 above the bottom; 1.5.8): bursts of
    10 arrows (1.5.8; was 6), right and left barrel in turn, 3 ticks apart (~1.4 s), 4 degrees of
    spread, 3 s after the last burst (1.5.8; was 2.5), within 36
    blocks; 6 to a Hard player each (flat, whatever the arrow's speed), half to raiders (ravagers
    included); firework-launch sound; each arrow leaves a vanilla `firework` spark behind every tick
    in flight (1.5.7; was every 2) (client only; no speed of its own, so it hangs, slowly sinks and fades over
    2.4-3 s, like `/particle minecraft:firework`). Arrows vanish 5 s after landing.
  - Paratroopers: a 16 s countdown that only runs while one of its targets is within 36 blocks
    (pausing otherwise) drops two Aviators at once, one at each side (each with a random weapon, see
    Aviator), 1.5 blocks out from the centre, under the hull (1.5.8), 3 s of Slow Falling; 6 per balloon
    (1.0.3: drops of 2, 2, 2). In a raid they join the wave. 1.5.7: the countdown
    holds at 2 s left until a target is in range, then plays `drop_warning` and runs out into the drop
    (in range or not), like the weapons' ready sounds.
  - Targets: players, villagers, iron golems, guards (and whatever hurts it). One shared nearest-first
    search every 0.5 s; each weapon keeps its target until it has been out of reach or view for 3 s.
- At 0 health (any cause): any paratroopers still aboard all bail out at once beside it (no Slow
  Falling; each a random 0-3 blocks higher than a normal drop, so the fall damage varies, and stunned
  2 s (1.5.8): `alexscaves:stunned` I, Slowness IV without Alex's Caves), then it blows up (radius 2.6,
  no block damage, credited to nobody) with the pilot still aboard (taking half), then throws the pilot
  out. 1.5.8, with the bail-out spot now under the hull: about 5-31 to each paratrooper on Hard (the
  highest-starting ones can die to the blast alone), the pilot about 5; with the fall from mid height
  about half of them die, from low height about 1 in 6. Everything the blast reaches catches fire for
  6 s (1.5.8; mobs only, never fire blocks; Aviators are fireproof). 36 envelope tatters (1.0.2) and 18 burning
  pieces burst out more steeply upward than the usual debris (sideways x0.9 bits / x1.2 flames, 1.5.8;
  upward x0.75; `DEATH_*` constants). Also (1.5.8, client only): a ring that always faces you, growing
  from 6 to 18 blocks across in 0.5 s (particle `balloon_death_ring`), and 8 smoke puffs 3.75 blocks across
  scattered through a 6-block ball around the blast, hanging 4 s with a slow random drift and fading
  over 2 s (particle `balloon_death_smoke`). Burning pieces (particle `burning_debris`:
  flame texture, full-bright, 0.49x gravity, upright and never turning, stick where they land, 5.5 s)
  shrink like a vanilla flame (to half size over their life) before the short fade. Its body isn't
  drawn after death.
- Damage stages under 70 and under 25 health (1.0.2; `damage_stage_1` / `damage_stage_2` sounds and 10
  envelope tatters each); under 25 health it gives off flames. Each stage has its own texture (1.5.7, see Models).
- Mayday (1.5.7): `entity.siege_balloon.mayday` every 3 s while it loses health on its own (ravager
  gone) or is at its last damage stage (under 25 health), heard ~48 blocks; the first one at once.
- Pilot: an Aviator (below). Aboard: half damage from everything (not /kill or the void), no weapon
  of its own, faces the balloon's target, no suffocation or fall damage. Thrown out: full damage, and
  it always draws a revolver.
- Sounds (`entity.siege_balloon.*`, placeholders): ambient, hurt, death, rise_change, deploy,
  cannon_ready, crossbow_ready (1.5 s before each shot / burst), crossbow_fire, damage_stage_1,
  damage_stage_2, bleed, drop_warning (2 s before each paratrooper drop; 1.5.7), mayday (1.5.7). The cannon uses
  the hand cannon's own fire sound; the pilot pillager sounds.

### Aviator (`entity/Aviator`; 1.5.5, was the Raid Pilot)
- `gunmetal:aviator` (own spawn egg; `gunmetal:raid_pilot` is gone, so old Raid Pilots in a world
  are removed): pillager-shaped, 22 health (1.0.2), speed 0.35, fully fireproof (fire, lava, magma). Drops: 2.5%
  salvage on a player kill (see 1.0.3 changes); its weapon 8% on a player kill, randomly damaged,
  +1% per Looting level (1.0.4).
- Balloon pilot (see Siege Balloon), the only mob the balloon's paratrooper drops bring, and on its
  own in raids (a flat 10% chance of one on every wave).
- Paratroopers and raid Aviators carry one of, evenly: revolver, repeater, hatchet (1.0.4; Illager
  Invasion's platinum infused hatchet or a copper axe, 10 damage before difficulty). Pilots: the Pilot
  Revolver. Guns: fights like a Realm Arms gun pillager (revolver like
  a pistol, repeater like an arquebus), with 25-30 ticks of aim before every aimed revolver round.
- Fanning (1.5.6; revolver only since 1.5.8): with a revolver and its target within 4 blocks
  (`FAN_RANGE`), the first shot of a clip is still aimed, then it fans the rest every 3 ticks (1.0.2), each
  shot anywhere in an 8 degree cone (plus the usual 3 degree pillager aim error). Stops as
  soon as the target is further than 4 blocks. Other mobs with these guns never fan. Hatchet:
  plain melee, 10 before difficulty (`HATCHET_DAMAGE`), ATTACKING arm pose.
- Spawn egg: a pilot on foot (pilot texture, draws a revolver). `/summon`: an airman with a random weapon.
- Textures (`textures/entity/illager/`): `aviator_pilot.png` for balloon pilots and spawn-egg Aviators;
  everyone else picks `aviator_airman_a` / `_b` / `_c` at random (saved with the mob).

### Brass Deployable (`item/BrassDeployableItem`, `entity/ThrownBrassDeployable`, `entity/DeployedGolem`)
- Crafted redstone torch / carved pumpkin / Create brass block in one column. The six other slots may hold
  any mix of gunpowder, blaze powder, Additional Additions copper patina, redstone, glowstone dust and
  Alex's Caves sulfur dust (the "load", kept on the item; the tooltip lists it). Plain ones stack
  with plain ones; loaded ones only with the same load. The plain recipe is in the recipe book
  (unlocked by a brass block); loaded ones aren't.
- Thrown like a snowball (8 s cooldown, stacks to 16). It passes through mobs (dropping straight
  down), stops where it hits a block, and 3 s later becomes a brass golem in the open block there.
- The deployed golem (`gunmetal:deployed_brass_golem`): wanders and ticks. Takes 20% of all damage
  (not /kill or the void). Aggro pulses when it appears and when its count passes 15, 30 and 45
  (marks under its starting count are skipped): every hostile mob within 12 blocks (not bosses, the
  Warden, Elder Guardians or neutral mobs) targets it; mobs may switch away between pulses. The
  blast breaks no blocks, spares no one, deals full damage to illagers and counts as the thrower's
  kill. Killed before it blows up: 50% chance to drop a plain Brass Deployable. Not an Enemy.
- Load effects:
  - gunpowder: the n-th powder adds (15 + 2n)% blast size (+17 / 36 / 57 / 80 / 105 / 132%; damage
    grows with size as in vanilla)
  - blaze powder: half a powder for blast size (6 blaze = 3 gunpowder); what the blast reaches burns 3 s per blaze powder
  - sulfur dust: half a powder for blast size; what the blast reaches gets Irradiated 6 s per sulfur
    (skipped without Alex's Caves)
  - copper patina: +10 fuse threshold each (+10 s left alone)
  - redstone: starts the count 10 higher each (6 = blows up on appearing)
  - glowstone dust: +1.5 pulse radius each (+3 across)
- Its texture shows the load: gunpowder (also empty), blaze, patina, redstone, glowstone, sulfur,
  or mixed (two or more kinds).

**Last raiders glow.** Bombardiers and Sharpshooters get Glowing while the raid has 3 or fewer
raiders left alive, so a hidden one can't stall the wave.

### Raid waves: `data/gunmetal/raid_spawns.json`
Re-read at every wave, so a datapack override takes effect after `/reload`. Per entity id, per
wave: `chance` (default 1), then a uniform count `min`..`max` (default 1), plus optional `bonus_chance`
(a separate roll for one more, 1.5.2; or a list of chances, each its own independent roll for one more, 1.0.3). A wave that isn't listed
uses the closest listed wave below it; below the lowest listed wave, none.

| Wave | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 (bonus) |
|---|---|---|---|---|---|---|---|---|
| Bombardier | - | - | - | 0-1 | 0-2 | 1-2 | 1-3 | 1-4 |
| Sharpshooter (one, chance) | 15% | 15% | 32% | 64% | 32% | 64% | 32% | 100% |
| Trooper | - | - | 0-3 | 0-4 | 1-4 | 1-6 | 2-6 | 3-6 |
| Artificer (chance) | - | - | 50% | 10% | 50% | 80% | 10% | 1, 50% a second |
| Siege Balloon (chance + independent extra rolls; 1.0.3) | 1% | 2% | 4% | 8% +1% | 16% +4% +1% | 32% +8% +2% | 64% +16% +4% | 100% +32% +8% |
| Aviator (one, chance; 1.5.5) | 10% | 10% | 10% | 10% | 10% | 10% | 10% | 10% |

They join through `Raid#joinRaid` from `mixin/RaidMixin` (end of `Raid#spawnGroup`, before the wave
counter goes up), so they count toward the raid bar like any raider.

### Sounds
Ambient/hurt/death for the first three are in `assets/gunmetal/sounds/<mob>/` (`entity.<mob>.ambient|hurt|death`).
Artificer, drone and golem sounds are vanilla placeholders in `sounds.json` (`entity.artificer.*`,
`entity.drone.*`, `entity.brass_golem.*`); the Artificer's cast and celebrate sounds are the evoker's.
`entity.trooper.horn` is your recording (`sounds/trooper/horn.ogg`); `entity.trooper.rally_yell` and
`item.brass_deployable.throw` are placeholders.

### Models (Entity Model Features)
State signals: every frame the mod writes each illager's state into its invisible vanilla `hat`
part, where a .jem/.jpm can read it:
- `hat.rz` = state number (below). Use `round(hat.rz)` to compare.
- `abs(hat.rx)` = seconds since its last shot (Artificer: last pulse), smooth, capped at 100. Use it
  for recoil (e.g. kick back at 0 and settle over 1 s).
- `hat.ry` = Trooper only: shield hits left (3, 2, 1; 0 = broken). Use `round(hat.ry)`.

| State | Bombardier | Sharpshooter | Trooper | Artificer |
|---|---|---|---|---|
| 0 | idle (no target) | idle | idle (no target) | idle |
| 1 | reloading (between shots) | target acquired (tracking, glint) | gun: aiming | has a target |
| 2 | aiming a lob (3 s before a high shot) | shot locked (1.5 s windup) | smoke round chambered (aiming it) | pulse warning (2 s) |
| 3 | aiming direct fire (3 s, within 16 blocks, in view) | reloading (after a shot) | fixing bayonet (1.5 s) | pulse showing (2.5 s) |
| 4 | | | bayonet fixed, not charging | |
| 5 | | | charging | |
| 6 | | | gun: reloading (any shot, the smoke round too) | |

The Trooper's bayonet jab is the normal attack swing (`swing_progress`). All three gunners also
swing their main hand on each shot. The vanilla arm poses are unchanged (Bombardier NEUTRAL /
CROSSBOW_HOLD; Sharpshooter CROSSBOW_HOLD / BOW_AND_ARROW while aiming; Trooper CROSSBOW_HOLD or
CHARGE with the gun, NEUTRAL with the bayonet, BOW_AND_ARROW while charging; Artificer evoker poses).

Held gun per state: the Sharpshooter's and Trooper's main-hand item is drawn with its state name as
`custom_model_data` string 0 (a copy made for drawing; the real item never carries it), so an item
definition can `select` on it and give each state its own model and `thirdperson_righthand`
position. Names: `sharpshooter_idle / _target_acquired / _shot_locked / _reloading`;
`trooper_idle / _aiming / _smoke_chambered / _fixing / _bayonet_fixed / _charging / _reloading`.
Grouping states is done in the item definitions (`items/sharpshooter_rifle.json`,
`trooper_arquebus.json`, `trooper_bayonet.json`), no build needed. Positions switch instantly
(item positions can't blend). The item definitions' fallback (the old loaded/loading switching)
only applies to an untagged stack.

Handedness: all Realm Raids illagers spawn right-handed (vanilla makes 5% left-handed); ones that
spawned left-handed before 1.4.2 stay that way.

Model layers (EMF file = `assets/gunmetal/emf/cem/<name>.jem`, or `optifine/cem/`):
- `bombardier`, `sharpshooter`, `trooper`, `artificer` (illager geometry; all but the Bombardier
  drawn at 93.75%, pillager size)
- `drone` (bee geometry and motion, all levels; drawn at 1x at every level since 1.4.7). Part tree:
  root > bone > body (stinger, left_antenna, right_antenna), right_wing, left_wing, front_legs,
  middle_legs, back_legs; root > level_signal. An extra empty part,
  `level_signal`, carries the level: `round(level_signal.rz)` = 1, 2, 3. In `drone.jem`, add a
  `level_signal` entry and give it the visibility lines, e.g.
  `"var.level": "round(level_signal.rz)"`, `"gunbarrel.visible": "var.level == 1"`,
  `"doublegun.visible": "var.level >= 2"`, `"widebarrel.visible": "var.level == 3"`
- `brass_golem` (copper golem geometry; both golems)
- `artificer`: also has two empty parts for the summon telegraph (1.5.0):
  `summon_signal.rx` / `.ry` / `.rz` = drones / golems / Puppeted about to appear (0-3 each; from the
  start of the cast until they appear, 1 s), `upgrade_signal.rz` = 1 during an upgrade move's whole
  cast (the wrench), else 0.
- `siege_balloon` (vanilla ghast geometry: `body`, `tentacle0`...`tentacle8`, drawn 4.5x like the
  ghast) plus empty parts `balloon_signal`: `round(.rx)` rise state (0 high, 1 mid, 2 low),
  `round(.ry)` 1 = pilot lost, `round(.rz)` 1 = cut loose; and `balloon_signal2` (1.5.3):
  `round(.rx)` damage stage (0, 1 under 70 health, 2 under 25), `.ry` seconds since the last cannon
  shot, `.rz` seconds since the last crossbow burst; and `balloon_signal3` (1.5.7): `round(.rx)` 0
  normal, 1 the 2 s before a paratrooper drop, 2 the 2 s after, `round(.ry)` 1 while no tether is drawn (1.0.1); and (1.5.8) `cannon_signal` /
  `crossbow_signal`: `.rx` aim pitch and `.ry` aim yaw relative to the balloon's facing (radians;
  pitch + up, yaw + to its left), `round(.rz)` 1 while that weapon has a target (0, 0 at rest), a
  straight line from that weapon's muzzle to its target's middle. It turns to face its target;
  `head_pitch` = its aim pitch. Textures by damage stage (1.5.7; 64x32, ghast layout; the .jem must not
  set a texture): `textures/entity/siege_balloon/siege_balloon.png`, `siege_balloon_damaged.png` (50
  health and below), `siege_balloon_burning.png` (20 and below); tether chain
  `textures/entity/siege_balloon/tether.png` (one tile = 0.5 blocks of chain along the texture's height).
- `aviator` (1.5.5, was `raid_pilot`; illager geometry at pillager size): `hat.rz` 0 aboard, 1 aboard
  and aiming, 2 on foot, 3 on foot with a target (gun), 4 on foot with a target (axe / sword), 5
  fanning the hammer (1.5.6), 6 reloading a gun (1.5.7); `round(hat.ry)` = weapon group (1.5.7): 0 nothing
  in hand (pilot aboard), 1 axe / sword / musketoon, 2 pistol / revolver, 3 carbine / repeater; melee
  swings drive `swing_progress`;
  `abs(hat.rx)` seconds since the last shot. Textures `textures/entity/illager/aviator_pilot.png`,
  `aviator_airman_a.png`, `_b`, `_c` (letters, so Entity Texture Features doesn't treat them as variants).
- `puppeted_incomplete`, `puppeted_complete` (vanilla piglin geometry; the Puppeted's two states).
  Built from Fresh Animations' zombified_piglin / piglin files with `headwear` renamed `hat`
  (mod model layers use vanilla part names).

Textures: `textures/entity/illager/<illager>.png`, `textures/entity/drone/drone.png` (64x64, bee layout),
`textures/entity/brass_golem/brass_golem[_hostile|_blaze|_patina|_redstone|_glowstone|_sulfur|_mixed].png`
(Entity Texture Features: a matching `_e.png` draws glowing parts), item
`textures/item/brass_deployable.png`, `textures/entity/puppeted/incomplete.png`, `complete_a.png`,
`complete_b.png` (64x64, piglin layout; letters, not numbers, so Entity Texture Features doesn't
treat them as random variants). A .jem's `"model": "x.jpm"` is relative to its own folder.

### Advancements (Adventure tab, 1.5.0)

Under vanilla's Voluntary Exile (which is hidden until earned, so these show once it is):
- Poet of a Machine (copper sword): kill an Artificer.
  - Autonomy Corporation (hidden; brass deployable): kill an Artificer with your own thrown brass
    golem's blast.
- Children of the Ancient City (spyglass): kill a Sharpshooter.
- Steel Lotus (shield): kill an officer Trooper.
- And Then is Shelled No More (TNT): kill a Bombardier.
- String Technocracy (string): kill 15 Puppeted (any state; counted by the game's kill statistic, so
  kills from before 1.5.0 count too). Granted in code (`Puppeted.die`).

### Notes
- Tags: `#minecraft:raiders` (the four illagers, the drone, the Puppeted, the Siege Balloon and the Aviator) and `#minecraft:illager` (the four illagers).
- Villagers don't run from these (vanilla's list of mobs villagers flee is fixed). Not changed.
- Armored Foes doesn't touch them (it works by entity type), as intended.
- Performance: summon sparks, golem ticking and ring particles are drawn client-side only. A golem's
  fuse count is sent to players every 0.5 count (every 10 ticks idle; at once on a hit); each
  player's game fills in between. A lit fuse sends its tick count each tick for its 30 ticks. Deployed golems search for mobs only at their
  4 pulses. A blast of radius ~4.6 (6 powders) costs about as much as TNT; many loaded golems going
  off in the same tick can hitch the server. A golem caught in another's blast gains +5 and, if
  that fills it, lights its own 1.5 s fuse, so chains spread out.
- Mobs that run on the newer "brain" AI (piglins, hoglins, zoglins, breezes, wardens) often ignore
  a deployed golem's lure.
