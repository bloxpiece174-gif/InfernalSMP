package com.heartssmp.listeners;

import com.heartssmp.HeartsSMPPlugin;
import com.heartssmp.data.PlayerData;
import com.heartssmp.gems.GemManager;
import com.heartssmp.gems.common.StoneGem;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * GemAbilityListener — routes player interactions to the correct gem ability.
 *
 * Key enforcement:
 *   • Player MUST be holding the gem item in main hand to trigger active abilities.
 *   • Stone Gem special: Left-Attack while liftedEarth is active → Throw;
 *                        Sneak key while liftedEarth is active → Barrier.
 */
public class GemAbilityListener implements Listener {

    private final HeartsSMPPlugin plugin;
    private final GemManager gemManager;

    public GemAbilityListener(HeartsSMPPlugin plugin) {
        this.plugin     = plugin;
        this.gemManager = plugin.getGemManager();
    }

    /** Right-click to trigger gem active abilities */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        String gemId = gemManager.getGemIdFromItem(hand);
        if (gemId == null) return;

        PlayerData data = plugin.getDataManager().get(player.getUniqueId());
        if (data == null) return;
        int mastery = data.getGemMastery(gemId);

        // Route to gem-specific active ability
        switch (gemId) {
            case "COMMON_STONE" -> {
                // Check if player already has lifted earth — right-click with earth active does nothing
                if (StoneGem.hasLiftedEarth(player.getUniqueId())) {
                    player.sendMessage("§8⚫ §7Earth mass active. §8Left-click§7 to throw or §8Sneak§7 to form a barrier.");
                } else {
                    StoneGem.activateGroundLift(player, mastery, plugin);
                    if (mastery >= 3 && player.isSneaking()) {
                        StoneGem.activateStoneGiant(player, mastery, plugin);
                    }
                }
            }
            case "COMMON_EMBER" -> activateEmberGem(player, mastery);
            case "COMMON_TIDE"  -> activateTideGem(player, mastery);
            case "UNCOMMON_GALE" -> activateGaleGem(player, mastery);
            case "EPIC_SHADOW"   -> activateShadowGem(player, mastery);
            case "LEGENDARY_AURORA" -> activateAuroraGem(player, mastery);
            case "MYTHICAL_VOID"    -> activateVoidGem(player, mastery);
            case "DIVINE_CELESTIA"  -> activateCelestiaGem(player, mastery);
        }
    }

    /** Attack (left-click entity) while stone lift is active → throw */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerAttack(EntityDamageByEntityDamager event) {
        // Custom event — handled via EntityDamageByEntityEvent below
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!StoneGem.hasLiftedEarth(player.getUniqueId())) return;
        StoneGem.throwEarthMass(player, plugin);
    }

    /** Sneak toggle while stone lift is active → barrier */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!event.isSneaking()) return;
        if (!StoneGem.hasLiftedEarth(player.getUniqueId())) return;
        StoneGem.formBarrier(player, plugin);
    }

    /** Guardian kill tracking for Trial 3 */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        // Divine guardian
        if (entity.getScoreboardTags().stream()
                .anyMatch(t -> t.startsWith("divine_guardian_" + killer.getUniqueId()))) {
            plugin.getDivineTrialManager().onDivineGuardianKill(killer);
        }
        // Divine boss
        if (entity.getScoreboardTags().stream()
                .anyMatch(t -> t.startsWith("divine_boss_" + killer.getUniqueId()))) {
            plugin.getDivineTrialManager().completeTrial(killer, 7);
        }
        // Kill streak trial
        PlayerData data = plugin.getDataManager().get(killer.getUniqueId());
        if (data != null) {
            plugin.getDivineTrialManager().onMobKill(killer);
        }
    }

    // ── Gem ability implementations ───────────────────────────────────────────

    private void activateEmberGem(Player player, int mastery) {
        if (!checkCooldown(player, "ember", 10_000)) return;
        World world = player.getWorld();
        player.sendMessage("§c🔥 Ember Burst!");
        world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.2f);

        // Multi-stage fireball burst based on mastery
        int count = mastery == 1 ? 1 : mastery == 2 ? 3 : 6;
        for (int i = 0; i < count; i++) {
            final int fi = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                double spread = fi * 0.15;
                org.bukkit.util.Vector dir = player.getEyeLocation().getDirection()
                        .add(new org.bukkit.util.Vector((Math.random()-0.5)*spread,
                             (Math.random()-0.5)*spread*0.3, (Math.random()-0.5)*spread));
                SmallFireball ball = player.getWorld().spawn(player.getEyeLocation(), SmallFireball.class, fb -> {
                    fb.setDirection(dir);
                    fb.setShooter(player);
                    fb.setYield(1.5f);
                    fb.setIsIncendiary(true);
                });
                // Particle trail
                plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
                    if (ball.isDead()) { task.cancel(); return; }
                    world.spawnParticle(Particle.FLAME,    ball.getLocation(), 3, 0.1, 0.1, 0.1, 0.02);
                    world.spawnParticle(Particle.FIREWORK, ball.getLocation(), 2, 0.1, 0.1, 0.1, 0.01);
                }, 0L, 1L);
            }, (long)(fi * 3));
        }
        if (mastery >= 2)
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE, 20*15, 0, false, true));
    }

    private void activateTideGem(Player player, int mastery) {
        if (!checkCooldown(player, "tide", 12_000)) return;
        World world = player.getWorld();
        player.sendMessage("§9🌊 Tide Surge!");
        world.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1.2f);

        // Water rush: launch a wave of water entities / particles in player's facing direction
        org.bukkit.util.Vector dir = player.getEyeLocation().getDirection().multiply(2.5);
        int range = mastery == 1 ? 10 : mastery == 2 ? 20 : 30;
        Location start = player.getLocation();
        for (int dist = 1; dist <= range; dist++) {
            final int d = dist;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Location point = start.clone().add(dir.clone().normalize().multiply(d));
                world.spawnParticle(Particle.DRIPPING_WATER, point, 30, 0.5, 0.5, 0.5, 0.2);
                world.spawnParticle(Particle.SPLASH,         point, 20, 0.5, 0.5, 0.5, 0.3);
                // Knockback entities at each wave point
                for (Entity e : world.getNearbyEntities(point, 2, 2, 2)) {
                    if (e instanceof LivingEntity le && !e.equals(player)) {
                        le.damage(mastery * 2.0, player);
                        le.setVelocity(dir.clone().normalize().multiply(1.5).add(new org.bukkit.util.Vector(0, 0.5, 0)));
                    }
                }
            }, (long)(dist * 2));
        }
        if (mastery >= 3)
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.DOLPHINS_GRACE, 20*20, 0, false, true));
    }

    private void activateGaleGem(Player player, int mastery) {
        if (!checkCooldown(player, "gale", 8_000)) return;
        World world = player.getWorld();
        player.sendMessage("§b💨 Gale Launch!");
        world.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1.5f);

        org.bukkit.util.Vector launch = player.getEyeLocation().getDirection().multiply(2.5 + mastery);
        launch.setY(launch.getY() + 0.5);
        player.setVelocity(launch);
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING, 40, 0, false, true));

        // Wind burst particles
        for (int i = 0; i < 60; i++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                world.spawnParticle(Particle.CLOUD, player.getLocation(), 5, 0.5, 0.3, 0.5, 0.1);
                world.spawnParticle(Particle.POOF,  player.getLocation(), 3, 0.3, 0.2, 0.3, 0.1);
            }, (long)(i * 2));
        }
    }

    private void activateShadowGem(Player player, int mastery) {
        if (!checkCooldown(player, "shadow", 15_000)) return;
        player.sendMessage("§8🌑 Shadow Veil!");
        int duration = mastery * 5;
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY, 20*duration, 0, false, false));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED,        20*duration, 1, false, false));
        if (mastery >= 2)
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION, 20*duration, 0, false, false));
        player.getWorld().spawnParticle(Particle.SQUID_INK, player.getLocation().add(0,1,0), 80, 0.5, 0.5, 0.5, 0.15);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);
    }

    private void activateAuroraGem(Player player, int mastery) {
        if (!checkCooldown(player, "aurora", 20_000)) return;
        player.sendMessage("§e✨ Aurora Surge!");
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2f, 1.3f);

        // Heal player + nearby allies
        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + mastery * 4));
        for (Entity e : world.getNearbyEntities(player.getLocation(), 10, 10, 10)) {
            if (e instanceof Player ally && !e.equals(player)) {
                ally.setHealth(Math.min(ally.getMaxHealth(), ally.getHealth() + mastery * 2));
                ally.sendMessage("§e✨ Blessed by " + player.getName() + "'s Aurora Surge!");
            }
        }

        // Spectacular aurora particle helix
        new org.bukkit.scheduler.BukkitRunnable() {
            double t = 0;
            @Override public void run() {
                if (t > 6*Math.PI) { cancel(); return; }
                double x = Math.cos(t) * 3;
                double z = Math.sin(t) * 3;
                double y = t / Math.PI;
                world.spawnParticle(Particle.END_ROD,  player.getLocation().add(x, y, z), 2, 0, 0, 0, 0.01);
                world.spawnParticle(Particle.FIREWORK,  player.getLocation().add(-x,y,-z), 2, 0, 0, 0, 0.01);
                t += 0.1;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void activateVoidGem(Player player, int mastery) {
        if (!checkCooldown(player, "void", 25_000)) return;
        player.sendMessage("§5🌌 Void Rift!");
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 1.5f, 0.7f);

        // Pull all nearby enemies toward player, then release
        Location center = player.getLocation().add(0, 1, 0);
        for (Entity e : world.getNearbyEntities(center, 15, 10, 15)) {
            if (e instanceof LivingEntity target && !e.equals(player)) {
                org.bukkit.util.Vector pull = center.toVector().subtract(target.getLocation().toVector()).normalize().multiply(3);
                target.setVelocity(pull);
                target.damage(mastery * 3.0, player);
            }
        }
        // Void implosion particles
        new org.bukkit.scheduler.BukkitRunnable() {
            double r = 15;
            @Override public void run() {
                if (r < 0.5) { cancel(); return; }
                for (int i = 0; i < 16; i++) {
                    double a = (2*Math.PI/16)*i;
                    world.spawnParticle(Particle.SQUID_INK, center.clone().add(Math.cos(a)*r, 0, Math.sin(a)*r), 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.ASH, center.clone().add(Math.cos(a)*r, 0, Math.sin(a)*r), 1, 0, 0, 0, 0);
                }
                r -= 0.8;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void activateCelestiaGem(Player player, int mastery) {
        if (!checkCooldown(player, "celestia", 30_000)) return;
        player.sendMessage("§e⭐ §6§lCELESTIA DIVINE BLESSING!");
        plugin.getServer().broadcastMessage("§e⭐ " + player.getName() + " channels the power of Celestia!");
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 2f, 1.5f);
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2f, 0.6f);

        // Massive buff suite
        player.setHealth(player.getMaxHealth());
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION,  20*30, 2, false, true));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH,      20*30, mastery, false, true));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED,         20*30, 1, false, true));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE,    20*30, 1, false, true));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION,    20*30, mastery+1, false, true));

        // Spectacular divine star burst
        for (int ring = 0; ring < 5; ring++) {
            final int r = ring;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (int i = 0; i < 36; i++) {
                    double a = (2*Math.PI/36)*i;
                    double rx = r * 1.5;
                    world.spawnParticle(Particle.END_ROD,  player.getLocation().add(Math.cos(a)*rx, r*0.4, Math.sin(a)*rx), 1, 0, 0, 0, 0.02);
                    world.spawnParticle(Particle.FIREWORK,  player.getLocation().add(Math.cos(a)*rx, r*0.4, Math.sin(a)*rx), 1, 0, 0, 0, 0.02);
                }
                world.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, (float)(1.0 + r*0.15));
            }, (long)(r * 8));
        }
    }

    // ── Cooldown utility ──────────────────────────────────────────────────────

    private static final Map<String, Long> COOLDOWNS = new HashMap<>();

    private boolean checkCooldown(Player player, String id, long ms) {
        String key = player.getUniqueId() + "_gem_" + id;
        long now  = System.currentTimeMillis();
        Long last = COOLDOWNS.get(key);
        if (last != null && (now - last) < ms) {
            player.sendMessage("§7[" + id + "] on cooldown: " + ((ms - (now-last))/1000) + "s");
            return false;
        }
        COOLDOWNS.put(key, now);
        return true;
    }

    // Required import shim for EntityDamageByEntityDamager (doesn't exist — handled above)
    private static class EntityDamageByEntityDamager {}
    private static final Map<String, Long> cooldownMap = new HashMap<>();
}
