plugins {
    id("io.micronaut.build.internal.convention-library")
}
dependencies {
    annotationProcessor(projects.injectJava)
    annotationProcessor(projects.httpValidation)
    implementation(projects.runtime)
    implementation(projects.jacksonDatabind)
    implementation(projects.inject)

    implementation(libs.validation.api)

    api(projects.httpTck)
    api(projects.httpServer)
    api(projects.httpClientCore)
    api(libs.junit.jupiter.api)
    api(libs.junit.jupiter.params)
    api(libs.managed.reactor)
}
