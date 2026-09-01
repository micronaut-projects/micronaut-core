plugins {
    id("io.micronaut.build.internal.convention-library")
}

micronautBuild {
    binaryCompatibility {
        enabledAfter("5.2.0")
    }
}

dependencies {
    api(projects.micronautInjectPython)
    api(projects.micronautContext)
    api(projects.micronautContextPython)
    api(libs.managed.groovy)
    api(libs.spock) {
        exclude(module = "groovy-all")
    }
    api(libs.jetbrains.annotations)
    api(libs.jakarta.inject.api)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.micronaut.test.junit5)
    testImplementation(platform(libs.test.boms.micronaut.validation))
    testImplementation(platform(libs.test.boms.micronaut.data))
    testImplementation(platform(libs.test.boms.micronaut.sql))
    testImplementation(platform(libs.test.boms.micronaut.serde))
    testImplementation(libs.managed.reactor)
    testImplementation(projects.micronautInjectJavaHelper)
    testImplementation(projects.micronautRetry)
    testImplementation(libs.micronaut.validation)
    testImplementation(libs.micronaut.validation.processor) {
        exclude(group = "io.micronaut")
    }
    testImplementation("io.micronaut.data:micronaut-data-processor") {
        exclude(group = "io.micronaut")
    }
    testImplementation("io.micronaut.data:micronaut-data-jdbc") {
        exclude(group = "io.micronaut")
    }
    testImplementation("io.micronaut.data:micronaut-data-model") {
        exclude(group = "io.micronaut")
    }
    testImplementation("io.micronaut.serde:micronaut-serde-api") {
        exclude(group = "io.micronaut")
    }
    testImplementation("jakarta.data:jakarta.data-api:1.1.0-M3")
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(projects.micronautHttpClient)
    testImplementation(projects.micronautJacksonDatabind)
}

tasks {
    sourcesJar {
        from("$projectDir/src/main/groovy")
    }
}
