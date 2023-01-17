plugins {
    id("io.micronaut.build.internal.convention-library")
}
dependencies {
    annotationProcessor(projects.injectJava)
    annotationProcessor(projects.validation)
    annotationProcessor(projects.httpValidation)
    implementation(projects.validation)
    implementation(projects.runtime)
    implementation(projects.jacksonDatabind)
    implementation(projects.inject)
    api(projects.httpServer)
    api(projects.httpClientCore)
    api(libs.junit.jupiter.api)
    api(libs.junit.jupiter.params)
    api(libs.managed.reactor)
}
