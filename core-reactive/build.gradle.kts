plugins {
    id("io.micronaut.build.internal.convention-library")
    id("io.micronaut.build.internal.kotlin-base")
    alias(libs.plugins.managed.kotlin.jvm)
}

dependencies {
    api(projects.micronautCore)
    api(libs.managed.reactive.streams)

    compileOnly(libs.managed.reactor)
    compileOnly(libs.managed.kotlinx.coroutines.core)

    testImplementation(libs.managed.reactor)
}
