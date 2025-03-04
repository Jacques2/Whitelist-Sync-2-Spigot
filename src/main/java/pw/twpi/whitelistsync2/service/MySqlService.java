package pw.twpi.whitelistsync2.service;

import pw.twpi.whitelistsync2.WhitelistSync2;
import pw.twpi.whitelistsync2.json.WhitelistedPlayersFileUtilities;
import pw.twpi.whitelistsync2.models.WhitelistedPlayer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;

import java.sql.*;
import java.util.ArrayList;
import java.util.UUID;

public class MySqlService implements BaseService {

    private final String databaseName;
    private final String url;
    private final String username;
    private final String password;

    public MySqlService() {
        this.databaseName = WhitelistSync2.CONFIG.getString("mysql.database-name");
        this.url = "jdbc:mysql://" + WhitelistSync2.CONFIG.getString("mysql.ip") + ":" + WhitelistSync2.CONFIG.getString("mysql.port") + "/?useSSL=false";
        this.username = WhitelistSync2.CONFIG.getString("mysql.username");
        this.password = WhitelistSync2.CONFIG.getString("mysql.password");
    }

    // Function used to initialize the database file
    @Override
    public boolean initializeDatabase() {
        WhitelistSync2.LOGGER.info("Setting up the MySQL service...");
        boolean isSuccess = true;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver").newInstance();
        } catch (Exception e) {
            WhitelistSync2.LOGGER.severe("Failed to init mysql-connector. Is the library missing?");
            e.printStackTrace();
            isSuccess = false;
        }


        if (isSuccess) {
            try {
                Connection conn = DriverManager.getConnection(url, username, password);
                WhitelistSync2.LOGGER.info("Connected to " + url + " successfully!");
                conn.close();
            } catch (SQLException e) {
                WhitelistSync2.LOGGER.severe("Failed to connect to the mySQL database! Did you set one up in the config?");
                e.printStackTrace();
                isSuccess = false;
            }
        }

        if (isSuccess) {
            // Create database
            try {

                // Create database
                String sql = "CREATE DATABASE IF NOT EXISTS " + databaseName + ";";

                // Create statement
                Connection conn = DriverManager.getConnection(url, username, password);
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.execute();
                stmt.close();

                // Create whitelist table
                sql = "CREATE TABLE IF NOT EXISTS " + databaseName + ".whitelist ("
                        + "`uuid` VARCHAR(60) NOT NULL,"
                        + "`name` VARCHAR(20) NOT NULL,"
                        + "`whitelisted` TINYINT NOT NULL DEFAULT 1,"
                        + "PRIMARY KEY (`name`)"
                        + ")";
                PreparedStatement stmt2 = conn.prepareStatement(sql);
                stmt2.execute();
                stmt2.close();

                // Create opped players table if enabled
                if (WhitelistSync2.CONFIG.getBoolean("general.sync-ops")) {
                    sql = "CREATE TABLE IF NOT EXISTS " + databaseName + ".op ("
                            + "`uuid` VARCHAR(60) NOT NULL,"
                            + "`name` VARCHAR(20) NOT NULL,"
                            + "`isOp` TINYINT NOT NULL DEFAULT 1,"
                            + "PRIMARY KEY (`uuid`)"
                            + ")";
                    PreparedStatement stmt3 = conn.prepareStatement(sql);
                    stmt3.execute();
                    stmt3.close();


                    // Remove old op level field if it exists
                    sql =
                            "SELECT COUNT(*) AS count " +
                                    "FROM INFORMATION_SCHEMA.COLUMNS " +
                                    "WHERE TABLE_SCHEMA = '" + databaseName + "' AND TABLE_NAME = 'op' AND COLUMN_NAME = 'level'";
                    PreparedStatement stmt4 = conn.prepareStatement(sql);
                    ResultSet rs = stmt4.executeQuery();
                    rs.next();

                    int count = rs.getInt("count");

                    if(count > 0) {
                        sql = "ALTER TABLE " + databaseName + ".op DROP COLUMN level";
                        PreparedStatement stmt5 = conn.prepareStatement(sql);
                        stmt5.execute();
                        stmt5.close();
                        WhitelistSync2.LOGGER.info("Removed unused op table \"level\" column.");
                    }
                    rs.close();
                    stmt4.close();


                    // Remove old op bypassesPlayerLimit field if it exists
                    sql =
                            "SELECT COUNT(*) AS count " +
                                    "FROM INFORMATION_SCHEMA.COLUMNS " +
                                    "WHERE TABLE_SCHEMA = '" + databaseName + "' AND TABLE_NAME = 'op' AND COLUMN_NAME = 'bypassesPlayerLimit'";
                    PreparedStatement stmt5 = conn.prepareStatement(sql);
                    ResultSet rs1 = stmt5.executeQuery();
                    rs1.next();

                    int count1 = rs1.getInt("count");

                    if(count1 > 0) {
                        sql = "ALTER TABLE " + databaseName + ".op DROP COLUMN bypassesPlayerLimit";
                        PreparedStatement stmt6 = conn.prepareStatement(sql);
                        stmt6.execute();
                        stmt6.close();
                        WhitelistSync2.LOGGER.info("Removed unused op table \"bypassesPlayerLimit\" column.");
                    }
                    rs1.close();
                    stmt5.close();

                }

                WhitelistSync2.LOGGER.info("Setup MySQL database!");
                conn.close();
            } catch (Exception e) {
                WhitelistSync2.LOGGER.severe("Error initializing database and database tables.");
                e.printStackTrace();
                isSuccess = false;
            }
        }


