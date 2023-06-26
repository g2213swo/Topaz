dependencies {

    implementation(project(":common"))
    implementation(project(":api"))

    implementation(kotlin("stdlib"))

    compileOnly(fileTree("libs"))

    compileOnly("me.clip:placeholderapi:2.11.3")
    compileOnly ("io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")

    implementation("net.kyori:adventure-api:4.14.0")
    implementation("net.kyori:adventure-platform-bukkit:4.3.0")
    implementation("net.kyori:adventure-text-minimessage:4.14.0")
    implementation("net.kyori:adventure-text-serializer-legacy:4.14.0")
    implementation("de.tr7zw:item-nbt-api:2.11.3")
    implementation("org.bstats:bstats-bukkit:3.0.1")
}

tasks {
    shadowJar {
        relocate ("org.bstats", "net.momirealms.topaz.libraries.bstats")
        relocate ("net.kyori", "net.momirealms.topaz.libraries.kyori")
        relocate ("de.tr7zw", "net.momirealms.topaz.libraries.tr7zw")
    }
}
