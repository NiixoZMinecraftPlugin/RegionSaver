package fr.niixoz.regionSaver.managers;

import fr.niixoz.regionSaver.RegionSaver;
import fr.niixoz.regionSaver.command.RegionSaverCommand;

public class CommandsManager {

    public static void registerCommands() {
        RegionSaver plugin = RegionSaver.getInstance();
        plugin.getCommand("regionsaver").setExecutor(new RegionSaverCommand());

    }
}
