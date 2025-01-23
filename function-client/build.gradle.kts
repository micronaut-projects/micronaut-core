plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    api(projects.micronautFunction)
    api(projects.micronautHttpClient)

    implementation(libs.managed.reactor)
}
