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

                        if(PRINT_HOME) {
                            System.out.println("=================================");
                            System.out.println("Player: " + username);
                            for(HomeLocation home : playerHomesList) {
                                System.out.println("home: " + home.getHomeName() + ", world: " + home.getWorldName() + ", x: " + home.getX() + ", z: " + home.getZ());
                            }
                            System.out.println("=================================");
                        }
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

        if(PRINT_REGIONS) {
            // Print all regions to save
            for (Map.Entry<String, List<String>> entry : regionsName.entrySet()) {
                System.out.println("World: " + entry.getKey());
                for (String region : entry.getValue()) {
                    System.out.println("Region: " + region);
                }
            }

            // Print total regions to save
            for(Map.Entry<String, List<String>> entry : regionsName.entrySet()) {
                System.out.println("World: " + entry.getKey() + ", regions to save: " + entry.getValue().size());
            }
        }

        return regionsName;
    }

    private static void copyRegionsInSaveFolder(HashMap<String, List<String>> regionsName) {

        // Clear ouput Folder
        for(File file : outputFolder.listFiles()) {
            deleteDirectory(file);
        }

        File worldsRegion = Bukkit.getWorldContainer();

        for(File world : Objects.requireNonNull(worldsRegion.listFiles())) {
            if(!isWorldDirectory(world)) continue;
            if(world.getName().endsWith("_nether")) {
                File regionFolder = new File(world, "DIM-1/region");
                loopRegion(regionsName.get(world.getName()), regionFolder, world.getName());
            }
            else if(world.getName().endsWith("_the_end")) {
                File regionFolder = new File(world, "DIM1/region");
                loopRegion(regionsName.get(world.getName()), regionFolder, world.getName());
            }
            else {
                File regionFolder = getRegionFolder(world);
                //new File(world, "region");
                loopRegion(regionsName.get(world.getName()), regionFolder, world.getName());
            }
        }
    }

    private static boolean isWorldDirectory(File file) {
        if(!file.isDirectory()) return false;
        return new File(file, "level.dat").exists();
    }

    private static File getRegionFolder(File file) {

        // recursive loop in world directory until find the region folder (contains .mca files)

        for(File subFile : file.listFiles()) {
            if(subFile.isDirectory()) {
                File folder = getRegionFolder(subFile);
                if(folder != null)
                    return folder;
            }
            else if(subFile.getName().endsWith(".mca")) {
                return subFile.getParentFile();
            }
        }

        return null;
    }

    private static void loopRegion(List<String> regionsToSave, File regionFolder, String worldName) {
        if(regionsToSave == null)
            return;
        if(!new File(outputFolder, worldName).exists()) {
            new File(outputFolder, worldName).mkdirs();
        }
        for(File region : Objects.requireNonNull(regionFolder.listFiles())) {
            if(region.getName().endsWith(".mca")) {
                if(regionsToSave.contains(region.getName())) {
                    try {
                        File output = new File(outputFolder, worldName + File.separator + region.getName());
                        System.out.println("Input - File: " + region + " path: " + region.getAbsolutePath());
                        System.out.println("Output - File: " + output + " path: " + output.getAbsolutePath());
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
}
