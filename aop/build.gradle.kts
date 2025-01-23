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
        kotlinOptions.jvmTarget = "17"
        kotlinOptions.languageVersion = "1.7"
    }
}
