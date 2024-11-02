package fr.niixoz.regionSaver.command;

import fr.niixoz.regionSaver.permissions.PermissionEnum;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public abstract class AbstractCommand implements CommandExecutor, TabCompleter {

    protected String name;
    protected String description;
    protected String usage;
    protected String permission;

    public AbstractCommand(String name, String description, String usage, PermissionEnum permission) {
        this.name = name;
        this.description = description;
        this.usage = usage;
        this.permission = permission.getPermission();
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {
        if(!(commandSender instanceof Player player))
            return true;
        try {
            if(!commandSender.hasPermission(permission)) {
                player.sendMessage("§cTu n'a pas la permission pour executer cette commande.");
                return true;
            }
            return executeCommand(player, command, s, args);
        } catch(Exception e){
            e.printStackTrace();
            player.sendMessage("[RegionSaver] Error while executing command.");
        }
        return true;
    }

    public abstract boolean executeCommand(Player player, Command command, String s, String[] args) throws Exception;

    protected void usage() {}
    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public String getUsage() {
        return this.usage;
    }

    public String getPermission() {
        return this.permission;
    }

    public void sendUsage(CommandSender commandSender) {
        commandSender.sendMessage("§cUsage: " + this.usage);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if(!(sender instanceof Player))
            return null;

        if(args.length == 1) {
            return null;
        }

        return Arrays.asList("");
    }
}
