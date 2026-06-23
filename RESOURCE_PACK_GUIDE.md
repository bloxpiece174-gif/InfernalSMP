# HeartsSMP v2 — Resource Pack Guide

This guide maps every `CustomModelData` ID used in the plugin to the correct
JSON model file location in your resource pack. Follow this 1-to-1 to get
every gem, skill item, and divine weapon rendering correctly in-game.

---

## Resource Pack Folder Structure

```
resourcepack/
├── pack.mcmeta
├── pack.png
└── assets/
    └── minecraft/
        ├── models/
        │   └── item/
        │       ├── emerald.json          ← overrides base gem model
        │       ├── blaze_rod.json        ← overrides base staff/skill model
        │       └── golden_hoe.json       ← overrides base weapon model
        └── textures/
            └── item/
                └── heartssmp/
                    ├── gems/
                    ├── skills/
                    └── weapons/
```

---

## pack.mcmeta

```json
{
  "pack": {
    "pack_format": 46,
    "description": "HeartsSMP v2 Custom Resource Pack"
  }
}
```

> `pack_format 46` = Minecraft 1.21.4. Check https://minecraft.wiki/w/Pack_format for updates.

---

## Gem Models — Base item: `EMERALD`

Override `assets/minecraft/models/item/emerald.json`:

```json
{
  "parent": "minecraft:item/handheld",
  "textures": { "layer0": "minecraft:item/emerald" },
  "overrides": [
    { "predicate": { "custom_model_data": 10001 }, "model": "heartssmp:item/gems/ember_gem" },
    { "predicate": { "custom_model_data": 10002 }, "model": "heartssmp:item/gems/stone_gem" },
    { "predicate": { "custom_model_data": 10003 }, "model": "heartssmp:item/gems/tide_gem" },
    { "predicate": { "custom_model_data": 10011 }, "model": "heartssmp:item/gems/gale_gem" },
    { "predicate": { "custom_model_data": 10021 }, "model": "heartssmp:item/gems/shadow_gem" },
    { "predicate": { "custom_model_data": 10031 }, "model": "heartssmp:item/gems/aurora_gem" },
    { "predicate": { "custom_model_data": 10041 }, "model": "heartssmp:item/gems/void_gem" },
    { "predicate": { "custom_model_data": 10051 }, "model": "heartssmp:item/gems/celestia_gem" }
  ]
}
```

| CMD ID | Item ID          | Display Name      | Texture File                          |
|--------|------------------|-------------------|---------------------------------------|
| 10001  | COMMON_EMBER     | Ember Gem         | `heartssmp/textures/item/gems/ember`  |
| 10002  | COMMON_STONE     | Stone Gem         | `heartssmp/textures/item/gems/stone`  |
| 10003  | COMMON_TIDE      | Tide Gem          | `heartssmp/textures/item/gems/tide`   |
| 10011  | UNCOMMON_GALE    | Gale Gem          | `heartssmp/textures/item/gems/gale`   |
| 10021  | EPIC_SHADOW      | Shadow Gem        | `heartssmp/textures/item/gems/shadow` |
| 10031  | LEGENDARY_AURORA | Aurora Gem        | `heartssmp/textures/item/gems/aurora` |
| 10041  | MYTHICAL_VOID    | Void Gem          | `heartssmp/textures/item/gems/void`   |
| 10051  | DIVINE_CELESTIA  | Celestia Gem      | `heartssmp/textures/item/gems/celestia`|

---

## Skill Models — Base item: `BLAZE_ROD`

Override `assets/minecraft/models/item/blaze_rod.json`:

```json
{
  "parent": "minecraft:item/handheld",
  "textures": { "layer0": "minecraft:item/blaze_rod" },
  "overrides": [
    { "predicate": { "custom_model_data": 20001 }, "model": "heartssmp:item/skills/graceful_enlightenment" },
    { "predicate": { "custom_model_data": 30006 }, "model": "heartssmp:item/weapons/aurora_staff" },
    { "predicate": { "custom_model_data": 30016 }, "model": "heartssmp:item/weapons/storm_staff" }
  ]
}
```

| CMD ID | Item ID                  | Display Name            |
|--------|--------------------------|-------------------------|
| 20001  | graceful_enlightenment   | (skill slot item)       |
| 30006  | aurora_staff             | Aurora Staff            |
| 30016  | storm_staff              | Storm Staff             |

---

## Weapon Models — Base item: `GOLDEN_HOE`

> **Why GOLDEN_HOE?** Using a non-vanilla-weapon base means players never see
> the default golden hoe texture. All visual identity comes entirely from your
> resource pack. This avoids overriding swords/axes which players legitimately use.

