plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.0.13"
    id("org.jetbrains.kotlin.jvm") version "1.8.20"
}

group="com.maxwen"
version="unspecified"


javafx {
    version = "17.0.2"
    modules("javafx.controls", "javafx.fxml")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "com.maxwen.osmviewer.Main"
    applicationDefaultJvmArgs = listOf("-Dosm.db.path=${System.getProperty("user.home")}/Maps/osm/db2",
        "-Dosm.tiles.path=${System.getProperty("user.home")}/Maps/osm/tiles",
        "-Dosm.calc_route.path=" + file("${projectDir}/calc_route").absolutePath,
        "-Xmx8192m",
        "-Dprism.verbose=true",
        "-Dprism.forceGPU=true")
}

sourceSets.main {
    java.setSrcDirs(listOf("src/main/java"))
    kotlin.setSrcDirs(listOf("src/main/java"))
    resources.setSrcDirs(listOf("src/main/resources"))
}

dependencies {
    implementation(project(":shared"))
    testImplementation("junit:junit:4.+")
    implementation(libs.json.simple)
    implementation(libs.sqlite.jdbc)
    implementation(libs.jSerialComm)
    implementation(libs.controlsfx)
}

tasks.register<Copy>("copyRoutingLib") {
    from(layout.projectDirectory.dir(file("${projectDir}/../routing-lib/build/lib/main/debug/librouting-lib.so").absolutePath))
    into(layout.projectDirectory.dir(file("${projectDir}/calc_route/routing/lib").absolutePath))

    from(layout.projectDirectory.dir(file("${projectDir}/../routing/build/libs/routing.jar").absolutePath))
    into(layout.projectDirectory.dir(file("${projectDir}/calc_route/routing/lib").absolutePath))
}

tasks.named("processResources") { finalizedBy("copyRoutingLib") }

tasks.build {
    dependsOn(":routing:build")
}