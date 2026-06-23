package com.heartssmp.god;

import com.heartssmp.HeartsSMPPlugin;
import com.heartssmp.ai.GodAIClient;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import java.util.*;

/**
 * GodEntity v2 — Overhauled God NPC with AI integration, invulnerability,
 * custom skin via Paper PlayerProfile, and rich ambient particle animations.
 *
 * The entity uses a skinned ArmorStand which is universally compatible with
 * Paper 1.21 without requiring NMS reflection. The custom skin texture is
 * loaded from config.yml (god-npc.skin-texture) as a Base64 value.
 *
 * Invulnerability is enforced via GodNPCListener which cancels all
 * EntityDamageByEntityEvent events targeting this entity.
 */
public class GodEntity {

    public enum GodForm { GUIDE, POWER_25, POWER_50, POWER_75 }

    private final HeartsSMPPlugin plugin;
    private final GodAIClient aiClient;
    private Entity godNPCEntity;
    private Location location;
    private final GodForm form;
    private final UUID summoner;
    private boolean active = false;
    private int floatTaskId = -1;
    private long spawnTime;
    private final Map<UUID, Long> lastAIRequest = new HashMap<>();
    private final long aiCooldownMs;
    private final double proximityRadius;

    private static final Map<GodForm, Integer> ITEM_LIMITS = Map.of(
        GodForm.POWER_25, 16,
        GodForm.POWER_50, 64,
        GodForm.POWER_75, 1728
    );

    public GodEntity(HeartsSMPPlugin plugin, GodAIClient aiClient,
                     Location location, GodForm form, UUID summoner) {
        this.plugin          = plugin;
        this.aiClient        = aiClient;
        this.location        = location;
        this.form            = form;
        this.summoner        = summoner;
        this.aiCooldownMs    = plugin.getConfig().getLong("ai.cooldown-seconds", 8) * 1000L;
        this.proximityRadius = plugin.getConfig().getDouble("ai.proximity-radius", 20.0);
    }

    // ── Spawn / Despawn ───────────────────────────────────────────────────────

    public void spawn() {
        despawn();
        World world = location.getWorld();
        if (world == null) return;

        godNPCEntity = world.spawn(location, ArmorStand.class, as -> {
            as.setCustomName(getGodTitle());
            as.setCustomNameVisible(true);
            as.setGravity(false);
            as.setVisible(true);
            as.setInvulnerable(true);
            as.setSmall(false);
            as.setBasePlate(false);
            as.setArms(true);
            as.setPersistent(true);
            as.addScoreboardTag("god_entity");
            as.addScoreboardTag("heartssmp_god");

            // Custom skinned skull from config
            String texture   = plugin.getConfig().getString("god-npc.skin-texture", "");
            String signature = plugin.getConfig().getString("god-npc.skin-signature", "");
            as.getEquipment().setHelmet(buildCustomSkullHead(texture, signature));

            ItemStack chest = enchanted(new ItemStack(Material.GOLDEN_CHESTPLATE));
            ItemStack legs  = enchanted(new ItemStack(Material.GOLDEN_LEGGINGS));
            ItemStack boots = enchanted(new ItemStack(Material.GOLDEN_BOOTS));
            as.getEquipment().setChestplate(chest);
            as.getEquipment().setLeggings(legs);
            as.getEquipment().setBoots(boots);
            as.getEquipment().setItemInMainHand(createGodsTrident());

            // Lock all slots to prevent item theft
            for (org.bukkit.inventory.EquipmentSlot slot : org.bukkit.inventory.EquipmentSlot.values()) {
                try {
                    as.addEquipmentLock(slot, ArmorStand.LockType.ADDING_OR_CHANGING);
                    as.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
                } catch (Exception ignored) {}
            }
        });

        active = true;
        spawnTime = System.currentTimeMillis();
        broadcastSpawn();
        startFloatingAnimation();
        startAmbientParticles();
        if (form != GodForm.GUIDE) startSessionTimer();
    }

