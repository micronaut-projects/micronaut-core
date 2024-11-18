plugins {
    id("io.micronaut.build.internal.convention-library")
    alias(libs.plugins.managed.kotlin.jvm)
}

dependencies {
    api(projects.micronautCore)
    api(libs.managed.reactive.streams)

    compileOnly(libs.managed.reactor)
    compileOnly(libs.managed.kotlinx.coroutines.core)

    testImplementation(libs.managed.reactor)
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "17"
            languageVersion = "1.7"
        }
    }
}
