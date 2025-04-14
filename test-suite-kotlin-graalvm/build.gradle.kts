plugins {
    id("org.graalvm.buildtools.native")
    alias(libs.plugins.managed.kotlin.jvm)
    alias(libs.plugins.managed.ksp)
}

description = "Test suite for Kotlin in native image"

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    testImplementation(libs.managed.kotlin.reflect)
    kspTest(projects.micronautInjectKotlin)
    kspTest(projects.micronautGraal)
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(projects.micronautHttpClient)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(libs.logback.classic)
    testImplementation(libs.micronaut.test.junit5) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.managed.kotlinx.coroutines.core)
    testImplementation(libs.managed.kotlinx.coroutines.jdk8)
    testImplementation(libs.managed.kotlinx.coroutines.reactive)
    testImplementation(libs.managed.kotlinx.coroutines.reactor)

    testImplementation(platform(libs.test.boms.micronaut.kotlin))
    testImplementation(libs.micronaut.kotlin.runtime) {
        exclude(group = "io.micronaut")
    }
}

graalvmNative {
    toolchainDetection = false
    metadataRepository {
        enabled = true
    }
    binaries {
        configureEach {
            resources.autodetect()
        }
    }
}

configurations.testRuntimeClasspath {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(libs.versions.managed.kotlin.asProvider().get())
        }
    }
}
