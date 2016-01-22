package fr.azuxul.bomberman.entity;

import fr.azuxul.bomberman.Bomberman;
import fr.azuxul.bomberman.GameManager;
import fr.azuxul.bomberman.player.PlayerBomberman;
import fr.azuxul.bomberman.powerup.BombPowerup;
import fr.azuxul.bomberman.powerup.BoosterPowerup;
import fr.azuxul.bomberman.powerup.PowerupTypes;
import fr.azuxul.bomberman.powerup.RadiusPowerup;
import net.minecraft.server.v1_8_R3.EntityTNTPrimed;
import net.minecraft.server.v1_8_R3.EnumParticle;
import net.minecraft.server.v1_8_R3.World;
import org.apache.commons.lang.math.RandomUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Bomb entity
 *
 * @author Azuxul
 * @version 1.0
 */
public class Bomb extends EntityTNTPrimed {

    private static GameManager gameManager;
    private final int radius;
    private final PlayerBomberman owner;

    public Bomb(World world, double x, double y, double z, int fuseTicks, int radius, Player owner) {
        super(world, x, y, z, ((CraftPlayer) owner).getHandle());

        gameManager = Bomberman.getGameManager();
        this.fuseTicks = fuseTicks;
        this.radius = radius;
        this.owner = gameManager.getPlayer(owner.getUniqueId());

        this.owner.setPlacedBombs(this.owner.getPlacedBombs() + 1);
    }

    private void setFuseTicks(int ticks) {
        this.fuseTicks = ticks;
    }

    private boolean explodeBlock(Location location, boolean breakCobblestone) {

        org.bukkit.World world = location.getWorld();
        boolean blockBreak = false;
        boolean placePowerup = false;

        for (int i = 0; i <= 2; i++) {

            Block block = world.getBlockAt(location.add(0, 1, 0));

            if (block.getType().equals(Material.DIRT) || (breakCobblestone && block.getType().equals(Material.COBBLESTONE))) {

                block.setType(Material.AIR);
                blockBreak = true;
                placePowerup = true;
            } else {
                blockBreak = block.getType().equals(Material.COBBLESTONE);
                break;
            }
        }

        if (placePowerup) {

            int random = RandomUtils.nextInt(1000);
            Location locationPowerup = location.add(0, -1.3, 0);

            if (random <= 220)
                gameManager.getPowerupManager().spawnPowerup(new BoosterPowerup(), locationPowerup);

            else if (random <= 500)
                gameManager.getPowerupManager().spawnPowerup(new RadiusPowerup(), locationPowerup);

            else if (random <= 650)
                gameManager.getPowerupManager().spawnPowerup(new BombPowerup(), locationPowerup);

        }

        Location locationY = location.clone();

        locationY.setY(0);

        damagePlayersAtBlock(locationY);
        explodeBombs(locationY);
        world.createExplosion(location.add(0, 1, 0), 0);

        return blockBreak;
    }

    /**
     * Explode blocks
     *
     * @param faces            map with boolean and face
     * @param location         center of explosion
     * @param radius           radus of explosion
     * @param breakCobblestone if true break cobblestone
     */
    private void explodeBlocks(Map<BlockFace, Boolean> faces, Location location, int radius, boolean breakCobblestone) {

        faces.entrySet().stream().filter(Map.Entry::getValue).forEach(entry -> {

            BlockFace blockFace = entry.getKey();
            double x = (double) radius * blockFace.getModX();
            double z = (double) radius * blockFace.getModZ();

            entry.setValue(!explodeBlock(location.clone().add(x, -1, z), breakCobblestone));
        });

    }

    private void damagePlayersAtBlock(Location location) {

        List<PlayerBomberman> playerBombermanList = new ArrayList<>(gameManager.getInGamePlayers().values());

        for (PlayerBomberman playerBomberman : playerBombermanList) {

            Player player = playerBomberman.getPlayerIfOnline();

            Location playerLocation = player.getLocation().clone();
            playerLocation.setY(0);

            if (playerLocation.distance(location) <= 0.5)
                player.damage(100.0D, owner.getPlayerIfOnline());
        }
    }

    private void explodeBombs(Location location) {

        List<Bomb> bombList = gameManager.getBombManager().getBombs();

        for (Bomb bomb : bombList) {

            Location playerLocation = new Location(bomb.getWorld().getWorld(), bomb.locX, bomb.locY, bomb.locZ);
            playerLocation.setY(0);

            if (playerLocation.distance(location) <= 0.5)
                bomb.setFuseTicks(0);
        }

    }

    @Override
    public void t_() {
        if (this.world.spigotConfig.currentPrimedTnt++ <= this.world.spigotConfig.maxTntTicksPerTick) {

            if (this.fuseTicks-- <= 0) {
                if (!this.world.isClientSide) {
                    this.explode();
                }

                this.die();
            } else {
                this.W();
                this.world.addParticle(EnumParticle.SMOKE_NORMAL, this.locX, this.locY + 0.5D, this.locZ, 0.0D, 0.0D, 0.0D);
            }

        }
    }

    private void explode() {

        this.owner.setPlacedBombs(this.owner.getPlacedBombs() - 1);

        Location location = new Location(getWorld().getWorld(), locX, locY, locZ);
        EnumMap<BlockFace, Boolean> faces = new EnumMap<>(BlockFace.class);

        faces.put(BlockFace.NORTH, true);
        faces.put(BlockFace.EAST, true);
        faces.put(BlockFace.SOUTH, true);
        faces.put(BlockFace.WEST, true);

        for (int i = 1; i <= radius; i++) {

            if (owner.getPowerupTypes() == null) {
                explodeBlocks(faces, location.clone(), i, false);
            } else if (owner.getPowerupTypes().equals(PowerupTypes.HYPER_BOMB)) {

                explodeBlocks(faces, location.clone(), i, true);

            } else if (owner.getPowerupTypes().equals(PowerupTypes.SUPER_BOMB)) {

                for (BlockFace face : faces.keySet()) {

                    double x = (double) i * face.getModX();
                    double z = (double) i * face.getModZ();

                    explodeBlock(location.clone().add(x, -1, z), false);
                }
            } else {
                explodeBlocks(faces, location.clone(), i, false);
            }
        }
    }

    @Override
    public boolean equals(Object comapeObject) {

        if (this == comapeObject)
            return true;
        if (!(comapeObject instanceof Bomb))
            return false;
        if (!super.equals(comapeObject))
            return false;

        Bomb bomb = (Bomb) comapeObject;
        return radius == bomb.radius;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), radius);
    }
}
