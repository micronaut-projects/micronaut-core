plugins {
    id("java")
}

description = "A simple remapping visitor"

dependencies {
    implementation(projects.micronautInjectJava)
    // Use an enum that shouldn't be present and the runtime to simulate added enum value that doesn't exist
    implementation(libs.blaze.persistence.core)
}
