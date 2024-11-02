package fr.niixoz.regionSaver.command;

import fr.niixoz.regionSaver.RegionSaver;
import fr.niixoz.regionSaver.permissions.PermissionEnum;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class RegionSaverCommand extends AbstractCommand {

    public RegionSaverCommand() {
        super("regionsaver", "Permet d'ouvrir une table de craft.", "/RegionSaver", PermissionEnum.ADMIN_REGION_SAVE);
    }

    @Override
    public boolean executeCommand(Player player, Command command, String s, String[] args) {

        if(args.length == 0) {
            player.sendMessage("[RegionSaver] Utilise /RegionSaver <save> pour faire une save des regions.");
            return true;
        }

        if(args[0].equalsIgnoreCase("save")) {
            player.sendMessage("[RegionSaver] Sauvegarde des regions...");
            // Run asynchronously
            Bukkit.getScheduler().runTaskAsynchronously(RegionSaver.getInstance(), () -> {
                RegionSaver.saveRegions();
                player.sendMessage("[RegionSaver] Sauvegarde terminée.");
            });
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if(!(sender instanceof Player))
            return null;

        if(args.length == 1) {
            return null;
        }

        return Arrays.asList("save");
    }
}
