package com.heartssmp.skills.divine;

import com.heartssmp.HeartsSMPPlugin;
import com.heartssmp.skills.Skill;
import com.heartssmp.skills.SkillRarity;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * GracefulEnlightenment — Divine-tier skill, unlocked only after completing
 * all 12 Castles of God in the Divine Trial.
 *
 * Item: God's Trident (GOLDEN_HOE base, CustomModelData 30004)
 * Player MUST hold the God's Trident in main hand to use any ability.
 *
 * Abilities:
 *   Mastery 1 — HOLY LIGHT AURA
 *       Passive: Regeneration II + Absorption II while holding trident.
 *       Active (Right-click): RADIANT BURST — emit a shockwave of holy light
 *       that damages and blinds all enemies in 10-block radius. 15s cooldown.
 *
 *   Mastery 2 — DIVINE LANCE
 *       Active (Right-click, sneak): Hurl a lance of pure light as a multi-
 *       stage projectile. Phase 1: fires a slow snowball. Phase 2: on impact,
 *       spawn 5 smaller light shards in a starburst. 20s cooldown.
 *       Enhanced passive: Regeneration III.
 *
 *   Mastery 3 — ASCENSION
 *       Active (Right-click, shift+jump): The player ascends in a pillar of
 *       divine fire, becomes temporarily invincible (5s), then crashes down
 *       in a holy explosion that sends shockwaves outward. 60s cooldown.
 *       Passive: Full suite — Regen III + Speed II + Absorption III.
 */
public class GracefulEnlightenment extends Skill {

    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long BURST_CD_MS   = 15_000L;
    private static final long LANCE_CD_MS   = 20_000L;
    private static final long ASCEND_CD_MS  = 60_000L;

    public GracefulEnlightenment() {
        super("graceful_enlightenment", "GracefulEnlightenment", SkillRarity.DIVINE,
              "The weapon of the Divine. Hold God's Trident to channel its power.");
    }

    // ── Passive ───────────────────────────────────────────────────────────────

    @Override
    public void onPassiveTick(Player player, int mastery) {
        // Only active while holding the God's Trident
        if (!isHoldingTrident(player)) return;

        switch (mastery) {
            case 1 -> {
                applyEffect(player, PotionEffectType.REGENERATION, 1);
                applyEffect(player, PotionEffectType.ABSORPTION,   1);
            }
            case 2 -> {
                applyEffect(player, PotionEffectType.REGENERATION, 2);
                applyEffect(player, PotionEffectType.ABSORPTION,   2);
            }
            case 3 -> {
                applyEffect(player, PotionEffectType.REGENERATION, 2);
                applyEffect(player, PotionEffectType.ABSORPTION,   2);
                applyEffect(player, PotionEffectType.SPEED,        1);
                // Ambient divine glow particles every other tick
                if (System.currentTimeMillis() % 2000 < 100) {
                    player.getWorld().spawnParticle(Particle.END_ROD,
                            player.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0.02);
                }
            }
        }
    }

    // ── Active — right-click dispatch ─────────────────────────────────────────

    @Override
    public void onRightClick(Player player, int mastery, HeartsSMPPlugin plugin) {
        if (!isHoldingTrident(player)) {
            player.sendMessage("§6✦ §7You must hold the §6God's Trident §7to use this ability.");
            return;
        }
        boolean sneaking = player.isSneaking();

        switch (mastery) {
            case 1 -> activateRadiantBurst(player, plugin);
            case 2 -> {
                if (sneaking) activateDivineLance(player, plugin);
                else          activateRadiantBurst(player, plugin);
            }
            case 3 -> {
                if (sneaking) activateAscension(player, plugin);
                else          activateDivineLance(player, plugin);
            }
        }
    }

    // ── Ability: Radiant Burst ────────────────────────────────────────────────

    private void activateRadiantBurst(Player player, HeartsSMPPlugin plugin) {
        if (!checkCooldown(player, "burst", BURST_CD_MS)) return;

        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1, 0);

