plugins {
    id("java")
    id("application")
}

group="com.maxwen"
version="unspecified"

application {
    mainClass = "com.maxwen.osmviewer.importer.Importer"
    applicationDefaultJvmArgs = listOf("-Dosm.db.path=${System.getProperty("user.home")}/Maps/osm/db",
        "-Dosm.map.path=${System.getProperty("user.home")}/Downloads/geofabrik",
        "-Xmx8192m")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "com.maxwen.osmviewer.importer.Importer"
    }
}

sourceSets.main {
    java.setSrcDirs(listOf("src/main/java"))
    resources.setSrcDirs(listOf("src/main/resources"))
}

dependencies {
    implementation(project(":shared"))
    testImplementation("junit:junit:4.+")
    implementation(libs.json.simple)
    implementation(libs.sqlite.jdbc)
    implementation(libs.parallelpbf)
}

tasks.register<Copy>("copyImportMapping") {
    from(layout.projectDirectory.dir("config/mapping.json"))
    into(layout.projectDirectory.dir("src/main/resources/com/maxwen/osmviewer/importer"))
}


tasks.named("processResources") { finalizedBy("copyImportMapping") }

tasks.build {
    dependsOn(":routing:build")
}