plugins {
    java
    id("com.gradleup.shadow")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    implementation(project(":core"))
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
}

tasks.shadowJar {
    relocate("dev.ah.core", "dev.ah.auctionhouse.core")
    archiveFileName.set("AuctionHouse-${project.version}.jar")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}