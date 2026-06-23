package com.heartssmp.managers;

import com.heartssmp.HeartsSMPPlugin;
import com.heartssmp.data.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * ItemManager v2 — All 30 custom items plus God's Trident.
 *
 * Key changes:
 *  • Every item has a unique CustomModelData ID for resource pack mapping.
 *  • Items use GOLDEN_HOE / BLAZE_ROD / etc. as base (no vanilla weapon override).
 *  • Items tagged with PDC key "heartssmp_item" for event identification.
 *  • Non-droppable / non-placeable enforcement is handled by ItemProtectionListener.
 *
 * RESOURCE PACK MAPPING — see config.yml custom-model-data section and
 * the guide in README.md. Each CustomModelData ID maps to a model in:
 *   assets/heartssmp/models/item/<id>.json
 */
public class ItemManager {

    public static final String PDC_ITEM_ID_KEY = "heartssmp_item_id";
    public static final String PDC_BOUND_KEY   = "heartssmp_bound";

    private final HeartsSMPPlugin plugin;
    private final NamespacedKey itemIdKey;
    private final NamespacedKey boundKey;

    // All custom items defined statically
    private static final List<CustomItem> ITEMS = new ArrayList<>();

    static {
        // ── Tier 1 — Starter ────────────────────────────────────────────────
        // Base material: EMERALD (gems), PAPER (scrolls), custom CMD IDs start at 30001
        ITEMS.add(new CustomItem("ember_shard",    "Ember Shard",         "§cWarm shard with extra fire damage",          Material.BLAZE_ROD,        1, 30101));
        ITEMS.add(new CustomItem("tide_pearl",     "Tide Pearl",          "§9Ocean pearl — throw to summon a water rush", Material.ENDER_PEARL,      1, 30102));
        ITEMS.add(new CustomItem("stone_core",     "Stone Core",          "§7Earth core — passive Resistance I",          Material.COBBLESTONE,      1, 30103));
        ITEMS.add(new CustomItem("wind_feather",   "Wind Feather",        "§bWind feather — passive Speed II",            Material.FEATHER,          1, 30104));
        ITEMS.add(new CustomItem("shadow_mask",    "Shadow Mask",         "§8Darkness mask — sneak for invisibility",     Material.CARVED_PUMPKIN,   1, 30105));
        ITEMS.add(new CustomItem("aurora_crown",   "Aurora Crown",        "§eAurora crown — passive Regen I",             Material.GOLDEN_HELMET,    1, 30106));
        ITEMS.add(new CustomItem("void_lens",      "Void Lens",           "§5Void lens — see players through walls",      Material.GLASS,            1, 30107));
        ITEMS.add(new CustomItem("celestia_dust",  "Celestia Dust",       "§eDivine dust — temporarily boosts all stats", Material.GLOWSTONE_DUST,  1, 30108));
        ITEMS.add(new CustomItem("life_crystal",   "Life Crystal",        "§aLife crystal — use to gain +1 life",         Material.PRISMARINE_CRYSTALS, 1, 30109));
        ITEMS.add(new CustomItem("heart_shard",    "Heart Shard",         "§cHeart shard — use to gain +1 heart",         Material.RED_DYE,          1, 30110));

        // ── Tier 2 — Combat ─────────────────────────────────────────────────
        // These use GOLDEN_HOE / BLAZE_ROD bases with unique CustomModelData
        // so they do NOT override vanilla sword/axe/bow textures
        ITEMS.add(new CustomItem("soul_blade",     "Soul Blade",          "§8Blade that harvests souls on kill",          Material.GOLDEN_HOE,       2, 30001));
        ITEMS.add(new CustomItem("flame_bow",      "Flame Bow",           "§cBow that fires flaming arrows",              Material.BOW,              2, 30011));
        ITEMS.add(new CustomItem("frost_shield",   "Frost Shield",        "§bShield that slows attackers on block",       Material.SHIELD,           2, 30012));
        ITEMS.add(new CustomItem("venom_arrow",    "Venom Arrow",         "§2Arrows tipped with lethal poison",           Material.TIPPED_ARROW,     2, 30013));
        ITEMS.add(new CustomItem("thunder_axe",    "Thunder Axe",         "§eAxe that strikes lightning on kill",         Material.GOLDEN_HOE,       2, 30014));
        ITEMS.add(new CustomItem("dragon_scale",   "Dragon Scale",        "§6Dragon scale armor — massive defense",       Material.DIAMOND_CHESTPLATE,2, 30015));
        ITEMS.add(new CustomItem("void_dagger",    "Void Dagger",         "§5Dagger that ignores 50% armor",              Material.GOLDEN_HOE,       2, 30005));
        ITEMS.add(new CustomItem("storm_staff",    "Storm Staff",         "§9Staff of storms — rightclick for lightning", Material.BLAZE_ROD,        2, 30016));
        ITEMS.add(new CustomItem("blood_gem_ring", "Blood Gem Ring",      "§4Ring — killing restores 1 heart",            Material.RED_DYE,          2, 30017));
        ITEMS.add(new CustomItem("phase_boots",    "Phase Boots",         "§dBoots that let you dash through 1-block gaps",Material.IRON_BOOTS,      2, 30018));

        // ── Tier 3 — Legendary ───────────────────────────────────────────────
        ITEMS.add(new CustomItem("omega_gauntlet",  "Omega Gauntlet",     "§dGauntlet of omega — greatly boosts punch damage",Material.LEATHER_CHESTPLATE, 3, 30019));
        ITEMS.add(new CustomItem("chrono_watch",    "Chrono Watch",       "§6Time watch — use to freeze nearby enemies 3s",  Material.CLOCK,          3, 30020));
        ITEMS.add(new CustomItem("celestial_blade", "Celestial Blade",    "§eCelestial light blade — divine power",          Material.GOLDEN_HOE,     3, 30002));
        ITEMS.add(new CustomItem("void_cloak",      "Void Cloak",         "§5Void cloak — 5s invincibility once per 2min",   Material.LEATHER_CHESTPLATE, 3, 30021));
        ITEMS.add(new CustomItem("titan_hammer",    "Titan Hammer",       "§8Titan hammer — ground slam on rightclick",      Material.GOLDEN_HOE,     3, 30003));
        ITEMS.add(new CustomItem("aurora_staff",    "Aurora Staff",       "§eAurora staff — heal nearby allies on rightclick",Material.BLAZE_ROD,     3, 30006));
        ITEMS.add(new CustomItem("shadow_cloak",    "Shadow Cloak",       "§8Cloak of shadows — invisible on sneak",         Material.LEATHER_CHESTPLATE, 3, 30022));
        ITEMS.add(new CustomItem("hell_core",       "Hellcore Fragment",   "§4Hell fragment — burst fire on rightclick",      Material.NETHER_BRICK,   3, 30023));
        ITEMS.add(new CustomItem("star_fragment",   "Star Fragment",       "§eFallen star — absorb for 3 random skill uses",  Material.NETHER_STAR,    3, 30024));
        ITEMS.add(new CustomItem("divine_scroll",   "Divine Scroll",       "§6Scroll of divinity — guides toward the Trial",  Material.PAPER,          3, 30025));

        // ── Divine ──────────────────────────────────────────────────────────
        ITEMS.add(new CustomItem("gods_trident",    "God's Trident",       "§6§lThe weapon of the Divine — GracefulEnlightenment skill item", Material.GOLDEN_HOE, 4, 30004));
        ITEMS.add(new CustomItem("golden_torch",    "Golden Torch",        "§6Torch blessed by the divine — marks sacred ground",            Material.LANTERN,    3, 30026));
    }

