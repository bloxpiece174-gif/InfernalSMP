package com.heartssmp.god;

import com.heartssmp.HeartsSMPPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * DivineWorldManager v2 — Generates the Divine Dimension with 12 unique
 * castles of God, golden/white aesthetics, and progression tracking.
 *
 * Each castle is procedurally built using a unique theme variation.
 * Castle coordinates are evenly spaced in the divine world.
 */
public class DivineWorldManager {

    private final HeartsSMPPlugin plugin;

    // Castle themes — each has primary/secondary/accent block choices
    private static final CastleTheme[] CASTLE_THEMES = {
        new CastleTheme("Castle of Light",       Material.QUARTZ_BLOCK,        Material.SMOOTH_QUARTZ,       Material.GOLD_BLOCK),
        new CastleTheme("Castle of Gold",        Material.GOLD_BLOCK,          Material.QUARTZ_BLOCK,        Material.GLOWSTONE),
        new CastleTheme("Castle of Clouds",      Material.WHITE_CONCRETE,      Material.SMOOTH_QUARTZ,       Material.END_STONE),
        new CastleTheme("Castle of Stars",       Material.SMOOTH_QUARTZ,       Material.GOLD_BLOCK,          Material.SEA_LANTERN),
        new CastleTheme("Castle of Dawn",        Material.CALCITE,             Material.QUARTZ_BRICKS,       Material.GOLD_BLOCK),
        new CastleTheme("Castle of Eternity",    Material.QUARTZ_BRICKS,       Material.WHITE_CONCRETE,      Material.CRYING_OBSIDIAN),
        new CastleTheme("Castle of Judgment",    Material.POLISHED_BASALT,     Material.QUARTZ_BLOCK,        Material.GOLD_BLOCK),
        new CastleTheme("Castle of Radiance",    Material.SMOOTH_STONE,        Material.QUARTZ_PILLAR,       Material.SEA_LANTERN),
        new CastleTheme("Castle of Ascension",   Material.END_STONE_BRICKS,    Material.SMOOTH_QUARTZ,       Material.AMETHYST_BLOCK),
        new CastleTheme("Castle of Trials",      Material.DEEPSLATE_BRICKS,    Material.CHISELED_QUARTZ_BLOCK,Material.GOLD_BLOCK),
        new CastleTheme("Castle of Divinity",    Material.CHISELED_QUARTZ_BLOCK,Material.GOLD_BLOCK,         Material.BEACON),
        new CastleTheme("Castle of the Almighty",Material.GOLD_BLOCK,          Material.CRYING_OBSIDIAN,     Material.BEACON)
    };

    public DivineWorldManager(HeartsSMPPlugin plugin) {
        this.plugin = plugin;
    }

    // ── World / Castle generation ─────────────────────────────────────────────

