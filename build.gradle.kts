plugins {
    id("org.gradle.java")
    id("application")
    id("org.gradle.maven-publish")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    kotlin("jvm") version "1.8.22"
}

group = "net.momirealms"
version = "1.0"

kotlin {
    jvmToolchain(11)
}

allprojects {

    apply<JavaPlugin>()
    apply(plugin = "java")
    apply(plugin = "application")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "com.github.johnrengelman.shadow")
    apply(plugin = "org.gradle.maven-publish")

    application {
        mainClass.set("")
    }

    repositories {
        maven("https://maven.aliyun.com/repository/public/")
        mavenLocal()
        mavenCentral()
        maven("https://oss.sonatype.org/content/groups/public/")
        maven("https://repo.codemc.org/repository/maven-public/")
        maven("https://jitpack.io")
        maven("https://maven.aliyun.com/repository/public/")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    }

    dependencies {
        testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")
        testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.3")
        testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.3")
    }

    tasks.processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

subprojects {

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    tasks.shadowJar {
        destinationDirectory.set(file("$rootDir/target"))
        archiveClassifier.set("")
        archiveFileName.set("Topaz-" + project.name + "-" + project.version + ".jar")
    }

    if ("api" == project.name) {
        java {
            withSourcesJar()
            withJavadocJar()
        }
    }

    tasks.jar.get().dependsOn("shadowJar")
}