    private ItemStack buildCustomSkullHead(String texture, String signature) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            PlayerProfile profile = plugin.getServer().createProfile(UUID.randomUUID(), "TheDivine");
            if (texture != null && !texture.isBlank()) {
                if (signature != null && !signature.isBlank()) {
                    profile.setProperty(new ProfileProperty("textures", texture, signature));
                } else {
                    profile.setProperty(new ProfileProperty("textures", texture));
                }
            }
            meta.setPlayerProfile(profile);
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createGodsTrident() {
        // Golden Hoe base + CustomModelData 30004 maps to God's Trident in resource pack
        ItemStack item = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lGod's Trident");
            meta.setCustomModelData(30004);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                              org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack enchanted(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, 10, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void despawn() {
        active = false;
        if (floatTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(floatTaskId);
            floatTaskId = -1;
        }
        if (godNPCEntity != null && !godNPCEntity.isDead()) {
            godNPCEntity.getWorld().spawnParticle(
                    Particle.END_ROD, godNPCEntity.getLocation(), 120, 1.2, 1.2, 1.2, 0.25);
            godNPCEntity.getWorld().playSound(
                    godNPCEntity.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
            godNPCEntity.remove();
            godNPCEntity = null;
        }
        aiClient.clearHistory(summoner);
    }

    /** Returns true if entity e is this God NPC (for damage event cancellation). */
    public boolean isGodEntity(Entity e) {
        return godNPCEntity != null && godNPCEntity.equals(e);
    }

    // ── AI / Chat Handling ────────────────────────────────────────────────────

    public void handlePlayerInteraction(Player player, String message) {
        if (!active) return;
        if (godNPCEntity != null) {
            if (player.getLocation().distance(godNPCEntity.getLocation()) > proximityRadius) return;
        }
        long now = System.currentTimeMillis();
        Long last = lastAIRequest.get(player.getUniqueId());
        if (last != null && (now - last) < aiCooldownMs) {
            long rem = (aiCooldownMs - (now - last)) / 1000;
            player.sendMessage("§7*The Divine needs " + rem + "s to respond...*");
            return;
        }
        String lower = message.toLowerCase();
        if (containsItemRequest(lower)) { handleItemRequest(player, lower, message); return; }

        lastAIRequest.put(player.getUniqueId(), now);
        godSpeak("§7§o*" + getGodName() + " considers your words, " + player.getName() + "...*");

        boolean aiEnabled = plugin.getConfig().getBoolean("ai.enabled", true);
        if (!aiEnabled) {
            godSpeak(getGodPrefix() + "§e*The divine remains silent today.*");
            return;
        }

        String ctx = buildContext(player, message);
        aiClient.askAsync(player.getUniqueId(), player.getName(), ctx,
            response -> {
                for (String line : response.split("\n")) {
                    if (!line.isBlank()) godSpeak(getGodPrefix() + "§e" + line.trim());
                }
                spawnGodSpeakParticles();
            },
            err -> godSpeak(getGodPrefix() + "§7*The heavens tremble in silence — ask again.*")
        );
    }

    private String buildContext(Player player, String message) {
        var data = plugin.getDataManager().get(player.getUniqueId());
        int hearts = data != null ? data.getHearts() : -1;
        int lives  = data != null ? data.getLives() : -1;
        return "[hearts=" + hearts + ",lives=" + lives + ",form=" + form.name() + "] " + message;
    }

    // ── Floating & Particle Animations ────────────────────────────────────────

    private void startFloatingAnimation() {
        final double baseY = location.getY();
        floatTaskId = new BukkitRunnable() {
            double tick = 0;
            @Override public void run() {
                if (!active || godNPCEntity == null || godNPCEntity.isDead()) { cancel(); return; }
                tick += 0.05;
                Location l = godNPCEntity.getLocation();
                l.setY(baseY + Math.sin(tick) * 0.3);
                l.setYaw(l.getYaw() + 1.5f);
                godNPCEntity.teleport(l);
            }
        }.runTaskTimer(plugin, 0L, 1L).getTaskId();
    }

    private void startAmbientParticles() {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!active || godNPCEntity == null || godNPCEntity.isDead()) { cancel(); return; }
                tick++;
                Location loc = godNPCEntity.getLocation().add(0, 1.2, 0);
                World w = loc.getWorld();
                if (w == null) return;
                for (int i = 0; i < 8; i++) {
                    double a = (2 * Math.PI / 8) * i + tick * 0.1;
                    w.spawnParticle(Particle.END_ROD,
                        loc.clone().add(Math.cos(a) * 1.2, Math.sin(tick * 0.15 + i) * 0.4, Math.sin(a) * 1.2),
                        1, 0, 0, 0, 0.01);
                }
                if (tick % 40 == 0) {
                    w.spawnParticle(Particle.FIREWORK, loc, 20, 0.6, 0.6, 0.6, 0.15);
                    w.spawnParticle(Particle.FLAME,    loc, 10, 0.3, 0.3, 0.3, 0.05);
                }
                if (form == GodForm.POWER_75 && tick % 25 == 0) {
                    w.spawnParticle(Particle.SOUL, loc, 5, 0.5, 0.5, 0.5, 0.05);
                    w.strikeLightningEffect(godNPCEntity.getLocation());
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    private void startSessionTimer() {
        long ticks = 20L * 60 * 15;
        new BukkitRunnable() { public void run() {
            if (!active) return;
            godSpeak(getGodPrefix() + "§7*The audience ends. I return to eternity.*");
            despawn();
        }}.runTaskLater(plugin, ticks);
        new BukkitRunnable() { public void run() {
            if (!active) return;
            godSpeak(getGodPrefix() + "§7*Five minutes remain, mortal. Choose your words wisely.*");
        }}.runTaskLater(plugin, ticks - 20L * 60 * 5);
    }

    // ── Item request (deterministic fallback) ─────────────────────────────────

    private boolean containsItemRequest(String l) {
        return (l.contains("give me") || l.contains("i want") || l.contains("i need"))
            && (l.contains("diamond") || l.contains("netherite") || l.contains("emerald")
             || l.contains("gold") || l.contains("iron") || l.contains("obsidian")
             || l.contains("ancient debris") || l.contains("elytra") || l.contains("totem")
             || l.contains("beacon") || l.contains("shulker") || l.contains("apple"));
    }

    private void handleItemRequest(Player player, String lower, String orig) {
        int limit = ITEM_LIMITS.getOrDefault(form, 16);
        int req = extractNumber(orig); if (req <= 0) req = 64;
        int amount = Math.min(req, limit);
        Material mat = detectMaterial(lower);
        if (mat == null) { godSpeak(getGodPrefix() + "§7That which you seek exists not in this realm, " + player.getName() + "."); return; }
        if (form == GodForm.POWER_25 && isLuxuryItem(mat)) { godSpeak(getGodPrefix() + "§7At 25% power, such artifacts are beyond my grasp."); return; }
        int given = 0, ss = mat.getMaxStackSize();
        while (given < amount) { int t = Math.min(ss, amount - given); player.getInventory().addItem(new ItemStack(mat, t)); given += t; }
        spawnGodBlessing(player);
        godSpeak(getGodPrefix() + "§e*waves hand* " + amount + "x §6" + fmt(mat) + " §eis yours, " + player.getName() + "."
            + (req > limit ? " §7(Capped at " + limit + ".)" : ""));
    }

    private Material detectMaterial(String l) {
        if (l.contains("diamond block"))   return Material.DIAMOND_BLOCK;
        if (l.contains("diamond"))         return Material.DIAMOND;
        if (l.contains("netherite block")) return Material.NETHERITE_BLOCK;
        if (l.contains("netherite ingot") || l.contains("netherite")) return Material.NETHERITE_INGOT;
        if (l.contains("ancient debris"))  return Material.ANCIENT_DEBRIS;
        if (l.contains("emerald block"))   return Material.EMERALD_BLOCK;
        if (l.contains("emerald"))         return Material.EMERALD;
        if (l.contains("gold block"))      return Material.GOLD_BLOCK;
        if (l.contains("gold ingot") || l.contains("gold")) return Material.GOLD_INGOT;
        if (l.contains("iron block"))      return Material.IRON_BLOCK;
        if (l.contains("iron ingot") || l.contains("iron")) return Material.IRON_INGOT;
        if (l.contains("obsidian"))        return Material.OBSIDIAN;
        if (l.contains("elytra"))          return Material.ELYTRA;
        if (l.contains("totem"))           return Material.TOTEM_OF_UNDYING;
        if (l.contains("beacon"))          return Material.BEACON;
        if (l.contains("nether star"))     return Material.NETHER_STAR;
        if (l.contains("shulker"))         return Material.SHULKER_BOX;
        if (l.contains("enchanted golden apple") || l.contains("god apple")) return Material.ENCHANTED_GOLDEN_APPLE;
        if (l.contains("golden apple"))    return Material.GOLDEN_APPLE;
        if (l.contains("end crystal"))     return Material.END_CRYSTAL;
        if (l.contains("dragon egg"))      return Material.DRAGON_EGG;
        if (l.contains("heart of the sea")) return Material.HEART_OF_THE_SEA;
        if (l.contains("steak") || l.contains("cooked beef")) return Material.COOKED_BEEF;
        if (l.contains("bread") || l.contains("food")) return Material.BREAD;
        if (l.contains("apple"))           return Material.APPLE;
        return null;
    }

    private boolean isLuxuryItem(Material m) {
        return switch (m) {
            case NETHERITE_INGOT, NETHERITE_BLOCK, ANCIENT_DEBRIS,
                 ELYTRA, TOTEM_OF_UNDYING, BEACON, NETHER_STAR,
                 ENCHANTED_GOLDEN_APPLE, DRAGON_EGG, END_CRYSTAL,
                 HEART_OF_THE_SEA -> true;
            default -> false;
        };
    }

    private int extractNumber(String msg) {
        for (String w : msg.split("\\s+")) {
            try { int n = Integer.parseInt(w.replaceAll("[^0-9]", "")); if (n > 0) return n; }
            catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private String fmt(Material mat) {
        String r = mat.name().replace("_", " ").toLowerCase();
        return Character.toUpperCase(r.charAt(0)) + r.substring(1);
    }

    // ── Broadcast / Speak ─────────────────────────────────────────────────────

    private void broadcastSpawn() {
        switch (form) {
            case GUIDE -> {
                plugin.getServer().broadcastMessage("");
                plugin.getServer().broadcastMessage("§e§l✦ ═══════════════════════════════ ✦");
                plugin.getServer().broadcastMessage("§6§l          THE DIVINE APPEARS          ");
                plugin.getServer().broadcastMessage("§7      The God has descended to guide you...");
                plugin.getServer().broadcastMessage("§e§l✦ ═══════════════════════════════ ✦");
                plugin.getServer().broadcastMessage("");
            }
            case POWER_25 -> {
                plugin.getServer().broadcastMessage("");
                plugin.getServer().broadcastMessage("§e§l✦ ══════════════════════════════════ ✦");
                plugin.getServer().broadcastMessage("§6§l       GOD HAS BEEN SUMMONED [25%]       ");
                plugin.getServer().broadcastMessage("§7   The Divine awakens at a quarter of power...");
                plugin.getServer().broadcastMessage("§e   You have §a15 minutes §eto speak with God.");
                plugin.getServer().broadcastMessage("§e§l✦ ══════════════════════════════════ ✦");
                plugin.getServer().broadcastMessage("");
            }
            case POWER_50 -> {
                plugin.getServer().broadcastMessage("");
                plugin.getServer().broadcastMessage("§c§l✦ ══════════════════════════════════ ✦");
                plugin.getServer().broadcastMessage("§6§l       GOD HAS BEEN SUMMONED [50%]       ");
                plugin.getServer().broadcastMessage("§c   The air trembles. God grows stronger...");
                plugin.getServer().broadcastMessage("§e   You have §a15 minutes §eto speak with God.");
                plugin.getServer().broadcastMessage("§c§l✦ ══════════════════════════════════ ✦");
                plugin.getServer().broadcastMessage("");
            }
            case POWER_75 -> {
                plugin.getServer().broadcastMessage("");
                plugin.getServer().broadcastMessage("§4§l✦ ═══════════════════════════════════════ ✦");
                plugin.getServer().broadcastMessage("§6§k§lXX§r §4§l    !! GOD — FINAL SUMMON [75%] !!    §6§k§lXX");
                plugin.getServer().broadcastMessage("§4   THE DIVINE REACHES 75% POWER. ALL SHALL TREMBLE.");
                plugin.getServer().broadcastMessage("§e   You have §a15 minutes §ewith God. Final audience.");
                plugin.getServer().broadcastMessage("§4§l✦ ═══════════════════════════════════════ ✦");
                plugin.getServer().broadcastMessage("");
            }
        }
        // Delayed greeting (always AI or static based on form)
        new BukkitRunnable() { public void run() {
            if (!active) return;
            String greeting = switch (form) {
                case GUIDE    -> "§e*manifests in golden light* I am here, mortals. Seek me.";
                case POWER_25 -> "§e*manifests in a divine glow* I am here, mortal. 25% of my power. You have 15 minutes.";
                case POWER_50 -> "§c*the ground shakes* I return at 50% of my true might. Speak — what do you seek?";
                case POWER_75 -> "§4*THE HEAVENS CRACK OPEN* 75% — I am nearly whole. Choose your words carefully.";
            };
            godSpeak(getGodPrefix() + greeting);
        }}.runTaskLater(plugin, 60L);
    }

    public void godSpeak(String message) {
        plugin.getServer().broadcastMessage(message);
        if (godNPCEntity != null && !godNPCEntity.isDead())
            godNPCEntity.getWorld().playSound(godNPCEntity.getLocation(), Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.4f, 1.8f);
    }

    private void spawnGodSpeakParticles() {
        if (godNPCEntity == null || godNPCEntity.isDead()) return;
        Location loc = godNPCEntity.getLocation().add(0, 2, 0);
        loc.getWorld().spawnParticle(Particle.END_ROD,  loc, 30, 0.5, 0.3, 0.5, 0.08);
        loc.getWorld().spawnParticle(Particle.FIREWORK, loc, 15, 0.4, 0.2, 0.4, 0.06);
    }

    private void spawnGodBlessing(Player player) {
        player.getWorld().spawnParticle(Particle.END_ROD,  player.getLocation().add(0,1,0), 80, 0.5, 0.5, 0.5, 0.1);
        player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0,1,0), 40, 0.4, 0.4, 0.4, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.5f);
    }

    private String getGodTitle() {
        return switch (form) {
            case GUIDE    -> "§e✦ §6§lThe Divine §e✦ §7[Guide]";
            case POWER_25 -> "§e✦ §6§lGod §e✦ §7[25% Power]";
            case POWER_50 -> "§e✦ §6§lGod §e✦ §c[50% Power]";
            case POWER_75 -> "§e✦ §6§l§kX§r §6§lGOD §e✦ §4[75% Power] §6§k§lX";
        };
    }

    private String getGodPrefix() {
        return switch (form) {
            case GUIDE    -> "§e✦ The Divine §7[Guide]: ";
            case POWER_25 -> "§6✦ God §7[25%]: ";
            case POWER_50 -> "§c✦ God §c[50%]: ";
            case POWER_75 -> "§4✦ §6§lGOD §4[75%]: ";
        };
    }

    private String getGodName() {
        return switch (form) {
            case GUIDE -> "The Divine Guide";
            case POWER_25 -> "The Awakened God";
            case POWER_50 -> "The Empowered God";
            case POWER_75 -> "THE ALMIGHTY";
        };
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean isActive()       { return active; }
    public GodForm getForm()        { return form; }
    public UUID getSummoner()       { return summoner; }
    public Entity getGodNPCEntity() { return godNPCEntity; }
}
