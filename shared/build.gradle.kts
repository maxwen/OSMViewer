plugins {
    id("my-java-library")
    alias(libs.plugins.jvm)
}

dependencies {
    implementation(libs.json.simple)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
}

kotlin {
    jvmToolchain(17)
}