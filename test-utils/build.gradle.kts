plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

dependencies {
    api(libs.managed.groovy)
    testImplementation(libs.managed.snakeyaml)
}
