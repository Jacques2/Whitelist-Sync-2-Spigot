package pw.twpi.whitelistsync2.Listener;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;

public class TabCompleter implements org.bukkit.command.TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] strings) {
        if (strings.length < 1) {
            return Arrays.asList("list", "add", "remove", "sync", "copyservertodatabase");
        }
        return null;
    }
}
