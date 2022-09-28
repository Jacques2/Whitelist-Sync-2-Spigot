package pw.twpi.whitelistsync2.Listener;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import pw.twpi.whitelistsync2.WhitelistSync2;
import pw.twpi.whitelistsync2.models.WhitelistedPlayer;

import java.util.Locale;
import java.util.UUID;

public class PlayerPreLoginListener implements Listener {
    private final WhitelistSync2 plugin;

    public PlayerPreLoginListener(WhitelistSync2 plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        boolean verified = false;
        String username = event.getName().toLowerCase();
        UUID uuid = event.getUniqueId();

        for (WhitelistedPlayer player : WhitelistedPlayer.whitelistedPlayers){
            if (player.getName().toLowerCase().equals(username.toLowerCase()) && !player.isIDWhitelisted()){
                verified = true;
                player.setUuid(String.valueOf(uuid));
                player.setIDWhitelisted(true);
                WhitelistSync2.LOGGER.info(username + " connected with username, recording UUID");
                WhitelistSync2.whitelistService.updateWhitelistPlayerToID(username,String.valueOf(uuid));
                break;
            }
            if (String.valueOf(uuid).equals(player.getUuid()) && player.isIDWhitelisted()){
                verified = true;
                WhitelistSync2.LOGGER.info(username + " connected with UUID");
                break;
            }
        }
        if (!verified){
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
            event.setKickMessage("You are not whitelisted for Divergent SMP!");
        }
    }
}