package net.pandadev.vitelist;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import org.spongepowered.configurate.ConfigurateException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VlistCommand implements SimpleCommand {

    private final Main plugin;

    public VlistCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!source.hasPermission("vitelist.*")) {
            source.sendMessage(Component.text(Main.getPrefix() + "§cYou don't have permission to use this command"));
            return;
        }
        if (args.length < 1) {
            source.sendMessage(Component.text(Main.getPrefix() + "§cInvalid usage"));
            return;
        }
        switch (args[0].toLowerCase()) {
            case "add":
                if (!source.hasPermission("vitelist.add")) {
                    source.sendMessage(Component.text(Main.getPrefix() + "§cYou don't have permission to use this command"));
                    return;
                }
                if (args.length < 2) {
                    source.sendMessage(Component.text(Main.getPrefix() + "§cPlease specify a player name to add"));
                    return;
                }
                CompletableFuture.runAsync(() -> {
                    try {
                        String uuid = getUUID(args[1]);
                        if (uuid != null) {
                            addUuidToWhitelist(uuid, source);
                        } else {
                            source.sendMessage(Component.text(Main.getPrefix() + "§cCould not find UUID for player name"));
                        }
                    } catch (Exception e) {
                        source.sendMessage(Component.text(Main.getPrefix() + "§cAn error occurred while processing the command: " + e.getMessage()));
                    }
                });
                break;
            case "remove":
                if (!source.hasPermission("vitelist.remove")) {
                    source.sendMessage(Component.text(Main.getPrefix() + "§cYou don't have permission to use this command"));
                    return;
                }
                if (args.length < 2) {
                    source.sendMessage(Component.text(Main.getPrefix() + "§cPlease specify a player name to remove"));
                    return;
                }
                CompletableFuture.runAsync(() -> {
                    try {
                        String uuid = getUUID(args[1]);
                        if (uuid != null) {
                            removeUuidFromWhitelist(uuid, source);
                        } else {
                            source.sendMessage(Component.text(Main.getPrefix() + "§cCould not find UUID for player name"));
                        }
                    } catch (Exception e) {
                        source.sendMessage(Component.text(Main.getPrefix() + "§cAn error occurred while processing the command: " + e.getMessage()));
                    }
                });
                break;
            case "on":
                if (!source.hasPermission("vitelist.on")) {
                    source.sendMessage(Component.text(Main.getPrefix() + "§cYou don't have permission to use this command"));
                    return;
                }
                plugin.setWhitelistEnabled(true);
                source.sendMessage(Component.text(Main.getPrefix() + "§7Vitelist enabled"));
                break;
            case "off":
                if (!source.hasPermission("vitelist.off")) {
                    source.sendMessage(Component.text(Main.getPrefix() + "§cYou don't have permission to use this command"));
                    return;
                }
                plugin.setWhitelistEnabled(false);
                source.sendMessage(Component.text(Main.getPrefix() + "§7Vitelist disabled"));
                break;
            case "list":
                if (!source.hasPermission("vitelist.list")) {
                    source.sendMessage(Component.text(Main.getPrefix() + "§cYou don't have permission to use this command"));
                    return;
                }
                CompletableFuture.runAsync(() -> {
                    try {
                        List<String> uuids = getWhitelistedUuids();
                        if (!uuids.isEmpty()) {
                            List<String> playerNames = new ArrayList<>();
                            for (String uuid : uuids) {
                                String playerName = getNameFromUUID(uuid);
                                if (playerName != null) {
                                    playerNames.add(playerName);
                                }
                            }
                            if (!playerNames.isEmpty()) {
                                String playerNameList = String.join(", ", playerNames);
                                source.sendMessage(Component.text("§8----- [ §d§lVitelisted players §8] -----"));
                                source.sendMessage(Component.text(""));
                                for (String player : playerNames) {
                                    source.sendMessage(Component.text("§7" + player));
                                }
                                source.sendMessage(Component.text(""));
                                source.sendMessage(Component.text("§8--------------------------------"));
                            } else {
                                source.sendMessage(Component.text(Main.getPrefix() + "§7No players are currently vitelisted"));
                            }
                        } else {
                            source.sendMessage(Component.text(Main.getPrefix() + "§7No UUIDs are currently vitelisted"));
                        }
                    } catch (Exception e) {
                        source.sendMessage(Component.text(Main.getPrefix() + "§cAn error occurred while processing the command: " + e.getMessage()));
                    }
                });
                break;
            default:
                source.sendMessage(Component.text(Main.getPrefix() + "§cInvalid command"));
        }
    }

    private List<String> getWhitelistedUuids() throws ConfigurateException {
        var root = plugin.getLoader().load();
        return root.node("whitelisted-uuids").getList(String.class);
    }

    private String getNameFromUUID(String uuid) {
        try {
            URL url = new URL("https://playerdb.co/api/player/minecraft/" + uuid);
            return getPlayerNameFromAPI(url);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getUUID(String name) {
        try {
            URL url = new URL("https://playerdb.co/api/player/minecraft/" + name);
            return getPlayerUUIDFromAPI(url);
        } catch (Exception e) {
            System.out.println("Unable to get UUID for: " + name + " due to error: " + e.getMessage());
        }
        return null;
    }

    private static String getPlayerNameFromAPI(URL url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.connect();
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new RuntimeException("HttpResponseCode: " + responseCode);
        } else {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject data = jsonObject.getAsJsonObject("data");
                JsonObject player = data.getAsJsonObject("player");
                return player.get("username").getAsString();
            }
        }
    }

    private static String getPlayerUUIDFromAPI(URL url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.connect();
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new RuntimeException("HttpResponseCode: " + responseCode);
        } else {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject data = jsonObject.getAsJsonObject("data");
                JsonObject player = data.getAsJsonObject("player");
                return player.get("id").getAsString();
            }
        }
    }

    private void addUuidToWhitelist(String uuid, CommandSource source) {
        try {
            var root = plugin.getLoader().load();
            List<String> uuids = root.node("whitelisted-uuids").getList(String.class);
            assert uuids != null;
            if (!uuids.contains(uuid)) {
                uuids.add(uuid);
                root.node("whitelisted-uuids").set(uuids);
                plugin.getLoader().save(root);
                source.sendMessage(Component.text(Main.getPrefix() + "§7Added §a" + getNameFromUUID(uuid) + " §7to the vitelist"));
            } else {
                source.sendMessage(Component.text(Main.getPrefix() + "§a" + getNameFromUUID(uuid) + " §7is already on the vitelist"));
            }
        } catch (ConfigurateException e) {
            source.sendMessage(Component.text(Main.getPrefix() + "§cAn error occurred while processing the command: " + e.getMessage()));
        }
    }

    private void removeUuidFromWhitelist(String uuid, CommandSource source) {
        try {
            var root = plugin.getLoader().load();
            List<String> uuids = root.node("whitelisted-uuids").getList(String.class);
            assert uuids != null;
            if (uuids.remove(uuid)) {
                root.node("whitelisted-uuids").set(uuids);
                plugin.getLoader().save(root);
                source.sendMessage(Component.text(Main.getPrefix() + "§7Removed §a" + getNameFromUUID(uuid) + " §7from the vitelist"));
            } else {
                source.sendMessage(Component.text(Main.getPrefix() + "§a" + getNameFromUUID(uuid) + " §7not found on the vitelist"));
            }
        } catch (ConfigurateException e) {
            source.sendMessage(Component.text(Main.getPrefix() + "§cAn error occurred while processing the command: " + e.getMessage()));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0 || (args.length == 1 && args[0].isEmpty())) {
            return Stream.of("add", "remove", "on", "off", "list").collect(Collectors.toList());
        } else if (args.length == 1) {
            return Stream.of("add", "remove", "on", "off", "list")
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            return Stream.of("<player>").collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            return getWhitelistedPlayerNames();
        }
        return List.of();
    }

    private List<String> getWhitelistedPlayerNames() {
        try {
            List<String> uuids = getWhitelistedUuids();
            List<String> playerNames = new ArrayList<>();
            for (String uuid : uuids) {
                String playerName = getNameFromUUID(uuid);
                if (playerName != null) {
                    playerNames.add(playerName);
                }
            }
            return playerNames;
        } catch (ConfigurateException e) {
            e.printStackTrace();
            return List.of();
        }
    }
}