        player.sendMessage("§6✦ §e§lRADIANT BURST!");
        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.8f);
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE,         1.5f, 1.5f);

        // Shockwave visual — expanding ring of END_ROD particles
        new BukkitRunnable() {
            double radius = 0.5;
            @Override public void run() {
                if (radius > 10) { cancel(); return; }
                int points = (int)(radius * 12);
                for (int i = 0; i < points; i++) {
                    double angle = (2 * Math.PI / points) * i;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    world.spawnParticle(Particle.END_ROD,   center.clone().add(x, 0, z), 1, 0, 0, 0, 0.01);
                    world.spawnParticle(Particle.FIREWORK,  center.clone().add(x, 0.3, z), 1, 0, 0, 0, 0.01);
                }
                radius += 0.6;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Damage + blind enemies in radius
        new BukkitRunnable() {
            @Override public void run() {
                for (Entity e : world.getNearbyEntities(center, 10, 5, 10)) {
                    if (e instanceof LivingEntity target && !e.equals(player)) {
                        target.damage(8.0, player);
                        if (target instanceof Player tp) {
                            tp.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                            tp.sendMessage("§6✦ §7You were blinded by a Radiant Burst!");
                        }
                        // Knock back
                        Vector kb = target.getLocation().toVector()
                                .subtract(center.toVector()).normalize().multiply(1.5);
                        kb.setY(0.5);
                        target.setVelocity(kb);
                    }
                }
            }
        }.runTaskLater(plugin, 8L);
    }

    // ── Ability: Divine Lance ─────────────────────────────────────────────────

    private void activateDivineLance(Player player, HeartsSMPPlugin plugin) {
        if (!checkCooldown(player, "lance", LANCE_CD_MS)) return;

        World world = player.getWorld();
        Location eye = player.getEyeLocation();

        player.sendMessage("§6✦ §e§lDIVINE LANCE!");
        world.playSound(eye, Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1.8f);
        world.playSound(eye, Sound.BLOCK_BEACON_POWER_SELECT,  1f, 1.2f);

        // Phase 1: slow snowball projectile with trailing particles
        Snowball lance = player.launchProjectile(Snowball.class);
        lance.setVelocity(eye.getDirection().multiply(2.5));
        lance.setShooter(player);

        new BukkitRunnable() {
            @Override public void run() {
                if (lance.isDead() || !lance.isValid()) { cancel(); return; }
                // Trailing holy light trail
                world.spawnParticle(Particle.END_ROD, lance.getLocation(), 4, 0.1, 0.1, 0.1, 0.02);
                world.spawnParticle(Particle.FIREWORK, lance.getLocation(), 3, 0.1, 0.1, 0.1, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Phase 2: on impact, detected via ProjectileHitEvent in listener
        // We store the UUID so SkillAbilityListener can handle the explosion
        plugin.getSkillManager().trackDivineLance(lance.getEntityId(), player.getUniqueId());
    }

    /**
     * Called by SkillAbilityListener when a tracked Divine Lance snowball hits.
     */
    public static void onDivineLanceImpact(Location impact, Player shooter, HeartsSMPPlugin plugin) {
        World world = impact.getWorld();
        if (world == null) return;

        world.playSound(impact, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.6f);
        world.spawnParticle(Particle.FIREWORK, impact, 60, 0.5, 0.5, 0.5, 0.2);
        world.spawnParticle(Particle.END_ROD,  impact, 80, 0.4, 0.4, 0.4, 0.15);

        // 5 light shards burst outward in a star pattern
        double[] angles = {0, 72, 144, 216, 288};
        for (double angle : angles) {
            double rad = Math.toRadians(angle);
            Vector dir = new Vector(Math.cos(rad), 0.3, Math.sin(rad)).normalize().multiply(1.8);
            Snowball shard = shooter.launchProjectile(Snowball.class);
            shard.teleport(impact);
            shard.setVelocity(dir);
            shard.setShooter(shooter);

            // Particle trail for each shard
            new BukkitRunnable() {
                @Override public void run() {
                    if (shard.isDead() || !shard.isValid()) { cancel(); return; }
                    world.spawnParticle(Particle.END_ROD, shard.getLocation(), 2, 0, 0, 0, 0.01);
                }
            }.runTaskTimer(plugin, 0L, 1L);

            // Remove shards after 2s
            new BukkitRunnable() { public void run() { if (!shard.isDead()) shard.remove(); }
            }.runTaskLater(plugin, 40L);
        }

        // Damage area
        for (Entity e : world.getNearbyEntities(impact, 5, 5, 5)) {
            if (e instanceof LivingEntity target && !e.equals(shooter)) {
                target.damage(12.0, shooter);
            }
        }
    }

    // ── Ability: Ascension ────────────────────────────────────────────────────

    private void activateAscension(Player player, HeartsSMPPlugin plugin) {
        if (!checkCooldown(player, "ascend", ASCEND_CD_MS)) return;

        World world = player.getWorld();
        player.sendMessage("§6✦ §e§l§kX§r §6§l✦ ASCENSION ✦ §e§k§lX§r");
        plugin.getServer().broadcastMessage("§6§l✦ " + player.getName() + " §ecalls upon divine ASCENSION! §6§l✦");

        // Phase 1: Launch player upward in pillar of divine fire
        player.setVelocity(new Vector(0, 2.5, 0));
        player.setInvulnerable(true);

        world.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 1.8f);
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE,     2f, 0.8f);

        // Pillar particles rising from ground
        new BukkitRunnable() {
            int t = 0;
            final Location base = player.getLocation().clone();
            @Override public void run() {
                if (t++ > 30) { cancel(); return; }
                for (double dy = 0; dy < 20; dy += 0.5) {
                    double spread = dy * 0.08;
                    world.spawnParticle(Particle.FLAME,   base.clone().add(0, dy, 0), 1, spread, 0.05, spread, 0.01);
                    world.spawnParticle(Particle.END_ROD, base.clone().add(0, dy, 0), 1, spread, 0.05, spread, 0.01);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Phase 2: 5s of divine invincibility at peak
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }
                t++;
                // Halo ring effect
                Location loc = player.getLocation().add(0, 0.5, 0);
                for (int i = 0; i < 12; i++) {
                    double a = (2*Math.PI/12)*i + t*0.2;
                    world.spawnParticle(Particle.END_ROD,
                        loc.clone().add(Math.cos(a)*2, 0, Math.sin(a)*2), 1, 0, 0, 0, 0.01);
                }
                world.spawnParticle(Particle.FIREWORK, loc, 3, 0.3, 0.3, 0.3, 0.05);

                if (t >= 100) { // 5s
                    cancel();
                    triggerAscensionSlam(player, plugin);
                }
            }
        }.runTaskTimer(plugin, 10L, 1L);
    }

    private void triggerAscensionSlam(Player player, HeartsSMPPlugin plugin) {
        if (!player.isOnline()) return;
        World world = player.getWorld();

        player.sendMessage("§6✦ §c§lSLAM!");
        world.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.5f);

        // Slam player downward
        player.setVelocity(new Vector(0, -4.0, 0));

        // Wait for landing then create shockwave
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!player.isOnline() || t++ > 60) { player.setInvulnerable(false); cancel(); return; }
                // Detect near-ground
                if (player.isOnGround() || t > 40) {
                    cancel();
                    Location impact = player.getLocation();
                    player.setInvulnerable(false);

                    // Holy explosion shockwave
                    world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
                    world.playSound(impact, Sound.BLOCK_BEACON_ACTIVATE,   2f, 1.5f);

                    // Expanding shockwave rings
                    new BukkitRunnable() {
                        double r = 0;
                        @Override public void run() {
                            if (r > 20) { cancel(); return; }
                            int pts = (int)(r * 16);
                            for (int i = 0; i < pts; i++) {
                                double a = (2*Math.PI/pts)*i;
                                Location p = impact.clone().add(Math.cos(a)*r, 0.1, Math.sin(a)*r);
                                world.spawnParticle(Particle.END_ROD,  p, 1, 0, 0.1, 0, 0.01);
                                world.spawnParticle(Particle.FIREWORK,  p, 1, 0, 0.1, 0, 0.01);
                                world.spawnParticle(Particle.FLAME,     p, 1, 0, 0.1, 0, 0.01);
                            }
                            r += 0.8;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);

                    // Massive damage in 15-block radius
                    for (Entity e : world.getNearbyEntities(impact, 15, 8, 15)) {
                        if (e instanceof LivingEntity target && !e.equals(player)) {
                            double dist = target.getLocation().distance(impact);
                            double dmg  = Math.max(5, 25 - dist);
                            target.damage(dmg, player);
                            Vector kb = target.getLocation().toVector()
                                    .subtract(impact.toVector()).normalize().multiply(3.5);
                            kb.setY(1.2);
                            target.setVelocity(kb);
                        }
                    }

                    // Grant player status after slam
                    player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20*10, 2));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,   20*15, 3));
                    player.sendMessage("§6✦ §eAscension complete. The earth trembles at your might.");
                    plugin.getServer().broadcastMessage("§6§l✦ §c" + player.getName()
                            + "'s ASCENSION SLAM shook the earth! §6§l✦");
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isHoldingTrident(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) return false;
        ItemMeta meta = hand.getItemMeta();
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == 30004;
    }

    private void applyEffect(Player player, PotionEffectType type, int amp) {
        if (!player.hasPotionEffect(type)
                || Objects.requireNonNull(player.getPotionEffect(type)).getAmplifier() < amp) {
            player.addPotionEffect(new PotionEffect(type, 80, amp, true, false));
        }
    }

    /** Returns true if cooldown cleared; sends message and returns false if on cd. */
    private boolean checkCooldown(Player player, String ability, long cdMs) {
        UUID uuid = player.getUniqueId();
        String key = uuid + "_" + ability;
        long now  = System.currentTimeMillis();
        Long last = cooldowns.get(key);
        if (last != null && (now - last) < cdMs) {
            long rem = (cdMs - (now - last)) / 1000;
            player.sendMessage("§6✦ §7" + ability + " cooldown: §e" + rem + "s");
            return false;
        }
        cooldowns.put(key, now);
        return true;
    }

    @Override
    public String getSkillDescription(int mastery) {
        return switch (mastery) {
            case 1 -> "§eHoly Light Aura (passive) + §6Radiant Burst §e(right-click, 15s cd)";
            case 2 -> "§eEnhanced Aura + §6Divine Lance §e(sneak+right-click, 20s cd)";
            case 3 -> "§eMax Aura + §6Ascension Slam §e(sneak+right-click, 60s cd)";
            default -> "Unlock via Divine Trial";
        };
    }
}
