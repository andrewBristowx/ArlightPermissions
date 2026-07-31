package com.arlight.permissions;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.util.Tristate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class LuckPermsService {
    private final LuckPerms luckPerms;

    LuckPermsService(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    List<String> groups() {
        return luckPerms.getGroupManager().getLoadedGroups().stream()
                .map(Group::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    CompletableFuture<User> user(UUID uuid) {
        return luckPerms.getUserManager().loadUser(uuid);
    }

    CompletableFuture<User> user(String name) {
        return luckPerms.getUserManager().lookupUniqueId(name)
                .thenCompose(uuid -> uuid == null
                        ? CompletableFuture.failedFuture(
                                new IllegalArgumentException("Jugador no encontrado"))
                        : luckPerms.getUserManager().loadUser(uuid));
    }

    boolean has(User user, String permission) {
        return user.getCachedData().getPermissionData()
                .checkPermission(permission) == Tristate.TRUE;
    }

    CompletableFuture<Void> setUser(
            User user, String permission, boolean value, Duration duration) {
        removePermissionNodes(user.data().toCollection(), permission, user.data()::remove);
        user.data().add(permission(permission, value, duration));
        return luckPerms.getUserManager().saveUser(user);
    }

    CompletableFuture<Void> clearUser(User user, String permission) {
        removePermissionNodes(user.data().toCollection(), permission, user.data()::remove);
        return luckPerms.getUserManager().saveUser(user);
    }

    CompletableFuture<Void> setGroup(
            String groupName, String permission, boolean value, Duration duration) {
        Group group = luckPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Rango no encontrado"));
        }

        removePermissionNodes(group.data().toCollection(), permission, group.data()::remove);
        group.data().add(permission(permission, value, duration));
        return luckPerms.getGroupManager().saveGroup(group);
    }

    CompletableFuture<Void> clearGroup(String groupName, String permission) {
        Group group = luckPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Rango no encontrado"));
        }

        removePermissionNodes(group.data().toCollection(), permission, group.data()::remove);
        return luckPerms.getGroupManager().saveGroup(group);
    }

    private void removePermissionNodes(
            Iterable<Node> source,
            String permission,
            java.util.function.Consumer<Node> remover) {
        String normalized = permission.toLowerCase(Locale.ROOT);
        List<Node> matches = new ArrayList<>();

        for (Node existing : source) {
            if (existing.getKey().toLowerCase(Locale.ROOT).equals(normalized)) {
                matches.add(existing);
            }
        }

        for (Node match : matches) {
            remover.accept(match);
        }
    }

    private Node permission(String key, boolean value, Duration duration) {
        PermissionNode.Builder builder = PermissionNode.builder(key).value(value);
        if (duration != null && !duration.isZero() && !duration.isNegative()) {
            builder.expiry(duration);
        }
        return builder.build();
    }
}
