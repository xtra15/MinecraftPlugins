plugins {
    id("com.gradleup.shadow") version "9.6.1" apply false
}

subprojects {
    group = "dev.ah"
    version = providers.gradleProperty("pluginVersion").get()
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}