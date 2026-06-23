package com.heartssmp.gems.common;

import com.heartssmp.gems.Gem;
import com.heartssmp.gems.GemRarity;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Stone Gem v2 — Full 3D Earth manipulation implementation.
 *
 * Passive: Resistance I (Mastery 3+: Resistance II) while holding the gem item.
 *
 * Active Abilities (must be holding the gem item in main hand):
 *   Mastery 1 — Earth Shield: 15% passive damage reduction aura
 *   Mastery 2 — GROUND LIFT: Lift 16x16 earth layer as packet-based falling block entities.
 *               After lift, player chooses:
 *                 • SNEAK = form Protective Barrier (wall of stone around player)
 *                 • ATTACK = throw the mass as a massive earth projectile
 *   Mastery 3 — Stone Giant: Double max HP temporarily + knockback immunity
 */
public class StoneGem extends Gem {

    // Tracks players who have a lifted earth mass ready to throw/place
    private static final Map<UUID, LiftedEarth> liftedEarthMap = new HashMap<>();
    // Cooldowns (ms)
    private static final Map<UUID, Long> abilityCooldowns = new HashMap<>();

    private static final long LIFT_COOLDOWN_MS = 20_000;
    private static final long GIANT_COOLDOWN_MS = 90_000;

    public StoneGem() {
        super("COMMON_STONE", "Stone Gem", GemRarity.COMMON,
                "A solid gem radiating earthen power. Commands the very ground beneath your feet.");
    }

    // ── Passive ───────────────────────────────────────────────────────────────

