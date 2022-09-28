package pw.twpi.whitelistsync2.models;


import java.util.ArrayList;

/**
 * DAO for a whitelisted user
 * @author Richard Nader, Jr. <rmnader@svsu.edu>
 */
public class WhitelistedPlayer {

    private String uuid;
    private String name;
    private boolean isIDWhitelisted;

    public static ArrayList<WhitelistedPlayer> whitelistedPlayers = new ArrayList<>();

    public WhitelistedPlayer() {
    }

    public WhitelistedPlayer(String uuid, String name, boolean isWhitelisted) {
        this.uuid = uuid;
        this.name = name;
        this.isIDWhitelisted = isWhitelisted;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isIDWhitelisted() {
        return isIDWhitelisted;
    }

    public void setIDWhitelisted(boolean IDWhitelisted) {
        isIDWhitelisted = IDWhitelisted;
    }

    @Override
    public String toString() {
        return "WhitelistedPlayer{" +
                "uuid='" + uuid + '\'' +
                ", name='" + name + '\'' +
                ", isWhitelisted=" + isIDWhitelisted +
                '}';
    }
}
