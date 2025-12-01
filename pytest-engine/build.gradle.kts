plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

dependencies {
    implementation(projects.micronautCore)
    implementation(projects.micronautContextPython)
    implementation(libs.junit.platform.engine)
    implementation(libs.junit.platform.launcher)
    implementation(libs.managed.graalpy) {
        artifact {
            type = "pom"
        }
    }
    implementation(libs.managed.graalpy.embedding)
    compileOnly(libs.jetbrains.annotations)

    testImplementation(libs.junit.platform.engine)
    testImplementation(libs.junit.platform.testkit)
    testImplementation(projects.micronautInjectPython)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
