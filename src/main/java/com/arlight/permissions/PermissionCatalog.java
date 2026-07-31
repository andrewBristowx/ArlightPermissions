package com.arlight.permissions;

import org.bukkit.configuration.ConfigurationSection;import org.bukkit.plugin.java.JavaPlugin;import java.util.*;
record PermissionEntry(String node,String name,String description){}
record PermissionCategory(String id,String display,String icon,String description,List<PermissionEntry> entries){}
final class PermissionCatalog {
 private final JavaPlugin plugin; PermissionCatalog(JavaPlugin p){plugin=p;}
 List<PermissionCategory> load(){List<PermissionCategory> out=new ArrayList<>();ConfigurationSection root=plugin.getConfig().getConfigurationSection("categories");if(root==null)return out;for(String id:root.getKeys(false)){ConfigurationSection c=root.getConfigurationSection(id);if(c==null)continue;List<PermissionEntry> entries=new ArrayList<>();for(Map<?,?> map:c.getMapList("permissions")){entries.add(new PermissionEntry(String.valueOf(map.get("node")),String.valueOf(map.get("name")),String.valueOf(map.get("description"))));}out.add(new PermissionCategory(id,c.getString("display",id),c.getString("icon","•"),c.getString("description",""),List.copyOf(entries)));}return List.copyOf(out);}
}