Override `assets/minecraft/models/item/golden_hoe.json`:

```json
{
  "parent": "minecraft:item/handheld",
  "textures": { "layer0": "minecraft:item/golden_hoe" },
  "overrides": [
    { "predicate": { "custom_model_data": 30001 }, "model": "heartssmp:item/weapons/soul_blade" },
    { "predicate": { "custom_model_data": 30002 }, "model": "heartssmp:item/weapons/celestial_blade" },
    { "predicate": { "custom_model_data": 30003 }, "model": "heartssmp:item/weapons/titan_hammer" },
    { "predicate": { "custom_model_data": 30004 }, "model": "heartssmp:item/weapons/gods_trident" },
    { "predicate": { "custom_model_data": 30005 }, "model": "heartssmp:item/weapons/void_dagger" },
    { "predicate": { "custom_model_data": 30014 }, "model": "heartssmp:item/weapons/thunder_axe" }
  ]
}
```

| CMD ID | Item ID         | Display Name      | Visual Style             |
|--------|-----------------|-------------------|--------------------------|
| 30001  | soul_blade      | Soul Blade        | Dark purple with soul fx |
| 30002  | celestial_blade | Celestial Blade   | White/gold longsword     |
| 30003  | titan_hammer    | Titan Hammer      | Massive stone warhammer  |
| 30004  | gods_trident    | God's Trident     | Gold 3-pronged trident   |
| 30005  | void_dagger     | Void Dagger       | Black void blade         |
| 30014  | thunder_axe     | Thunder Axe       | Crackling electric axe   |

---

## Other Custom Items

| CMD ID | Base Material        | Item ID          | Display Name       |
|--------|----------------------|------------------|--------------------|
| 30011  | BOW                  | flame_bow        | Flame Bow          |
| 30012  | SHIELD               | frost_shield     | Frost Shield       |
| 30015  | DIAMOND_CHESTPLATE   | dragon_scale     | Dragon Scale       |
| 30017  | RED_DYE              | blood_gem_ring   | Blood Gem Ring     |
| 30020  | CLOCK                | chrono_watch     | Chrono Watch       |
| 30025  | PAPER                | divine_scroll    | Divine Scroll      |
| 30026  | LANTERN              | golden_torch     | Golden Torch       |

---

## God's Trident — Key Design Notes

The `gods_trident` item (CMD 30004, base GOLDEN_HOE) should be modelled as a
**gold 3-pronged trident** with white/divine accents, matching the God entity
skin shown in the reference images. Suggested Blockbench workflow:

1. Open Blockbench → **Java Block/Item** model
2. Create a long thin handle (1×1×12 voxels, gold texture)
3. Add 3 prongs at top spreading outward (Y+15 to Y+18)
4. Apply golden/white trim to prong tips
5. Add a subtle enchantment-glow layer
6. Export as `assets/heartssmp/models/item/weapons/gods_trident.json`

---

## Gem Texture Artistic Direction (matching reference image)

Based on the reference pixel art gem sheet provided:

| Gem          | Color Palette         | Shape         |
|--------------|-----------------------|---------------|
| Ember        | Red → Orange gradient | Classic faceted gem  |
| Stone        | Grey/Brown earth tones| Rough oval    |
| Tide         | Blue → Teal           | Teardrop      |
| Gale         | Light blue/white      | Crystal point |
| Shadow       | Deep purple/black     | Dark faceted  |
| Aurora       | Gold/Yellow shimmer   | Rectangular   |
| Void         | Purple/Black void     | Oval dark gem |
| Celestia     | White/Gold divine     | Heart/Star    |

---

## God Entity Skin

To set the God's skin, get a Base64 texture string from https://mineskin.org:

1. Upload your god skin PNG (white robes + gold accents — matching IMG_4234.jpeg)
2. Copy the **Texture Value** (long Base64 string)
3. Paste into `config.yml` under `god-npc.skin-texture`
4. Optionally paste the **Signature** under `god-npc.skin-signature`
5. Run `/godadmin reload` in-game

The included default texture value in config.yml uses the default
Minecraft "elder" style skin as a placeholder.

---

## Quick Setup Checklist

- [ ] Set `ai.api-key` in config.yml
- [ ] Set `ai.provider` (anthropic / openai / gemini)  
- [ ] Set `ai.model` to your preferred model
- [ ] Upload god skin to MineSkin.org and paste value into `god-npc.skin-texture`
- [ ] Create world named `divine_realm` and run `/godadmin castles generate`
- [ ] Install resource pack on your server (via `server.properties: resource-pack=`)
- [ ] Run `/godadmin spawn guide` to test the God Entity
- [ ] Run `/godadmin ai test Hello God` to verify AI is working

