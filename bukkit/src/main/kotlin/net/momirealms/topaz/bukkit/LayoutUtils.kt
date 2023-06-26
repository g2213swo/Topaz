package net.momirealms.topaz.bukkit

import net.momirealms.topaz.bukkit.inv.InventoryType

object LayoutUtils {

    @JvmStatic
    fun getInventoryProperties(
        layout : List<String>
    ) : InventoryType {
        if (layout.isEmpty()) {
            return InventoryType.UNKNOWN
        }
        return when (layout[0].length) {
            3 -> InventoryType.DISPENSER
            5 -> InventoryType.HOPPER
            9 -> InventoryType.CHEST
            else -> InventoryType.UNKNOWN
        }
    }
}