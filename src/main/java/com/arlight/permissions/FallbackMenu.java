package com.arlight.permissions;
import net.kyori.adventure.text.Component;import org.bukkit.*;import org.bukkit.entity.Player;import org.bukkit.inventory.*;import org.bukkit.inventory.meta.ItemMeta;import java.util.*;
final class FallbackMenu {
 static void open(Player p,List<PermissionCategory> cats){Inventory inv=Bukkit.createInventory(null,54,Component.text("§d✦ Arlight Permissions §8• Respaldo"));int slot=10;for(PermissionCategory c:cats){if(slot>=44)break;ItemStack it=new ItemStack(Material.NETHER_STAR);ItemMeta m=it.getItemMeta();m.displayName(Component.text("§d"+c.icon()+" §f"+c.display()));m.lore(List.of(Component.text("§7"+c.description()),Component.text("§8Instala/actualiza ArlightChatClient para el panel completo.")));it.setItemMeta(m);inv.setItem(slot,it);slot+=(slot%9==7?3:2);}p.openInventory(inv);}
}
