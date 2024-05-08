plugins {
    id("my-java-base")
    id("application")
    id("org.openjfx.javafxplugin") version "0.0.13"
    alias(libs.plugins.jvm)
}

group = "com.maxwen"
version = "unspecified"


javafx {
    version = "17.0.2"
    modules("javafx.controls", "javafx.fxml")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "com.maxwen.osmviewer.Main"
    applicationDefaultJvmArgs = listOf(
        "-Dosm.db.path=${System.getProperty("user.home")}/Maps/osm/db2",
        "-Dosm.tiles.path=${System.getProperty("user.home")}/Maps/osm/tiles",
        "-Dosm.calc_route.path=" + file("${projectDir}/calc_route").absolutePath,
        "-Xmx8192m",
        "-Dprism.verbose=true",
        "-Dprism.forceGPU=true"
    )
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":routing"))
    implementation(libs.json.simple)
    implementation(libs.sqlite.jdbc)
    implementation(libs.jSerialComm)
    implementation(libs.controlsfx)
}

tasks.register<Copy>("copyRoutingLib") {
    from(layout.projectDirectory.dir(file("${projectDir}/../routing-lib/build/lib/main/release/librouting-lib.so").absolutePath))
    into(layout.projectDirectory.dir(file("${projectDir}/calc_route/routing/lib").absolutePath))

    from(configurations.runtimeClasspath)
    into(layout.projectDirectory.dir(file("${projectDir}/calc_route/routing/lib").absolutePath))
}

tasks.register<Copy>("copyRoutingBin") {
    from(layout.projectDirectory.dir(file("${projectDir}/../routing/build/scripts/routing").absolutePath))
    into(layout.projectDirectory.dir(file("${projectDir}/calc_route/routing/bin").absolutePath))
}

tasks.named("processResources") {
    finalizedBy("copyRoutingLib")
}
tasks.named("copyRoutingLib") {
    dependsOn(":routing-lib:linkRelease")
    finalizedBy("copyRoutingBin")
}

tasks.named("copyRoutingBin") {
    dependsOn(":routing:startScripts")
}