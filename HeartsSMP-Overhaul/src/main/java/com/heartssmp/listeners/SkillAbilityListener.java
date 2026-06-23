package com.heartssmp.listeners;

import com.heartssmp.HeartsSMPPlugin;
import com.heartssmp.data.PlayerData;
import com.heartssmp.skills.divine.GracefulEnlightenment;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * SkillAbilityListener — routes right-click/interact events to the correct
 * skill ability based on the item held in the player's main hand.
 *
 * Holds-in-hand enforcement:
 *   Players must be holding the specific skill item (identified by CustomModelData)
 *   to trigger any ability. Passives similarly only apply while holding the item.
 */
public class SkillAbilityListener implements Listener {

    private final HeartsSMPPlugin plugin;
    // divine_lance tracking: projectile entityId → shooter UUID
    private final Map<Integer, UUID> divineLanceProjectiles = new HashMap<>();

    public SkillAbilityListener(HeartsSMPPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ── Right-click routing ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || !hand.hasItemMeta()) return;
        ItemMeta meta = hand.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return;

        int cmd = meta.getCustomModelData();
        PlayerData data = plugin.getDataManager().get(player.getUniqueId());
        if (data == null) return;

        switch (cmd) {
            // God's Trident — GracefulEnlightenment
            case 30004 -> {
                int mastery = data.getSkillMastery("graceful_enlightenment");
                if (mastery <= 0) {
                    player.sendMessage("§c✦ You have not unlocked GracefulEnlightenment.");
                    return;
                }
                GracefulEnlightenment ge = (GracefulEnlightenment)
                        plugin.getSkillManager().getSkill("graceful_enlightenment");
                if (ge != null) ge.onRightClick(player, mastery, plugin);
            }
            // Aurora Staff — healing beam (separate from gem)
            case 30006 -> activateAuroraStaff(player, data);
            // Storm Staff
            case 30016 -> activateStormStaff(player, data);
            // Titan Hammer
            case 30003 -> activateTitanHammer(player, data);
            // Chrono Watch — freeze enemies
            case 30020 -> activateChronoWatch(player, data);
        }
    }

    // ── Staff / weapon abilities ──────────────────────────────────────────────

    private void activateAuroraStaff(Player player, PlayerData data) {
        if (!checkCooldown(player, "aurora_staff", 18_000)) return;
        World world = player.getWorld();
        player.sendMessage("§e✦ Aurora Staff — Healing Beam!");
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.5f);

        for (Entity e : world.getNearbyEntities(player.getLocation(), 10, 8, 10)) {
            if (e instanceof Player ally) {
                double heal = Math.min(ally.getMaxHealth(), ally.getHealth() + 6);
                ally.setHealth(heal);
                ally.sendMessage("§e✦ Healed by §6" + player.getName() + "§e's Aurora Staff!");
                world.spawnParticle(Particle.END_ROD, ally.getLocation().add(0,1,0), 30, 0.3, 0.5, 0.3, 0.05);
            }
        }
        world.spawnParticle(Particle.FIREWORK, player.getLocation().add(0,1,0), 80, 1, 1, 1, 0.15);
    }

    private void activateStormStaff(Player player, PlayerData data) {
        if (!checkCooldown(player, "storm_staff", 12_000)) return;
        player.sendMessage("§9⚡ Storm Staff — Lightning Barrage!");
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1f);

        // 5 lightning strikes at random positions around target entity
        Entity target = getTargetEntity(player, 20);
        Location base = target != null ? target.getLocation() : player.getLocation().add(
                player.getEyeLocation().getDirection().multiply(15));

        for (int i = 0; i < 5; i++) {
            final int fi = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                double rx = (Math.random()-0.5)*6;
                double rz = (Math.random()-0.5)*6;
                world.strikeLightning(base.clone().add(rx, 0, rz));
            }, (long)(fi * 5));
        }
    }

    private void activateTitanHammer(Player player, PlayerData data) {
        if (!checkCooldown(player, "titan_hammer", 20_000)) return;
        player.sendMessage("§8🔨 §7Titan Hammer — Ground Slam!");
        World world = player.getWorld();
        Location ground = player.getLocation();
        world.playSound(ground, Sound.ENTITY_IRON_GOLEM_ATTACK, 2f, 0.5f);

        // Shockwave ring of particles + damage
        new org.bukkit.scheduler.BukkitRunnable() {
            double r = 0;
            @Override public void run() {
                if (r > 12) { cancel(); return; }
                int pts = Math.max(8, (int)(r * 8));
                for (int i = 0; i < pts; i++) {
                    double a = (2*Math.PI/pts)*i;
                    Location p = ground.clone().add(Math.cos(a)*r, 0.1, Math.sin(a)*r);
                    world.spawnParticle(Particle.BLOCK, p, 3, 0.1, 0.1, 0.1, 0.05, Material.COBBLESTONE.createBlockData());
                    world.spawnParticle(Particle.POOF,  p, 2, 0, 0, 0, 0.05);
                }
                if (r == 2 || r == 5 || r == 8) {
                    for (Entity e : world.getNearbyEntities(ground, r+0.5, 3, r+0.5)) {
                        if (e instanceof LivingEntity le && !e.equals(player)) {
                            le.damage(8 - r*0.4, player);
                            org.bukkit.util.Vector kb = le.getLocation().toVector()
                                    .subtract(ground.toVector()).normalize().multiply(2.5);
                            kb.setY(0.8);
                            le.setVelocity(kb);
                        }
                    }
                }
                r += 0.5;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void activateChronoWatch(Player player, PlayerData data) {
        if (!checkCooldown(player, "chrono_watch", 30_000)) return;
        player.sendMessage("§6⏱ Chrono Watch — Time Freeze!");
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 0.5f);

        List<LivingEntity> frozen = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(player.getLocation(), 8, 8, 8)) {
            if (e instanceof LivingEntity le && !e.equals(player)) {
                frozen.add(le);
                le.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW, 20*3, 9, false, false));
                le.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 20*3, 0, false, false));
                world.spawnParticle(Particle.SNOWFLAKE, le.getLocation().add(0,1,0), 30, 0.5, 0.5, 0.5, 0.05);
            }
        }
        player.sendMessage("§6⏱ §7" + frozen.size() + " entities frozen for 3 seconds.");
    }

    // ── Passive tick dispatcher — called by PassiveTickTask ──────────────────

    public void dispatchPassiveTick(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || !hand.hasItemMeta()) return;
        ItemMeta meta = hand.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return;

        PlayerData data = plugin.getDataManager().get(player.getUniqueId());
        if (data == null) return;

        int cmd = meta.getCustomModelData();
        // Dispatch to correct skill passive
        if (cmd == 30004) {
            GracefulEnlightenment ge = (GracefulEnlightenment)
                    plugin.getSkillManager().getSkill("graceful_enlightenment");
            if (ge != null) {
                int mastery = data.getSkillMastery("graceful_enlightenment");
                ge.onPassiveTick(player, mastery);
            }
        }
    }

    // ── Divine Lance impact tracking ──────────────────────────────────────────

    public void trackDivineLance(int projectileId, UUID shooterUUID) {
        divineLanceProjectiles.put(projectileId, shooterUUID);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile proj = event.getEntity();
        UUID shooterUUID = divineLanceProjectiles.remove(proj.getEntityId());
        if (shooterUUID == null) return;

        Player shooter = Bukkit.getPlayer(shooterUUID);
        if (shooter == null) return;

        Location impact = proj.getLocation();
        GracefulEnlightenment.onDivineLanceImpact(impact, shooter, plugin);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Entity getTargetEntity(Player player, double range) {
        for (Entity e : player.getNearbyEntities(range, range, range)) {
            if (e instanceof LivingEntity && !e.equals(player)) return e;
        }
        return null;
    }

    private static final Map<String, Long> COOLDOWNS = new HashMap<>();

    private boolean checkCooldown(Player player, String id, long ms) {
        String key = player.getUniqueId() + "_skill_" + id;
        long now = System.currentTimeMillis();
        Long last = COOLDOWNS.get(key);
        if (last != null && (now - last) < ms) {
            player.sendMessage("§7[" + id + "] on cooldown: " + ((ms-(now-last))/1000) + "s");
            return false;
        }
        COOLDOWNS.put(key, now);
        return true;
    }
}
