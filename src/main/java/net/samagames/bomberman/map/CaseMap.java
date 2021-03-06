package net.samagames.bomberman.map;

import net.samagames.bomberman.GameManager;
import net.samagames.bomberman.entity.Bomb;
import net.samagames.bomberman.entity.Powerup;
import net.samagames.bomberman.player.PlayerBomberman;
import net.samagames.bomberman.powerup.*;
import org.apache.commons.lang.math.RandomUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/*
 * This file is part of Bomberman.
 *
 * Bomberman is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Bomberman is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Bomberman.  If not, see <http://www.gnu.org/licenses/>.
 */
public class CaseMap {

    private final GameManager gameManager;
    private final Location worldLocation;
    private final int xMap;
    private final int yMap;
    private Powerup powerup;
    private Bomb bomb;
    private Material block;
    private List<PlayerBomberman> players;

    public CaseMap(GameManager gameManager, Location worldLocation, int xMap, int yMap) {

        this.worldLocation = worldLocation;
        this.xMap = xMap;
        this.yMap = yMap;

        this.players = new ArrayList<>();
        this.block = worldLocation.getBlock().getType();
        this.powerup = null;
        this.gameManager = gameManager;
    }

    private static boolean canDamagePlayerAtLocation(PlayerBomberman playerBomberman, PlayerBomberman source, Location location) {

        Location playerLoc = playerBomberman.getPlayerIfOnline().getLocation();

        boolean locations = location.getBlockX() == playerLoc.getBlockX() && location.getBlockZ() == playerLoc.getBlockZ();

        return !playerBomberman.hasPowerup(Powerups.INVULNERABILITY) && !(playerBomberman.hasPowerup(Powerups.SELF_INVULNERABILITY) && playerBomberman.equals(source)) && locations;
    }

    public void explode(boolean cobblestone, boolean ignoreFirstBreak, PlayerBomberman source, boolean originalBomb) {

        int radius = source.getRadius();
        EnumMap<BlockFace, Boolean> faces = new EnumMap<>(BlockFace.class);

        faces.put(BlockFace.NORTH, true);
        faces.put(BlockFace.EAST, true);
        faces.put(BlockFace.SOUTH, true);
        faces.put(BlockFace.WEST, true);

        explodeCase(cobblestone, source, 0);
        killEntitys(source);

        for (int i = 1; i <= radius; i++) {
            final int finalI = i;
            faces.entrySet().stream().filter(Map.Entry::getValue).forEach(entry -> {
                BlockFace face = entry.getKey();
                int x = xMap + finalI * face.getModX();
                int y = yMap + finalI * face.getModZ();

                if (hasValidCoordinates(x, y)) {

                    CaseMap caseMap = gameManager.getMapManager().getMap()[x][y];

                    Material blockBreak = caseMap.explodeCase(cobblestone, source, finalI);
                    boolean continueExplode = false;

                    if (blockBreak.equals(Material.AIR) || (blockBreak.equals(Material.DIRT) && ignoreFirstBreak))
                        continueExplode = true;

                    entry.setValue(continueExplode);
                }
            });
        }

        if (source.hasPowerup(Powerups.MULTIPLE_BOMB) && originalBomb) {
            faces.keySet().forEach(blockFace -> {
                Bomb bombPlace = gameManager.getMapManager().spawnBombEntity(worldLocation.clone().add(0, 0, 0), source);
                bombPlace.setNormalBomb(false);

                bombPlace.move(radius * blockFace.getModX(), 1.3, radius * blockFace.getModZ());
            });
        }
    }

    public boolean hasValidCoordinates(int x, int y) {

        return x < gameManager.getMapManager().getWight() && x > -1 && y < gameManager.getMapManager().getHeight() && y > -1;
    }

    public Material explodeCase(boolean cobblestone, PlayerBomberman source, int indexRadius) {

        Material blockExplode = block;

        if (block.equals(Material.AIR))
            killEntitys(source);
        else if (block.equals(Material.DIRT) || (cobblestone && block.equals(Material.COBBLESTONE))) {
            block = Material.AIR;
            spawnPowerup(worldLocation);
        }

        updateInWorld();

        if (block.equals(Material.AIR)) {

            if (source.hasPowerup(Powerups.FIRE)) {
                worldLocation.getBlock().setType(Material.FIRE);
                gameManager.getServer().getScheduler().runTaskLater(gameManager.getPlugin(), () -> worldLocation.getBlock().setType(Material.AIR), 60L);
            }

            displayExplosion(indexRadius);
        }

        return blockExplode;
    }

    public void spawnWall() {

        block = Material.DIRT;

        killEntitys(null);

        updateInWorld();
    }

    private void killEntitys(PlayerBomberman source) {

        if (powerup != null && powerup.isAlive()) {
            powerup.die();
            powerup = null;
        }

        if (bomb != null && bomb.isAlive()) {
            bomb.die();
            bomb = null;
        }

        if (!players.isEmpty() && source != null && source.getPlayerIfOnline() != null) {

            players.stream().filter(p -> p.getPlayerIfOnline() != null && canDamagePlayerAtLocation(p, source, worldLocation)).forEach(p ->
                    p.getPlayerIfOnline().damage(777.77D, source.getPlayerIfOnline())
            );

            players.clear();
        }
    }

    private void updateInWorld() {

        for (int y = 0; y <= 2; y++) {
            worldLocation.clone().add(0, y, 0).getBlock().setType(block, false);
        }
    }

    @SuppressWarnings("deprecation")
    private void displayExplosion(int radius) {

        if (radius <= 3 || RandomUtils.nextInt(1000) >= (radius - 3) * 50) {

            Location location = worldLocation.clone().add(0, -1, 0);

            location.getBlock().setTypeIdAndData(Material.STAINED_CLAY.getId(), (byte) 14, false);
            gameManager.getServer().getScheduler().runTaskLater(gameManager.getPlugin(), () -> location.getBlock().setType(Material.STONE, false), RandomUtils.nextInt(30) + 30L);
        }
    }

    private void spawnPowerup(Location location) {

        int random = RandomUtils.nextInt(1000);
        Location locationPowerup = location.clone().add(0.5, 0.8, 0.5);
        net.samagames.tools.powerups.Powerup powerupToSpawn = null;

        if (random <= 100)
            powerupToSpawn = new BombModifierPowerup();

        else if (random <= 250)
            powerupToSpawn = new RadiusPowerup();

        else if (random <= 450)
            powerupToSpawn = new BombPowerup();

        else if (random <= 550)
            powerupToSpawn = new SpeedPowerup();

        else if (random <= 600)
            powerupToSpawn = new BoosterPowerup();

        else if (random <= 700)
            powerupToSpawn = new CadeauPowerup();

        if (powerupToSpawn != null)
            powerup = gameManager.getPowerupManager().spawnPowerup(powerupToSpawn, locationPowerup);
    }

    public boolean isEmpty() {

        return players.isEmpty() && (powerup == null || !powerup.isAlive()) && block.equals(Material.AIR);
    }

    public Location getWorldLocation() {
        return worldLocation;
    }

    public Powerup getPowerup() {
        return powerup;
    }

    public Material getBlock() {
        return block;
    }

    public List<PlayerBomberman> getPlayers() {
        return players;
    }

    public int getxMap() {
        return xMap;
    }

    public int getyMap() {
        return yMap;
    }

    public Bomb getBomb() {
        return bomb;
    }

    public void setBomb(Bomb bomb) {
        this.bomb = bomb;
    }
}
