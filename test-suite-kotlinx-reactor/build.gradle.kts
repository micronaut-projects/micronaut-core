plugins {
    id("io.micronaut.build.internal.convention-test-library")
    id("io.micronaut.build.internal.kotlin-base")
    alias(libs.plugins.managed.kotlin.jvm)
    alias(libs.plugins.managed.ksp)
}

micronautBuild {
    core {
        usesMicronautTestJunit()
    }
}

// This suite checks the Kotlin suspend route support without kotlinx-coroutines-reactor on the classpath
configurations.all {
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-reactor")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-reactive")
}

dependencies {
    testImplementation(libs.managed.kotlin.stdlib)
    testImplementation(libs.managed.kotlin.reflect)
    testImplementation(libs.managed.kotlinx.coroutines.core)
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(projects.micronautHttpClient)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(libs.managed.kotlin.test)
    testImplementation(libs.junit.jupiter.api)
    kspTest(projects.micronautInjectKotlin)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

configurations.testRuntimeClasspath {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(libs.versions.managed.kotlin.asProvider().get())
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}
