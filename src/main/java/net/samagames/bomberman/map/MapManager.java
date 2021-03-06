package net.samagames.bomberman.map;

import net.samagames.bomberman.GameManager;
import net.samagames.bomberman.entity.Bomb;
import net.samagames.bomberman.player.PlayerBomberman;
import net.samagames.bomberman.powerup.Powerups;
import net.samagames.tools.Area;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_9_R2.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;

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
public class MapManager {

    private final CaseMap[][] map;
    private final int height;
    private final int wight;
    private final GameManager gameManager;
    private final Area area;
    private final int minBlockX;
    private final int minBlokcZ;


    public MapManager(GameManager gameManager, Location smallerLoc, Location higherLoc) {

        this.gameManager = gameManager;

        this.area = new Area(smallerLoc, higherLoc);

        this.wight = area.getSizeX() + 1;
        this.height = area.getSizeZ() + 1;

        this.map = new CaseMap[wight][height];
        this.minBlockX = area.getMin().getBlockX();
        this.minBlokcZ = area.getMin().getBlockZ();

        for (int x = area.getMin().getBlockX(); x <= area.getMax().getBlockX(); x++) {
            for (int z = area.getMin().getBlockZ(); z <= area.getMax().getBlockZ(); z++) {

                int mapX = worldLocXToMapLocX(x);
                int mapY = worldLocZToMapLocY(z);

                map[mapX][mapY] = new CaseMap(gameManager, new Location(smallerLoc.getWorld(), x, smallerLoc.getY(), z), mapX, mapY);
            }
        }
    }

    public CaseMap[][] getMap() {
        return map;
    }

    public int worldLocXToMapLocX(int xWorld) {

        return xWorld - minBlockX;
    }

    public int worldLocZToMapLocY(int zWorld) {

        return zWorld - minBlokcZ;
    }

    public int getHeight() {
        return height;
    }

    public int getWight() {
        return wight;
    }

    public CaseMap getCaseAtWorldLocation(int blockX, int blockZ) {

        int x = worldLocXToMapLocX(blockX);
        int y = worldLocZToMapLocY(blockZ);

        if (x < wight && x > -1 && y < height && y > -1)
            return map[x][y];
        else
            return null;
    }

    public void movePlayer(Player player, Location locTo) {

        PlayerBomberman playerBomberman = gameManager.getPlayer(player.getUniqueId());
        CaseMap caseMap = playerBomberman.getCaseMap();

        if (caseMap != null)
            caseMap.getPlayers().remove(playerBomberman);

        caseMap = getCaseAtWorldLocation(locTo.getBlockX(), locTo.getBlockZ());

        if (caseMap != null) {
            caseMap.getPlayers().add(playerBomberman);

            if (playerBomberman.hasPowerup(Powerups.AUTO_PLACE) && caseMap.getBomb() == null && playerBomberman.getBombNumber() > playerBomberman.getPlacedBombs())
                gameManager.getMapManager().spawnBomb(locTo.getBlock().getLocation(), playerBomberman);
            else if (playerBomberman.hasPowerup(Powerups.FREEZER))
                freezeBombs(2, locTo);
        } else
            player.kickPlayer(ChatColor.RED + "Sortie de la map !");
    }

    private void freezeBombs(int radius, Location center) {

        int minX = radius * -1;

        for (int x = minX; x <= radius; x++) {

            int minZ = Math.abs(x) - radius;
            int maxZ = Math.abs(minZ);

            for (int y = minZ; y <= maxZ; y++) {

                CaseMap caseMap = getCaseAtWorldLocation(center.getBlockX() + x, center.getBlockZ() + y);

                if (caseMap != null && caseMap.getBomb() != null && caseMap.getBomb().isAlive()) {

                    Bomb bomb = caseMap.getBomb();

                    bomb.setExplodeTicks(bomb.getExplodeTicks() + 80);
                }
            }
        }
    }

    public void spawnWall(Location location, PlayerBomberman player) {

        location.setY(gameManager.getBombY());

        CaseMap caseMap = gameManager.getMapManager().getCaseAtWorldLocation(location.getBlockX(), location.getBlockZ());

        if (caseMap != null) {

            caseMap.spawnWall();
            player.setPlacedBombs(player.getPlacedBombs() + 1);
        }
    }

    public Bomb spawnBombEntity(Location location, PlayerBomberman player) {

        location.setY(gameManager.getBombY());

        CaseMap caseMap = gameManager.getMapManager().getCaseAtWorldLocation(location.getBlockX(), location.getBlockZ());

        if (caseMap != null) {
            Bomb bomb = new Bomb(((CraftWorld) location.getWorld()).getHandle(), location.getX() + 0.5, location.getY(), location.getZ() + 0.5, player.getFuseTicks(), player.getRadius(), player.getPlayerIfOnline());

            ((CraftWorld) location.getWorld()).getHandle().addEntity(bomb, CreatureSpawnEvent.SpawnReason.CUSTOM);

            return bomb;
        }

        return null;
    }

    @SuppressWarnings("deprecation")
    public boolean spawnBomb(Location location, PlayerBomberman player) {

        location.setY(gameManager.getBombY());
        Block block = location.getBlock();

        CaseMap caseMap = gameManager.getMapManager().getCaseAtWorldLocation(location.getBlockX(), location.getBlockZ());

        if (caseMap != null && (caseMap.getBomb() == null || !caseMap.getBomb().isAlive())) {
            player.setPlacedBombs(player.getPlacedBombs() + 1);

            block.setTypeIdAndData(Material.CARPET.getId(), (byte) 8, false);

            Bomb bomb = new Bomb(((CraftWorld) location.getWorld()).getHandle(), location.getX() + 0.5, location.getY(), location.getZ() + 0.5, player.getFuseTicks(), player.getRadius(), player.getPlayerIfOnline());

            caseMap.setBomb(bomb);
            player.getAliveBombs().add(bomb);
            player.addTotalBomb(1);

            gameManager.getServer().getScheduler().runTaskLater(gameManager.getPlugin(), () -> {

                if (caseMap.getBomb() != null && caseMap.getBomb().isAlive() && bomb.isAlive()) {
                    block.setType(Material.AIR, false);

                    ((CraftWorld) location.getWorld()).getHandle().addEntity(bomb, CreatureSpawnEvent.SpawnReason.CUSTOM);
                }

            }, 20L);

            return true;
        } else {
            return false;
        }
    }

}
