pluginManagement {
    repositories.gradlePluginPortal()
    includeBuild("gradle/plugins")
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

dependencyResolutionManagement {
    repositories.mavenCentral()
}

rootProject.name="OSMViewer"

include("importer")
include("shared")
include("viewer")
include("routing")
include("routing-lib")
