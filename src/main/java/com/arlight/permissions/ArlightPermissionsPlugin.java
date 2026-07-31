package com.arlight.permissions;

import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class ArlightPermissionsPlugin extends JavaPlugin implements PluginMessageListener, CommandExecutor {
    private LuckPermsService lp;
    private List<PermissionCategory> categories = List.of();
    private AuditLog audit;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        RegisteredServiceProvider<LuckPerms> registration =
                getServer().getServicesManager().getRegistration(LuckPerms.class);
        if (registration == null) {
            getLogger().severe("LuckPerms no está disponible.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        lp = new LuckPermsService(registration.getProvider());
        audit = new AuditLog(this);
        reloadCatalog();

        getServer().getMessenger().registerOutgoingPluginChannel(this, PanelProtocol.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, PanelProtocol.CHANNEL, this);
        Objects.requireNonNull(getCommand("permisos"), "Falta el comando permisos en plugin.yml")
                .setExecutor(this);
        getLogger().info("ArlightPermissions 1.0.3 listo: panel visual + LuckPerms.");
    }

    private void reloadCatalog() {
        reloadConfig();
        categories = new PermissionCatalog(this).load();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este panel se abre dentro del juego.");
            return true;
        }

        if (!player.hasPermission(getConfig().getString(
                "admin-permission", "arlightpermissions.admin"))) {
            player.sendMessage(Component.text("§cNo tienes acceso al panel de permisos."));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadCatalog();
            player.sendMessage(Component.text("§aCatálogo recargado."));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("fallback")) {
            FallbackMenu.open(player, categories);
            return true;
        }
        try {
            open(player);
        } catch (Throwable error) {
            getLogger().log(java.util.logging.Level.SEVERE, "No se pudo abrir /permisos para " + player.getName(), error);
            player.sendMessage(Component.text("§cNo se pudo abrir el panel. Revisa latest.log y usa /permisos fallback."));
        }
        return true;
    }

    private void open(Player player) {
        // En Arclight/NeoForge los canales de CustomPayload no siempre aparecen en
        // Player#getListeningPluginChannels. Enviar directamente es seguro: los clientes
        // sin el mod simplemente ignoran el canal. El respaldo queda en /permisos fallback.
        PanelProtocol.send(this, player, snapshot("OPEN", "", ""));
    }

    private String snapshot(String mode, String selectedType, String selectedName) {
        String cats = categories.stream()
                .map(category -> PanelProtocol.enc(category.id()) + ","
                        + PanelProtocol.enc(category.display()) + ","
                        + PanelProtocol.enc(category.icon()) + ","
                        + PanelProtocol.enc(category.description()) + ","
                        + category.entries().stream()
                                .map(entry -> PanelProtocol.enc(entry.node()) + "~"
                                        + PanelProtocol.enc(entry.name()) + "~"
                                        + PanelProtocol.enc(entry.description()))
                                .collect(Collectors.joining("^")))
                .collect(Collectors.joining(";"));

        String groups = lp.groups().stream()
                .map(PanelProtocol::enc)
                .collect(Collectors.joining(","));

        String online = Bukkit.getOnlinePlayers().stream()
                .map(onlinePlayer -> onlinePlayer.getUniqueId() + ","
                        + PanelProtocol.enc(onlinePlayer.getName()))
                .collect(Collectors.joining(";"));

        return mode + "|" + PanelProtocol.enc(selectedType) + "|"
                + PanelProtocol.enc(selectedName) + "|" + cats + "|" + groups + "|" + online;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(PanelProtocol.CHANNEL)
                || !player.hasPermission(getConfig().getString(
                        "admin-permission", "arlightpermissions.admin"))) {
            return;
        }

        final String raw;
        try {
            raw = PanelProtocol.read(message);
        } catch (Exception ignored) {
            return;
        }

        String[] parts = raw.split("\\|", -1);
        if (parts.length == 0) {
            return;
        }

        switch (parts[0]) {
            case "REFRESH" -> open(player);
            case "APPLY" -> apply(player, parts);
            case "SEARCH" -> search(player, parts);
            default -> { }
        }
    }

    private void search(Player actor, String[] parts) {
        if (parts.length < 2) {
            return;
        }

        final String query = PanelProtocol.dec(parts[1]).trim();
        if (query.isEmpty()) {
            PanelProtocol.send(this, actor,
                    "ERROR|" + PanelProtocol.enc("Escribe un jugador."));
            return;
        }

        lp.user(query).whenComplete((user, error) ->
                Bukkit.getScheduler().runTask(this, () -> {
                    if (error != null) {
                        PanelProtocol.send(this, actor,
                                "ERROR|" + PanelProtocol.enc("No se encontró al jugador " + query));
                        return;
                    }

                    String username = user.getUsername() == null ? query : user.getUsername();
                    PanelProtocol.send(this, actor, snapshot("OPEN", "user", username));
                }));
    }

    private void apply(Player actor, String[] parts) {
        if (parts.length < 7) {
            return;
        }

        final String type = PanelProtocol.dec(parts[1]);
        final String target = PanelProtocol.dec(parts[2]);
        final String node = PanelProtocol.dec(parts[3]);
        final String operation = parts[4];
        final boolean value = Boolean.parseBoolean(parts[5]);

        long parsedSeconds;
        try {
            parsedSeconds = Long.parseLong(parts[6]);
        } catch (NumberFormatException ignored) {
            parsedSeconds = 0L;
        }

        final long seconds = Math.max(0L, parsedSeconds);
        final Duration duration = seconds > 0L ? Duration.ofSeconds(seconds) : null;

        CompletableFuture<Void> future;
        if (type.equals("group")) {
            future = operation.equals("CLEAR")
                    ? lp.clearGroup(target, node)
                    : lp.setGroup(target, node, value, duration);
        } else {
            future = lp.user(target).thenCompose(user -> operation.equals("CLEAR")
                    ? lp.clearUser(user, node)
                    : lp.setUser(user, node, value, duration));
        }

        future.whenComplete((ignored, error) ->
                Bukkit.getScheduler().runTask(this, () -> {
                    if (error != null) {
                        String detail = error.getMessage() == null
                                ? "No se pudo guardar"
                                : error.getMessage();
                        PanelProtocol.send(this, actor,
                                "ERROR|" + PanelProtocol.enc(detail));
                        return;
                    }

                    String action = operation + " " + node + "=" + value
                            + " para " + type + ":" + target
                            + (seconds > 0L ? " durante " + seconds + "s" : "");
                    audit.add(actor.getName(), action);
                    PanelProtocol.send(this, actor,
                            "SUCCESS|" + PanelProtocol.enc("Cambio guardado en LuckPerms"));
                    open(actor);
                }));
    }
}
