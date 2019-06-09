package com.potatosaucevfx.whitelistsync2.service;

import com.potatosaucevfx.whitelistsync2.json.OPlistRead;
import com.potatosaucevfx.whitelistsync2.json.WhitelistRead;
import com.potatosaucevfx.whitelistsync2.models.OpUser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.logging.Logger;

public class SqLiteService implements BaseService {

    private File databaseFile;
    private Connection conn = null;
    private JavaPlugin plugin;
    private FileConfiguration config;
    private Logger logger;

    private String databasePath;
    private boolean logRemoteChanges;
    private boolean syncOps;

    public SqLiteService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.logger = plugin.getLogger();
        plugin.getLogger().info("Setting up the SQLITE service...");

        this.databasePath = config.getString("sqlite.database-path");
        this.syncOps = config.getBoolean("general.sync-ops");
        this.logRemoteChanges = config.getBoolean("general.log-remote-changes");

        this.databaseFile = new File(databasePath);
        loadDatabase();
    }

    /**
     * Method to load database on startup.
     *
     * @return success
     */
    private boolean loadDatabase() {
        // If database does not exist.
        if (!databaseFile.exists()) {
            createNewDatabase();
        }

        // Create whitelist table if it doesn't exist.
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            logger.info("Connected to SQLite database successfully!");

            // SQL statement for creating a new table
            String sql = "CREATE TABLE IF NOT EXISTS whitelist (\n"
                    + "	uuid text NOT NULL PRIMARY KEY,\n"
                    + "	name text,\n"
                    + " whitelisted integer NOT NULL);";
            Statement stmt = conn.createStatement();
            stmt.execute(sql);

            if (syncOps) {
                // SQL statement for creating a new table
                sql = "CREATE TABLE IF NOT EXISTS op (\n"
                        + "	uuid text NOT NULL PRIMARY KEY,\n"
                        + "	name text,\n"
                        + "	level integer,\n"
                        + "	bypassesPlayerLimit integer,\n"
                        + " isOp integer NOT NULL);";
                Statement stmt2 = conn.createStatement();
                stmt2.execute(sql);
            }
            conn.close();
            return true;
        } catch (SQLException e) {
            logger.severe("Error creating op or whitelist table!\n" + e.getMessage());
            return false;
        }

    }
    
    /**
     * Pushes local json whitelist to the database
     *
     * @param server
     * @return success
     */
    @Override
    public boolean pushLocalWhitelistToDatabase(Server server) {
        // Load local whitelist to memory.
        ArrayList<String> uuids = WhitelistRead.getWhitelistUUIDs();
        ArrayList<String> names = WhitelistRead.getWhitelistNames();

        // Start job on thread to avoid lag.
        // Keep track of records.
        int records = 0;
        try {
            // Connect to database.
            Connection conn1 = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            Statement stmt = conn1.createStatement();
            long startTime = System.currentTimeMillis();
            // Loop through local whitelist and insert into database.
            for (int i = 0; i < uuids.size() || i < names.size(); i++) {
                if ((uuids.get(i) != null) && (names.get(i) != null)) {
                    PreparedStatement sql = conn1.prepareStatement("INSERT OR REPLACE INTO whitelist(uuid, name, whitelisted) VALUES (?, ?, 1)");
                    sql.setString(1, uuids.get(i));
                    sql.setString(2, names.get(i));
                    sql.executeUpdate();

                    records++;
                }
            }
            // Record time taken.
            long timeTaken = System.currentTimeMillis() - startTime;
            stmt.close();
            conn1.close();

            return true;
        } catch (SQLException e) {
            logger.severe("Failed to update database with local records.\n" + e.getMessage());
            return false;
        }
    }

    /**
     * Pushes local json op list to the database
     *
     * @param server
     * @return success
     */
    @Override
    public boolean pushLocalOpListToDatabase(Server server) {
        // Load local ops to memory.
        ArrayList<OpUser> opUsers = OPlistRead.getOppedUsers();

        // Start job on thread to avoid lag.
        // Keep track of records.
        int records = 0;
        try {
            // Connect to database.
            Connection conn1 = DriverManager.getConnection("jdbc:sqlite:" + databasePath);

            // If syncing op list
            if (syncOps) {
                records = 0;
                long opStartTime = System.currentTimeMillis();
                // Loop through ops list and add to DB
                for (OpUser opUser : opUsers) {
                    try {
                        PreparedStatement sql = conn1.prepareStatement("INSERT IGNORE INTO op(uuid, name, level, bypassesPlayerLimit, isOp) VALUES (?, ?, ?, ?, 1)");
                        sql.setString(1, opUser.getUuid());
                        sql.setString(2, opUser.getName());
                        sql.setInt(3, opUser.getLevel());
                        sql.setInt(4, opUser.isBypassesPlayerLimit() ? 1 : 0);
                        sql.executeUpdate();
                        records++;
                    } catch (ClassCastException e) {
                        e.printStackTrace();
                    }
                    records++;
                }
                // Record time taken.
                long opTimeTaken = System.currentTimeMillis() - opStartTime;
                logger.info("Wrote " + records + " to op table in " + opTimeTaken + "ms.");
            } else {
                // If op syncing not enabled
                logger.severe("Op list syncing is currently disabled in your config. "
                        + "Please enable it and restart the server to use this feature");
            }
            conn1.close();
            return true;

        } catch (SQLException e) {
            logger.severe("Failed to update database with local records.\n" + e.getMessage());
            return false;
        }
    }

    /**
     * Pull uuids of whitelisted players from database
     *
     * @param server
     * @return List of whitelisted player uuids
     */
    @Override
    public ArrayList<String> pullWhitelistedUuidsFromDatabase(Server server) {
        // ArrayList for uuids.
        ArrayList<String> uuids = new ArrayList<>();

        try {
            // Keep track of records.
            int records = 0;

            // Connect to database.
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            Statement stmt = conn.createStatement();
            String sql = "SELECT uuid, whitelisted FROM whitelist;";

            // Start time of querry.
            long startTime = System.currentTimeMillis();

            stmt.execute(sql);
            ResultSet rs = stmt.executeQuery(sql);

            // Add querried results to arraylist.
            while (rs.next()) {
                if (rs.getInt("whitelisted") == 1) {
                    uuids.add(rs.getString("uuid"));
                }
                records++;
            }

            // Time taken
            long timeTaken = System.currentTimeMillis() - startTime;


            stmt.close();
            conn.close();
        } catch (SQLException e) {
            logger.severe("Error querrying uuids from database!\n" + e.getMessage());
        }
        return uuids;
    }

    /**
     * Pull uuids of opped players from database
     *
     * @param server
     * @return List of opped player uuids
     */
    @Override
    public ArrayList<String> pullOpUuidsFromDatabase(Server server) {

        if (syncOps) {

            // ArrayList for uuids.
            ArrayList<String> uuids = new ArrayList<>();

            try {
                // Keep track of records.
                int records = 0;

                // Connect to database.
                Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                Statement stmt = conn.createStatement();
                String sql = "SELECT uuid, isOp FROM op;";

                // Start time of querry.
                long startTime = System.currentTimeMillis();

                stmt.execute(sql);
                ResultSet rs = stmt.executeQuery(sql);

                // Add querried results to arraylist.
                while (rs.next()) {
                    if (rs.getInt("isOp") == 1) {
                        uuids.add(rs.getString("uuid"));
                    }
                    records++;
                }

                // Time taken
                long timeTaken = System.currentTimeMillis() - startTime;


                stmt.close();
                conn.close();
            } catch (SQLException e) {
                logger.severe("Error querrying uuids from sqlite database!\n" + e.getMessage());
            }
            return uuids;

        } else {

            logger.severe("Op list syncing is currently disabled in your config. "
                    + "Please enable it and restart the server to use this feature");

            return new ArrayList<>();
        }
    }

    /**
     * Pull names of whitelisted players from database
     *
     * @param server
     * @return List of whitelisted players names
     */
    @Override
    public ArrayList<String> pullWhitelistedNamesFromDatabase(Server server) {
        // ArrayList for names.
        ArrayList<String> names = new ArrayList<>();

        try {

            // Keep track of records.
            int records = 0;

            // Connect to database.
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            Statement stmt = conn.createStatement();
            String sql = "SELECT name, whitelisted FROM whitelist;";

            // Start time of querry.
            long startTime = System.currentTimeMillis();

            stmt.execute(sql);
            ResultSet rs = stmt.executeQuery(sql);

            // Save querried return to names list.
            while (rs.next()) {
                if (rs.getInt("whitelisted") == 1) {
                    names.add(rs.getString("name"));
                }
                records++;
            }

            // Total time taken.
            long timeTaken = System.currentTimeMillis() - startTime;


            stmt.close();
            conn.close();
        } catch (SQLException e) {
            logger.severe("Error querrying names from database!\n" + e.getMessage());
        }
        return names;
    }

    /**
     * Pull names of opped players from database
     *
     * @param server
     * @return List of opped players names
     */
    @Override
    public ArrayList<String> pullOppedNamesFromDatabase(Server server) {

        if (syncOps) {
            // ArrayList for names.
            ArrayList<String> names = new ArrayList<>();

            try {

                // Keep track of records.
                int records = 0;

                // Connect to database.
                Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                Statement stmt = conn.createStatement();
                String sql = "SELECT name, isOp FROM op;";

                // Start time of querry.
                long startTime = System.currentTimeMillis();

                stmt.execute(sql);
                ResultSet rs = stmt.executeQuery(sql);

                // Save querried return to names list.
                while (rs.next()) {
                    if (rs.getInt("isOp") == 1) {
                        names.add(rs.getString("name"));
                    }
                    records++;
                }

                // Total time taken.
                long timeTaken = System.currentTimeMillis() - startTime;


                stmt.close();
                conn.close();
            } catch (SQLException e) {
                logger.severe("Error querrying names from database!\n" + e.getMessage());
            }
            return names;

        } else {
            logger.severe("Op list syncing is currently disabled in your config. "
                    + "Please enable it and restart the server to use this feature");

            return new ArrayList<>();
        }
    }

    /**
     * Method adds player to whitelist in database
     *
     * @param player
     * @return success
     */
    // TODO: Add some sort of feedback
    @Override
    public boolean addPlayerToDatabaseWhitelist(OfflinePlayer player) {

        try {
            // Open connection
            Connection conn1 = DriverManager.getConnection("jdbc:sqlite:" + databasePath);

            // Start time.
            long startTime = System.currentTimeMillis();

            PreparedStatement sql = conn1.prepareStatement("INSERT OR REPLACE INTO whitelist(uuid, name, whitelisted) VALUES (?, ?, 1)");
            sql.setString(1, player.getUniqueId().toString());
            sql.setString(2, player.getName());
            sql.executeUpdate();

            // Time taken.
            long timeTaken = System.currentTimeMillis() - startTime;
            conn1.close();
            return true;

        } catch (SQLException e) {
            logger.severe("Error adding " + player.getName() + " to whitelist database!\n" + e.getMessage());
            return false;
        }
    }

    /**
     * Method removes player from whitelist in database
     *
     * @param player
     * @return success
     */
    // TODO: Add some sort of feedback
    @Override
    public boolean removePlayerFromDatabaseWhitelist(OfflinePlayer player) {
        try {
            // Open connection
            Connection conn1 = DriverManager.getConnection("jdbc:sqlite:" + databasePath);

            // Start time.
            long startTime = System.currentTimeMillis();

            PreparedStatement sql = conn1.prepareStatement("INSERT OR REPLACE INTO whitelist(uuid, name, whitelisted) VALUES (?, ?, 0)");
            sql.setString(1, player.getUniqueId().toString());
            sql.setString(2, player.getName());
            sql.executeUpdate();

            // Time taken
            long timeTaken = System.currentTimeMillis() - startTime;
            conn1.close();
            return true;

        } catch (SQLException e) {
            logger.severe("Error removing " + player.getName() + " from whitelist database!\n" + e.getMessage());
            return false;
        }
    }

    /**
     * Method adds player to op list in database
     *
     * @param player
     * @return success
     */
    // TODO: Add some sort of feedback
    @Override
    public boolean addPlayerToDatabaseOp(OfflinePlayer player) {
        try {
            ArrayList<OpUser> oppedUsers = OPlistRead.getOppedUsers();
            // Open connection
            Connection conn1 = DriverManager.getConnection("jdbc:sqlite:" + databasePath);

            int playerOpLevel = 1;
            int isBypassesPlayerLimit = 1;

            for (OpUser opUser : oppedUsers) {
                if (opUser.getUuid().equalsIgnoreCase(player.getUniqueId().toString())) {
                    playerOpLevel = opUser.getLevel();
                    isBypassesPlayerLimit = opUser.isBypassesPlayerLimit() ? 1 : 0;
                }
            }

            // Start time.
            long startTime = System.currentTimeMillis();

            PreparedStatement sql = conn1.prepareStatement("INSERT OR REPLACE INTO op(uuid, name, level, isOp, bypassesPlayerLimit) VALUES (?, ?, ?, 1, ?)");
            sql.setString(1, player.getUniqueId().toString());
            sql.setString(2, player.getName());
            sql.setInt(3, playerOpLevel);
            sql.setInt(4, isBypassesPlayerLimit);
            sql.executeUpdate();

            // Time taken.
            long timeTaken = System.currentTimeMillis() - startTime;
            conn1.close();
            return true;

        } catch (SQLException e) {
            logger.severe("Error adding " + player.getName() + " to op database!\n" + e.getMessage());
            return false;
        }
    }

    /**
     * Method removes player from op list in database
     *
     * @param player
     * @return success
     */
    // TODO: Add some sort of feedback
    @Override
    public boolean removePlayerFromDatabaseOp(OfflinePlayer player) {
        try {
            // Open connection
            Connection conn1 = DriverManager.getConnection("jdbc:sqlite:" + databasePath);

            //String sql = "INSERT OR REPLACE INTO op(uuid, name, isOp) VALUES (\'" + player.getId() + "\', \'" + player.getName() + "\', 0);";
            // Start time.
            long startTime = System.currentTimeMillis();

            PreparedStatement sql = conn1.prepareStatement("INSERT OR REPLACE INTO op(uuid, name, isOp) VALUES (?, ?, 0)");
            sql.setString(1, player.getUniqueId().toString());
            sql.setString(2, player.getName());
            sql.executeUpdate();

            // Time taken
            long timeTaken = System.currentTimeMillis() - startTime;
            conn1.close();
            return true;

        } catch (SQLException e) {
            logger.severe("Error removing " + player.getName() + " from op database!\n" + e.getMessage());
            return false;
        }
    }

    /**
     * Method pulls whitelist from database and merges it into the local
     * whitelist
     *
     * @param server
     * @return success
     */
    @Override
    public boolean updateLocalWhitelistFromDatabase(Server server) {
        try {
            int records = 0;
            Connection conn1 = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            Statement stmt = conn1.createStatement();
            String sql = "SELECT name, uuid, whitelisted FROM whitelist;";
            long startTime = System.currentTimeMillis();
            stmt.execute(sql);
            ResultSet rs = stmt.executeQuery(sql);
            ArrayList<String> localUuids = WhitelistRead.getWhitelistUUIDs();
            while (rs.next()) {
                int whitelisted = rs.getInt("whitelisted");
                String uuid = rs.getString("uuid");
                String name = rs.getString("name");

                if (whitelisted == 1) {
                    if (!localUuids.contains(uuid)) {
                        try {
                            Bukkit.getOfflinePlayer(uuid).setWhitelisted(true);

                            if (logRemoteChanges) {
                                logger.info("Added " + name + " to whitelist.");
                            }

                        } catch (NullPointerException e) {
                            logger.severe("Player is null?\n" + e.getMessage());
                        }
                    }
                } else {
                    if (localUuids.contains(uuid)) {
                        Bukkit.getOfflinePlayer(uuid).setWhitelisted(false);

                        if (logRemoteChanges) {
                            logger.info("Removed " + name + " from whitelist.");
                        }
                    }
                }
                records++;
            }
            long timeTaken = System.currentTimeMillis() - startTime;
            //logger.info("Local whitelist.json up to date!");

            stmt.close();
            conn1.close();
            return true;

        } catch (SQLException e) {
            logger.severe("Error querying whitelisted players from database!\n" + e.getMessage());
            return false;

        }
    }

    /**
     * Method pulls op list from database and merges it into the local op list
     *
     * @param server
     * @return success
     */
    @Override
    public boolean updateLocalOpListFromDatabase(Server server) {
        try {
            int records = 0;
            Connection conn1 = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            Statement stmt = conn1.createStatement();
            String sql = "SELECT name, uuid, isOp FROM op;";
            long startTime = System.currentTimeMillis();
            stmt.execute(sql);
            ResultSet rs = stmt.executeQuery(sql);
            ArrayList<String> localUuids = OPlistRead.getOpsUUIDs();
            while (rs.next()) {

                String uuid = rs.getString("uuid");
                String name = rs.getString("name");
                int opped = rs.getInt("isOp");

                if (opped == 1) {
                    if (!localUuids.contains(uuid)) {
                        try {
                            Bukkit.getOfflinePlayer(uuid).setOp(true);

                            if (logRemoteChanges) {
                                logger.info("Opped " + name + ".");
                            }

                        } catch (NullPointerException e) {
                            logger.severe("Player is null?\n" + e.getMessage());
                        }
                    }
                } else {
                    if (localUuids.contains(uuid)) {
                        Bukkit.getOfflinePlayer(uuid).setOp(false);
                        if (logRemoteChanges) {
                            logger.info("Deopped " + name + ".");
                        }

                    }
                }
                records++;
            }
            long timeTaken = System.currentTimeMillis() - startTime;
            //logger.info("Local ops.json up to date!");

            stmt.close();
            conn1.close();
            return true;

        } catch (SQLException e) {
            logger.severe("Error querying whitelisted players from database!\n" + e.getMessage());
            return false;
        }
    }

    /**
     * Method to create a new sqlite database file
     */
    private void createNewDatabase() {
        String url = "jdbc:sqlite:" + databasePath;
        try {
            Connection conn = DriverManager.getConnection(url);
            if (conn != null) {
                logger.info("A new database \"" + databasePath + "\" has been created.");
            }
        } catch (SQLException e) {
            logger.severe("Error creating non-existing database!\n" + e.getMessage());
        }
    }

}
