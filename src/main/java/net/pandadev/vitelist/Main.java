package net.pandadev.vitelist;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Path;
import java.util.List;

@Plugin(
        id = "vitelist",
        name = "Main",
        version = "1.1",
        description = "A simple but useful whitelist plugin for Velocity",
        url = "https://pandadev.net",
        authors = {"PandaDEV"}
)
public class Main {

    public static String prefix = "§d§lVitelist §8» ";
    @Inject
    private Logger logger;
    @Inject
    private ProxyServer server;
    @Inject
    @DataDirectory
    private Path dataDirectory;
    private YamlConfigurationLoader loader;
    private boolean whitelistEnabled = true;

    private final Metrics.Factory metricsFactory;

    @Inject
    public Main(Metrics.Factory metricsFactory) {
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loader = YamlConfigurationLoader.builder().path(dataDirectory.resolve("config.yml")).build();
        try {
            var root = loader.load();
            if (!root.node("whitelisted-uuids").virtual()) {
                whitelistEnabled = root.node("whitelist-enabled").getBoolean(true);
            } else {
                root.node("whitelisted-uuids").set(List.of());
                root.node("whitelist-enabled").set(true);
                loader.save(root);
            }
        } catch (ConfigurateException e) {
            logger.error("Failed to load/create config file", e);
        }

        CommandManager commandManager = server.getCommandManager();
        commandManager.register(commandManager.metaBuilder("vlist").build(), new VlistCommand(this));

        int pluginId = 21540;
        metricsFactory.make(this, pluginId);
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        if (!whitelistEnabled) return;
        Player player = event.getPlayer();
        try {
            var root = loader.load();
            List<String> whitelistedUuids = root.node("whitelisted-uuids").getList(String.class);
            if (!whitelistedUuids.contains(player.getUniqueId().toString())) {
                player.disconnect(Component.text("You are not whitelisted on this server."));
            }
        } catch (ConfigurateException e) {
            logger.error("Failed to check whitelist", e);
        }
    }

    public YamlConfigurationLoader getLoader() {
        return loader;
    }

    public void setWhitelistEnabled(boolean whitelistEnabled) {
        this.whitelistEnabled = whitelistEnabled;
    }

    public static String getPrefix() {
        return prefix;
    }
}