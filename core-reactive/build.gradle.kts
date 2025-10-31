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
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        }
    }
}
