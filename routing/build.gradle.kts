plugins {
    id("my-java-base")
    id("application")
}

group = "com.maxwen"
version = "unspecified"

application {
    mainClass = "com.maxwen.osmviewer.routing.Main"
    applicationDefaultJvmArgs = listOf(
        "-Dosm.db.path=${System.getProperty("user.home")}/Maps/osm/db2",
        "-Djava.library.path=" + file("${projectDir}/../routing-lib/build/lib/main/release").absolutePath,
        "-Xmx8192m"
    )
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.json.simple)
    implementation(libs.sqlite.jdbc)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
}

tasks.build {
    dependsOn(":routing-lib:assembleRelease")
}
