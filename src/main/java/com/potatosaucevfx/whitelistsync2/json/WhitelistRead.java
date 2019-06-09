package com.potatosaucevfx.whitelistsync2.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.potatosaucevfx.whitelistsync2.WhitelistSync2;
import com.potatosaucevfx.whitelistsync2.models.WhitelistUser;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class WhitelistRead {

    private static JsonParser parser = new JsonParser();

    // Get whitelisted uuids as a string array list
    public static ArrayList getWhitelistUUIDs() {
        ArrayList<String> uuids = new ArrayList<>();
        // OMG ITS A LAMBDA EXPRESSION!!! :D
        getWhitelistJson().forEach(emp -> parseToList(emp.getAsJsonObject(), uuids, "uuid"));
        return uuids;
    }

    // Get whitelisted usernames as a string array list
    public static ArrayList getWhitelistNames() {
        ArrayList<String> names = new ArrayList<>();
        // WOAH ITS A LAMBDA EXPRESSION!!! CRAZY COMPLEX STUFF GOIN ON RIGHT HERE!!! :D
        getWhitelistJson().forEach(emp -> parseToList(emp.getAsJsonObject(), names, "name"));
        return names;
    }

    // Get Arraylist of whitelisted users on server.
    public static ArrayList<WhitelistUser> getWhitelistUsers() {
        ArrayList<WhitelistUser> users = new ArrayList<>();
        // HOLY SHIT.. ANOTHER LAMBDA EXPRESSION!!!!
        getWhitelistJson().forEach((user) -> {
            String uuid = ((JsonObject) user).get("uuid").getAsString();
            String name = ((JsonObject) user).get("name").getAsString();
            users.add(new WhitelistUser(uuid, name));
        });
        return users;
    }

    private static void parseToList(JsonObject whitelist, List list, String key) {
        list.add(whitelist.get(key).getAsString());
    }

    private static JsonArray getWhitelistJson() {
        JsonArray whitelist = null;
        try {


            whitelist = (JsonArray) parser.parse(new FileReader(WhitelistSync2.SERVER_FILEPATH + "/whitelist.json"));


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (JsonParseException e) {
            e.printStackTrace();
        }

        return whitelist;
    }

}
