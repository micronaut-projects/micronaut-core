plugins {
    id("io.micronaut.build.internal.convention-core-library")
    alias(libs.plugins.managed.kotlin.jvm)
}

micronautBuild {
    core {
        documented = false
    }
}

dependencies {
    api(projects.micronautInject)
    api(projects.micronautCore)
    compileOnly(projects.micronautCoreReactive)
    compileOnly(libs.managed.kotlinx.coroutines.core)
    compileOnly(libs.managed.reactor)
}

tasks {
    compileKotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        }
    }

    test {
        // there are no real tests in this project
        failOnNoDiscoveredTests = false
    }
}
