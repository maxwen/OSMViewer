plugins {
    id("java")
    id("application")
}

group = "com.maxwen"
version = "unspecified"

application {
    mainClass = "com.maxwen.osmviewer.routing.Main"
    applicationDefaultJvmArgs = listOf(
        "-Dosm.db.path=${System.getProperty("user.home")}/Maps/osm/db2",
        "-Djava.library.path=" + file("${projectDir}/../routing-lib/build/lib/main/debug").absolutePath,
        "-Xmx8192m"
    )
}

sourceSets.main {
    java.setSrcDirs(listOf("src/main/java"))
}

dependencies {
    implementation(project(":shared"))
    testImplementation("junit:junit:4.+")
    implementation(libs.json.simple)
    implementation(libs.sqlite.jdbc)
}

tasks.build {
    dependsOn(":routing-lib:build")
}
