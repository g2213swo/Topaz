package net.momirealms.topaz.bukkit

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bukkit.plugin.java.JavaPlugin

class BukkitPluginImpl : JavaPlugin() {

    companion object {
        lateinit var adventure: BukkitAudiences
    }

    @Override
    override fun onLoad() {

    }

    @Override
    override fun onEnable() {
        adventure = BukkitAudiences.create(this)
    }

    @Override
    override fun onDisable() {
        adventure.close()
    }
}