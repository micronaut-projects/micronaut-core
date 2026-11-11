plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

dependencies {
    testImplementation(libs.spock)
    testImplementation(projects.micronautContext)
    testImplementation(libs.logback.classic)
}
