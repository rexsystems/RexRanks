package me.rexsystems.rexranks;

import me.rexsystems.rexranks.api.RexRanks;
import me.rexsystems.rexranks.api.RexRanksProvider;
import me.rexsystems.rexranks.api.event.EventBus;
import me.rexsystems.rexranks.color.ColorWorkshop;
import me.rexsystems.rexranks.command.RexRanksCommand;
import me.rexsystems.rexranks.config.Messages;
import me.rexsystems.rexranks.config.PluginConfig;
import me.rexsystems.rexranks.editor.EditorService;
import me.rexsystems.rexranks.engine.PermissionCalculator;
import me.rexsystems.rexranks.event.SimpleEventBus;
import me.rexsystems.rexranks.hook.PapiHook;
import me.rexsystems.rexranks.hook.VaultHook;
import me.rexsystems.rexranks.listener.PermissibleInjector;
import me.rexsystems.rexranks.listener.PlayerConnectionListener;
import me.rexsystems.rexranks.manager.GroupManagerImpl;
import me.rexsystems.rexranks.manager.TrackManagerImpl;
import me.rexsystems.rexranks.manager.UserManagerImpl;
import me.rexsystems.rexranks.model.GroupImpl;
import me.rexsystems.rexranks.model.TrackImpl;
import me.rexsystems.rexranks.model.UserImpl;
import me.rexsystems.rexranks.service.ContextService;
import me.rexsystems.rexranks.service.KnownNodes;
import me.rexsystems.rexranks.service.UpdateChecker;
import me.rexsystems.rexranks.service.VerboseService;
import me.rexsystems.rexranks.storage.SqlDialect;
import me.rexsystems.rexranks.storage.SqlStorage;
import me.rexsystems.rexranks.storage.Storage;
import me.rexsystems.rexranks.storage.StorageContext;
import me.rexsystems.rexranks.storage.YamlStorage;
import me.rexsystems.rexranks.sync.SyncService;
import me.rexsystems.rexranks.util.Schedulers;
import me.rexsystems.rexranks.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class RexRanksPlugin extends JavaPlugin {
    private PluginConfig config;
    private Messages messages;
    private Schedulers schedulers;
    private SimpleEventBus eventBus;
    private ContextService contextService;
    private VerboseService verbose;
    private PermissionCalculator calculator;
    private final AtomicReference<Storage> storageRef = new AtomicReference<>();
    private StorageContext storageContext;
    private UserManagerImpl userManager;
    private GroupManagerImpl groupManager;
    private TrackManagerImpl trackManager;
    private KnownNodes knownNodes;
    private EditorService editorService;
    private ColorWorkshop colorWorkshop;
    private SyncService syncService;
    private RexRanksApiImpl api;
    private PermissibleInjector injector;
    private PlayerConnectionListener connections;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = new PluginConfig(this);
        this.messages = new Messages(this);
        this.schedulers = new Schedulers(this);
        this.eventBus = new SimpleEventBus(getLogger());
        this.verbose = new VerboseService();
        this.verbose.setMessages(messages);
        this.contextService = new ContextService(config);
        this.contextService.setPlayerLookup(id -> getServer().getPlayer(id));

        this.calculator = new PermissionCalculator(
                name -> {
                    Storage storage = storageRef.get();
                    return storage == null ? Optional.empty() : storage.getGroup(name).map(g -> g);
                },
                config.defaultGroup(),
                true
        );
        this.storageContext = new StorageContext(
                calculator,
                () -> contextService.optionsFor(new java.util.UUID(0, 0)),
                eventBus,
                config.updatePrimaryOnTrack(),
                config.defaultGroup()
        );

        try {
            Storage storage = createStorage(storageContext, getDataFolder().toPath(), config.storageType());
            storageRef.set(storage);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialise storage (" + config.storageType() + ")", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.userManager = new UserManagerImpl(storage(), storageContext, schedulers.async(), getLogger());
        this.groupManager = new GroupManagerImpl(storage(), storageContext, schedulers.async());
        this.trackManager = new TrackManagerImpl(storage(), storageContext, schedulers.async());
        this.knownNodes = new KnownNodes(storage(), config.knownNodes());

        this.api = new RexRanksApiImpl(this);
        RexRanksProvider.register(api);
        getServer().getServicesManager().register(RexRanks.class, api, this, ServicePriority.Normal);

        this.editorService = new EditorService(this);
        this.colorWorkshop = new ColorWorkshop(this);
        this.injector = new PermissibleInjector(this);
        this.connections = new PlayerConnectionListener(this, injector);

        RexRanksCommand command = new RexRanksCommand(this);
        PluginCommand pluginCommand = getCommand("rexranks");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(connections, this);
        getServer().getPluginManager().registerEvents(colorWorkshop, this);
        this.updateChecker = new UpdateChecker(this);
        getServer().getPluginManager().registerEvents(updateChecker, this);
        updateChecker.start();

        for (Player player : getServer().getOnlinePlayers()) {
            userManager.loadUser(player.getUniqueId(), player.getName());
            schedulers.runOnEntity(player, () -> injector.inject(player));
        }

        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            try {
                VaultHook.register(this);
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "Failed to register Vault providers", t);
            }
        }
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                PapiHook.register(this);
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "Failed to register PlaceholderAPI expansion", t);
            }
        }

        editorService.start();
        this.syncService = new SyncService(this);
        syncService.start();

        schedulers.runRepeatingAsync(() -> {
            try {
                connections.expireTemporaryNodes();
            } catch (Exception e) {
                getLogger().log(Level.FINE, "Expiry sweep: " + e.getMessage());
            }
        }, 20 * 60, 20 * 60);

        getLogger().info("RexRanks " + getPluginMeta().getVersion() + " enabled (" + storage().name() + ", "
                + storage().getLoadedGroups().size() + " groups, "
                + storage().getLoadedUsers().size() + " users).");
    }

    @Override
    public void onDisable() {
        try {
            saveAllNow();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to save data on disable", e);
        }
        if (syncService != null) {
            syncService.stop();
        }
        if (editorService != null) {
            editorService.stop();
        }
        Storage storage = storageRef.get();
        if (storage != null) {
            storage.close();
        }
        getServer().getServicesManager().unregisterAll(this);
        RexRanksProvider.unregister();
        eventBus.unregisterAll(this);
        getLogger().info("RexRanks disabled.");
    }

    public Storage createStorage(StorageContext ctx, Path dataFolder, String type) throws Exception {
        return openStorage(ctx, dataFolder, type, true);
    }

    private Storage openStorage(StorageContext ctx, Path dataFolder, String type, boolean load) throws Exception {
        String kind = type == null ? "h2" : type.toLowerCase();
        Storage storage = switch (kind) {
            case "yaml" -> new YamlStorage(dataFolder.resolve("data"), ctx, getLogger());
            case "sqlite", "sqlite3" -> new SqlStorage(
                    SqlDialect.SQLITE,
                    SqlStorage.fileConfig(SqlDialect.SQLITE, dataFolder.resolve("rexranks.db").toAbsolutePath().toString()),
                    ctx, getLogger());
            case "mysql", "mariadb", "maria" -> new SqlStorage(
                    SqlDialect.MYSQL,
                    SqlStorage.mysqlConfig(
                            config.mysqlHost(), config.mysqlPort(), config.mysqlDatabase(),
                            config.mysqlUsername(), config.mysqlPassword(),
                            config.mysqlPoolSize(), config.mysqlProperties()),
                    ctx, getLogger());
            default -> new SqlStorage(
                    SqlDialect.H2,
                    SqlStorage.fileConfig(SqlDialect.H2, dataFolder.resolve("rexranks").toAbsolutePath().toString()),
                    ctx, getLogger());
        };
        storage.init();
        if (load) {
            storage.loadAll();
        }
        return storage;
    }

    public void saveAllNow() {
        Storage storage = storageRef.get();
        if (storage == null) {
            return;
        }
        try {
            for (UserImpl user : storage.getLoadedUsers()) {
                storage.saveUser(user);
            }
            for (GroupImpl group : storage.getLoadedGroups()) {
                storage.saveGroup(group);
            }
            for (TrackImpl track : storage.getLoadedTracks()) {
                storage.saveTrack(track);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to save RexRanks data", e);
        }
    }

    public void invalidateAllCaches() {
        Storage storage = storageRef.get();
        if (storage == null) {
            return;
        }
        storage.getLoadedUsers().forEach(UserImpl::invalidateCache);
        storage.getLoadedGroups().forEach(GroupImpl::invalidateCache);
    }

    public void notifyChange(String reason) {
        invalidateAllCaches();
        if (syncService != null) {
            syncService.push(reason);
        }
    }

    public void reloadAll() {
        reloadConfig();
        config.reload();
        messages.reload();
        verbose.setMessages(messages);
    }

    public void migrateStorage(CommandSender sender, String type) {
        String kind = type.toLowerCase();
        if (!(kind.equals("yaml") || kind.equals("h2") || kind.equals("sqlite"))) {
            sender.sendMessage(Text.parse("<gray>[<gold>RexRanks<gray>] <red>Supported targets: yaml, h2, sqlite."));
            return;
        }
        Path dest = getDataFolder().toPath().resolve("migrate-" + kind);
        sender.sendMessage(Text.parse("<gray>[<gold>RexRanks<gray>] <gray>Copying current data into <white>" + dest + "<gray>…"));
        schedulers.runAsync(() -> {
            try {
                Storage target = openStorage(storageContext, dest, kind, false);
                try {
                    for (GroupImpl group : storage().getLoadedGroups()) {
                        target.saveGroup(group);
                    }
                    for (UserImpl user : storage().getLoadedUsers()) {
                        target.saveUser(user);
                    }
                    for (TrackImpl track : storage().getLoadedTracks()) {
                        target.saveTrack(track);
                    }
                } finally {
                    target.close();
                }
                schedulers.runSync(() -> sender.sendMessage(Text.parse(
                        "<gray>[<gold>RexRanks<gray>] <green>Copied. To switch, set <white>storage.type: " + kind
                                + " <green>in config.yml, move the files from <white>" + dest.getFileName()
                                + " <green>into the plugin folder, and restart.")));
            } catch (Exception e) {
                schedulers.runSync(() -> sender.sendMessage(Text.parse(
                        "<gray>[<gold>RexRanks<gray>] <red>Migration failed: " + e.getMessage())));
                getLogger().log(Level.WARNING, "Migration failed", e);
            }
        });
    }

    public PluginConfig config() { return config; }
    public Messages messages() { return messages; }
    public Schedulers schedulers() { return schedulers; }
    public EventBus eventBus() { return eventBus; }
    public ContextService contextService() { return contextService; }
    public VerboseService verbose() { return verbose; }
    public PermissionCalculator calculator() { return calculator; }
    public Storage storage() { return storageRef.get(); }
    public StorageContext storageContext() { return storageContext; }
    public UserManagerImpl userManager() { return userManager; }
    public GroupManagerImpl groupManager() { return groupManager; }
    public TrackManagerImpl trackManager() { return trackManager; }
    public KnownNodes knownNodes() { return knownNodes; }
    public EditorService editorService() { return editorService; }
    public ColorWorkshop colorWorkshop() { return colorWorkshop; }
    public SyncService syncService() { return syncService; }
}
