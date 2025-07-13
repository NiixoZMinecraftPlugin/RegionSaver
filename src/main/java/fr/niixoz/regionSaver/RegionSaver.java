package fr.niixoz.regionSaver;

import com.google.common.io.Files;
import fr.niixoz.regionSaver.managers.CommandsManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public final class RegionSaver extends JavaPlugin {

    private static boolean DEBUG = false;
    private static boolean PRINT_HOME = false;
    private static boolean BIG_RADIUS = true;
    private static boolean PRINT_REGIONS = false;

    private static int BATCH_SIZE = 50;
    private static long PAUSE_MILLIS = 100;

    public static File playerData;
    public static File outputFolder;
    private static RegionSaver instance;

    private static final AtomicInteger COPY_COUNTER = new AtomicInteger();

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        BATCH_SIZE = getConfig().getInt("batchSize", 50);
        PAUSE_MILLIS = getConfig().getLong("pauseMillis", 100);

        playerData = new File(Bukkit.getPluginManager().getPlugin("SurvivalCore").getDataFolder(), "playerdata");
        outputFolder = new File(getDataFolder(), "saved_regions");
        if(!outputFolder.exists()) {
            outputFolder.mkdirs();
        }

        CommandsManager.registerCommands();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static RegionSaver getInstance() {
        return instance;
    }

    public void startBackup(CommandSender sender) {
        // Préparation sync : flush & désactive autosave
        Bukkit.getScheduler().runTask(this, () -> {
            Map<World, Boolean> previous = new HashMap<>();
            for (World w : Bukkit.getWorlds()) {
                w.save();
                previous.put(w, w.isAutoSave());
                w.setAutoSave(false);
            }
            sender.sendMessage(ChatColor.GRAY + "[RegionSaver] Début de la sauvegarde…");

            // Sauvegarde asynchrone
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                long start = System.currentTimeMillis();
                boolean ok = true;
                try {
                    HashMap<String, List<String>> regions = detectRegions();
                    copyRegionsInSaveFolder(regions);
                } catch (Exception ex) {
                    ok = false;
                    getLogger().log(Level.SEVERE, "Backup failed", ex);
                }
                long dur = System.currentTimeMillis() - start;

                // Restauration sync
                boolean finalOk = ok;
                Bukkit.getScheduler().runTask(this, () -> {
                    previous.forEach(World::setAutoSave);
                    String msg = finalOk ? ChatColor.GREEN + "[RegionSaver] Sauvegarde terminée en " + dur + "ms." : ChatColor.RED + "[RegionSaver] Sauvegarde échouée (voir console).";
                    sender.sendMessage(msg);
                });
            });
        });
    }

    /*
    public static void saveRegions() {
        copyRegionsInSaveFolder(detectRegions());
    }*/

    private static HashMap<String, List<String>> detectRegions() {

        Yaml yaml = new Yaml();
        List<HomeLocation> homes = new ArrayList<>();
        HashMap<String, List<String>> regionsName = new HashMap<>();

        for(File file : Objects.requireNonNull(playerData.listFiles())) {
            if(file.getName().endsWith(".yml")) {
                try (InputStream inputStream = new FileInputStream(file)) {
                    Map<String, Object> data = yaml.load(inputStream);
                    if(data.containsKey("homes")) {
                        Map<String, Object> playerHomes = (Map<String, Object>) data.get("homes");
                        homes.addAll(getHomes(playerHomes));
                    }
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }

        for(HomeLocation home : homes) {
            int[] coords = getRegionCoords(home.getX(), home.getZ());
            if(!regionsName.containsKey(home.getWorldName())) {
                regionsName.put(home.getWorldName(), new ArrayList<>());
            }
            regionsName.get(home.getWorldName()).add(getRegionName(coords[0], coords[1]));
        }

        // Remove doubles
        regionsName.values().forEach(list -> {
            Set<String> uniqueRegions = new HashSet<>(list);
            list.clear();
            list.addAll(uniqueRegions);
        });

        if (DEBUG) getInstance().getLogger().info("Regions détectées : " + regionsName);
        return regionsName;
    }

    private static void copyRegionsInSaveFolder(HashMap<String, List<String>> regionsName) {

        // Clear ouput Folder
        for(File file : Objects.requireNonNull(outputFolder.listFiles())) {
            deleteDirectory(file);
        }

        File worldsRegion = Bukkit.getWorldContainer();

        for(File world : Objects.requireNonNull(worldsRegion.listFiles())) {
            if(!isWorldDirectory(world)) continue;
            WorldInfo worldInfo = new WorldInfo();
            updateWorldInfo(world, worldInfo);
            if(worldInfo.region != null)
                loopMCAFolder(regionsName.get(world.getName()), worldInfo.region, FolderType.REGION, world.getName());
            if(worldInfo.entities != null)
                loopMCAFolder(regionsName.get(world.getName()), worldInfo.entities, FolderType.ENTITIES, world.getName());
            if(worldInfo.poi != null)
                loopMCAFolder(regionsName.get(world.getName()), worldInfo.poi, FolderType.POI, world.getName());
        }
    }

    private static boolean isWorldDirectory(File file) {
        if(!file.isDirectory()) return false;
        return new File(file, "level.dat").exists();
    }

    private static void updateWorldInfo(File file, WorldInfo worldInfo) {

        for(File subFile : Objects.requireNonNull(file.listFiles())) {
            if(subFile.isDirectory()) {
                boolean found = checkIfMCAInFolder(subFile);
                try {
                    switch (FolderType.valueOf(subFile.getName().toUpperCase())) {
                        case REGION:
                            worldInfo.region = subFile;
                            break;
                        case POI:
                            worldInfo.poi = subFile;
                            break;
                        case ENTITIES:
                            worldInfo.entities = subFile;
                            break;
                    }
                }
                catch (Exception e){}
                if(!found) {
                    updateWorldInfo(subFile, worldInfo);
                }
            }
        }
    }

    private static boolean checkIfMCAInFolder(File folder) {
        for(File subFile : Objects.requireNonNull(folder.listFiles())) {
            if(subFile.getName().endsWith(".mca")) {
                return true;
            }
        }
        return false;
    }

    private static void loopMCAFolder(List<String> regionsToSave, File regionFolder, FolderType folderType, String worldName) {
        if(regionsToSave == null)
            return;
        String ouputPath = worldName + File.separator + folderType.name().toLowerCase();
        if(!new File(outputFolder, ouputPath).exists()) {
            new File(outputFolder, ouputPath).mkdirs();
        }

        for(File region : Objects.requireNonNull(regionFolder.listFiles())) {
            if(region.getName().endsWith(".mca") && regionsToSave.contains(region.getName())) {
                try {
                    File output = new File(outputFolder, ouputPath + File.separator + region.getName());

                    java.nio.file.Files.copy(region.toPath(), output.toPath(),
                            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
                catch(Exception e) {
                    System.out.println("Error copying " + region.getName());
                }

                if (COPY_COUNTER.incrementAndGet() % BATCH_SIZE == 0) {
                    try { Thread.sleep(PAUSE_MILLIS); } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    private static int[] getRegionCoords(float x, float z) {
        int[] coords = new int[2];
        float xMod = x % 512;
        float zMod = z % 512;
        if(xMod < 0) {
            xMod += 512;
        }
        if(zMod < 0) {
            zMod += 512;
        }
        x -= xMod;
        z -= zMod;

        coords[0] = (int) (x / 512);
        coords[1] = (int) (z / 512);
        return coords;
    }

    private static String getRegionName(int x, int z) {
        return "r." + x + "." + z + ".mca";
    }

    private static List<HomeLocation> getHomes(Map<String, Object> playerHomes) {
        List<HomeLocation> homes = new ArrayList<>();

        for (Map.Entry<String, Object> entry : playerHomes.entrySet()) {
            Map<String, Object> home = (Map<String, Object>) entry.getValue();
            String world = (String) home.get("world");
            float x;
            float z;
            if (home.get("x") instanceof Double) {
                x = (int) Math.round((double) home.get("x"));
                z = (int) Math.round((double) home.get("z"));
            } else {
                x = (int) home.get("x");
                z = (int) home.get("z");
            }

            homes.add(new HomeLocation(entry.getKey(), world, x, z));
            if(BIG_RADIUS) {
                homes.add(new HomeLocation(entry.getKey(), world, x - 512, z - 512));
                homes.add(new HomeLocation(entry.getKey(), world, x - 512, z + 512));
                homes.add(new HomeLocation(entry.getKey(), world, x - 512, z));
                homes.add(new HomeLocation(entry.getKey(), world, x, z - 512));
                homes.add(new HomeLocation(entry.getKey(), world, x + 512, z - 512));
                homes.add(new HomeLocation(entry.getKey(), world, x, z + 512));
                homes.add(new HomeLocation(entry.getKey(), world, x + 512, z));
                homes.add(new HomeLocation(entry.getKey(), world, x + 512, z + 512));
            }
        }

        return homes;
    }

    private static boolean deleteDirectory(File directoryToBeDeleted) {
        try {
            File[] allContents = directoryToBeDeleted.listFiles();
            if (allContents != null) {
                for (File file : allContents) {
                    deleteDirectory(file);
                }
            }
        }
        catch(Exception e) {
            System.out.println("Failed to delete directory " + directoryToBeDeleted);
        }
        return directoryToBeDeleted.delete();
    }

    private static class WorldInfo {
        private File region = null;
        private File poi = null;
        private File entities = null;

        WorldInfo() {}

        WorldInfo(File region, File poi, File entities) {
            this.region = region;
            this.poi = poi;
            this.entities = entities;
        }
    }

    private enum FolderType {
        REGION,
        POI,
        ENTITIES
    }
}
