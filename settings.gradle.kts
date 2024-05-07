pluginManagement {
    repositories.gradlePluginPortal()
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
