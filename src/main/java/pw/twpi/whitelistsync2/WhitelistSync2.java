package pw.twpi.whitelistsync2;

import org.bukkit.Bukkit;
import pw.twpi.whitelistsync2.Listener.PlayerPreLoginListener;
import pw.twpi.whitelistsync2.Listener.TabCompleter;
import pw.twpi.whitelistsync2.commands.CommandWhitelist;
import pw.twpi.whitelistsync2.service.BaseService;
import pw.twpi.whitelistsync2.service.MySqlService;
import pw.twpi.whitelistsync2.service.SqLiteService;
import pw.twpi.whitelistsync2.service.SyncThread;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public class WhitelistSync2 extends JavaPlugin {

    public static FileConfiguration CONFIG;
    public Server server = getServer();

    public static Logger LOGGER;
    public static String SERVER_FILEPATH;

    // Database Service
    public static BaseService whitelistService;

    @Override
    public void onEnable() {
        CONFIG = getConfig();
        LOGGER = getLogger();

        LOGGER.info("Setting up Whitelist Sync!");

        SERVER_FILEPATH = getServer().getWorldContainer().getAbsolutePath();
        Bukkit.getPluginManager().registerEvents(new PlayerPreLoginListener(this), this);

        LOGGER.info("Server Filepath: " + SERVER_FILEPATH);

        // Setup config
        LoadConfiguration();

        // Setup Services
        if(LoadServices()) {
            // Commands
            this.getCommand("wl").setExecutor(new CommandWhitelist(this, whitelistService));
            this.getCommand("wl").setTabCompleter(new TabCompleter());

            StartSyncThread(this, whitelistService);
        }
    }

    @Override
    public void onDisable() {
        LOGGER.info("Shutting down Whitelist Sync!");
    }

    public static void StartSyncThread(JavaPlugin plugin, BaseService service) {
        new SyncThread(plugin, service);
        plugin.getLogger().info("Sync Thread Started!");
    }

    public void LoadConfiguration() {
        CONFIG.options().copyDefaults(true);
        saveConfig();
    }

    public boolean LoadServices() {
        boolean setupSuccessful = true;


        if(CONFIG.getString("general.sync-mode").equalsIgnoreCase("SQLITE")) {
            whitelistService = new SqLiteService();
            LOGGER.info("Database setup!");
        }
        else if(CONFIG.getString("general.sync-mode").equalsIgnoreCase("MYSQL")) {
            whitelistService = new MySqlService();
            LOGGER.info("Database setup!");
        }
        else {
            LOGGER.severe("Please check what sync-mode is set in the config and make sure it is set to a supported mode!");
            LOGGER.severe("Failed to setup Whitelist Sync Database!");
            setupSuccessful = false;
        }

        if(!whitelistService.initializeDatabase() || !setupSuccessful) {
            LOGGER.severe("Error initializing whitelist sync database. Disabling mod functionality. Please correct errors and restart.");
            return false;
        } else {
            return true;
        }
    }

}
