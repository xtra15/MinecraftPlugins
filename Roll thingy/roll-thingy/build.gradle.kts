plugins {
    java
    id("com.gradleup.shadow")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

dependencies {
    implementation(project(":roll-core"))
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
}

tasks.shadowJar {
    relocate("dev.rollthingy.core", "dev.rollthingy.shaded.core")
    archiveFileName.set("RollThingy-${project.version}.jar")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}