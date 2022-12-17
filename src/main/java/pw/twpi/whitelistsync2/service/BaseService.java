package pw.twpi.whitelistsync2.service;

import pw.twpi.whitelistsync2.models.WhitelistedPlayer;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;

import java.util.ArrayList;

public interface BaseService {

    public boolean initializeDatabase();


    // Getter functions
    public ArrayList<WhitelistedPlayer> getWhitelistedPlayersFromDatabase();

    public ArrayList<WhitelistedPlayer> getWhitelistedPlayersFromLocal();


    // Syncing functions
    public boolean copyLocalWhitelistedPlayersToDatabase();

    public boolean copyDatabaseWhitelistedPlayersToLocal(Server server);


    // Addition functions
    public boolean addWhitelistPlayer(OfflinePlayer player);
    public boolean updateWhitelistPlayerToID(String name, String uuid);
    public boolean updateWhitelistPlayerName(String name, String uuid);


    // Removal functions
    public boolean removeWhitelistPlayer(OfflinePlayer player);
}
