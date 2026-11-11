plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    api(projects.micronautFunction)
    api(projects.micronautHttpClient)

    implementation(libs.managed.reactor)

    testAnnotationProcessor(projects.micronautInjectJava)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
