package net.pandadev.vitelist;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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

        if (!source.hasPermission("vitelist.*") && !source.hasPermission("vitelist.user")) {
            source.sendMessage(Component.text(Main.getPrefix() + "§cYou don't have permission to use this command"));
            return;
        }
        if (args.length < 1) {
            source.sendMessage(Component.text(Main.getPrefix() + "§cInvalid Usage! Available commands: add, remove, on, off, list"));
            return;
        }
        switch (args[0].toLowerCase()) {
            case "add":
                if (!source.hasPermission("vitelist.add") && !source.hasPermission("vitelist.*")) {
                    source.sendMessage(Component.text(Main.getPrefix() + "§cYou don't have permission to use this command"));
                    return;
                }
                if (args.length < 2) {
                    source.sendMessage(Component.text(Main.getPrefix() + "§cPlease specify a player name to add"));
                    return;
                }
                CompletableFuture.runAsync(() -> {
                    final String name = args[1];
                    try {
                        String uuid = getUUID(name);
                        if (uuid == null) {
                            if (isValidUUID(name)) {
                                uuid = name;
                            } else {
                                source.sendMessage(Component.text(Main.getPrefix() + "§cCould not find UUID for player name " + name));
                                return;
                            }
                        }
                        addUuidToWhitelist(uuid, source);
                    } catch (Exception e) {
                        source.sendMessage(Component.text(Main.getPrefix() + "§cAn error occurred while processing the command: " + e.getMessage()));
                    }
                });
                break;
            case "remove":
                if (!source.hasPermission("vitelist.remove") && !source.hasPermission("vitelist.*")) {
                    source.sendMessage(Component.text(Main.getPrefix() + "§cYou don't have permission to use this command"));
                    return;
                }
                if (args.length < 2) {
                    source.sendMessage(Component.text(Main.getPrefix() + "§cPlease specify a player name to remove"));
                    return;
                }
                final String name = args[1];
                plugin.getLogger().info("[DEBUG] /vlist remove command initiated for '" + name + "'.");
                plugin.getLogger().info("[DEBUG] Starting asynchronous task...");
                CompletableFuture.runAsync(() -> {
                    try {
                        plugin.getLogger().info("[DEBUG] Asynchronous task started.");
                        plugin.getLogger().info("[DEBUG] Getting UUID for '" + name + "'...");
                        String uuid = getUUID(name);
                        plugin.getLogger().info("[DEBUG] UUID received: " + uuid);

                        if (uuid == null) {
                            if (isValidUUID(name)) {
                                uuid = name;
                                plugin.getLogger().info("[DEBUG] Input is a valid UUID. Using it directly: " + uuid);
                            } else {
                                source.sendMessage(Component.text(Main.getPrefix() + "§cCould not find UUID for player name " + name));
                                plugin.getLogger().info("[DEBUG] Could not find UUID and input is not a valid UUID. Aborting.");
                                return;
                            }
                        }

                        final String finalUuid = uuid;
                        Component playerNameComponent = getNameFromUUID(finalUuid);

                        plugin.getLogger().info("[DEBUG] Loading whitelist file...");
                        var root = plugin.getLoader().load();
                        plugin.getLogger().info("[DEBUG] Whitelist file loaded.");
                        List<String> uuids = new ArrayList<>(root.node("whitelisted-uuids").getList(String.class));

                        if (uuids.remove(finalUuid)) {
                            plugin.getLogger().info("[DEBUG] UUID found and removed. Saving file...");
                            root.node("whitelisted-uuids").set(uuids);
                            plugin.getLoader().save(root);
                            plugin.getLogger().info("[DEBUG] File saved.");
                            source.sendMessage(Component.text(Main.getPrefix() + "§7Removed §a").append(playerNameComponent).append(Component.text(" §7from the vitelist")));
                        } else {
                            plugin.getLogger().info("[DEBUG] UUID not found in whitelist.");
                            source.sendMessage(Component.text(Main.getPrefix() + "§a").append(playerNameComponent).append(Component.text(" §7not found on the vitelist")));
                        }
                    } catch (Exception e) {
                        plugin.getLogger().error("[DEBUG] An error occurred in the asynchronous task.", e);
                        source.sendMessage(Component.text(Main.getPrefix() + "§cAn error occurred while processing the command: " + e.getMessage()));
                    }
                    plugin.getLogger().info("[DEBUG] Asynchronous task finished.");
                });
                break;
            case "on":
                if (!source.hasPermission("vitelist.list") && !source.hasPermission("vitelist.*")) {
                    source.sendMessage(Component.text(Main.getPrefix() + "§cYou don't have permission to use this command"));
                    return;
                }
                plugin.setWhitelistEnabled(true);
                source.sendMessage(Component.text(Main.getPrefix() + "§7Vitelist enabled"));
                break;
            case "off":
                if (!source.hasPermission("vitelist.off") && !source.hasPermission("vitelist.*")) {
                    source.sendMessage(Component.text(Main.getPrefix() + "§cYou don't have permission to use this command"));
                    return;
                }
                plugin.setWhitelistEnabled(false);
                source.sendMessage(Component.text(Main.getPrefix() + "§7Vitelist disabled"));
                break;
            case "list":
                if (!source.hasPermission("vitelist.on") && !source.hasPermission("vitelist.*")) {
                    source.sendMessage(Component.text(Main.getPrefix() + "§cYou don't have permission to use this command"));
                    return;
                }
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        source.sendMessage(Component.text(Main.getPrefix() + "§cInvalid page number."));
                        return;
                    }
                }

                final int finalPage = page;
                CompletableFuture.runAsync(() -> {
                    try {
                        List<String> uuids = getWhitelistedUuids();
                        if (uuids.isEmpty()) {
                            source.sendMessage(Component.text(Main.getPrefix() + "§7No players are currently vitelisted."));
                            return;
                        }

                        int playersPerPage = 10;
                        int totalPages = (int) Math.ceil((double) uuids.size() / playersPerPage);

                        if (finalPage < 1 || finalPage > totalPages) {
                            source.sendMessage(Component.text(Main.getPrefix() + "§cInvalid page number. Page must be between 1 and " + totalPages));
                            return;
                        }

                        int startIndex = (finalPage - 1) * playersPerPage;
                        int endIndex = Math.min(startIndex + playersPerPage, uuids.size());
                        List<String> pageUuids = uuids.subList(startIndex, endIndex);

                        List<Component> playerNames = new ArrayList<>();
                        for (String uuid : pageUuids) {
                            playerNames.add(getNameFromUUID(uuid));
                        }

                        source.sendMessage(Component.text("§8----- [ §d§lVitelisted players §7(Page " + finalPage + "/" + totalPages + ") §8] -----"));
                        source.sendMessage(Component.text(""));
                        for (Component player : playerNames) {
                            source.sendMessage(player);
                        }
                        source.sendMessage(Component.text(""));
                        source.sendMessage(Component.text("§8--------------------------------"));

                    } catch (Exception e) {
                        plugin.getLogger().error("Error in 'list' command", e);
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

    private Component getNameFromUUID(String uuid) {
        if (!isValidUUID(uuid)) {
            plugin.getLogger().warn("Invalid UUID format in whitelist: " + uuid);
            return Component.text("§c" + uuid)
                    .clickEvent(ClickEvent.copyToClipboard(uuid))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to copy UUID")))
                    .append(Component.text(" §8(§cINVALID§8)"));
        }
        try {
            URL url = new URL("https://playerdb.co/api/player/minecraft/" + uuid);
            String playerName = getPlayerNameFromAPI(url);
            if (playerName == null) {
                return Component.text("§c" + uuid)
                        .clickEvent(ClickEvent.copyToClipboard(uuid))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to copy UUID")))
                        .append(Component.text(" §8(§cERROR§8)"));
            }
            return Component.text("§7" + playerName);
        } catch (Exception e) {
            plugin.getLogger().error("Failed to get player name for UUID: " + uuid, e);
            return Component.text("§c" + uuid)
                    .clickEvent(ClickEvent.copyToClipboard(uuid))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to copy UUID")))
                    .append(Component.text(" §8(§cERROR§8)"));
        }
    }

    private boolean isValidUUID(String uuid) {
        if (uuid == null) {
            return false;
        }
        return uuid.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
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
        if (responseCode == 400) {
            return null;
        }
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
        CompletableFuture.runAsync(() -> {
            try {
                Component playerNameComponent = getNameFromUUID(uuid);
                var root = plugin.getLoader().load();
                List<String> uuids = new ArrayList<>(root.node("whitelisted-uuids").getList(String.class));
                if (!uuids.contains(uuid)) {
                    uuids.add(uuid);
                    root.node("whitelisted-uuids").set(uuids);
                    plugin.getLoader().save(root);
                    source.sendMessage(Component.text(Main.getPrefix() + "§7Added §a").append(playerNameComponent).append(Component.text(" §7to the vitelist")));
                } else {
                    source.sendMessage(Component.text(Main.getPrefix() + "§a").append(playerNameComponent).append(Component.text(" §7is already on the vitelist")));
                }
            } catch (ConfigurateException e) {
                source.sendMessage(Component.text(Main.getPrefix() + "§cAn error occurred while processing the command: " + e.getMessage()));
            }
        });
    }

    // This method is no longer needed as the logic has been moved into the main execute block.
    // private void removeUuidFromWhitelist(String uuid, CommandSource source) { ... }

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
            return Stream.of("<player/uuid>").collect(Collectors.toList());
        }
        return List.of();
    }

    private List<String> getWhitelistedPlayerNames() {
        try {
            List<String> uuids = getWhitelistedUuids();
            List<String> playerNames = new ArrayList<>();
            for (String uuid : uuids) {
                Component playerComponent = getNameFromUUID(uuid);
                if (playerComponent.clickEvent() == null) {
                    // A bit of a hack to get the plain text from the component for suggestions.
                    // This is not ideal, but it's the simplest way without a serializer.
                    String text = playerComponent.toString();
                    playerNames.add(text.substring(text.indexOf("content=\"") + 9, text.length() - 2));
                }
            }
            return playerNames;
        } catch (ConfigurateException e) {
            e.printStackTrace();
            return List.of();
        }
    }
}