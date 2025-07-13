package fr.niixoz.regionSaver;

import com.google.common.io.Files;
import fr.niixoz.regionSaver.managers.CommandsManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

public final class RegionSaver extends JavaPlugin {

    private static boolean DEBUG = false;
    private static boolean PRINT_HOME = false;
    private static boolean BIG_RADIUS = true;
    private static boolean PRINT_REGIONS = false;

    public static File playerData;
    public static File outputFolder;
    private static RegionSaver instance;

    @Override
    public void onEnable() {
        instance = this;

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

    public static void saveRegions() {
        copyRegionsInSaveFolder(detectRegions());
    }

    private static HashMap<String, List<String>> detectRegions() {

        Yaml yaml = new Yaml();
        List<HomeLocation> homes = new ArrayList<>();
        HashMap<String, List<String>> regionsName = new HashMap<>();

        for(File file : Objects.requireNonNull(playerData.listFiles())) {
            if(file.getName().endsWith(".yml")) {
                try {
                    InputStream inputStream = new FileInputStream(file);
                    Map<String, Object> data = yaml.load(inputStream);
                    if(data.containsKey("homes")) {
                        Map<String, Object> playerHomes = (Map<String, Object>) data.get("homes");
                        String username = "Unkown Player";
                        if (data.containsKey("player")) {
                            Map<String, Object> player = (Map<String, Object>) data.get("player");
                            Map<String, Object> info = (Map<String, Object>) player.get("info");
                            username = (String) info.get("lastSeenAs");
                        } else {
                            username = (String) data.get("username");
                        }

                        List<HomeLocation> playerHomesList = getHomes(playerHomes);
                        homes.addAll(playerHomesList);
                    }
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // Exception folder ?

        for(HomeLocation home : homes) {
            int[] coords = getRegionCoords(home.getX(), home.getZ());
            if(!regionsName.containsKey(home.getWorldName())) {
                regionsName.put(home.getWorldName(), new ArrayList<>());
            }
            regionsName.get(home.getWorldName()).add(getRegionName(coords[0], coords[1]));
        }

        // Remove doubles
        for(Map.Entry<String, List<String>> entry : regionsName.entrySet()) {
            List<String> regions = entry.getValue();
            Set<String> hs = new HashSet<>();
            hs.addAll(regions);
            regions.clear();
            regions.addAll(hs);
        }

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
            if(region.getName().endsWith(".mca")) {
                if(regionsToSave.contains(region.getName())) {
                    try {
                        File output = new File(outputFolder, ouputPath + File.separator + region.getName());
                        Files.copy(region, output);
                    }
                    catch(Exception e) {
                        System.out.println("Error copying " + region.getName());
                    }
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