    @Override
    public void onPassiveTick(org.bukkit.entity.Player player, int mastery) {
        int level = mastery >= 3 ? 1 : 0;
        if (!player.hasPotionEffect(PotionEffectType.RESISTANCE) ||
                Objects.requireNonNull(player.getPotionEffect(PotionEffectType.RESISTANCE)).getAmplifier() < level) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, level, true, false));
        }
    }

    // ── Active Abilities ──────────────────────────────────────────────────────

    /**
     * Trigger ground lift — called by GemAbilityListener on right-click
     * when the player is holding the Stone Gem item.
     */
    public static void activateGroundLift(org.bukkit.entity.Player player, int mastery, JavaPlugin plugin) {
        if (mastery < 2) {
            player.sendMessage("§8⚫ §7You need §8Mastery 2 §7to use Ground Lift.");
            return;
        }
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = abilityCooldowns.get(uuid);
        if (last != null && (now - last) < LIFT_COOLDOWN_MS) {
            long rem = (LIFT_COOLDOWN_MS - (now - last)) / 1000;
            player.sendMessage("§8⚫ Ground Lift on cooldown: §7" + rem + "s");
            return;
        }
        if (liftedEarthMap.containsKey(uuid)) {
            player.sendMessage("§8⚫ §7You already have an earth mass active! §8Sneak§7 to form a Barrier, §8Attack§7 to Throw.");
            return;
        }
        abilityCooldowns.put(uuid, now);
        performGroundLift(player, plugin);
    }

    private static void performGroundLift(org.bukkit.entity.Player player, JavaPlugin plugin) {
        World world = player.getWorld();
        Location center = player.getLocation();
        int radius = 8; // 16x16 = radius 8

        player.sendMessage("§8⚫ §6You lift the earth around you! §7Sneak = Barrier | Left-click = Throw!");
        world.playSound(center, Sound.BLOCK_GRAVEL_BREAK, 2f, 0.5f);
        world.playSound(center, Sound.BLOCK_STONE_BREAK,  2f, 0.6f);
        world.spawnParticle(Particle.BLOCK, center, 200,
                2, 0.5, 2, 0.3, Material.DIRT.createBlockData());

        // Collect surface blocks in 16x16 grid
        List<Block> surfaceBlocks = new ArrayList<>();
        List<Material> capturedMaterials = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int bx = center.getBlockX() + dx;
                int bz = center.getBlockZ() + dz;
                int highY = world.getHighestBlockYAt(bx, bz) - 1;
                Block b = world.getBlockAt(bx, highY, bz);

                // Only lift earth-type blocks
                if (isEarthMaterial(b.getType())) {
                    surfaceBlocks.add(b);
                    capturedMaterials.add(b.getType());
                }
            }
        }

        // Store lifted earth reference
        LiftedEarth lifted = new LiftedEarth(capturedMaterials, center.clone());
        liftedEarthMap.put(player.getUniqueId(), lifted);

        // Animate: spawn FallingBlock entities rising upward for each block
        int liftHeight = 4;
        new BukkitRunnable() {
            int step = 0;
            @Override public void run() {
                if (step >= surfaceBlocks.size()) {
                    cancel();
                    // Show particles showing mass floating above player
                    visualizeFloatingMass(player, plugin);
                    return;
                }
                // Batch 10 blocks per tick for performance
                for (int i = step; i < Math.min(step + 10, surfaceBlocks.size()); i++) {
                    Block b = surfaceBlocks.get(i);
                    spawnRisingFallingBlock(world, b, liftHeight, plugin);
                }
                step += 10;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static void spawnRisingFallingBlock(World world, Block block, int height, JavaPlugin plugin) {
        Material mat = block.getType();
        if (mat == Material.AIR) return;
        Location loc = block.getLocation().add(0.5, 0, 0.5);
        BlockData data = mat.createBlockData();

        // Spawn FallingBlock and give it upward velocity
        FallingBlock fb = world.spawnFallingBlock(loc, data);
        fb.setVelocity(new Vector(0, 0.3 + Math.random() * 0.1, 0));
        fb.setDropItem(false);
        fb.setHurtEntities(false);

        // Remove it after it rises (avoid permanent falling block)
        new BukkitRunnable() {
            @Override public void run() {
                if (!fb.isDead()) fb.remove();
            }
        }.runTaskLater(plugin, 60L);
    }

    private static void visualizeFloatingMass(org.bukkit.entity.Player player, JavaPlugin plugin) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!liftedEarthMap.containsKey(player.getUniqueId()) || tick > 200) {
                    cancel(); return;
                }
                tick++;
                Location above = player.getLocation().add(0, 5, 0);
                player.getWorld().spawnParticle(Particle.BLOCK, above, 30,
                        1.5, 0.3, 1.5, 0.05, Material.DIRT.createBlockData());
                player.getWorld().spawnParticle(Particle.DUST,  above, 10,
                        1.5, 0.3, 1.5, 0.0,
                        new Particle.DustOptions(Color.fromRGB(101, 67, 33), 2.0f));
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * Throw the lifted earth mass as a projectile.
     * Called by GemAbilityListener on left-click/attack while mass is active.
     */
    public static void throwEarthMass(org.bukkit.entity.Player player, JavaPlugin plugin) {
        UUID uuid = player.getUniqueId();
        LiftedEarth lifted = liftedEarthMap.remove(uuid);
        if (lifted == null) return;

        World world = player.getWorld();
        Location origin = player.getEyeLocation();
        Vector dir = player.getEyeLocation().getDirection().multiply(1.8);

        player.sendMessage("§8⚫ §6You hurl the earth mass forward!");
        world.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);

        // Spawn a cluster of FallingBlocks hurled in the direction the player is looking
        new BukkitRunnable() {
            int count = 0;
            @Override public void run() {
                if (count >= Math.min(lifted.materials.size(), 80)) { cancel(); return; }
                Material mat = lifted.materials.get(count % lifted.materials.size());
                if (mat != Material.AIR) {
                    // Spread the blocks slightly for a shotgun effect
                    double spread = 0.3;
                    Vector velocity = dir.clone().add(new Vector(
                            (Math.random() - 0.5) * spread,
                            (Math.random() - 0.5) * spread * 0.5,
                            (Math.random() - 0.5) * spread));

                    FallingBlock fb = world.spawnFallingBlock(origin.clone(), mat.createBlockData());
                    fb.setVelocity(velocity);
                    fb.setDropItem(false);
                    fb.setHurtEntities(true);
                    fb.setDamagePerBlock(1.5f);
                    // Remove after 5s
                    new BukkitRunnable() { public void run() { if (!fb.isDead()) fb.remove(); }
                    }.runTaskLater(plugin, 100L);
                }
                count++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Impact explosion effect at target area after ~1s
        new BukkitRunnable() {
            @Override public void run() {
                Location impact = player.getLocation().add(dir.normalize().multiply(15));
                impact.setY(world.getHighestBlockYAt(impact.getBlockX(), impact.getBlockZ()));
                world.spawnParticle(Particle.BLOCK, impact, 300, 2, 1, 2, 0.4, Material.DIRT.createBlockData());
                world.spawnParticle(Particle.DUST, impact, 50, 2, 1, 2, 0.0,
                        new Particle.DustOptions(Color.fromRGB(101, 67, 33), 3f));
                world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
                world.playSound(impact, Sound.BLOCK_STONE_BREAK, 2f, 0.5f);
            }
        }.runTaskLater(plugin, 25L);
    }

    /**
     * Form a stone barrier around the player.
     * Called by GemAbilityListener on sneak while mass is active.
     */
    public static void formBarrier(org.bukkit.entity.Player player, JavaPlugin plugin) {
        UUID uuid = player.getUniqueId();
        LiftedEarth lifted = liftedEarthMap.remove(uuid);
        if (lifted == null) return;

        World world = player.getWorld();
        Location center = player.getLocation();
        int barrierRadius = 3;
        int barrierHeight = 5;

        player.sendMessage("§8⚫ §6The earth forms a protective barrier around you!");
        world.playSound(center, Sound.BLOCK_STONE_PLACE, 2f, 0.7f);

        // Place temporary stone barrier blocks
        List<Location> barrierBlocks = new ArrayList<>();
        for (int angle = 0; angle < 360; angle += 15) {
            double rad = Math.toRadians(angle);
            int bx = center.getBlockX() + (int)(Math.cos(rad) * barrierRadius);
            int bz = center.getBlockZ() + (int)(Math.sin(rad) * barrierRadius);
            for (int dy = 0; dy < barrierHeight; dy++) {
                Location blockLoc = new Location(world, bx, center.getBlockY() + dy, bz);
                Block b = blockLoc.getBlock();
                if (b.getType() == Material.AIR) {
                    b.setType(Material.STONE);
                    barrierBlocks.add(blockLoc);
                    world.spawnParticle(Particle.BLOCK, blockLoc.clone().add(0.5, 0.5, 0.5),
                            5, 0.2, 0.2, 0.2, 0.01, Material.STONE.createBlockData());
                }
            }
        }

        // Apply Resistance to player inside barrier
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 15, 1, false, true));
        player.sendMessage("§8⚫ §7Barrier active for §615 seconds§7. You have Resistance II.");

        // Remove barrier after 15 seconds
        new BukkitRunnable() {
            @Override public void run() {
                for (Location loc : barrierBlocks) {
                    Block b = loc.getBlock();
                    if (b.getType() == Material.STONE) {
                        b.setType(Material.AIR);
                        world.spawnParticle(Particle.BLOCK, loc.clone().add(0.5, 0.5, 0.5),
                                5, 0.2, 0.2, 0.2, 0.1, Material.STONE.createBlockData());
                    }
                }
                world.playSound(center, Sound.BLOCK_STONE_BREAK, 1.5f, 0.8f);
                player.sendMessage("§8⚫ §7The stone barrier crumbles away.");
            }
        }.runTaskLater(plugin, 20L * 15);
    }

    /**
     * Stone Giant — mastery 3 ability.
     */
    public static void activateStoneGiant(org.bukkit.entity.Player player, int mastery, JavaPlugin plugin) {
        if (mastery < 3) { player.sendMessage("§8⚫ §7Requires Mastery 3."); return; }
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = abilityCooldowns.get(uuid + "_giant");
        if (last != null && (now - last) < GIANT_COOLDOWN_MS) {
            player.sendMessage("§8⚫ Stone Giant on cooldown: §7" + ((GIANT_COOLDOWN_MS - (now - last)) / 1000) + "s");
            return;
        }
        abilityCooldowns.put(uuid + "_giant", now);

        World world = player.getWorld();
        Location loc = player.getLocation();
        world.spawnParticle(Particle.BLOCK, loc, 300, 1, 1, 1, 0.5, Material.STONE.createBlockData());
        world.playSound(loc, Sound.ENTITY_IRON_GOLEM_HURT, 2f, 0.5f);
        player.sendMessage("§8⚫ §6§lSTONE GIANT FORM — You are immovable for 15 seconds!");

        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,         20*15, 4, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,           20*15, 2, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW,               20*15, 2, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,         20*15, 9, false, true));
        player.setMaxHealth(Math.min(player.getMaxHealth() * 2, 40));
        player.setHealth(player.getMaxHealth());

        // Periodic ground-shake particles
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t++ > 30 || !player.isOnline()) { cancel(); return; }
                world.spawnParticle(Particle.BLOCK, player.getLocation(), 50,
                        0.8, 0.1, 0.8, 0.3, Material.COBBLESTONE.createBlockData());
            }
        }.runTaskTimer(plugin, 0L, 10L);

        new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline()) return;
                player.setMaxHealth(Math.max(player.getMaxHealth() / 2, 20));
                player.sendMessage("§8⚫ §7Stone Giant form has ended.");
                world.spawnParticle(Particle.BLOCK, player.getLocation(), 100,
                        0.8, 0.8, 0.8, 0.4, Material.COBBLESTONE.createBlockData());
            }
        }.runTaskLater(plugin, 20L * 15);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean isEarthMaterial(Material mat) {
        return switch (mat) {
            case DIRT, GRASS_BLOCK, COARSE_DIRT, PODZOL, ROOTED_DIRT,
                 STONE, COBBLESTONE, GRAVEL, SAND, RED_SAND,
                 SANDSTONE, ANDESITE, DIORITE, GRANITE,
                 DEEPSLATE, COBBLED_DEEPSLATE, TUFF -> true;
            default -> false;
        };
    }

    /** Returns true if this player has an active lifted earth mass */
    public static boolean hasLiftedEarth(UUID uuid) {
        return liftedEarthMap.containsKey(uuid);
    }

    // ── Ability descriptions ──────────────────────────────────────────────────

    @Override
    public String getSkillDescription(int masteryLevel) {
        return switch (masteryLevel) {
            case 1 -> "§8Earth Shield§7: Passive Resistance while holding this gem";
            case 2 -> "§8Ground Lift§7: Lift 16x16 earth — §6Throw§7 it as a projectile or §6Sneak§7 to form a Barrier (20s cd)";
            case 3 -> "§8Stone Giant§7: Double HP + Resistance V + Strength III for 15s (90s cd)";
            default -> "None";
        };
    }

    // ── Inner class ───────────────────────────────────────────────────────────

    private static class LiftedEarth {
        final List<Material> materials;
        final Location origin;
        LiftedEarth(List<Material> materials, Location origin) {
            this.materials = materials;
            this.origin    = origin;
        }
    }
}