    public void generateAllCastles() {
        String worldName = plugin.getConfig().getString("divine-dimension.world-name", "divine_realm");
        int spacing      = plugin.getConfig().getInt("divine-dimension.castle-spacing", 2000);

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[DivineWorld] World '" + worldName
                    + "' not found. Create it before generating castles.");
            return;
        }

        for (int i = 0; i < 12; i++) {
            // Arrange castles in a circle
            double angle = (2 * Math.PI / 12) * i;
            int cx = (int)(Math.cos(angle) * spacing);
            int cz = (int)(Math.sin(angle) * spacing);
            int cy = 100; // base Y
            buildCastle(world, cx, cy, cz, CASTLE_THEMES[i], i + 1);
            plugin.getLogger().info("[DivineWorld] Castle " + (i+1) + " '" + CASTLE_THEMES[i].name + "' placed at " + cx + "," + cy + "," + cz);
        }
    }

    /**
     * Build a single castle at the given world coordinates.
     * Structure: foundation platform → walls → towers → keep → spire → interior decorations
     */
    public void buildCastle(World world, int cx, int cy, int cz, CastleTheme theme, int castleIndex) {
        Material primary   = theme.primary;
        Material secondary = theme.secondary;
        Material accent    = theme.accent;

        // ── Foundation platform (40x40) ────────────────────────────────────
        fillBox(world, cx-20, cy-1, cz-20, cx+20, cy-1, cz+20, secondary);

        // ── Outer walls (36x36, height 12) ────────────────────────────────
        buildHollowBox(world, cx-18, cy, cz-18, cx+18, cy+12, cz+18, primary, 2);

        // ── Battlements on top of walls ────────────────────────────────────
        buildBattlements(world, cx-18, cy+13, cz-18, cx+18, cz+18, primary);

        // ── Corner towers (5x5, height 20) ────────────────────────────────
        int[][] corners = {{cx-18, cz-18}, {cx+18, cz-18}, {cx-18, cz+18}, {cx+18, cz+18}};
        for (int[] c : corners) {
            buildTower(world, c[0]-2, cy, c[1]-2, c[0]+2, cy+20, c[1]+2, primary, accent);
        }

        // ── Central keep (16x16, height 18) ───────────────────────────────
        buildHollowBox(world, cx-8, cy, cz-8, cx+8, cy+18, cz+8, secondary, 2);

        // ── Keep battlements ───────────────────────────────────────────────
        buildBattlements(world, cx-8, cy+19, cz-8, cx+8, cz+8, secondary);

        // ── Central spire (gold, height 10 above keep) ─────────────────────
        buildSpire(world, cx, cy+18, cz, accent, 10);

        // ── Interior: Trial floor pattern ──────────────────────────────────
        buildTrialFloor(world, cx, cy, cz, castleIndex, primary, accent);

        // ── Interior: Pillars ──────────────────────────────────────────────
        int[][] pillarOffsets = {{-6,-6},{6,-6},{-6,6},{6,6}};
        for (int[] p : pillarOffsets) {
            fillBox(world, cx+p[0]-1, cy, cz+p[1]-1, cx+p[0]+1, cy+15, cz+p[1]+1, primary);
            setBlock(world, cx+p[0], cy+16, cz+p[1], accent);
        }

        // ── Lighting: Sea lanterns / glowstone scattered ───────────────────
        placeLighting(world, cx, cy, cz, accent);

        // ── Castle nameplate (beacon at center top) ────────────────────────
        setBlock(world, cx, cy+30, cz, Material.BEACON);
        // Beacon base
        fillBox(world, cx-1, cy+29, cz-1, cx+1, cy+29, cz+1, Material.IRON_BLOCK);
        setBlock(world, cx, cy+29, cz, Material.IRON_BLOCK);
    }

    // ── Structure helpers ─────────────────────────────────────────────────────

    private void fillBox(World w, int x1, int y1, int z1, int x2, int y2, int z2, Material mat) {
        for (int x = Math.min(x1,x2); x <= Math.max(x1,x2); x++)
        for (int y = Math.min(y1,y2); y <= Math.max(y1,y2); y++)
        for (int z = Math.min(z1,z2); z <= Math.max(z1,z2); z++)
            w.getBlockAt(x, y, z).setType(mat, false);
    }

    /** Build a hollow box (walls only) with given wall thickness */
    private void buildHollowBox(World w, int x1, int y1, int z1, int x2, int y2, int z2,
                                  Material mat, int thickness) {
        for (int x = x1; x <= x2; x++)
        for (int y = y1; y <= y2; y++)
        for (int z = z1; z <= z2; z++) {
            boolean wall = x < x1+thickness || x > x2-thickness
                        || z < z1+thickness || z > z2-thickness
                        || y == y1 || y == y2;
            if (wall) w.getBlockAt(x, y, z).setType(mat, false);
            else      w.getBlockAt(x, y, z).setType(Material.AIR, false);
        }
    }

    private void buildTower(World w, int x1, int y1, int z1, int x2, int y2, int z2,
                              Material mat, Material accent) {
        fillBox(w, x1, y1, z1, x2, y2, z2, mat);
        // Hollow out interior
        fillBox(w, x1+1, y1+1, z1+1, x2-1, y2-1, z2-1, Material.AIR);
        // Accent top cap
        fillBox(w, x1, y2, z1, x2, y2+1, z2, accent);
        // Thin spire
        setBlock(w, (x1+x2)/2, y2+2, (z1+z2)/2, accent);
        setBlock(w, (x1+x2)/2, y2+3, (z1+z2)/2, accent);
    }

    private void buildBattlements(World w, int x1, int y, int z1, int x2, int z2, Material mat) {
        // Merlons every 2 blocks along all 4 wall edges
        for (int x = x1; x <= x2; x++) {
            if (x % 2 == 0) { setBlock(w, x, y, z1, mat); setBlock(w, x, y, z2, mat); }
        }
        for (int z = z1; z <= z2; z++) {
            if (z % 2 == 0) { setBlock(w, x1, y, z, mat); setBlock(w, x2, y, z, mat); }
        }
    }

    private void buildSpire(World w, int cx, int baseY, int cz, Material mat, int height) {
        for (int dy = 0; dy < height; dy++) {
            int r = Math.max(0, (height - dy) / 3);
            if (r == 0) { setBlock(w, cx, baseY+dy, cz, mat); }
            else {
                for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) == r || Math.abs(dz) == r)
                        setBlock(w, cx+dx, baseY+dy, cz+dz, mat);
                }
            }
        }
    }

    private void buildTrialFloor(World w, int cx, int cy, int cz, int castleIndex,
                                   Material primary, Material accent) {
        // Checkered pattern unique to each castle
        for (int dx = -7; dx <= 7; dx++)
        for (int dz = -7; dz <= 7; dz++) {
            Material m = ((dx + dz + castleIndex) % 2 == 0) ? primary : accent;
            setBlock(w, cx+dx, cy, cz+dz, m);
        }
        // Trial sigil at center
        setBlock(w, cx,   cy, cz,   Material.BEACON);
        setBlock(w, cx+1, cy, cz,   accent);
        setBlock(w, cx-1, cy, cz,   accent);
        setBlock(w, cx,   cy, cz+1, accent);
        setBlock(w, cx,   cy, cz-1, accent);
    }

    private void placeLighting(World w, int cx, int cy, int cz, Material accent) {
        Material light = (accent == Material.GLOWSTONE || accent == Material.SEA_LANTERN)
                ? accent : Material.SEA_LANTERN;
        // Interior lights along walls
        int[][] positions = {{-14,4,-14},{14,4,-14},{-14,4,14},{14,4,14},
                             {0,4,-17},{0,4,17},{-17,4,0},{17,4,0}};
        for (int[] p : positions) setBlock(w, cx+p[0], cy+p[1], cz+p[2], light);
        // Keep ceiling lights
        for (int dx = -6; dx <= 6; dx += 4)
        for (int dz = -6; dz <= 6; dz += 4)
            setBlock(w, cx+dx, cy+17, cz+dz, light);
    }

    private void setBlock(World w, int x, int y, int z, Material mat) {
        w.getBlockAt(x, y, z).setType(mat, false);
    }

    /** Returns the spawn location for a given castle (1-indexed) */
    public Location getCastleLocation(int castleNumber) {
        String worldName = plugin.getConfig().getString("divine-dimension.world-name", "divine_realm");
        int spacing      = plugin.getConfig().getInt("divine-dimension.castle-spacing", 2000);
        World world      = Bukkit.getWorld(worldName);
        if (world == null) return null;

        int idx   = Math.max(0, Math.min(11, castleNumber - 1));
        double a  = (2 * Math.PI / 12) * idx;
        int cx    = (int)(Math.cos(a) * spacing);
        int cz    = (int)(Math.sin(a) * spacing);
        return new Location(world, cx + 0.5, 101, cz + 0.5);
    }

    public String getCastleName(int castleNumber) {
        int idx = Math.max(0, Math.min(11, castleNumber - 1));
        return CASTLE_THEMES[idx].name;
    }

    // ── Inner record ──────────────────────────────────────────────────────────

    public static class CastleTheme {
        public final String name;
        public final Material primary, secondary, accent;
        CastleTheme(String name, Material primary, Material secondary, Material accent) {
            this.name      = name;
            this.primary   = primary;
            this.secondary = secondary;
            this.accent    = accent;
        }
    }
}
