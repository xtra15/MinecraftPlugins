plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
}

tasks.shadowJar {
    archiveFileName.set("DeathSwap-1.0.0.jar")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
