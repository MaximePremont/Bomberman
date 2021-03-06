package net.samagames.bomberman;

import net.minecraft.server.v1_9_R2.Entity;
import net.minecraft.server.v1_9_R2.EntityTypes;
import net.samagames.api.SamaGamesAPI;
import net.samagames.api.resourcepacks.IResourceCallback;
import net.samagames.bomberman.commands.CommandSpawnPowerup;
import net.samagames.bomberman.entity.Bomb;
import net.samagames.bomberman.entity.Powerup;
import net.samagames.bomberman.event.PlayerEvent;
import net.samagames.bomberman.player.PlayerBomberman;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
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
public class Bomberman extends JavaPlugin {

    private static GameManager gameManager;

    public static GameManager getGameManager() {
        return gameManager;
    }

    private static void registerEntityInEntityEnum(Class paramClass, String paramString, int paramInt) throws NoSuchFieldException, IllegalAccessException {
        ((Map<String, Class<? extends Entity>>) getPrivateStatic(EntityTypes.class, "c")).put(paramString, paramClass);
        ((Map<Class<? extends Entity>, String>) getPrivateStatic(EntityTypes.class, "d")).put(paramClass, paramString);
        ((Map<Integer, Class<? extends Entity>>) getPrivateStatic(EntityTypes.class, "e")).put(paramInt, paramClass);
        ((Map<Class<? extends Entity>, Integer>) getPrivateStatic(EntityTypes.class, "f")).put(paramClass, paramInt);
        ((Map<String, Integer>) getPrivateStatic(EntityTypes.class, "g")).put(paramString, paramInt);
    }

    private static Object getPrivateStatic(Class clazz, String f) throws NoSuchFieldException, IllegalAccessException {
        Field field = clazz.getDeclaredField(f);
        field.setAccessible(true);

        return field.get(null);
    }

    @Override
    public void onEnable() {

        SamaGamesAPI samaGamesAPI = SamaGamesAPI.get();

        synchronized (this) {
            gameManager = new GameManager(this);
        }

        samaGamesAPI.getGameManager().registerGame(gameManager); // Register game on SamaGameAPI
        if (!gameManager.isTestServer())
            samaGamesAPI.getResourcePacksManager().forceUrlPack("http://resources.samagames.net/BomberMan.zip", "53a7f1fbe5298363b4ae351feeedc5d341a0380b", new IResourceCallback() { // Hash SHA-1
                @Override
                public void callback(Player player, PlayerResourcePackStatusEvent.Status status) {

                    if (status.equals(PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED)) {
                        PlayerBomberman playerBomberman = gameManager.getPlayer(player.getUniqueId());
                        if (playerBomberman != null) {
                            playerBomberman.setRecordPlayTime(-2);
                            playerBomberman.setPlayMusic(true);
                        }
                    }
                }

                @Override
                public boolean automaticKick(Player player) {
                    return true;
                }
            });
        samaGamesAPI.getGameManager().getGameProperties(); // Get properties

        // Register events
        getServer().getPluginManager().registerEvents(new PlayerEvent(gameManager), this);

        // Register command
        getServer().getPluginCommand("spawnpowerup").setExecutor(new CommandSpawnPowerup(gameManager));

        // Register timer
        getServer().getScheduler().runTaskTimerAsynchronously(this, gameManager.getTimer(), 0L, 20L);

        // Register entity
        try {
            registerEntityInEntityEnum(Bomb.class, "Bomb", 69);
            registerEntityInEntityEnum(Powerup.class, "Powerup", 70);
        } catch (Exception e) {
            getLogger().warning("Error to register entitys " + e);
        }

        // Kick players
        getServer().getOnlinePlayers().forEach(player -> player.kickPlayer(""));

        Location spawn = gameManager.getSpawn();
        org.bukkit.World world = spawn.getWorld();

        world.setPVP(true); // Enable pvp for damage player
        world.setSpawnLocation(spawn.getBlockX(), spawn.getBlockY() + 3, spawn.getBlockZ()); // Set spawn location
        world.setDifficulty(Difficulty.EASY); // Set difficulty
        world.setGameRuleValue("naturalRegeneration", String.valueOf(false)); // Disable naturalRegeneration
        world.setGameRuleValue("doMobSpawning", String.valueOf(false)); // Set doMobSpawning game rule
        world.setGameRuleValue("reducedDebugInfo", String.valueOf(true)); // Reduce debug info (Mask location)
        world.setGameRuleValue("keepInventory", String.valueOf(true)); // Set player keep inventory
        world.setStorm(false); // Clear storm
        world.setThundering(false); // Clear weather
        world.setThunderDuration(0); // Clear weather
        world.setWeatherDuration(0); // Clear weather

    }
}