        return isSuccess;
    }

    @Override
    public ArrayList<WhitelistedPlayer> getWhitelistedPlayersFromDatabase() {
        // ArrayList for whitelisted players.
        ArrayList<WhitelistedPlayer> whitelistedPlayers = new ArrayList<>();

        try {
            // Keep track of records.
            int records = 0;

            // Connect to database.
            Connection conn = DriverManager.getConnection(url, username, password);
            long startTime = System.currentTimeMillis();

            String sql = "SELECT uuid, name, whitelisted FROM " + databaseName + ".whitelist;";
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();

            // Add queried results to arraylist.
            while (rs.next()) {
                whitelistedPlayers.add(new WhitelistedPlayer(rs.getString("uuid"), rs.getString("name"), true));
                records++;
            }

            // Time taken
            long timeTaken = System.currentTimeMillis() - startTime;

            //WhitelistSync2.LOGGER.debug("Database pulled whitelisted players | Took " + timeTaken + "ms | Read " + records + " records.");

            rs.close();
            stmt.close();
            conn.close();
        } catch (SQLException e) {
            // Something is wrong...
            WhitelistSync2.LOGGER.severe("Error querying whitelisted players from database!");
            e.printStackTrace();
        }
        return whitelistedPlayers;
    }

    @Override
    public ArrayList<WhitelistedPlayer> getWhitelistedPlayersFromLocal() {
        return WhitelistedPlayersFileUtilities.getWhitelistedPlayers();
    }

    @Override
    public boolean copyLocalWhitelistedPlayersToDatabase() {
        // Load local whitelist to memory.
        ArrayList<WhitelistedPlayer> whitelistedPlayers = WhitelistedPlayersFileUtilities.getWhitelistedPlayers();

        // TODO: Start job on thread to avoid lag?
        // Keep track of records.
        int records = 0;
        try {
            // Connect to database.
            Connection conn = DriverManager.getConnection(url, username, password);
            long startTime = System.currentTimeMillis();
            // Loop through local whitelist and insert into database.
            for (WhitelistedPlayer player : WhitelistedPlayer.whitelistedPlayers) {

                if (player.getUuid() != null && player.getName() != null) {
                    PreparedStatement stmt = conn.prepareStatement("REPLACE INTO " + databaseName + ".whitelist(uuid, name, whitelisted) VALUES (?, ?, ?)");
                    stmt.setString(1, player.getUuid());
                    stmt.setString(2, player.getName());
                    int idWhitelisted = player.isIDWhitelisted() ? 1 : 0;
                    stmt.setInt(3, idWhitelisted);
                    stmt.executeUpdate();
                    stmt.close();

                    records++;
                }
            }
            // Record time taken.
            long timeTaken = System.currentTimeMillis() - startTime;
            //WhitelistSync2.LOGGER.debug("Whitelist table updated | Took " + timeTaken + "ms | Wrote " + records + " records.");
            conn.close();

            return true;
        } catch (SQLException e) {
            WhitelistSync2.LOGGER.severe("Failed to update database with local records.");
            e.printStackTrace();
        }

        return false;
    }


    @Override
    public boolean copyDatabaseWhitelistedPlayersToLocal(Server server) {
        try {
            int records = 0;

            // Open connection
            Connection conn = DriverManager.getConnection(url, username, password);
            long startTime = System.currentTimeMillis();

            String sql = "SELECT name, uuid, whitelisted FROM " + databaseName + ".whitelist";
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();

            ArrayList<WhitelistedPlayer> localWhitelistedPlayers = WhitelistedPlayersFileUtilities.getWhitelistedPlayers();

            ArrayList<WhitelistedPlayer> newPlayers = new ArrayList<>();
            while (rs.next()) {
                String uuid = rs.getString("uuid");
                String name = rs.getString("name");
                int whitelisted = rs.getInt("whitelisted");
                boolean isIDwhitelisted = whitelisted == 1;

                newPlayers.add(new WhitelistedPlayer(uuid,name,isIDwhitelisted));
            }
            WhitelistedPlayer.whitelistedPlayers = newPlayers;
            long timeTaken = System.currentTimeMillis() - startTime;
            //WhitelistSync2.LOGGER.debug("Copied whitelist database to local | Took " + timeTaken + "ms | Wrote " + records + " records.");

            rs.close();
            stmt.close();
            conn.close();
            return true;
        } catch (SQLException e) {
            WhitelistSync2.LOGGER.warning("Error querying whitelisted players from database!");
            //e.printStackTrace();
        }

        return false;
    }


    @Override
    public boolean addWhitelistPlayer(OfflinePlayer player) {
        try {
            // Open connection=
            Connection conn = DriverManager.getConnection(url, username, password);
            long startTime = System.currentTimeMillis();

            String sql = "REPLACE INTO " + databaseName + ".whitelist(uuid, name, whitelisted) VALUES (?, ?, false)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, player.getUniqueId().toString());
            stmt.setString(2, player.getName());
            stmt.executeUpdate();

            // Time taken.
            long timeTaken = System.currentTimeMillis() - startTime;
            //WhitelistSync2.LOGGER.debug("Added " + player.getName() + " to whitelist | Took " + timeTaken + "ms");
            stmt.close();
            conn.close();
            return true;

        } catch (SQLException e) {
            WhitelistSync2.LOGGER.severe("Error adding " + player.getName() + " to whitelist database!");
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public boolean updateWhitelistPlayerToID(String name, String uuid) {
        try {
            // Open connection=
            Connection conn = DriverManager.getConnection(url, username, password);
            long startTime = System.currentTimeMillis();

            String sql = "UPDATE " + databaseName + ".whitelist SET uuid = '" + uuid + "', whitelisted = 1 WHERE name = '" + name + "';";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.execute();
            // Time taken.
            long timeTaken = System.currentTimeMillis() - startTime;
            stmt.close();
            conn.close();
            return true;

        } catch (SQLException e) {
            WhitelistSync2.LOGGER.severe("Error adding " + uuid + " to whitelist database!");
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public boolean updateWhitelistPlayerName(String name, String uuid) {
        try {
            // Open connection=
            Connection conn = DriverManager.getConnection(url, username, password);
            long startTime = System.currentTimeMillis();

            String sql = "UPDATE " + databaseName + ".whitelist SET name = '" + name + "' WHERE uuid = '" + uuid + "';";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.execute();
            // Time taken.
            long timeTaken = System.currentTimeMillis() - startTime;
            stmt.close();
            conn.close();
            return true;

        } catch (SQLException e) {
            WhitelistSync2.LOGGER.severe("Error adding " + uuid + " to whitelist database!");
            e.printStackTrace();
        }

        return false;
    }


    @Override
    public boolean removeWhitelistPlayer(OfflinePlayer player) {
        try {
            // Open connection=
            Connection conn = DriverManager.getConnection(url, username, password);
            long startTime = System.currentTimeMillis();

            String sql = "DELETE FROM " + databaseName + ".whitelist WHERE name = '" + player.getName() + "';";
            PreparedStatement stmt = conn.prepareStatement(sql);
            WhitelistedPlayer.whitelistedPlayers.removeIf(obj -> obj.getName().equals(player.getName()));
            stmt.executeUpdate();

            // Time taken.
            long timeTaken = System.currentTimeMillis() - startTime;
            //WhitelistSync2.LOGGER.debug("Removed " + player.getName() + " from whitelist | Took " + timeTaken + "ms");
            stmt.close();
            conn.close();
            return true;

        } catch (SQLException e) {
            WhitelistSync2.LOGGER.severe("Error removing " + player.getName() + " to whitelist database!");
            e.printStackTrace();
        }

        return false;
    }
}