    public ItemManager(HeartsSMPPlugin plugin) {
        this.plugin   = plugin;
        this.itemIdKey = new NamespacedKey(plugin, PDC_ITEM_ID_KEY);
        this.boundKey  = new NamespacedKey(plugin, PDC_BOUND_KEY);
    }

    // ── Item Creation ─────────────────────────────────────────────────────────

    public ItemStack createItem(String id) {
        CustomItem ci = getDefinition(id);
        if (ci == null) return null;

        ItemStack stack = new ItemStack(ci.material);
        ItemMeta meta   = stack.getItemMeta();
        if (meta == null) return stack;

        // Display name
        meta.displayName(Component.text(ci.name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));

        // Lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(ci.description, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Tier " + ci.tier + tierStars(ci.tier), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("HeartsSMP Bound Item", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("§8[Non-Droppable | Hold to Activate]", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        // CustomModelData for resource pack
        meta.setCustomModelData(ci.customModelData);

        // Tier 3/4 enchant glow
        if (ci.tier >= 3) {
            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        // PDC tags
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, ci.id);
        meta.getPersistentDataContainer().set(boundKey,  PersistentDataType.BYTE, (byte) 1);

        // Hide all vanilla attribute text
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        stack.setItemMeta(meta);
        return stack;
    }

    public void giveItem(Player player, String id) {
        ItemStack item = createItem(id);
        if (item == null) { player.sendMessage(plugin.prefix() + "§cUnknown item: " + id); return; }
        player.getInventory().addItem(item);
        CustomItem ci = getDefinition(id);
        player.sendMessage(plugin.prefix() + "§aYou received: §e" + (ci != null ? ci.name : id));
    }

    /** Returns the heartssmp item ID stored in the item's PDC, or null if not a custom item. */
    public String getItemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer()
                .get(itemIdKey, PersistentDataType.STRING);
    }

    public boolean isCustomItem(ItemStack stack) {
        return getItemId(stack) != null;
    }

    public boolean isBoundItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        Byte val = stack.getItemMeta().getPersistentDataContainer().get(boundKey, PersistentDataType.BYTE);
        return val != null && val == 1;
    }

    public List<CustomItem> getAllItems() { return Collections.unmodifiableList(ITEMS); }

    public CustomItem getDefinition(String id) {
        return ITEMS.stream().filter(i -> i.id.equals(id)).findFirst().orElse(null);
    }

    private String tierStars(int tier) {
        return switch (tier) {
            case 1 -> " ✦";
            case 2 -> " ✦✦";
            case 3 -> " ✦✦✦";
            case 4 -> " §6✦✦✦✦ DIVINE";
            default -> "";
        };
    }

    // ── Inner Record ──────────────────────────────────────────────────────────

    public static class CustomItem {
        public final String id, name, description;
        public final Material material;
        public final int tier, customModelData;

        public CustomItem(String id, String name, String description,
                          Material material, int tier, int customModelData) {
            this.id              = id;
            this.name            = name;
            this.description     = description;
            this.material        = material;
            this.tier            = tier;
            this.customModelData = customModelData;
        }
    }
}
