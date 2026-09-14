plugins {
    id("com.gradleup.shadow") version "9.6.1" apply false
}

subprojects {
    group = "dev.rollthingy"
    version = providers.gradleProperty("pluginVersion").get()
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}